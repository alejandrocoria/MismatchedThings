package games.alejandrocoria.mismatchedthings.fixture.sibling;

import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedthings.api.WorkbenchExtensions;
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

public final class SiblingRecipe implements WorkbenchRecipe {
    public static final Identifier ID = Identifier.parse("workbench_sibling:compose");
    private static final SiblingRecipe INSTANCE = new SiblingRecipe();
    public static final RecipeSerializer<SiblingRecipe> SERIALIZER = new RecipeSerializer<>(
            MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

    public static void install() {
        // Linking this call fails if the loader accidentally selects the older core.
        WorkbenchExtensions.registerExtendedContributor(ID, SiblingRecipe::accepts);
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
        ItemStack primary = input.left().copyWithCount(1);
        ItemStack inverse = input.right().copyWithCount(1);
        primary.set(DataComponents.CUSTOM_NAME, Component.literal("Sibling primary"));
        inverse.set(DataComponents.CUSTOM_NAME, Component.literal("Sibling inverse"));
        return new WorkbenchResult(primary, inverse);
    }

    @Override
    public RecipeSerializer<SiblingRecipe> getSerializer() {
        return SERIALIZER;
    }
}
