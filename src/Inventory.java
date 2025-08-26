public class Inventory {
    private final Item[] hotbar;
    private final Item[] bag; // full inventory grid
    private int selectedIndex = 0;

    public Inventory(int hotbarSize) {
        this.hotbar = new Item[hotbarSize];
        this.bag = new Item[27]; // 3 rows x 9 cols
    }

    public int size() {
        return hotbar.length;
    }

    public Item get(int index) {
        if (index < 0 || index >= hotbar.length) return null;
        return hotbar[index];
    }

    public void set(int index, Item item) {
        if (index < 0 || index >= hotbar.length) return;
        hotbar[index] = item;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        if (index < 0 || index >= hotbar.length) return;
        selectedIndex = index;
    }

    public Item getSelected() {
        return get(selectedIndex);
    }

    public void addBlockToHotbar(int blockId, String displayName) {
        // Try stacking into existing block item
        for (int i = 0; i < hotbar.length; i++) {
            Item it = hotbar[i];
            if (it != null && it.isBlock() && it.blockId == blockId) {
                it.count += 1;
                return;
            }
        }
        // Place into first empty slot
        for (int i = 0; i < hotbar.length; i++) {
            if (hotbar[i] == null) {
                hotbar[i] = new Item(displayName, blockId, 1);
                return;
            }
        }
        // If full, drop on the floor later; for now, ignore overflow
    }

    public boolean addBlockToBag(int blockId, String displayName) {
        for (int i = 0; i < bag.length; i++) {
            Item it = bag[i];
            if (it != null && it.isBlock() && it.blockId == blockId) {
                it.count += 1;
                return true;
            }
        }
        for (int i = 0; i < bag.length; i++) {
            if (bag[i] == null) {
                bag[i] = new Item(displayName, blockId, 1);
                return true;
            }
        }
        return false;
    }

    public void addBlock(int blockId, String displayName) {
        // Try hotbar first, then bag
        int before = totalCountFor(blockId);
        addBlockToHotbar(blockId, displayName);
        int after = totalCountFor(blockId);
        if (after == before) {
            addBlockToBag(blockId, displayName);
        }
    }

    public int totalCountFor(int blockId) {
        int c = 0;
        for (Item it : hotbar) if (it != null && it.isBlock() && it.blockId == blockId) c += it.count;
        for (Item it : bag) if (it != null && it.isBlock() && it.blockId == blockId) c += it.count;
        return c;
    }

    public int bagSize() { return bag.length; }
    public Item getBagItem(int index) { if (index < 0 || index >= bag.length) return null; return bag[index]; }

    public boolean consumeSelectedBlockOne() {
        Item sel = getSelected();
        if (sel == null || !sel.isBlock() || sel.count <= 0) return false;
        sel.count -= 1;
        if (sel.count == 0) {
            setSelectedIndex(selectedIndex); // keep index; clear slot
            hotbar[selectedIndex] = null;
        }
        return true;
    }
}


