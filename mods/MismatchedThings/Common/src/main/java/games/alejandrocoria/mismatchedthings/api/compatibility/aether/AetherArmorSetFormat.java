package games.alejandrocoria.mismatchedthings.api.compatibility.aether;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.List;

/** The known alpha.4.1 field upgrade only; callers own item/history admission and complete decode success. */
public final class AetherArmorSetFormat {
    private static final Identifier SET = Identifier.parse("aether_ii:armor_set");
    private static final List<String> SIDES = List.of("left", "right");

    private AetherArmorSetFormat() {
    }

    /** Leaves other components and opaque custom data alone, including removed-component entries. */
    public static <T> T upgradePatch(DynamicOps<T> ops, T patch, String historyKey) {
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(SET);
        var codec = type.map(value -> value.codec()).orElse(null);
        return codec == null ? patch : upgradePatch(ops, patch, historyKey, codec);
    }

    static <T> T upgradePatch(DynamicOps<T> ops, T patch, String historyKey, Codec<?> codec) {
        var original = new Dynamic<>(ops, patch);
        var updated = field(original, codec).update(historyKey, history -> {
            var result = history;
            for (String side : SIDES) {
                if (result.get(side).get("components").result().isPresent()) {
                    result = result.update(side, source -> source.update("components", value -> field(value, codec)));
                }
            }
            return result;
        });
        return patch.equals(updated.getValue()) ? patch : updated.getValue();
    }

    private static <T> Dynamic<T> field(Dynamic<T> patch, Codec<?> codec) {
        var old = patch.get(SET.toString()).result();
        if (old.isEmpty() || old.get().asString().result().isEmpty()) {
            return patch;
        }

        var wrapped = patch.emptyMap().set("armor", old.get());
        try {
            // A native codec that still accepts strings owns that representation; do not rewrite it.
            if (codec.parse(old.get()).isError() && codec.parse(wrapped).result().isPresent()) {
                return patch.set(SET.toString(), wrapped);
            }
        } catch (RuntimeException unreadable) {
            // An optional foreign codec cannot turn an otherwise preservable input into a failed load.
        }
        return patch;
    }
}
