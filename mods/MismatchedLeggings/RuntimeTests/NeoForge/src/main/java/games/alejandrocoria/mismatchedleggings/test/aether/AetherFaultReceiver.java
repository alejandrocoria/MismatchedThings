package games.alejandrocoria.mismatchedleggings.test.aether;

import net.minecraft.world.item.ItemStack;

/** Kept outside the early-loading mixin plugin; the native receiver itself stays unchanged. */
public final class AetherFaultReceiver {
    private AetherFaultReceiver() {}

    public static ItemStack receiver(ItemStack original) {
        return original;
    }
}
