package games.alejandrocoria.mismatchedboots.mixin.compatibility.mysticalagriculture;

import games.alejandrocoria.mismatchedboots.compatibility.mysticalagriculture.MysticalAugmentsCompatibility;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.blakebr0.mysticalagriculture.augment.HealthBoostAugment", remap = false)
abstract class MysticalHealthCleanupMixin {
    @Unique private static boolean mismatchedboots$maHealthCleanupReady() { return false; }

    @Inject(method = "onPlayerTick", at = @At("RETURN"), require = 0, expect = 0)
    private void mismatchedboots$maHealthCleanup(Level level, Player player, @Coerce Object cache, CallbackInfo callback) {
        if (mismatchedboots$maHealthCleanupReady()) { MysticalAugmentsCompatibility.refreshHealthCleanup(cache, this, player); }
    }
}
