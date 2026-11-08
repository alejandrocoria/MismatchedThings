package games.alejandrocoria.mismatchedleggings.test.mystical.mixin;

import games.alejandrocoria.mismatchedleggings.test.mystical.SpikeClient;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.blakebr0.mysticalagriculture.augment.SpeedAugment", remap = false)
abstract class SpeedWitness {
    @Inject(method = "onPlayerTick", at = @At("HEAD"))
    private void spike$witness(Level level, Player player, @Coerce Object cache, CallbackInfo ci) {
        SpikeClient.observe(player);
    }
}
