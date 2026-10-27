package games.alejandrocoria.mismatchedthings.fixture.baseline;

import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
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

/** Source provenance only after recording: normal verification runs the pinned compiled artifact instead. */
public final class BaselineRecipe implements WorkbenchRecipe {
    public static final Identifier ID = Identifier.parse("workbench_baseline:compose");
    private static final BaselineRecipe INSTANCE = new BaselineRecipe();
    public static final RecipeSerializer<BaselineRecipe> SERIALIZER = new RecipeSerializer<>(
            MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

    public static void install() {
        MismatchedWorkbench.registerContributor(ID, BaselineRecipe::accepts);
    }

    private static boolean accepts(ItemStack stack) {
        return stack.is(Items.IRON_LEGGINGS) || stack.is(Items.GOLDEN_LEGGINGS);
    }

    @Override
    public boolean matches(WorkbenchInput input, Level level) {
        return accepts(input.left()) && accepts(input.right());
    }

    @Override
    public WorkbenchResult assemblePair(WorkbenchInput input) {
        ItemStack primary = input.left().copyWithCount(1);
        ItemStack inverse = input.right().copyWithCount(1);
        primary.set(DataComponents.CUSTOM_NAME, Component.literal("Baseline primary"));
        inverse.set(DataComponents.CUSTOM_NAME, Component.literal("Baseline inverse"));
        return new WorkbenchResult(primary, inverse);
    }

    @Override
    public RecipeSerializer<BaselineRecipe> getSerializer() {
        return SERIALIZER;
    }
}
