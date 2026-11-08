package games.alejandrocoria.mismatchedthings.api.compatibility.undergarden;

import net.minecraft.world.item.ItemStack;

import java.util.ServiceLoader;

/** Optional native reservoir policy; products retain source extraction and transaction ownership. */
public interface RogdoriumInfusionService {
    RogdoriumInfusionService INSTANCE = ServiceLoader.load(RogdoriumInfusionService.class).findFirst()
            .orElseGet(() -> new RogdoriumInfusionService() {});

    /** Initializes a disposable new orientation. Inputs are read-only; discard output on failure. */
    default void initializeCreation(ItemStack output, ItemStack owner, ItemStack other) {
    }

    /** Restores exact live presence/value without interpreting or reconstructing the previous record. */
    default void preserveState(ItemStack previous, ItemStack output) {
    }
}
