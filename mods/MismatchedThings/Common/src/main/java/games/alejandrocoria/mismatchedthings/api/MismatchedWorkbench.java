package games.alejandrocoria.mismatchedthings.api;

import games.alejandrocoria.mismatchedthings.internal.WorkbenchContributors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;

import java.util.function.Predicate;

/** Shared station identity and contribution entry point; no consumer owns registration of the station itself. */
public final class MismatchedWorkbench {
    public static final String MOD_ID = "mismatchedthings";
    public static final Identifier ID = Identifier.fromNamespaceAndPath(MOD_ID, "mismatched_workbench");

    private MismatchedWorkbench() {
    }

    /** Register once during mod initialization. Admission does not replace loaded recipe pair matching. */
    public static void registerContributor(Identifier owner, Predicate<ItemStack> accepts) {
        WorkbenchContributors.register(owner, accepts);
    }

    public static RecipeType<WorkbenchRecipe> recipeType() {
        return WorkbenchRecipe.TYPE;
    }

    /** Registry-backed access is available after content registration. */
    public static Block block() {
        return BuiltInRegistries.BLOCK.get(ID).orElseThrow().value();
    }

    public static Item item() {
        return BuiltInRegistries.ITEM.get(ID).orElseThrow().value();
    }

    public static MenuType<?> menuType() {
        return BuiltInRegistries.MENU.get(ID).orElseThrow().value();
    }
}
