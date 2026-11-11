package games.alejandrocoria.mismatchedleggings.test.silentgear.mixin;

import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearMagnetApi;
import games.alejandrocoria.mismatchedleggings.test.silentgear.MagnetDispatchProbe;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SilentGearMagnetApi.class, remap = false)
public abstract class MagnetDispatchWitnessMixin {
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lgames/alejandrocoria/mismatchedleggings/compatibility/silentgear/SilentGearMagnetApi;invoke(Ljava/lang/Object;Ljava/lang/Object;Lnet/minecraft/world/entity/player/Player;)V"))
    private void before(Object effect, Player player, Object instance, ItemStack carrier, boolean selected, CallbackInfo ci) {
        MagnetDispatchProbe.before(instance, carrier);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lgames/alejandrocoria/mismatchedleggings/compatibility/silentgear/SilentGearMagnetApi;invoke(Ljava/lang/Object;Ljava/lang/Object;Lnet/minecraft/world/entity/player/Player;)V", shift = At.Shift.AFTER))
    private void after(Object effect, Player player, Object instance, ItemStack carrier, boolean selected, CallbackInfo ci) {
        MagnetDispatchProbe.after();
    }
}
