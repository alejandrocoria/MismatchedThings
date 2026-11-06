package games.alejandrocoria.mismatchedleggings.compatibility.forbiddenarcanus;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.interaction.LeggingsSmithing;
import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifierService;
import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifiers;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Product-owned authority and transaction boundary around the optional native service. */
public final class ForbiddenModifierAdapter {
    public static final ForbiddenModifierAdapter INSTANCE = new ForbiddenModifierAdapter();
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private ForbiddenModifierAdapter() {}

    public ForbiddenModifiers inherit(ItemStack left, ItemStack right) {
        return ForbiddenModifierService.INSTANCE.inherit(left, right);
    }

    /** Operates on a fresh output; failure must discard it rather than publish a partial result. */
    public ItemStack finish(ItemStack output, HolderLookup.@Nullable Provider registries) {
        var state = output.getOrDefault(LeggingsContent.forbiddenModifiersType(), ForbiddenModifiers.EMPTY);
        var result = ForbiddenModifierService.INSTANCE.finish(output, state, registries);
        if (!state.equals(result.getOrDefault(LeggingsContent.forbiddenModifiersType(), ForbiddenModifiers.EMPTY))) {
            throw new IllegalArgumentException("Native modifier application changed mixed leggings authority");
        }
        return result;
    }

    public SmithingDecision smithing(ServerLevel level, ItemStack template, ItemStack root, ItemStack addition) {
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (data == null) {
            return SmithingDecision.PASS;
        }
        try {
            var current = root.getOrDefault(LeggingsContent.forbiddenModifiersType(), ForbiddenModifiers.EMPTY);
            var decision = ForbiddenModifierService.INSTANCE.smithing(level, template, root, addition, current,
                    List.of(data.left().source(), data.right().source()));
            if (decision.match().isEmpty()) {
                return decision.handled() ? SmithingDecision.DENIED : SmithingDecision.PASS;
            }
            var match = decision.match().orElseThrow();
            var output = root.copy();
            output.set(LeggingsContent.forbiddenModifiersType(), match.modifiers());
            output = finish(output, level.registryAccess());
            if (MixedLeggingsStructure.validData(output).isEmpty()) {
                throw new IllegalArgumentException("Modifier smithing changed mixed leggings structure");
            }
            return new SmithingDecision(true, Optional.of(new LeggingsSmithing.Result(output, match.recipe(), null)));
        } catch (RuntimeException | LinkageError failure) {
            if (WARNED.compareAndSet(false, true)) {
                LeggingsContent.LOG.warn("Forbidden Arcanus mixed leggings smithing refused: {}", failure.toString());
            }
            return SmithingDecision.DENIED;
        }
    }

    public record SmithingDecision(boolean handled, Optional<LeggingsSmithing.Result> result) {
        public static final SmithingDecision PASS = new SmithingDecision(false, Optional.empty());
        public static final SmithingDecision DENIED = new SmithingDecision(true, Optional.empty());
    }
}
