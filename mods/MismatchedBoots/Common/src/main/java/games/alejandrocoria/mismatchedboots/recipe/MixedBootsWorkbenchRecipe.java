package games.alejandrocoria.mismatchedboots.recipe;

import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.WorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.WorkbenchResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class MixedBootsWorkbenchRecipe implements WorkbenchRecipe {
    private static final MixedBootsWorkbenchRecipe INSTANCE = new MixedBootsWorkbenchRecipe();
    private static final MapCodec<MixedBootsWorkbenchRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    private static final StreamCodec<RegistryFriendlyByteBuf, MixedBootsWorkbenchRecipe> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<MixedBootsWorkbenchRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final PairFactory pairFactory;

    public MixedBootsWorkbenchRecipe() {
        this(MixedBootsFactory::createPair);
    }

    /** Client examples capture the synchronized rules explicitly, not physical-side inference. */
    public MixedBootsWorkbenchRecipe(AttributeRuleSet attributes, ComponentRules components) {
        this((left, right) -> MixedBootsFactory.createPair(left, right, attributes, components));
        java.util.Objects.requireNonNull(attributes, "attributes");
        java.util.Objects.requireNonNull(components, "components");
    }

    MixedBootsWorkbenchRecipe(PairFactory pairFactory) {
        this.pairFactory = pairFactory;
    }

    @Override
    public boolean matches(WorkbenchInput input, Level level) {
        return BootHalf.isValidSource(input.left()) && BootHalf.isValidSource(input.right());
    }

    @Override
    public WorkbenchResult assemblePair(WorkbenchInput input) {
        if (!matches(input, null)) {
            return WorkbenchResult.EMPTY;
        }

        try {
            MixedBootsPair pair = pairFactory.create(input.left().copy(), input.right().copy());
            if (pair.primary().isEmpty() || pair.inverse().isEmpty()) {
                Constants.LOG.debug("Rejected empty workbench output for {} and {}",
                                    BuiltInRegistries.ITEM.getKey(input.left().getItem()),
                                    BuiltInRegistries.ITEM.getKey(input.right().getItem()));
                return WorkbenchResult.EMPTY;
            }

            return new WorkbenchResult(pair.primary(), pair.inverse());
        } catch (IllegalArgumentException exception) {
            Constants.LOG.debug("Rejected workbench composition for {} and {}: {}",
                                BuiltInRegistries.ITEM.getKey(input.left().getItem()),
                                BuiltInRegistries.ITEM.getKey(input.right().getItem()),
                                exception.getMessage());
            return WorkbenchResult.EMPTY;
        }
    }

    @Override
    public WorkbenchResult assemblePreview(
            WorkbenchInput input, Level level) {
        // This callback is client preview only. Assembly above retains server policy authority.
        return new MixedBootsWorkbenchRecipe(AttributeRuleState.current(), ComponentRuleState.current()).assemblePair(input);
    }

    @Override
    public RecipeSerializer<MixedBootsWorkbenchRecipe> getSerializer() {
        return SERIALIZER;
    }

    @FunctionalInterface
    interface PairFactory {
        MixedBootsPair create(ItemStack left, ItemStack right);
    }
}
