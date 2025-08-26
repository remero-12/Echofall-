import static org.lwjgl.opengl.GL11.*;

public class GameWorld {
    private final int TILE_SIZE = 8;
    private Player player;
    private long seed;
    private float cameraX = 0f;
    private final int SEA_LEVEL = 30; // tile Y for water filling
    private final Inventory inventory = new Inventory(9);
    private final CraftingSystem craftingSystem = new CraftingSystem();
    private float zoom = 2.0f; // world zoom ( >1.0 zooms in )
    // Mining state
    private java.util.HashMap<Long, Integer> overrides = new java.util.HashMap<>(); // key=(tx<<32)|ty -> tile id
    private java.util.ArrayList<Drop> drops = new java.util.ArrayList<>();
    // reserved for future rate limiting
    // private long lastMineNs = 0L;
    // private long mineCooldownNs = 150_000_000L; // 150ms
    private int miningTx = Integer.MIN_VALUE;
    private int miningTy = Integer.MIN_VALUE;
    private float miningProgress = 0f; // 0..1
    // private float miningSpeed = 1.0f; // scaled by tool
    private boolean showCrafting = false;
    private int selectedRecipeIndex = 0;

    public GameWorld(int width, int height, long seed) {
        this.seed = seed;
        int spawnTileX = width / TILE_SIZE / 2;
        int spawnY = (groundTileY(spawnTileX) - 1) * TILE_SIZE - (int)(2.0f * TILE_SIZE);
        if (spawnY < 0) spawnY = 0;
        player = new Player(spawnTileX * TILE_SIZE, spawnY, TILE_SIZE * 0.9f, TILE_SIZE * 1.8f);

        // Seed starter tools
        inventory.set(0, new Item("Wood Pickaxe", ToolType.PICKAXE));
        inventory.set(1, new Item("Wood Axe", ToolType.AXE));
        inventory.set(2, new Item("Wood Shovel", ToolType.SHOVEL));
    }

    public void setHotbarSelected(int index) {
        inventory.setSelectedIndex(index);
    }

