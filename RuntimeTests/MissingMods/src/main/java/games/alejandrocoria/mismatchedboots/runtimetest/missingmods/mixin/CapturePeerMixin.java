package games.alejandrocoria.mismatchedboots.runtimetest.missingmods.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import games.alejandrocoria.mismatchedboots.runtimetest.missingmods.CaptureWitness;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;
import java.util.stream.Stream;

@Mixin(value = ItemStack.class, priority = 900)
abstract class CapturePeerMixin {
    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE", target =
            "Lcom/mojang/serialization/MapCodec;recursive(Ljava/lang/String;Ljava/util/function/Function;)Lcom/mojang/serialization/MapCodec;"))
    private static MapCodec<ItemStack> peer(String name, Function<Codec<ItemStack>, MapCodec<ItemStack>> factory,
            Operation<MapCodec<ItemStack>> original) {
        // A real competing wrapper which skips its operation must trigger production's independent startup witness.
        if (Boolean.getBoolean("mismatchedboots.test.missingModsBypass")) { return MapCodec.unit(() -> ItemStack.EMPTY); }
        Function<Codec<ItemStack>, MapCodec<ItemStack>> wrapped = self -> {
            var delegate = factory.apply(self);
            return new MapCodec<>() {
                @Override public <T> Stream<T> keys(DynamicOps<T> ops) { return delegate.keys(ops); }
                @Override public <T> RecordBuilder<T> encode(ItemStack value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                    return delegate.encode(value, ops, prefix);
                }
                @Override public <T> DataResult<ItemStack> decode(DynamicOps<T> ops, MapLike<T> input) {
                    CaptureWitness.peerDecodes++;
                    return delegate.decode(ops, input);
                }
            };
        };
        return original.call(name, wrapped);
    }
}
