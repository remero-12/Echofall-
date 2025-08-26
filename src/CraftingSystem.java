import java.util.ArrayList;
import java.util.List;

public class CraftingSystem {
    private final List<Recipe> recipes;

    public CraftingSystem() {
        recipes = new ArrayList<>();
        initializeRecipes();
    }

    private void initializeRecipes() {
        // Stone tools (upgrade from wood)
        recipes.add(new Recipe(
            "Stone Pickaxe",
            new Item("Stone Pickaxe", ToolType.PICKAXE),
            new Item[]{
                new Item("Wood", 3, 1),
                new Item("Stone", 6, 1)
            },
            new int[]{2, 3}
        ));

        recipes.add(new Recipe(
            "Stone Axe", 
            new Item("Stone Axe", ToolType.AXE),
            new Item[]{
                new Item("Wood", 3, 1),
                new Item("Stone", 6, 1)
            },
            new int[]{2, 3}
        ));

        recipes.add(new Recipe(
            "Stone Shovel",
            new Item("Stone Shovel", ToolType.SHOVEL),
            new Item[]{
                new Item("Wood", 3, 1),
                new Item("Stone", 6, 1)
            },
            new int[]{2, 2}
        ));

        // Building blocks
        recipes.add(new Recipe(
            "Wood Planks",
            new Item("Wood Planks", 7, 4), // New block ID 7 for planks
            new Item[]{
                new Item("Wood", 3, 1)
            },
            new int[]{1}
        ));

        recipes.add(new Recipe(
            "Stone Bricks",
            new Item("Stone Bricks", 8, 4), // New block ID 8 for bricks
            new Item[]{
                new Item("Stone", 6, 1)
            },
            new int[]{4}
        ));
    }

    public List<Recipe> getAvailableRecipes(Inventory inventory) {
        List<Recipe> available = new ArrayList<>();
        for (Recipe recipe : recipes) {
            if (recipe.canCraft(inventory)) {
                available.add(recipe);
            }
        }
        return available;
    }

    public List<Recipe> getAllRecipes() {
        return new ArrayList<>(recipes);
    }

    public boolean craftItem(Recipe recipe, Inventory inventory) {
        if (!recipe.canCraft(inventory)) {
            return false;
        }

        recipe.consumeIngredients(inventory);
        
        if (recipe.result.isBlock()) {
            inventory.addBlock(recipe.result.blockId, recipe.result.name);
        } else {
            inventory.addTool(recipe.result);
        }
        
        return true;
    }
}