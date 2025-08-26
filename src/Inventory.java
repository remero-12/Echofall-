public class Inventory {
    private final Item[] hotbar;
    private final Item[] bag; // full inventory grid
    private final CraftingGrid craftingGrid;
    private int selectedIndex = 0;
    private int selectedSlot = -1; // -1 = none, 0-8 = hotbar, 9+ = bag slots
    private Item heldItem = null; // item being dragged

    public Inventory(int hotbarSize) {
        this.hotbar = new Item[hotbarSize];
        this.bag = new Item[27]; // 3 rows x 9 cols
        this.craftingGrid = new CraftingGrid(2); // 2x2 crafting grid
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
    public void setBagItem(int index, Item item) { if (index >= 0 && index < bag.length) bag[index] = item; }
    
    public CraftingGrid getCraftingGrid() { return craftingGrid; }
    
    public Item getHeldItem() { return heldItem; }
    public void setHeldItem(Item item) { this.heldItem = item; }
    
    public int getSelectedSlot() { return selectedSlot; }
    public void setSelectedSlot(int slot) { this.selectedSlot = slot; }
    
    // Click handling for inventory slots
    public void handleSlotClick(int slotIndex) {
        Item slotItem = null;
        
        // Get item from clicked slot
        if (slotIndex >= 0 && slotIndex < hotbar.length) {
            slotItem = hotbar[slotIndex];
        } else if (slotIndex >= 9 && slotIndex < 9 + bag.length) {
            slotItem = bag[slotIndex - 9];
        }
        
        if (heldItem == null) {
            // Pick up item from slot
            if (slotItem != null) {
                heldItem = slotItem;
                setSlotItem(slotIndex, null);
            }
        } else {
            // Place held item in slot
            if (slotItem == null) {
                // Empty slot - place item
                setSlotItem(slotIndex, heldItem);
                heldItem = null;
            } else if (canStack(heldItem, slotItem)) {
                // Stack items
                int spaceLeft = 64 - slotItem.count; // Max stack size
                int toAdd = Math.min(spaceLeft, heldItem.count);
                slotItem.count += toAdd;
                heldItem.count -= toAdd;
                if (heldItem.count <= 0) {
                    heldItem = null;
                }
            } else {
                // Swap items
                setSlotItem(slotIndex, heldItem);
                heldItem = slotItem;
            }
        }
    }
    
    private void setSlotItem(int slotIndex, Item item) {
        if (slotIndex >= 0 && slotIndex < hotbar.length) {
            hotbar[slotIndex] = item;
        } else if (slotIndex >= 9 && slotIndex < 9 + bag.length) {
            bag[slotIndex - 9] = item;
        }
    }
    
    private boolean canStack(Item item1, Item item2) {
        if (item1 == null || item2 == null) return false;
        if (item1.isBlock() && item2.isBlock()) {
            return item1.blockId == item2.blockId;
        }
        if (!item1.isBlock() && !item2.isBlock()) {
            return item1.toolType == item2.toolType && item1.name.equals(item2.name);
        }
        return false;
    }
    
    public void handleCraftingSlotClick(int craftingSlot) {
        Item slotItem = craftingGrid.get(craftingSlot);
        
        if (heldItem == null) {
            // Pick up from crafting grid
            if (slotItem != null) {
                heldItem = slotItem;
                craftingGrid.set(craftingSlot, null);
            }
        } else {
            // Place in crafting grid
            if (slotItem == null) {
                // Place one item
                Item toPlace = new Item(heldItem.name, heldItem.isBlock() ? heldItem.blockId : 0, 1);
                if (!heldItem.isBlock()) {
                    toPlace = new Item(heldItem.name, heldItem.toolType);
                }
                craftingGrid.set(craftingSlot, toPlace);
                heldItem.count--;
                if (heldItem.count <= 0) {
                    heldItem = null;
                }
            } else if (canStack(heldItem, slotItem)) {
                // Add to existing stack
                slotItem.count++;
                heldItem.count--;
                if (heldItem.count <= 0) {
                    heldItem = null;
                }
            }
        }
    }
    
    public void handleCraftingResultClick() {
        Item result = craftingGrid.getResult();
        if (result != null && craftingGrid.canCraft()) {
            if (heldItem == null) {
                // Take crafted item
                heldItem = new Item(result.name, result.isBlock() ? result.blockId : 0, result.count);
                if (!result.isBlock()) {
                    heldItem = new Item(result.name, result.toolType);
                }
                craftingGrid.consumeMaterials();
            }
        }
    }

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

    public boolean hasToolType(ToolType toolType) {
        for (Item item : hotbar) {
            if (item != null && item.toolType == toolType) {
                return true;
            }
        }
        for (Item item : bag) {
            if (item != null && item.toolType == toolType) {
                return true;
            }
        }
        return false;
    }

    public void consumeBlocks(int blockId, int amount) {
        int remaining = amount;
        
        // First consume from hotbar
        for (int i = 0; i < hotbar.length && remaining > 0; i++) {
            Item item = hotbar[i];
            if (item != null && item.isBlock() && item.blockId == blockId) {
                int toConsume = Math.min(remaining, item.count);
                item.count -= toConsume;
                remaining -= toConsume;
                if (item.count == 0) {
                    hotbar[i] = null;
                }
            }
        }
        
        // Then consume from bag
        for (int i = 0; i < bag.length && remaining > 0; i++) {
            Item item = bag[i];
            if (item != null && item.isBlock() && item.blockId == blockId) {
                int toConsume = Math.min(remaining, item.count);
                item.count -= toConsume;
                remaining -= toConsume;
                if (item.count == 0) {
                    bag[i] = null;
                }
            }
        }
    }

    public void addTool(Item tool) {
        // Add tool to first empty hotbar slot, then bag
        for (int i = 0; i < hotbar.length; i++) {
            if (hotbar[i] == null) {
                hotbar[i] = tool;
                return;
            }
        }
        for (int i = 0; i < bag.length; i++) {
            if (bag[i] == null) {
                bag[i] = tool;
                return;
            }
        }
    }
}


