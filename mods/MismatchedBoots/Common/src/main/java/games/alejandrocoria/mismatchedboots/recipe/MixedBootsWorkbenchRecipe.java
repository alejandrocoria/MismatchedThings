package games.alejandrocoria.mismatchedboots.recipe;

import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.WorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.WorkbenchResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class MixedBootsWorkbenchRecipe implements WorkbenchRecipe {
    private static final MixedBootsWorkbenchRecipe INSTANCE = new MixedBootsWorkbenchRecipe();
    private static final MapCodec<MixedBootsWorkbenchRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    private static final StreamCodec<RegistryFriendlyByteBuf, MixedBootsWorkbenchRecipe> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<MixedBootsWorkbenchRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final ContextPairFactory pairFactory;

    public MixedBootsWorkbenchRecipe() {
        this((left, right, registries) -> MixedBootsFactory.createPair(left, right, registries));
    }

    /** Client examples capture the synchronized rules explicitly, not physical-side inference. */
    public MixedBootsWorkbenchRecipe(AttributeRuleSet attributes, ComponentRules components) {
        this((left, right, registries) -> MixedBootsFactory.createPair(left, right, attributes, components, registries));
        java.util.Objects.requireNonNull(attributes, "attributes");
        java.util.Objects.requireNonNull(components, "components");
    }

    MixedBootsWorkbenchRecipe(PairFactory pairFactory) {
        this((left, right, registries) -> pairFactory.create(left, right));
    }

    private MixedBootsWorkbenchRecipe(ContextPairFactory pairFactory) {
        this.pairFactory = pairFactory;
    }

    @Override
    public boolean matches(WorkbenchInput input, Level level) {
        return BootHalf.isValidSource(input.left()) && BootHalf.isValidSource(input.right());
    }

    @Override
    public WorkbenchResult assemblePair(WorkbenchInput input) {
        return assemblePair(input, null);
    }

    @Override
    public WorkbenchResult assemblePair(WorkbenchInput input, HolderLookup.@Nullable Provider registries) {
        if (!matches(input, null)) {
            return WorkbenchResult.EMPTY;
        }

        try {
            MixedBootsPair pair = pairFactory.create(input.left().copy(), input.right().copy(), registries);
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
        var rules = CompositionRulesClientLifecycle.synchronizedRules();
        return rules == null ? WorkbenchResult.EMPTY
                : new MixedBootsWorkbenchRecipe(rules.attributes(), rules.components()).assemblePair(input, level.registryAccess());
    }

    @Override
    public RecipeSerializer<MixedBootsWorkbenchRecipe> getSerializer() {
        return SERIALIZER;
    }

    @FunctionalInterface
    private interface ContextPairFactory {
        MixedBootsPair create(ItemStack left, ItemStack right, HolderLookup.@Nullable Provider registries);
    }

    interface PairFactory {
        MixedBootsPair create(ItemStack left, ItemStack right);
    }
}
