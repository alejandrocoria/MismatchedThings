package games.alejandrocoria.mismatchedboots.runtimetest.missingmods.mixin;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import games.alejandrocoria.mismatchedboots.data.SuspendedBootsItemCodec;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Explicit disposable-world negative control. No production configuration can disable capture. */
@Mixin(SuspendedBootsItemCodec.class)
abstract class NativeControlMixin {
    @Shadow @Final private MapCodec<ItemStack> delegate;

    @Inject(method = "decode", at = @At("HEAD"), cancellable = true, remap = false)
    private <T> void nativeControl(DynamicOps<T> ops, MapLike<T> input, CallbackInfoReturnable<DataResult<ItemStack>> result) {
        if (System.getProperty("mismatchedboots.test.missingModsCase", "").equals("control")) {
            result.setReturnValue(delegate.decode(ops, input));
        }
    }
}
