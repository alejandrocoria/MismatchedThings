package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.missingmods;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/** Research only: preserve entire unreadable component values before native partial decoding loses them. */
public final class ArchiveItemCodec extends MapCodec<ItemStack> {
    public static final String ARCHIVE = "mismatchedboots_runtime_test:missing_mod_archive";
    private static final String EQUIPPABLE = "minecraft:equippable";
    private final MapCodec<ItemStack> delegate;

    public ArchiveItemCodec(MapCodec<ItemStack> delegate) { this.delegate = delegate; }

    @Override public <T> Stream<T> keys(DynamicOps<T> ops) { return delegate.keys(ops); }

    @Override public <T> RecordBuilder<T> encode(ItemStack stack, DynamicOps<T> ops, RecordBuilder<T> prefix) {
        return delegate.encode(stack, ops, prefix);
    }

    @Override public <T> DataResult<ItemStack> decode(DynamicOps<T> ops, MapLike<T> input) {
        T id = input.get("id"), encoded = input.get("components");
        if (id == null || encoded == null || !ops.getStringValue(id).result().orElse("").equals("mismatchedboots:mixed_boots")) {
            return delegate.decode(ops, input);
        }
        var mapResult = ops.getMap(encoded);
        if (mapResult.result().isEmpty()) { return delegate.decode(ops, input); }
        Map<String, T> values = entries(ops, mapResult.getOrThrow());
        if (!values.containsKey("mismatchedboots:mixed_boots_data") && !values.containsKey(ARCHIVE)) {
            return delegate.decode(ops, input); // Bare /give shells do not acquire invented history.
        }
        T archive = values.remove(ARCHIVE);
        if (archive != null) {
            var saved = ops.getMap(archive);
            if (saved.result().isEmpty()) { return DataResult.error(() -> "Malformed research archive"); }
            var original = entries(ops, saved.getOrThrow());
            // The temporary feet-only shell is not a replacement for the protected original.
            if (original.containsKey(EQUIPPABLE) && feetShell(ops).equals(values.get(EQUIPPABLE))) {
                values.remove(EQUIPPABLE);
            }
            for (var entry : original.entrySet()) {
                // Never silently choose between a protected old value and an externally supplied replacement.
                if (values.containsKey(entry.getKey())) {
                    return DataResult.error(() -> "Conflicting protected component: " + entry.getKey());
                }
                values.put(entry.getKey(), entry.getValue());
            }
        }
        Map<String, T> readable = new LinkedHashMap<>(), protectedValues = new LinkedHashMap<>();
        for (var entry : values.entrySet()) {
            T singleton = ops.createMap(Stream.of(Pair.of(ops.createString(entry.getKey()), entry.getValue())));
            // A partial result is NOT sufficient: protect the complete original value, not only its failing members.
            var decoded = DataComponentPatch.CODEC.parse(ops, singleton).result();
            boolean complete = decoded.isPresent() && DataComponentPatch.CODEC.encodeStart(ops, decoded.get())
                    .result().filter(singleton::equals).isPresent();
            (complete ? readable : protectedValues).put(entry.getKey(), entry.getValue());
        }
        if (!protectedValues.isEmpty()) {
            readable.put(ARCHIVE, createMap(ops, protectedValues));
            if (protectedValues.containsKey(EQUIPPABLE)) { readable.put(EQUIPPABLE, feetShell(ops)); }
        }
        T replacement = createMap(ops, readable);
        T root = ops.createMap(input.entries().map(entry -> ops.getStringValue(entry.getFirst()).result().orElse("").equals("components")
                ? Pair.of(entry.getFirst(), replacement) : entry));
        return ops.getMap(root).flatMap(map -> delegate.decode(ops, map));
    }

    private static <T> Map<String, T> entries(DynamicOps<T> ops, MapLike<T> map) {
        Map<String, T> result = new LinkedHashMap<>();
        map.entries().forEach(entry -> result.put(ops.getStringValue(entry.getFirst()).getOrThrow(), entry.getSecond()));
        return result;
    }

    private static <T> T createMap(DynamicOps<T> ops, Map<String, T> values) {
        return ops.createMap(values.entrySet().stream().map(entry -> Pair.of(ops.createString(entry.getKey()), entry.getValue())));
    }

    private static <T> T feetShell(DynamicOps<T> ops) {
        return createMap(ops, Map.of("slot", ops.createString("feet")));
    }
}
