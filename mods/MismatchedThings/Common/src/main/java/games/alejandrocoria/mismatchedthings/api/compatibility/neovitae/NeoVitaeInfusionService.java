package games.alejandrocoria.mismatchedthings.api.compatibility.neovitae;

import net.minecraft.world.item.ItemStack;

import java.util.ServiceLoader;

/** Optional native infusion policy; products retain source extraction, transactions and structural validation. */
public interface NeoVitaeInfusionService {
    NeoVitaeInfusionService INSTANCE = ServiceLoader.load(NeoVitaeInfusionService.class).findFirst()
            .orElseGet(() -> new NeoVitaeInfusionService() {});

    /** Finalizes a disposable new orientation; both source stacks are read-only. Discard the output on failure. */
    default void initializeCreation(ItemStack output, ItemStack owner, ItemStack other) {
    }

    /** Restores exact live field presence, not source projections. The previous root is read-only. */
    default void preserveState(ItemStack previous, ItemStack output) {
    }
}
