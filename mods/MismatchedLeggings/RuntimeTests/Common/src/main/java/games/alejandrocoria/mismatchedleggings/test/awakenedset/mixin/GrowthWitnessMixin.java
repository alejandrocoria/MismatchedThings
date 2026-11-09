package games.alejandrocoria.mismatchedleggings.test.awakenedset.mixin;

import games.alejandrocoria.mismatchedleggings.test.awakenedset.SetControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.blakebr0.mysticalagriculture.item.armor.EssenceChestplateItem", remap = false)
abstract class GrowthWitnessMixin {
    @Inject(method = "handleGrowthTicks", at = @At("HEAD"))
    private static void witness(Level level, Player player, CallbackInfo info) {
        SetControl.activations++;
    }
}

