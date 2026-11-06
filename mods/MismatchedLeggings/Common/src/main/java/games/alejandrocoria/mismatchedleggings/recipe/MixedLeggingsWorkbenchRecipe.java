package games.alejandrocoria.mismatchedleggings.recipe;

import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.data.LeggingHalf;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.WorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.WorkbenchResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class MixedLeggingsWorkbenchRecipe implements WorkbenchRecipe {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(LeggingsContent.MOD_ID, "mixed_leggings_workbench");
    private static final MixedLeggingsWorkbenchRecipe INSTANCE = new MixedLeggingsWorkbenchRecipe();
    public static final RecipeSerializer<MixedLeggingsWorkbenchRecipe> SERIALIZER = new RecipeSerializer<>(
            MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

    @Override
    public boolean matches(WorkbenchInput input, Level level) {
        return LeggingHalf.isValidSource(input.left()) && LeggingHalf.isValidSource(input.right());
    }

    @Override
    public WorkbenchResult assemblePair(WorkbenchInput input) {
        return assemblePair(input, null);
    }

    @Override
    public WorkbenchResult assemblePair(WorkbenchInput input, HolderLookup.@Nullable Provider registries) {
        return assemble(input, CompositionRuleState.server(), registries);
    }

    @Override
    public WorkbenchResult assemblePreview(WorkbenchInput input, Level level) {
        // Integrated servers share this JVM. Never infer preview rules from the physical side.
        var snapshot = CompositionRuleState.synchronizedPreview();
        return snapshot == null ? WorkbenchResult.EMPTY : assemble(input, snapshot, level.registryAccess());
    }

    private WorkbenchResult assemble(WorkbenchInput input, CompositionRules rules, HolderLookup.@Nullable Provider registries) {
        if (!matches(input, null)) {
            return WorkbenchResult.EMPTY;
        }

        try {
            var pair = MixedLeggingsFactory.createPair(input.left(), input.right(), rules, registries);
            return new WorkbenchResult(pair.primary(), pair.inverse());
        } catch (IllegalArgumentException exception) {
            LeggingsContent.LOG.debug("Rejected leggings workbench composition: {}", exception.getMessage());
            return WorkbenchResult.EMPTY;
        }
    }

    @Override
    public RecipeSerializer<MixedLeggingsWorkbenchRecipe> getSerializer() {
        return SERIALIZER;
    }
}
