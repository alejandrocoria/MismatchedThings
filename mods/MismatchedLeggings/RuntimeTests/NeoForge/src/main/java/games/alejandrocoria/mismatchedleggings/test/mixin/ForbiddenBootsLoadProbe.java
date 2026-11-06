package games.alejandrocoria.mismatchedleggings.test.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;
import java.util.stream.Stream;

/** Opt-in persistent-load experiment only. The ordinary fixture does not wrap its codec. */
@Mixin(value = ItemStack.class, priority = 1100)
abstract class ForbiddenBootsLoadProbe {
    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target =
            "Lcom/mojang/serialization/MapCodec;recursive(Ljava/lang/String;Ljava/util/function/Function;)Lcom/mojang/serialization/MapCodec;"), index = 1)
    private static Function<Codec<ItemStack>, MapCodec<ItemStack>> leggings$legacyLoad(
            Function<Codec<ItemStack>, MapCodec<ItemStack>> factory) {
        return Boolean.getBoolean("leggings.test.forbiddenBootsSpike")
                ? self -> new LegacyLoadCodec(factory.apply(self)) : factory;
    }

    private static final class LegacyLoadCodec extends MapCodec<ItemStack> {
        private final MapCodec<ItemStack> delegate;

        private LegacyLoadCodec(MapCodec<ItemStack> delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return delegate.keys(ops);
        }

        @Override
        public <T> RecordBuilder<T> encode(ItemStack value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            return delegate.encode(value, ops, prefix);
        }

        @Override
        public <T> DataResult<ItemStack> decode(DynamicOps<T> ops, MapLike<T> input) {
            var decoded = delegate.decode(ops, input);
            if (!System.getProperty("leggings.test.forbiddenBootsStage", "").equals("adopt") || decoded.result().isEmpty()) {
                return decoded;
            }
            return decoded.map(stack -> {
                try {
                    return (ItemStack) Class.forName("games.alejandrocoria.mismatchedleggings.test.ForbiddenBootsCandidate")
                            .getMethod("adopt", ItemStack.class).invoke(null, stack);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Forbidden Boots load spike failed", failure);
                }
            });
        }
    }
}
