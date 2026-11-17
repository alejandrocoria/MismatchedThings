package spike.aether;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import games.alejandrocoria.mismatchedboots.data.SuspendedBootsData;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Experiment at the input boundary, before either product's existing suspension codec. */
public final class LocalConversion {
    private static final String SET = "aether_ii:armor_set";
    private static final List<String> SIDES = List.of("left", "right");

    private LocalConversion() {}

    public static <T> T convert(DynamicOps<T> ops, T input) {
        var root = new Dynamic<>(ops, input);
        String product = switch (root.get("id").asString("")) {
            case "mismatchedboots:mixed_boots" -> "boots";
            case "mismatchedleggings:mixed_leggings" -> "leggings";
            default -> "";
        };
        if (product.isEmpty() || !newNativeCodec(ops)) {
            return input;
        }
        String prefix = "mismatched" + product + ":";
        String history = prefix + "mixed_" + product + "_data";
        String archive = prefix + "suspended_" + product;
        return root.update("components", patch -> {
            if (patch.get(archive).result().isEmpty()) {
                return present(patch, history) ? patch(patch, history) : patch;
            }
            // Only the original owned patch is migrated; passive projection/conflicts keep their exact meaning.
            var raw = patch.get(archive).result().orElseThrow();
            if (product.equals("boots")) {
                var saved = SuspendedBootsData.CODEC.parse(raw).getOrThrow();
                var original = new Dynamic<>(NbtOps.INSTANCE, saved.original());
                if (!present(original, history)) { return patch; }
                var migrated = (CompoundTag) patch(original, history).getValue();
                if (migrated.equals(saved.original())) { return patch; }
                var updated = saved.quarantined() ? SuspendedBootsData.quarantined(migrated, saved.conflicts())
                        : SuspendedBootsData.of(migrated, saved.projection(), saved.conflicts());
                return patch.set(archive, new Dynamic<>(ops, SuspendedBootsData.CODEC.encodeStart(ops, updated).getOrThrow()));
            }
            var saved = SuspendedLeggingsData.CODEC.parse(raw).getOrThrow();
            var original = new Dynamic<>(NbtOps.INSTANCE, saved.original());
            if (!present(original, history)) { return patch; }
            var migrated = (CompoundTag) patch(original, history).getValue();
            if (migrated.equals(saved.original())) { return patch; }
            var updated = saved.quarantined() ? SuspendedLeggingsData.quarantined(migrated, saved.conflicts())
                    : SuspendedLeggingsData.of(migrated, saved.projection(), saved.conflicts());
            return patch.set(archive, new Dynamic<>(ops, SuspendedLeggingsData.CODEC.encodeStart(ops, updated).getOrThrow()));
        }).getValue();
    }

    private static <T> boolean newNativeCodec(DynamicOps<T> ops) {
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(Identifier.parse(SET));
        if (type.isEmpty() || type.get().codec() == null) { return false; }
        var text = new Dynamic<>(ops, ops.createString("aether_ii:armor/neptune"));
        var wrapped = text.emptyMap().set("armor", text);
        return type.get().codec().parse(ops, text.getValue()).isError()
                && type.get().codec().parse(ops, wrapped.getValue()).result().isPresent();
    }

    private static boolean present(Dynamic<?> patch, String history) {
        var data = patch.get(history);
        for (String side : SIDES) {
            String raw = data.get(side).get("id").asString("");
            var id = Identifier.tryParse(raw);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) { return false; }
        }
        return true;
    }

    private static <T> Dynamic<T> patch(Dynamic<T> patch, String history) {
        return field(patch).update(history, data -> {
            var result = data;
            for (String side : SIDES) {
                if (result.get(side).get("components").result().isPresent()) {
                    result = result.update(side, source -> source.update("components", LocalConversion::field));
                }
            }
            return result;
        });
    }

    private static <T> Dynamic<T> field(Dynamic<T> patch) {
        var old = patch.get(SET).asString().result();
        if (old.isEmpty()) { return patch; }
        var wrapped = patch.emptyMap().set("armor", patch.createString(old.get()));
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(Identifier.parse(SET)).orElseThrow();
        return type.codec().parse(patch.getOps(), wrapped.getValue()).result().isPresent() ? patch.set(SET, wrapped) : patch;
    }

}
