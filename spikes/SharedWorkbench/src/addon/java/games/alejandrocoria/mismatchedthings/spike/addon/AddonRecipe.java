package games.alejandrocoria.mismatchedthings.spike.addon;

import games.alejandrocoria.mismatchedthings.spike.core.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.spike.core.WorkbenchResult;
import games.alejandrocoria.mismatchedthings.spike.core.WorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.spike.core.WorkbenchExtensions;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Synthetic helmet outputs demonstrate extension, not a production Mismatched Helmets implementation. */
public final class AddonRecipe implements WorkbenchRecipe {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("workbench_addon_consumer", "compose");
    public static final AddonRecipe INSTANCE = new AddonRecipe();
    public static final RecipeSerializer<AddonRecipe> SERIALIZER = new RecipeSerializer<>(
            MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

    public static void install() {
        WorkbenchExtensions.registerAddon(ID, AddonRecipe::accepts);
    }

    private static boolean accepts(ItemStack stack) {
        return stack.is(Items.IRON_HELMET) || stack.is(Items.GOLDEN_HELMET);
    }

    @Override
    public boolean matches(WorkbenchInput input, Level level) {
        return accepts(input.left()) && accepts(input.right());
    }

    @Override
    public WorkbenchResult assemblePair(WorkbenchInput input) {
        ItemStack first = input.left().copyWithCount(1);
        ItemStack second = input.right().copyWithCount(1);
        first.set(DataComponents.CUSTOM_NAME, Component.literal("Sibling primary"));
        second.set(DataComponents.CUSTOM_NAME, Component.literal("Sibling inverse"));
        return new WorkbenchResult(first, second);
    }

    @Override
    public RecipeSerializer<AddonRecipe> getSerializer() {
        return SERIALIZER;
    }
}
