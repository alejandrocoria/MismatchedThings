package games.alejandrocoria.mismatchedboots.data;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import games.alejandrocoria.mismatchedboots.compatibility.forbiddenarcanus.ForbiddenBootsModifiers;
import net.minecraft.world.item.ItemStack;

import java.util.stream.Stream;

/** Runs after suspension/restoration and only on complete persistent decode success, including container contents. */
public final class ForbiddenBootsItemCodec extends MapCodec<ItemStack> {
    private final MapCodec<ItemStack> delegate;

    public ForbiddenBootsItemCodec(MapCodec<ItemStack> delegate) {
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
        return decoded.error().isPresent() ? decoded : decoded.map(ForbiddenBootsModifiers::adopt);
    }
}
