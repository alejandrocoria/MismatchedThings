package games.alejandrocoria.mismatchedthings.spike.boots;

import games.alejandrocoria.mismatchedthings.spike.core.WorkbenchCore;
import games.alejandrocoria.mismatchedthings.spike.core.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.spike.core.WorkbenchResult;
import games.alejandrocoria.mismatchedthings.spike.core.WorkbenchRecipe;

import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Frozen integration stand-in, compiled against core v1.0 and the real packaged Boots artifact. */
public final class BootsRecipe implements WorkbenchRecipe {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("workbench_boots_consumer", "compose");
    public static final BootsRecipe INSTANCE = new BootsRecipe();
    public static final RecipeSerializer<BootsRecipe> SERIALIZER = new RecipeSerializer<>(
            MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

    public static void install() {
        WorkbenchCore.register(ID, BootHalf::isValidSource);
    }

    @Override
    public boolean matches(WorkbenchInput input, Level level) {
        return BootHalf.isValidSource(input.left()) && BootHalf.isValidSource(input.right());
    }

    @Override
    public WorkbenchResult assemblePair(WorkbenchInput input) {
        var pair = MixedBootsFactory.createPair(input.left(), input.right());
        return new WorkbenchResult(pair.primary(), pair.inverse());
    }

    @Override
    public RecipeSerializer<BootsRecipe> getSerializer() {
        return SERIALIZER;
    }
}
