package games.alejandrocoria.mismatchedthings.test;

import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.WorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.WorkbenchResult;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Test-only contribution: the production core has no built-in armor recipe. */
public final class FixtureRecipe implements WorkbenchRecipe {
    public static final Identifier ID = Identifier.parse("workbench_test:compose");
    private static final FixtureRecipe INSTANCE = new FixtureRecipe();
    public static final RecipeSerializer<FixtureRecipe> SERIALIZER = new RecipeSerializer<>(
            MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

    public static boolean accepts(ItemStack stack) {
        return stack.is(Items.IRON_HELMET) || stack.is(Items.GOLDEN_HELMET);
    }

    @Override
    public boolean matches(WorkbenchInput input, Level level) {
        return accepts(input.left()) && accepts(input.right());
    }

    @Override
    public WorkbenchResult assemblePair(WorkbenchInput input) {
        ItemStack primary = input.left().copyWithCount(1);
        ItemStack inverse = input.right().copyWithCount(1);
        primary.set(DataComponents.CUSTOM_NAME, Component.literal("Fixture primary"));
        inverse.set(DataComponents.CUSTOM_NAME, Component.literal("Fixture inverse"));
        return new WorkbenchResult(primary, inverse);
    }

    @Override
    public RecipeSerializer<FixtureRecipe> getSerializer() {
        return SERIALIZER;
    }
}
