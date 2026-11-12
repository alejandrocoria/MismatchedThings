package games.alejandrocoria.mismatchedleggings.compatibility.justdirethings;

import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import games.alejandrocoria.mismatchedleggings.interaction.LeggingsSmithing;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.ServiceLoader;

/** Product-local lifecycle boundary; native types and capabilities remain loader-owned. */
public interface JustDireComposition {
    JustDireComposition INSTANCE = ServiceLoader.load(JustDireComposition.class).findFirst().orElse(new JustDireComposition() {});

    default void prepare(@Nullable ItemStack previous, ItemStack output, MixedLeggingsData data) {
    }

    default SmithingDecision smithing(ServerLevel level, ItemStack template, ItemStack root, ItemStack addition) {
        return SmithingDecision.PASS;
    }

    record SmithingDecision(boolean handled, Optional<LeggingsSmithing.Result> result) {
        public static final SmithingDecision PASS = new SmithingDecision(false, Optional.empty());
        public static final SmithingDecision DENIED = new SmithingDecision(true, Optional.empty());
    }
}
