package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearRemainingFixture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe production dispatch; leave the guarded native cancellation method completely untouched. */
@Mixin(targets = "games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCureApi", remap = false)
abstract class SilentGearCureWitnessMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void called(Object effect, Object instance, ItemStack copy, ServerLevel level, Player player, CallbackInfo ci) {
        try {
            String name = instance.getClass().getMethod("getTraitId").invoke(instance).toString().split(":")[1];
            SilentGearRemainingFixture.cureCalls.merge(name, 1, Integer::sum);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
