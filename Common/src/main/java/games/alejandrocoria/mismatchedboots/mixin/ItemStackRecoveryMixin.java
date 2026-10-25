package games.alejandrocoria.mismatchedboots.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedboots.data.RecoveryRefusalException;
import games.alejandrocoria.mismatchedboots.data.SuspendedBootsItemCodec;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/** Required persistence boundary: unlike optional gameplay adapters, native data loss is not a fallback. */
@Mixin(ItemStack.class)
abstract class ItemStackRecoveryMixin {
    @Unique private static boolean mismatchedboots$recoveryInstalled;
    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE", target =
            "Lcom/mojang/serialization/MapCodec;recursive(Ljava/lang/String;Ljava/util/function/Function;)Lcom/mojang/serialization/MapCodec;"),
            require = 1, expect = 1, allow = 1)
    private static MapCodec<ItemStack> mismatchedboots$capture(String name,
            Function<Codec<ItemStack>, MapCodec<ItemStack>> factory, Operation<MapCodec<ItemStack>> original) {
        var result = original.call(name, (Function<Codec<ItemStack>, MapCodec<ItemStack>>) self -> {
            var capture = new SuspendedBootsItemCodec(factory.apply(self));
            mismatchedboots$recoveryInstalled = true;
            return capture;
        });
        // Recursive codecs are lazy. Force the native-field guard now, before a world can be opened.
        result.keys(NbtOps.INSTANCE).count();
        return result;
    }

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void mismatchedboots$requireCapture(CallbackInfo callback) {
        if (!mismatchedboots$recoveryInstalled) {
            throw new RecoveryRefusalException("Cannot safely install mixed-boots capture: required wrapper was bypassed");
        }
    }
}
