package games.alejandrocoria.mismatchedleggings.compatibility.forbiddenarcanus;

import games.alejandrocoria.mismatchedleggings.data.ForbiddenModifiers;
import games.alejandrocoria.mismatchedleggings.interaction.LeggingsSmithing;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.ServiceLoader;

/** Composition boundary only; no native classes, world cache or wearer activation. */
public interface ForbiddenModifierAdapter {
    ForbiddenModifierAdapter INSTANCE = ServiceLoader.load(ForbiddenModifierAdapter.class).findFirst()
            .orElseGet(() -> new ForbiddenModifierAdapter() {});

    default ForbiddenModifiers inherit(ItemStack left, ItemStack right) {
        return ForbiddenModifiers.EMPTY;
    }

    /** Operates on a fresh output; failure must discard it rather than publish a partial result. */
    default ItemStack finish(ItemStack output, HolderLookup.@Nullable Provider registries) {
        return output;
    }

    /** Handled denial must not fall through to installing an effect in inactive source history. */
    default SmithingDecision smithing(ServerLevel level, ItemStack template, ItemStack root, ItemStack addition) {
        return SmithingDecision.PASS;
    }

    record SmithingDecision(boolean handled, Optional<LeggingsSmithing.Result> result) {
        public static final SmithingDecision PASS = new SmithingDecision(false, Optional.empty());
        public static final SmithingDecision DENIED = new SmithingDecision(true, Optional.empty());
    }
}
