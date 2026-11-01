package games.alejandrocoria.mismatchedleggings.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

/** A competing operation wrapper may skip capture entirely; startup must still refuse. */
@Mixin(value = ItemStack.class, priority = 900)
abstract class RecoveryBypassProbe {
    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE", target =
            "Lcom/mojang/serialization/MapCodec;recursive(Ljava/lang/String;Ljava/util/function/Function;)Lcom/mojang/serialization/MapCodec;"))
    private static MapCodec<ItemStack> leggings$bypass(String name, Function<Codec<ItemStack>, MapCodec<ItemStack>> factory,
                                                       Operation<MapCodec<ItemStack>> original) {
        return System.getProperty("leggings.test.recoveryStage", "").equals("bypassed")
                ? MapCodec.unit(() -> ItemStack.EMPTY) : original.call(name, factory);
    }
}