    // Deterministic pseudo-random based on index and seed
    private float rand01(long s, int i) {
        long x = s ^ (i * 0x9E3779B97F4A7C15L);
        x ^= (x >>> 30);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 27);
        x *= 0x94D049BB133111EBL;
        x ^= (x >>> 31);
        // Convert to [0,1)
        return ((x >>> 11) & 0xFFFFFFFFFFFFFL) / (float)(1L << 53);
    }

    // Height of ground in tile coords at a given tile X
    private int groundTileY(int tileX) {
        // Base band around midline
        float base = 10f; // nominal sky tiles above 0; actual screen height provided at render
        int step = 8; // distance between control points
        int i0 = (int) Math.floor(tileX / (float) step);
        int i1 = i0 + 1;
        float t = (tileX / (float) step) - i0;
        float f = t * t * (3f - 2f * t);
        float h0 = base + 10f + (rand01(seed, i0) - 0.5f) * 6f;
        float h1 = base + 10f + (rand01(seed, i1) - 0.5f) * 6f;
        float h = h0 + (h1 - h0) * f;
        // Add a finer octave
        int step2 = 4;
        int j0 = (int) Math.floor(tileX / (float) step2);
        int j1 = j0 + 1;
        float t2 = (tileX / (float) step2) - j0;
        float f2 = t2 * t2 * (3f - 2f * t2);
        float n0 = (rand01(seed + 1337, j0) - 0.5f) * 2f;
        float n1 = (rand01(seed + 1337, j1) - 0.5f) * 2f;
        h += n0 + (n1 - n0) * f2;
        return Math.max(1, Math.round(h));
    }

    // 2D value noise for caves
    private float noise2D(float x, float y, float scale, long salt) {
        float sx = x * scale;
        float sy = y * scale;
        int x0 = (int) Math.floor(sx);
        int y0 = (int) Math.floor(sy);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        float tx = sx - x0;
        float ty = sy - y0;
        float fx = tx * tx * (3f - 2f * tx);
        float fy = ty * ty * (3f - 2f * ty);
        float v00 = rand01(seed + salt, (x0 * 7349) ^ (y0 * 9157));
        float v10 = rand01(seed + salt, (x1 * 7349) ^ (y0 * 9157));
        float v01 = rand01(seed + salt, (x0 * 7349) ^ (y1 * 9157));
        float v11 = rand01(seed + salt, (x1 * 7349) ^ (y1 * 9157));
        float ix0 = v00 + (v10 - v00) * fx;
        float ix1 = v01 + (v11 - v01) * fx;
        return ix0 + (ix1 - ix0) * fy;
    }

    private boolean treeSpawnAt(int tx) {
        return rand01(seed + 7777L, tx) < 0.07f;
    }

    private int treeHeightAt(int tx) {
        return 3 + (int) (rand01(seed + 991L, tx) * 4f);
    }

    private boolean isTreeTrunkAt(int tx, int ty) {
        if (!treeSpawnAt(tx)) return false;
        int surface = groundTileY(tx) - 1;
        int h = treeHeightAt(tx);
        return ty <= surface && ty >= surface - (h - 1);
    }

    private boolean isLeafAt(int tx, int ty) {
        if (!treeSpawnAt(tx)) return false;
        int surface = groundTileY(tx) - 1;
        int h = treeHeightAt(tx);
        int topY = surface - (h - 1);
        int dy = ty - (topY - 1);
        int radius = 2;
        // diamond-ish canopy
        return dy >= -radius && dy <= radius && Math.abs(tx - tx) <= radius && ty < surface + 1 && ty <= surface + 1 && ty <= topY + radius && ty >= topY - radius && (Math.abs(ty - topY) + 0) <= radius;
    }

    // Tile definition: 0 air, 1 dirt, 2 water, 3 trunk, 4 leaves, 5 grass, 6 stone
    private int tileAt(int tx, int ty) {
        int ground = groundTileY(tx);
        // Trees (above ground)
        if (isTreeTrunkAt(tx, ty)) return 3;
        if (isLeafAt(tx, ty)) return 4;

        if (ty >= ground) {
            int depth = ty - ground;
            // Guarantee top 3 layers: grass then 2 dirt layers (no caves)
            if (depth == 0) return 5; // grass
            if (depth == 1 || depth == 2) return 1; // dirt

            // Base material: stone becomes common deeper underground
            // Large stone blobs using low-frequency noise
            float stoneBlob = 0.7f * noise2D(tx, ty, 0.06f, 555) + 0.3f * noise2D(tx, ty, 0.10f, 556);
            boolean deep = depth > 10 || ty > SEA_LEVEL + 8;
            boolean isStone = deep && stoneBlob > 0.55f;

            // Deeper layers can have caves (smaller, sparser) carving out both dirt and stone
            float cave = 0.6f * noise2D(tx, ty, 0.20f, 101) + 0.4f * noise2D(tx, ty, 0.40f, 202);
            if (cave < 0.18f) return 0; // cave air

            return isStone ? 6 : 1;
        } else {
            // Above ground: water at and below sea level
            if (ty >= SEA_LEVEL) return 2;
            return 0;
        }
    }

    // kept for reference; use isSolidResolved so mined tiles become non-solid
    // private boolean isSolidTile(int tx, int ty) {
    //     int t = tileAt(tx, ty);
    //     return t == 1 || t == 3 || t == 5 || t == 6; // dirt, trunk, grass, stone
    // }

    private int resolvedTileAt(int tx, int ty) {
        long key = (((long) tx) << 32) ^ (ty & 0xffffffffL);
        Integer v = overrides.get(key);
        if (v != null) return v;
        return tileAt(tx, ty);
    }

    public void update(float dt, boolean left, boolean right, boolean jump) {
        float speed = 9.0f * TILE_SIZE; // px/s
        float gravity = 55.0f * TILE_SIZE; // px/s^2
        float jumpVelocity = -19.0f * TILE_SIZE;

        player.velocityX = 0f;
        if (left) player.velocityX -= speed;
        if (right) player.velocityX += speed;

        if (jump && player.onGround) {
            player.velocityY = jumpVelocity;
            player.onGround = false;
        }

        // Integrate with per-axis collision against solid tiles
        player.velocityY += gravity * dt;

        // Horizontal move and collide
        float nextX = player.x + player.velocityX * dt;
        if (player.velocityX != 0f) {
            float minX = Math.min(player.x, nextX);
            float maxX = Math.max(player.x, nextX) + player.width - 0.001f;
            int tileY0 = (int) Math.floor(player.y / TILE_SIZE);
            int tileY1 = (int) Math.floor((player.y + player.height - 0.001f) / TILE_SIZE);
            if (player.velocityX > 0) {
                int tileX = (int) Math.floor(maxX / TILE_SIZE);
                for (int ty = tileY0; ty <= tileY1; ty++) {
                    if (isSolidResolved(tileX, ty)) {
                        nextX = tileX * TILE_SIZE - player.width;
                        player.velocityX = 0f;
                        break;
                    }
                }
            } else {
                int tileX = (int) Math.floor(minX / TILE_SIZE);
                for (int ty = tileY0; ty <= tileY1; ty++) {
                    if (isSolidResolved(tileX, ty)) {
                        nextX = (tileX + 1) * TILE_SIZE;
                        player.velocityX = 0f;
                        break;
                    }
                }
            }
        }
        player.x = nextX;

        // Vertical move and collide
        float nextY = player.y + player.velocityY * dt;
        player.onGround = false;
        if (player.velocityY != 0f) {
            float minY = Math.min(player.y, nextY);
            float maxY = Math.max(player.y, nextY) + player.height - 0.001f;
            int tileX0 = (int) Math.floor(player.x / TILE_SIZE);
            int tileX1 = (int) Math.floor((player.x + player.width - 0.001f) / TILE_SIZE);
            if (player.velocityY > 0) { // moving down
                int tileY = (int) Math.floor(maxY / TILE_SIZE);
                for (int tx = tileX0; tx <= tileX1; tx++) {
                    if (isSolidResolved(tx, tileY)) {
                        nextY = tileY * TILE_SIZE - player.height;
                        player.velocityY = 0f;
                        player.onGround = true;
                        break;
                    }
                }
            } else { // moving up
                int tileY = (int) Math.floor(minY / TILE_SIZE);
                for (int tx = tileX0; tx <= tileX1; tx++) {
                    if (isSolidResolved(tx, tileY)) {
                        nextY = (tileY + 1) * TILE_SIZE;
                        player.velocityY = 0f;
                        break;
                    }
                }
            }
        }
        player.y = nextY;

        // Camera follows is handled in render with zoom
    }

    public void updateWithInput(float dt, boolean left, boolean right, boolean jump,
                                double mouseX, double mouseY, int screenW, int screenH, boolean mining, boolean placing) {
        update(dt, left, right, jump);
        updateDrops(dt);
        handleMining(dt, mouseX, mouseY, screenW, screenH, mining);
        handlePlacing(mouseX, mouseY, screenW, screenH, placing);
    }

    private void handleMining(float dt, double mouseX, double mouseY, int screenW, int screenH, boolean miningHeld) {
        // Convert screen to world using zoom and cameraX
        float worldX = (float) mouseX / zoom + cameraX;
        float worldY = (float) mouseY / zoom;

        int tx = (int) Math.floor(worldX / TILE_SIZE);
        int ty = (int) Math.floor(worldY / TILE_SIZE);

        // Range check from player center
        float px = player.x + player.width * 0.5f;
        float py = player.y + player.height * 0.5f;
        float dx = worldX - px;
        float dy = worldY - py;
        float range = 5 * TILE_SIZE;
        boolean inRange = (dx * dx + dy * dy) <= range * range;

        int t = resolvedTileAt(tx, ty);
        boolean targetable = !(t == 0 || t == 2 || t == 4);

        if (!miningHeld || !inRange || !targetable) {
            miningProgress = 0f;
            miningTx = Integer.MIN_VALUE;
            miningTy = Integer.MIN_VALUE;
            return;
        }

        // Tool gating and speed
        Item sel = inventory.getSelected();
        ToolType tool = sel == null ? ToolType.NONE : sel.toolType;
        float speed = 1.0f;
        if (t == 6) { // stone
            if (tool != ToolType.PICKAXE) { miningProgress = 0f; return; }
            speed = 1.0f;
        } else if (t == 3) { // trunk
            if (tool != ToolType.AXE) { miningProgress = 0f; return; }
            speed = 1.2f;
        } else if (t == 1 || t == 5) { // dirt/grass
            if (tool == ToolType.SHOVEL) speed = 1.2f; else speed = 0.9f;
        }

        // Track target tile; reset progress if changed
        if (tx != miningTx || ty != miningTy) {
            miningTx = tx;
            miningTy = ty;
            miningProgress = 0f;
        }

        miningProgress += speed * dt; // seconds to break ~1s
        if (miningProgress >= 1.0f) {
            long key = (((long) tx) << 32) ^ (ty & 0xffffffffL);
            int brokenTile = t; // use resolved tile
            overrides.put(key, 0);
            // Spawn drop at tile center
            float cx = tx * TILE_SIZE + TILE_SIZE * 0.5f;
            float cy = ty * TILE_SIZE + TILE_SIZE * 0.5f;
            // Map some tiles to drop ids (grass drops dirt)
            int dropId = (brokenTile == 5) ? 1 : brokenTile;
            drops.add(new Drop(cx, cy, dropId));
            miningProgress = 0f;
            miningTx = Integer.MIN_VALUE;
            miningTy = Integer.MIN_VALUE;
        }
    }

    private void handlePlacing(double mouseX, double mouseY, int screenW, int screenH, boolean placingHeld) {
        if (!placingHeld) return;
        Item sel = inventory.getSelected();
        if (sel == null || !sel.isBlock()) return;

        float worldX = (float) mouseX / zoom + cameraX;
        float worldY = (float) mouseY / zoom;
        int tx = (int) Math.floor(worldX / TILE_SIZE);
        int ty = (int) Math.floor(worldY / TILE_SIZE);

        // Can't place inside player
        float px0 = player.x;
        float py0 = player.y;
        float px1 = player.x + player.width;
        float py1 = player.y + player.height;
        float bx0 = tx * TILE_SIZE;
        float by0 = ty * TILE_SIZE;
        float bx1 = bx0 + TILE_SIZE;
        float by1 = by0 + TILE_SIZE;
        boolean overlapsPlayer = !(bx1 <= px0 || bx0 >= px1 || by1 <= py0 || by0 >= py1);
        if (overlapsPlayer) return;

        int current = resolvedTileAt(tx, ty);
        if (current != 0 && current != 2) return; // place only in air/water

        long key = (((long) tx) << 32) ^ (ty & 0xffffffffL);
        overrides.put(key, sel.blockId);
        inventory.consumeSelectedBlockOne();
    }

    private boolean isSolidResolved(int tx, int ty) {
        int t = resolvedTileAt(tx, ty);
        return t == 1 || t == 3 || t == 5 || t == 6 || t == 7 || t == 8; // Added planks and bricks
    }

    public void render(int width, int height) {
        glViewport(0, 0, width, height);

        // World projection with zoom
        float viewWorldWidth = width / zoom;
        float viewWorldHeight = height / zoom;

        // Camera centers on player horizontally in world units
        cameraX = (player.x + player.width * 0.5f) - (viewWorldWidth * 0.5f);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, viewWorldWidth, viewWorldHeight, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glClearColor(0.5f, 0.75f, 1.0f, 1.0f); // sky blue
        glClear(GL_COLOR_BUFFER_BIT);

        int startTileX = (int) Math.floor(cameraX / TILE_SIZE) - 1;
        int endTileX = (int) Math.floor((cameraX + viewWorldWidth) / TILE_SIZE) + 1;
        int startTileY = 0;
        int endTileY = (int) Math.floor((viewWorldHeight) / TILE_SIZE) + 3;
        for (int tx = startTileX; tx <= endTileX; tx++) {
            for (int ty = startTileY; ty <= endTileY; ty++) {
                int tile = resolvedTileAt(tx, ty);
                if (tile == 0) continue;
                float px = tx * TILE_SIZE - cameraX;
                float py = ty * TILE_SIZE;
                if (tile == 1) glColor3f(0.4f, 0.2f, 0.1f); // dirt
                else if (tile == 2) glColor4f(0.2f, 0.5f, 0.9f, 0.8f); // water
                else if (tile == 3) glColor3f(0.5f, 0.35f, 0.2f); // trunk
                else if (tile == 4) glColor3f(0.2f, 0.8f, 0.2f); // leaves
                else if (tile == 5) glColor3f(0.15f, 0.75f, 0.25f); // grass top
                else if (tile == 6) glColor3f(0.55f, 0.55f, 0.6f); // stone
                else if (tile == 7) glColor3f(0.6f, 0.4f, 0.2f); // wood planks
                else if (tile == 8) glColor3f(0.7f, 0.7f, 0.75f); // stone bricks
                glBegin(GL_QUADS);
                glVertex2f(px, py);
                glVertex2f(px + TILE_SIZE, py);
                glVertex2f(px + TILE_SIZE, py + TILE_SIZE);
                glVertex2f(px, py + TILE_SIZE);
                glEnd();
            }
        }

        // Draw breaking overlay
        if (miningTx != Integer.MIN_VALUE) {
            float px = miningTx * TILE_SIZE - cameraX;
            float py = miningTy * TILE_SIZE;
            float p = Math.max(0f, Math.min(1f, miningProgress));
            // simple cracks: draw concentric lines increasing with progress
            glColor3f(1f, 1f - p, 1f - p);
            glBegin(GL_LINE_LOOP);
            glVertex2f(px + 1, py + 1);
            glVertex2f(px + TILE_SIZE - 1, py + 1);
            glVertex2f(px + TILE_SIZE - 1, py + TILE_SIZE - 1);
            glVertex2f(px + 1, py + TILE_SIZE - 1);
            glEnd();
            if (p > 0.33f) {
                glBegin(GL_LINES);
                glVertex2f(px + 2, py + 2); glVertex2f(px + TILE_SIZE - 2, py + TILE_SIZE - 2);
                glVertex2f(px + TILE_SIZE - 2, py + 2); glVertex2f(px + 2, py + TILE_SIZE - 2);
                glEnd();
            }
            if (p > 0.66f) {
                glBegin(GL_LINES);
                glVertex2f(px + TILE_SIZE / 2f, py + 2); glVertex2f(px + TILE_SIZE / 2f, py + TILE_SIZE - 2);
                glVertex2f(px + 2, py + TILE_SIZE / 2f); glVertex2f(px + TILE_SIZE - 2, py + TILE_SIZE / 2f);
                glEnd();
            }
        }

        // Draw player
        glColor3f(1.0f, 1.0f, 0.2f);
        glBegin(GL_QUADS);
        glVertex2f(player.x - cameraX, player.y);
        glVertex2f(player.x + player.width - cameraX, player.y);
        glVertex2f(player.x + player.width - cameraX, player.y + player.height);
        glVertex2f(player.x - cameraX, player.y + player.height);
        glEnd();

        // Switch to screen-space projection for UI so it doesn't get zoomed
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Render drops in world space
        renderDropsWorld();

        // UI: hotbar (screen space)
        drawHotbar(width, height);
    }

    private void updateDrops(float dt) {
        float gravity = 55.0f * TILE_SIZE;
        for (int i = 0; i < drops.size(); i++) {
            Drop d = drops.get(i);
            if (d.picked) continue;
            d.vy += gravity * dt;
            d.x += d.vx * dt;
            d.y += d.vy * dt;
            int tx = (int) Math.floor(d.x / TILE_SIZE);
            int ty = (int) Math.floor((d.y) / TILE_SIZE);
            if (isSolidResolved(tx, ty)) {
                d.y = ty * TILE_SIZE - 1;
                d.vy = 0;
                d.vx *= 0.7f;
            }
            float px = player.x + player.width * 0.5f;
            float py = player.y + player.height * 0.5f;
            float dx = d.x - px;
            float dy = d.y - py;
            float r = 2.5f * TILE_SIZE;
            if (dx * dx + dy * dy < r * r) {
                d.picked = true;
                // Add to inventory
                if (d.tileId == 1) inventory.addBlock(1, "Dirt");
                else if (d.tileId == 6) inventory.addBlock(6, "Stone");
                else if (d.tileId == 3) inventory.addBlock(3, "Wood");
                else if (d.tileId == 5) inventory.addBlock(1, "Dirt");
            }
        }
        for (int i = drops.size() - 1; i >= 0; i--) if (drops.get(i).picked) drops.remove(i);
    }

    private void renderDropsWorld() {
        for (int i = 0; i < drops.size(); i++) {
            Drop d = drops.get(i);
            if (d.picked) continue;
            float sx = d.x - cameraX - TILE_SIZE * 0.4f;
            float sy = d.y - TILE_SIZE * 0.4f;
            if (d.tileId == 6) glColor3f(0.55f, 0.55f, 0.6f);
            else if (d.tileId == 1) glColor3f(0.4f, 0.2f, 0.1f);
            else glColor3f(0.8f, 0.8f, 0.8f);
            float s = TILE_SIZE * 0.8f;
            glBegin(GL_QUADS);
            glVertex2f(sx, sy);
            glVertex2f(sx + s, sy);
            glVertex2f(sx + s, sy + s);
            glVertex2f(sx, sy + s);
            glEnd();
        }
    }

    private void drawHotbar(int width, int height) {
        int slotSize = TILE_SIZE * 4; // bigger UI
        int padding = 4;
        int totalWidth = inventory.size() * (slotSize + padding) - padding;
        int startX = (width - totalWidth) / 2;
        int y = height - slotSize - 8;

        for (int i = 0; i < inventory.size(); i++) {
            int x = startX + i * (slotSize + padding);
            boolean selected = i == inventory.getSelectedIndex();
            if (selected) glColor3f(1f, 1f, 1f); else glColor3f(0.8f, 0.8f, 0.8f);
            glLineWidth(2f);
            glBegin(GL_LINE_LOOP);
            glVertex2f(x, y);
            glVertex2f(x + slotSize, y);
            glVertex2f(x + slotSize, y + slotSize);
            glVertex2f(x, y + slotSize);
            glEnd();

            Item item = inventory.get(i);
            if (item != null) {
                int mx = x + slotSize / 4;
                int my = y + slotSize / 4;
                int mw = slotSize / 2;
                int mh = slotSize / 2;
                // Render textured-style icon: outline + fill of material color
                if (item.isBlock()) {
                    if (item.blockId == 1) glColor3f(0.4f, 0.2f, 0.1f);
                    else if (item.blockId == 6) glColor3f(0.55f, 0.55f, 0.6f);
                    else if (item.blockId == 3) glColor3f(0.5f, 0.35f, 0.2f);
                    else glColor3f(0.8f, 0.8f, 0.8f);
                    glBegin(GL_QUADS);
                    glVertex2f(mx, my);
                    glVertex2f(mx + mw, my);
                    glVertex2f(mx + mw, my + mh);
                    glVertex2f(mx, my + mh);
                    glEnd();
                    glColor3f(0, 0, 0);
                    glBegin(GL_LINE_LOOP);
                    glVertex2f(mx, my);
                    glVertex2f(mx + mw, my);
                    glVertex2f(mx + mw, my + mh);
                    glVertex2f(mx, my + mh);
                    glEnd();
                    // draw count bottom-right
                    if (item.count > 1) {
                        int numX = x + slotSize - (3 * (slotSize / 12)) - 6;
                        int numY = y + slotSize - (5 * (slotSize / 12)) - 6;
                        int scale = Math.max(2, slotSize / 12);
                        glColor3f(1f, 1f, 1f);
                        drawNumber(item.count, numX, numY, scale);
                    }
                } else {
                    if (item.toolType == ToolType.PICKAXE) glColor3f(0.8f, 0.7f, 0.2f);
                    else if (item.toolType == ToolType.AXE) glColor3f(0.7f, 0.5f, 0.2f);
                    else if (item.toolType == ToolType.SHOVEL) glColor3f(0.6f, 0.6f, 0.6f);
                    else glColor3f(0.9f, 0.9f, 0.9f);
                    glBegin(GL_QUADS);
                    glVertex2f(mx, my);
                    glVertex2f(mx + mw, my);
                    glVertex2f(mx + mw, my + mh);
                    glVertex2f(mx, my + mh);
                    glEnd();
                }
            }
        }
    }

    public void renderFullInventory(int width, int height) {
        int slotSize = TILE_SIZE * 4;
        int padding = 4;
        int cols = 9;
        int rows = 3;
        int gridW = cols * (slotSize + padding) - padding;
        int gridH = rows * (slotSize + padding) - padding;
        int startX = (width - gridW) / 2;
        int startY = (height - gridH) / 2;

        // Background panel
        glColor4f(0f, 0f, 0f, 0.5f);
        glBegin(GL_QUADS);
        glVertex2f(startX - 8, startY - 8);
        glVertex2f(startX + gridW + 8, startY - 8);
        glVertex2f(startX + gridW + 8, startY + gridH + 8);
        glVertex2f(startX - 8, startY + gridH + 8);
        glEnd();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                int x = startX + c * (slotSize + padding);
                int y = startY + r * (slotSize + padding);
                glColor3f(0.9f, 0.9f, 0.9f);
                glBegin(GL_LINE_LOOP);
                glVertex2f(x, y);
                glVertex2f(x + slotSize, y);
                glVertex2f(x + slotSize, y + slotSize);
                glVertex2f(x, y + slotSize);
                glEnd();

                Item item = inventory.getBagItem(idx);
                if (item != null) {
                    int mx = x + slotSize / 4;
                    int my = y + slotSize / 4;
                    int mw = slotSize / 2;
                    int mh = slotSize / 2;
                    if (item.isBlock()) {
                        if (item.blockId == 1) glColor3f(0.4f, 0.2f, 0.1f);
                        else if (item.blockId == 6) glColor3f(0.55f, 0.55f, 0.6f);
                        else if (item.blockId == 3) glColor3f(0.5f, 0.35f, 0.2f);
                        else glColor3f(0.8f, 0.8f, 0.8f);
                        glBegin(GL_QUADS);
                        glVertex2f(mx, my);
                        glVertex2f(mx + mw, my);
                        glVertex2f(mx + mw, my + mh);
                        glVertex2f(mx, my + mh);
                        glEnd();
                        glColor3f(0, 0, 0);
                        glBegin(GL_LINE_LOOP);
                        glVertex2f(mx, my);
                        glVertex2f(mx + mw, my);
                        glVertex2f(mx + mw, my + mh);
                        glVertex2f(mx, my + mh);
                        glEnd();
                        if (item.count > 1) {
                            int scale = Math.max(2, slotSize / 12);
                            int numX = x + slotSize - (3 * scale) - 4;
                            int numY = y + slotSize - (5 * scale) - 4;
                            glColor3f(1f, 1f, 1f);
                            drawNumber(item.count, numX, numY, scale);
                        }
                    } else {
                        if (item.toolType == ToolType.PICKAXE) glColor3f(0.8f, 0.7f, 0.2f);
                        else if (item.toolType == ToolType.AXE) glColor3f(0.7f, 0.5f, 0.2f);
                        else if (item.toolType == ToolType.SHOVEL) glColor3f(0.6f, 0.6f, 0.6f);
                        else glColor3f(0.9f, 0.9f, 0.9f);
                        glBegin(GL_QUADS);
                        glVertex2f(mx, my);
                        glVertex2f(mx + mw, my);
                        glVertex2f(mx + mw, my + mh);
                        glVertex2f(mx, my + mh);
                        glEnd();
                    }
                }
            }
        }
    }

    // Minimal 3x5 bitmap digits (1 = filled)
    private static final byte[][] DIGITS = new byte[][]{
        // 0
        {1,1,1,
         1,0,1,
         1,0,1,
         1,0,1,
         1,1,1},
        // 1
        {0,1,0,
         1,1,0,
         0,1,0,
         0,1,0,
         1,1,1},
        // 2
        {1,1,1,
         0,0,1,
         1,1,1,
         1,0,0,
         1,1,1},
        // 3
        {1,1,1,
         0,0,1,
         0,1,1,
         0,0,1,
         1,1,1},
        // 4
        {1,0,1,
         1,0,1,
         1,1,1,
         0,0,1,
         0,0,1},
        // 5
        {1,1,1,
         1,0,0,
         1,1,1,
         0,0,1,
         1,1,1},
        // 6
        {1,1,1,
         1,0,0,
         1,1,1,
         1,0,1,
         1,1,1},
        // 7
        {1,1,1,
         0,0,1,
         0,1,0,
         0,1,0,
         0,1,0},
        // 8
        {1,1,1,
         1,0,1,
         1,1,1,
         1,0,1,
         1,1,1},
        // 9
        {1,1,1,
         1,0,1,
         1,1,1,
         0,0,1,
         1,1,1}
    };

    private void drawDigit(int digit, int x, int y, int scale) {
        if (digit < 0 || digit > 9) return;
        byte[] m = DIGITS[digit];
        int idx = 0;
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 3; col++) {
                if (m[idx++] == 1) {
                    int px = x + col * scale;
                    int py = y + row * scale;
                    glBegin(GL_QUADS);
                    glVertex2f(px, py);
                    glVertex2f(px + scale, py);
                    glVertex2f(px + scale, py + scale);
                    glVertex2f(px, py + scale);
                    glEnd();
                }
            }
        }
    }

    private void drawNumber(int num, int x, int y, int scale) {
        String s = String.valueOf(num);
        int cursor = x;
        for (int i = 0; i < s.length(); i++) {
            int d = s.charAt(i) - '0';
            drawDigit(d, cursor, y, scale);
            cursor += (3 * scale) + scale; // digit width + spacing
        }
    }
}

