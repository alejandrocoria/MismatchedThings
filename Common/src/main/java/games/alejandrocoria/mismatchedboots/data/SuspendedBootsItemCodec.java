package games.alejandrocoria.mismatchedboots.data;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Preserves unavailable roots before native decoding; never activates a native partial result. */
public final class SuspendedBootsItemCodec extends MapCodec<ItemStack> {
    public static final String ARCHIVE = "mismatchedboots:suspended_boots";
    private static final String SOURCES = "mismatchedboots:mixed_boots_data";
    private static final String EQUIPPABLE = "minecraft:equippable";
    private static final String ITEM = "mismatchedboots:mixed_boots";
    private static final Set<String> PASSIVE = Set.of("minecraft:max_stack_size",
                                                      "minecraft:max_damage",
                                                      "minecraft:damage",
                                                      "minecraft:custom_name",
                                                      "minecraft:item_name",
                                                      "minecraft:custom_data",
                                                      "minecraft:damage_resistant");
    private final MapCodec<ItemStack> delegate;

    public SuspendedBootsItemCodec(MapCodec<ItemStack> delegate) {
        Set<String> keys = delegate.keys(NbtOps.INSTANCE).map(tag -> tag.asString().orElse("")).collect(Collectors.toSet());
        if (!keys.containsAll(Set.of("id", "count", "components"))) {
            throw new RecoveryRefusalException("Cannot safely install mixed-boots capture: native item fields unavailable");
        }
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
        T id = input.get("id"), components = input.get("components");
        if (id == null || components == null || !ops.getStringValue(id).result().orElse("").equals(ITEM)) {
            return delegate.decode(ops, input);
        }
        Tag encoded = components instanceof Tag tag ? tag : ops.convertTo(NbtOps.INSTANCE, components);
        if (!(encoded instanceof CompoundTag patch) || !patch.contains(SOURCES) && !patch.contains(ARCHIVE)) {
            return delegate.decode(ops, input);
        }
        SuspendedBootsData.validateBudget(patch, SuspendedBootsData.MAX_BYTES, Tag.MAX_DEPTH);
        T count = input.get("count");
        boolean oneItem = count == null || ops.getNumberValue(count).result().map(number -> number.doubleValue() == 1).orElse(false);
        boolean restoring = patch.contains(ARCHIVE);
        if (!restoring && oneItem && sourcesPresent(patch.get(SOURCES))) {
            DataResult<ItemStack> active = read(ops, input, patch);
            if (ready(active)) {
                return active;
            }
        }
        CompoundTag original;
        CompoundTag passive = new CompoundTag();
        CompoundTag conflicts = new CompoundTag();
        boolean quarantined = false;
        if (restoring) {
            var archive = SuspendedBootsData.CODEC.parse(NbtOps.INSTANCE, patch.get(ARCHIVE)).getOrThrow();
            quarantined = archive.quarantined();
            original = archive.original();
            conflicts = archive.conflicts();
            CompoundTag projection = archive.projection();
            for (String key : patch.keySet()) {
                if (key.equals(ARCHIVE)) {
                    continue;
                }
                Tag value = patch.get(key);
                if (value.equals(projection.get(key))) {
                    continue;
                }
                String prior = matchingKey(original, key);
                if (prior != null) {
                    if (!key.equals(prior) || !value.equals(original.get(prior))) {
                        conflicts.put(key, value.copy());
                    }
                } else if (PASSIVE.contains(type(key))) {
                    if (readablePassive(ops, key, value)) {
                        passive.put(key, value.copy());
                    } else {
                        original.put(key, value.copy());
                    }
                } else {
                    conflicts.put(key, value.copy());
                }
            }
            for (String key : projection.keySet()) {
                if (matchingKey(patch, key) == null) {
                    conflicts.put("!" + type(key), new CompoundTag());
                }
            }
        } else {
            original = new CompoundTag();
            for (String key : patch.keySet()) {
                if (PASSIVE.contains(type(key)) && readablePassive(ops, key, patch.get(key))) {
                    passive.put(key, patch.get(key).copy());
                } else {
                    original.put(key, patch.get(key).copy());
                }
            }
        }
        CompoundTag restored = passive.copy();
        original.keySet().forEach(key -> restored.put(key, original.get(key).copy()));
        if (restoring && !quarantined && conflicts.isEmpty() && oneItem && sourcesPresent(restored.get(SOURCES))) {
            DataResult<ItemStack> decoded = read(ops, input, restored);
            if (ready(decoded)) {
                return decoded;
            }
        }
        // The complete gameplay patch is parked even when only one foreign value was unreadable.
        CompoundTag projection = !quarantined && oneItem && admittedHistory(ops, original)
                                 ? SuspendedBootsData.equipmentProjection() : new CompoundTag();
        var archive = quarantined ? SuspendedBootsData.quarantined(original, conflicts)
                                  : SuspendedBootsData.of(original, projection, conflicts);
        CompoundTag suspended = passive.copy();
        projection.keySet().forEach(key -> suspended.put(key, projection.get(key).copy()));
        suspended.put(ARCHIVE, SuspendedBootsData.CODEC.encodeStart(NbtOps.INSTANCE, archive).getOrThrow());
        DataResult<ItemStack> result = read(ops, input, suspended);
        if (!safeShell(result, archive) || ItemStack.validateStrict(result.getOrThrow()).result().isEmpty()) {
            // Individually readable passive values can still be invalid in combination (e.g. stack size + durability).
            // Retain those values too, rather than letting the outer block-entity loader discard the container.
            passive.keySet().forEach(key -> original.put(key, passive.get(key).copy()));
            var opaque = SuspendedBootsData.quarantined(original, conflicts);
            CompoundTag inert = new CompoundTag();
            inert.put(ARCHIVE, SuspendedBootsData.CODEC.encodeStart(NbtOps.INSTANCE, opaque).getOrThrow());
            result = read(ops, input, inert);
            if (!safeShell(result, opaque)) {
                throw new RecoveryRefusalException("Native item decoder cannot preserve an inert mixed root");
            }
        }
        return result;
    }

