package games.alejandrocoria.mismatchedleggings.spike.silentgear.mixin;

import games.alejandrocoria.mismatchedleggings.spike.silentgear.WearerContracts;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.silentchaos512.gear.util.GearData", remap = false)
abstract class RecalculationMixin {
    @Inject(method = "recalculateGearData", at = @At("HEAD"))
    private static void observe(ItemStack stack, Player player, CallbackInfo ci) {
        if (WearerContracts.observing) { throw new AssertionError("Wearer work recalculated native gear"); }
    }
}
