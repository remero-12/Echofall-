public class Recipe {
    public final String name;
    public final Item result;
    public final Item[] ingredients;
    public final int[] quantities;

    public Recipe(String name, Item result, Item[] ingredients, int[] quantities) {
        this.name = name;
        this.result = result;
        this.ingredients = ingredients;
        this.quantities = quantities;
    }

    public boolean canCraft(Inventory inventory) {
        for (int i = 0; i < ingredients.length; i++) {
            Item ingredient = ingredients[i];
            int needed = quantities[i];
            int available = 0;
            
            if (ingredient.isBlock()) {
                available = inventory.totalCountFor(ingredient.blockId);
            } else {
                // For tools, just check if we have one (simplified)
                available = inventory.hasToolType(ingredient.toolType) ? 1 : 0;
            }
            
            if (available < needed) {
                return false;
            }
        }
        return true;
    }

    public void consumeIngredients(Inventory inventory) {
        for (int i = 0; i < ingredients.length; i++) {
            Item ingredient = ingredients[i];
            int needed = quantities[i];
            
            if (ingredient.isBlock()) {
                inventory.consumeBlocks(ingredient.blockId, needed);
            }
            // Note: We don't consume tools for crafting in this simple system
        }
    }
}