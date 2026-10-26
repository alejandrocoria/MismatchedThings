package games.alejandrocoria.mismatchedboots.recipe;

import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

import java.util.List;
import java.util.Optional;

/** Contributes only the mixed item to the smithing base-slot property set. */
public final class MixedBootsSmithingBaseRecipe implements SmithingRecipe {
    private static final MixedBootsSmithingBaseRecipe INSTANCE = new MixedBootsSmithingBaseRecipe();
    public static final RecipeSerializer<MixedBootsSmithingBaseRecipe> SERIALIZER = new RecipeSerializer<>(
            MapCodec.unit(INSTANCE), StreamCodec.<RegistryFriendlyByteBuf, MixedBootsSmithingBaseRecipe>unit(INSTANCE));

    private MixedBootsSmithingBaseRecipe() {
    }

    @Override
    public boolean matches(SmithingRecipeInput input, net.minecraft.world.level.Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input) {
        return ItemStack.EMPTY;
    }

    @Override
    public Optional<Ingredient> templateIngredient() {
        return Optional.empty();
    }

    @Override
    public Ingredient baseIngredient() {
        return Ingredient.of(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID));
    }

    @Override
    public Optional<Ingredient> additionIngredient() {
        return Optional.empty();
    }

    @Override
    public RecipeSerializer<MixedBootsSmithingBaseRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of();
    }
}