    public static boolean admittedSources(Tag raw) {
        if (!(raw instanceof CompoundTag data) || !data.keySet().equals(Set.of("left", "right"))) {
            return false;
        }
        for (String side : Set.of("left", "right")) {
            if (!(data.get(side) instanceof CompoundTag source) || source.getStringOr("id", "").isEmpty()
                    || Identifier.tryParse(source.getStringOr("id", "")) == null
                    || source.getStringOr("id", "").equals(ITEM) || source.getStringOr("id", "").equals("minecraft:air")
                    || source.contains("count") && !(source.get("count") instanceof IntTag)
                    || source.getIntOr("count", 1) != 1
                    || source.contains("components") && !(source.get("components") instanceof CompoundTag)) {
                return false;
            }
            var components = source.getCompoundOrEmpty("components");
            if (components.contains(SOURCES) || components.contains(ARCHIVE)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sourceRegistered(Tag raw) {
        if (!(raw instanceof CompoundTag source)) {
            return false;
        }
        Identifier id = Identifier.tryParse(source.getStringOr("id", ""));
        // The item registry's default (air) must not stand in for an absent source.
        return id != null && BuiltInRegistries.ITEM.containsKey(id);
    }

    private static boolean sourcesPresent(Tag raw) {
        return raw instanceof CompoundTag data && sourceRegistered(data.get("left")) && sourceRegistered(data.get("right"));
    }

    private static <T> boolean admittedHistory(DynamicOps<T> ops, CompoundTag original) {
        if (!admittedSources(original.get(SOURCES))
                || !original.getCompoundOrEmpty(EQUIPPABLE).getStringOr("slot", "").equals("feet")) {
            return false;
        }
        var sources = original.getCompoundOrEmpty(SOURCES);
        for (String side : Set.of("left", "right")) {
            if (!sourceRegistered(sources.get(side))) {
                continue;
            }
            try {
                var readable = ItemStack.CODEC.parse(ops, NbtOps.INSTANCE.convertTo(ops, sources.get(side))).result();
                // Missing registrations cannot prove gameplay readiness. Fully readable non-boots,
                // however, are known invalid history and must not acquire an invented feet slot.
                if (readable.isPresent() && !BootHalf.isValidSource(readable.get())) {
                    return false;
                }
            } catch (RuntimeException unreadable) { /* Keep structurally admitted unavailable history opaque. */ }
        }
        return true;
    }

    private static String type(String key) {
        return key.startsWith("!") ? key.substring(1) : key;
    }

    private static String matchingKey(CompoundTag map, String key) {
        String type = type(key);
        return map.contains(type) ? type : map.contains("!" + type) ? "!" + type : null;
    }

    private static boolean ready(DataResult<ItemStack> decoded) {
        var result = decoded.result();
        return result.isPresent() && ItemStack.validateStrict(result.get()).result().isPresent()
                && ModContent.getMixedBootsData(result.get()).filter(data ->
                                                                             BootHalf.isValidSource(data.left().source()) && BootHalf.isValidSource(
                                                                                     data.right().source())).isPresent();
    }

    private static boolean safeShell(DataResult<ItemStack> decoded, SuspendedBootsData expected) {
        var result = decoded.result();
        if (result.isEmpty() || ModContent.suspendedBootsData(result.get()).filter(expected::equals).isEmpty()) {
            return false;
        }
        var root = result.get();
        Equippable equipment = expected.projection().isEmpty() ? null
                                                               : Equippable.builder(EquipmentSlot.FEET).setDamageOnHurt(false).build();
        if (!Objects.equals(root.get(DataComponents.EQUIPPABLE), equipment)) {
            return false;
        }
        // Only our inert shell is checked here, not a foreign codec's migration or re-encoding.
        return root.getComponentsPatch().entrySet().stream().allMatch(entry -> {
            Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.getKey());
            return id != null && (id.toString().equals(ARCHIVE) || id.toString().equals(EQUIPPABLE) || PASSIVE.contains(id.toString()));
        });
    }

    private static <T> boolean readablePassive(DynamicOps<T> ops, String key, Tag value) {
        CompoundTag singleton = new CompoundTag();
        singleton.put(key, value.copy());
        try {
            return DataComponentPatch.CODEC.parse(ops, NbtOps.INSTANCE.convertTo(ops, singleton)).result().isPresent();
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    private <T> DataResult<ItemStack> read(DynamicOps<T> ops, MapLike<T> input, CompoundTag patch) {
        T replacement = NbtOps.INSTANCE.convertTo(ops, patch.copy());
        T root = ops.createMap(input.entries().map(entry -> ops.getStringValue(entry.getFirst()).result().orElse("").equals("components")
                                                            ? com.mojang.datafixers.util.Pair.of(entry.getFirst(), replacement) : entry));
        try {
            return ops.getMap(root).flatMap(map -> delegate.decode(ops, map));
        } catch (RuntimeException failure) {
            return DataResult.error(() -> "Native mixed root decoding failed");
        }
    }
}
