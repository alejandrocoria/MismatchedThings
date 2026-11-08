package games.alejandrocoria.mismatchedleggings.compatibility.undergarden;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import quek.undergarden.recipe.InfusingRecipe;
import quek.undergarden.recipe.ItemInfusingRecipe;

/** Product-local admission only; native code retains copying, timing, fuel and output. */
public final class InfuserCompatibility {
    private static final TagKey<Item> ARMOR = TagKey.create(Registries.ITEM, Identifier.parse("c:armors/humanoid"));
    private static volatile boolean recipeDisabled;
    private static volatile boolean menuDisabled;

    private InfuserCompatibility() {}

    public static boolean recipe(boolean original, Object recipe, Ingredient ingredient, ItemStack stack) {
        if (original || recipeDisabled || !valid(stack)) {
            return original;
        }
        try {
            if (recipe.getClass() != ItemInfusingRecipe.class) {
                return false;
            }
            var nativeRecipe = (ItemInfusingRecipe) recipe;
            if (nativeRecipe.getRecipeSlotType() != InfusingRecipe.SlotType.ROGDORIUM || nativeRecipe.input() != ingredient) {
                return false;
            }
            // Inspect this immutable recipe's actual ingredient, never a cached recipe ID or example item.
            return !ingredient.isCustom() && ingredient.getValues().unwrapKey().filter(ARMOR::equals).isPresent();
        } catch (RuntimeException | LinkageError failure) {
            disable(true, failure);
            return false;
        }
    }

    public static boolean menu(boolean original, ItemStack stack) {
        // Placement is not recipe availability: a datapack may remove processing while this slot remains permissive.
        return original || !menuDisabled && valid(stack);
    }

    private static boolean valid(ItemStack stack) {
        return MixedLeggingsStructure.validData(stack).isPresent();
    }

    static synchronized void disable(boolean recipe, Throwable failure) {
        if (recipe ? recipeDisabled : menuDisabled) {
            return;
        }
        if (recipe) {
            recipeDisabled = true;
        } else {
            menuDisabled = true;
        }
        String hook = recipe ? "infusing" : "Infuser menu";
        if (failure == null) {
            LeggingsContent.LOG.warn("Undergarden mixed leggings {} disabled: incompatible local operands", hook);
        } else {
            LeggingsContent.LOG.warn("Undergarden mixed leggings {} disabled: incompatible local operands", hook, failure);
        }
    }
}
