public class CraftingGrid {
    private final Item[] grid;
    private Item result;
    private final int size;

    public CraftingGrid(int size) {
        this.size = size;
        this.grid = new Item[size * size];
        this.result = null;
    }

    public Item get(int index) {
        if (index < 0 || index >= grid.length) return null;
        return grid[index];
    }

    public void set(int index, Item item) {
        if (index < 0 || index >= grid.length) return;
        grid[index] = item;
        updateResult();
    }

    public Item getResult() {
        return result;
    }

    public void clearGrid() {
        for (int i = 0; i < grid.length; i++) {
            grid[i] = null;
        }
        result = null;
    }

    public int getSize() {
        return size;
    }

    private void updateResult() {
        // Simple 2x2 crafting recipes
        if (size == 2) {
            // Wood -> Wood Planks (any single wood in grid)
            boolean hasWood = false;
            int woodCount = 0;
            for (Item item : grid) {
                if (item != null && item.isBlock() && item.blockId == 3) {
                    hasWood = true;
                    woodCount += item.count;
                }
            }
            if (hasWood) {
                result = new Item("Wood Planks", 7, Math.min(4, woodCount * 4));
                return;
            }

            // Stone -> Stone Bricks (4 stone in 2x2 pattern)
            int stoneCount = 0;
            boolean fullGrid = true;
            for (Item item : grid) {
                if (item != null && item.isBlock() && item.blockId == 6) {
                    stoneCount += item.count;
                } else if (item == null) {
                    fullGrid = false;
                }
            }
            if (fullGrid && stoneCount >= 4) {
                result = new Item("Stone Bricks", 8, 4);
                return;
            }

            // Tool recipes (simplified - check for wood + stone)
            boolean hasWoodForTool = false;
            boolean hasStone = false;
            int totalWood = 0;
            int totalStone = 0;
            
            for (Item item : grid) {
                if (item != null && item.isBlock()) {
                    if (item.blockId == 3) {
                        hasWoodForTool = true;
                        totalWood += item.count;
                    } else if (item.blockId == 6) {
                        hasStone = true;
                        totalStone += item.count;
                    }
                }
            }

            if (hasWoodForTool && hasStone && totalWood >= 2 && totalStone >= 3) {
                result = new Item("Stone Pickaxe", ToolType.PICKAXE);
                return;
            }
            if (hasWoodForTool && hasStone && totalWood >= 2 && totalStone >= 3) {
                result = new Item("Stone Axe", ToolType.AXE);
                return;
            }
            if (hasWoodForTool && hasStone && totalWood >= 2 && totalStone >= 2) {
                result = new Item("Stone Shovel", ToolType.SHOVEL);
                return;
            }
        }

        result = null;
    }

    public boolean canCraft() {
        if (result == null) return false;
        
        // Check if we have enough materials
        if (result.name.equals("Wood Planks")) {
            for (Item item : grid) {
                if (item != null && item.isBlock() && item.blockId == 3 && item.count >= 1) {
                    return true;
                }
            }
        } else if (result.name.equals("Stone Bricks")) {
            int stoneCount = 0;
            for (Item item : grid) {
                if (item != null && item.isBlock() && item.blockId == 6) {
                    stoneCount += item.count;
                }
            }
            return stoneCount >= 4;
        } else if (result.toolType != ToolType.NONE) {
            int woodCount = 0;
            int stoneCount = 0;
            for (Item item : grid) {
                if (item != null && item.isBlock()) {
                    if (item.blockId == 3) woodCount += item.count;
                    else if (item.blockId == 6) stoneCount += item.count;
                }
            }
            
            if (result.toolType == ToolType.PICKAXE || result.toolType == ToolType.AXE) {
                return woodCount >= 2 && stoneCount >= 3;
            } else if (result.toolType == ToolType.SHOVEL) {
                return woodCount >= 2 && stoneCount >= 2;
            }
        }
        
        return false;
    }

    public void consumeMaterials() {
        if (!canCraft()) return;

        if (result.name.equals("Wood Planks")) {
            for (int i = 0; i < grid.length; i++) {
                Item item = grid[i];
                if (item != null && item.isBlock() && item.blockId == 3) {
                    item.count--;
                    if (item.count <= 0) {
                        grid[i] = null;
                    }
                    break;
                }
            }
        } else if (result.name.equals("Stone Bricks")) {
            int needed = 4;
            for (int i = 0; i < grid.length && needed > 0; i++) {
                Item item = grid[i];
                if (item != null && item.isBlock() && item.blockId == 6) {
                    int consume = Math.min(needed, item.count);
                    item.count -= consume;
                    needed -= consume;
                    if (item.count <= 0) {
                        grid[i] = null;
                    }
                }
            }
        } else if (result.toolType != ToolType.NONE) {
            int woodNeeded = 2;
            int stoneNeeded = (result.toolType == ToolType.SHOVEL) ? 2 : 3;
            
            for (int i = 0; i < grid.length && (woodNeeded > 0 || stoneNeeded > 0); i++) {
                Item item = grid[i];
                if (item != null && item.isBlock()) {
                    if (item.blockId == 3 && woodNeeded > 0) {
                        int consume = Math.min(woodNeeded, item.count);
                        item.count -= consume;
                        woodNeeded -= consume;
                        if (item.count <= 0) {
                            grid[i] = null;
                        }
                    } else if (item.blockId == 6 && stoneNeeded > 0) {
                        int consume = Math.min(stoneNeeded, item.count);
                        item.count -= consume;
                        stoneNeeded -= consume;
                        if (item.count <= 0) {
                            grid[i] = null;
                        }
                    }
                }
            }
        }

        updateResult();
    }
}