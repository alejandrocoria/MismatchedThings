package games.alejandrocoria.mismatchedboots.runtimetest.missingmods.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedboots.data.SuspendedBootsItemCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.NbtOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

/** Opt-in fixture capture; the production mixin configurations deliberately do not contain this hook. */
@Mixin(ItemStack.class)
abstract class MissingModsItemCodecMixin {
    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE", target =
            "Lcom/mojang/serialization/MapCodec;recursive(Ljava/lang/String;Ljava/util/function/Function;)Lcom/mojang/serialization/MapCodec;"))
    private static MapCodec<ItemStack> capture(String name, Function<Codec<ItemStack>, MapCodec<ItemStack>> factory,
            Operation<MapCodec<ItemStack>> original) {
        Function<Codec<ItemStack>, MapCodec<ItemStack>> wrapped = self -> {
            var nativeCodec = factory.apply(self);
            return System.getProperty("mismatchedboots.test.missingModsCase", "").equals("candidate")
                    ? new SuspendedBootsItemCodec(nativeCodec) : nativeCodec;
        };
        MapCodec<ItemStack> result = original.call(name, wrapped);
        // MapCodec.recursive is lazy; force the shape guard before any world can be opened.
        if (System.getProperty("mismatchedboots.test.missingModsCase", "").equals("candidate")) {
            result.keys(NbtOps.INSTANCE).count();
        }
        return result;
    }
}
