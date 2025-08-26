public class Item {
    public final String name;
    public final ToolType toolType; // NONE for blocks
    public int count; // for stackables
    public final int blockId; // 0 if not a placeable block; else tile id (1 dirt, 6 stone, etc.)

    public Item(String name, ToolType toolType) {
        this.name = name;
        this.toolType = toolType;
        this.count = 1;
        this.blockId = 0;
    }

    public Item(String name, int blockId, int count) {
        this.name = name;
        this.toolType = ToolType.NONE;
        this.blockId = blockId;
        this.count = count;
    }

    public boolean isBlock() { return blockId != 0; }
}


