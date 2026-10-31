package games.alejandrocoria.mismatchedthings.api.composition;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.enchantment.Enchantable;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Value arithmetic/equivalence only. Consumers resolve supported rules and own warning deduplication. */
public final class ComponentValueMerger {
    private final BiConsumer<DataComponentType<?>, String> warnings;

    public ComponentValueMerger(BiConsumer<DataComponentType<?>, String> warnings) {
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    public <T> Values<T> merge(DataComponentType<T> type, T first, T second, ComponentRule rule) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(rule, "rule");
        return switch (rule.policy()) {
            case OWNER -> owner(first, second);
            case OMIT -> new Values<>(null, null);
            case SAME -> first != null && equivalentValues(type, first, second)
                         ? shared(type, first) : new Values<>(null, null);
            case SHARE -> share(type, first, second);
            case AVERAGE, MINIMUM, MAXIMUM -> numeric(type, first, second, rule);
            case INTERSECTION -> intersection(type, first, second);
        };
    }

    private <T> Values<T> owner(T first, T second) {
        return new Values<>(first, second);
    }

    private <T> Values<T> share(DataComponentType<T> type, T first, T second) {
        if (first == null) {
            return shared(type, second);
        }
        if (second == null || equivalentValues(type, first, second)) {
            return shared(type, first);
        }

        warn(type, "share conflict; retaining owner values");
        return owner(first, second);
    }

    private <T> Values<T> intersection(DataComponentType<T> type, T first, T second) {
        if (first == null || second == null) {
            return new Values<>(null, null);
        }

        DamageResistant overlap = intersectDamage((DamageResistant) first, (DamageResistant) second);
        @SuppressWarnings("unchecked") T value = (T) overlap;
        return new Values<>(value, value);
    }

    private DamageResistant intersectDamage(DamageResistant first, DamageResistant second) {
        var firstTag = first.types().unwrapKey();
        var secondTag = second.types().unwrapKey();
        if (firstTag.isPresent() || secondTag.isPresent()) {
            if (firstTag.isPresent() && firstTag.equals(secondTag)) {
                return first;
            }

            warn(DataComponents.DAMAGE_RESISTANT, "dynamic tag overlap cannot be represented; omitting resistance");
            return null;
        }

        Map<ResourceKey<DamageType>, Holder<DamageType>> left = damageIds(first.types());
        Map<ResourceKey<DamageType>, Holder<DamageType>> right = damageIds(second.types());
        if (left == null || right == null) {
            warn(DataComponents.DAMAGE_RESISTANT, "unkeyed damage type; omitting resistance");
            return null;
        }

        left.keySet().retainAll(right.keySet());
        return left.isEmpty() ? null : orderedDamage(left);
    }

    private DamageResistant orderedDamage(Map<ResourceKey<DamageType>, Holder<DamageType>> ids) {
        return new DamageResistant(HolderSet.direct(ids.entrySet().stream()
                                                       .sorted(Comparator.comparing(entry -> entry.getKey().identifier().toString()))
                                                       .map(Map.Entry::getValue).toList()));
    }

    private <T> Values<T> shared(DataComponentType<T> type, T value) {
        if (type == DataComponents.DAMAGE_RESISTANT && value != null) {
            DamageResistant resistance = (DamageResistant) value;
            if (resistance.types().unwrapKey().isEmpty()) {
                var ids = damageIds(resistance.types());
                if (ids != null) {
                    @SuppressWarnings("unchecked") T ordered = (T) orderedDamage(ids);
                    value = ordered;
                }
            }
        }

        return new Values<>(value, value);
    }

    private static Map<ResourceKey<DamageType>, Holder<DamageType>> damageIds(HolderSet<DamageType> types) {
        Map<ResourceKey<DamageType>, Holder<DamageType>> ids = new LinkedHashMap<>();
        for (Holder<DamageType> holder : types) {
            var key = holder.unwrapKey();
            if (key.isEmpty()) {
                return null;
            }
            ids.putIfAbsent(key.orElseThrow(), holder);
        }

        return ids;
    }

    public static <T> boolean equivalentValues(DataComponentType<T> type, T first, T second) {
        Objects.requireNonNull(type, "type");
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        if (type != DataComponents.DAMAGE_RESISTANT) {
            return Objects.equals(first, second);
        }

        HolderSet<DamageType> left = ((DamageResistant) first).types();
        HolderSet<DamageType> right = ((DamageResistant) second).types();
        var leftTag = left.unwrapKey();
        var rightTag = right.unwrapKey();
        if (leftTag.isPresent() || rightTag.isPresent()) {
            return leftTag.isPresent() && leftTag.equals(rightTag);
        }

        Map<ResourceKey<DamageType>, Holder<DamageType>> leftIds = damageIds(left);
        Map<ResourceKey<DamageType>, Holder<DamageType>> rightIds = damageIds(right);
        return leftIds != null && rightIds != null && leftIds.keySet().equals(rightIds.keySet());
    }

    private <T> Values<T> numeric(DataComponentType<T> type, T first, T second, ComponentRule rule) {
        if (type == DataComponents.ENCHANTABLE) {
            return enchantable(type, first, second, rule);
        }
        if (!matchesKind(first, rule.number()) || !matchesKind(second, rule.number())) {
            warn(type, "numeric type mismatch; retaining owner values");
            return owner(first, second);
        }

        validateInput(type, first);
        validateInput(type, second);
        if (first == null || second == null) {
            return owner(first, second);
        }

        Object value;
        if (rule.number() == ComponentNumericKind.INT && first instanceof Integer left && second instanceof Integer right) {
            value = calculate(left.longValue(), right.longValue(), rule.policy()).intValueExact();
        } else if (rule.number() == ComponentNumericKind.LONG && first instanceof Long left && second instanceof Long right) {
            value = calculate(left, right, rule.policy()).longValueExact();
        } else {
            warn(type, "numeric type mismatch; retaining owner values");
            return owner(first, second);
        }

        @SuppressWarnings("unchecked") T projected = (T) value;
        if (!codecAccepts(type, projected)) {
            warn(type, "numeric codec rejected result; retaining owner values");
            return owner(first, second);
        }

        return new Values<>(projected, projected);
    }

    private <T> Values<T> enchantable(DataComponentType<T> type, T first, T second, ComponentRule rule) {
        validateInput(type, first);
        validateInput(type, second);
        if (first == null || second == null) {
            return new Values<>(null, null);
        }

        int left = ((Enchantable) first).value();
        int right = ((Enchantable) second).value();
        int result = calculate(left, right, rule.policy()).intValueExact();
        @SuppressWarnings("unchecked") T projected = (T) new Enchantable(result);
        if (!codecAccepts(type, projected)) {
            warn(type, "enchantable codec rejected result; retaining owner values");
            return owner(first, second);
        }

        return new Values<>(projected, projected);
    }

    private boolean matchesKind(Object value, ComponentNumericKind kind) {
        return value == null || (kind == ComponentNumericKind.INT && value instanceof Integer)
                || (kind == ComponentNumericKind.LONG && value instanceof Long);
    }

    private <T> void validateInput(DataComponentType<T> type, T value) {
        if (value != null && !codecAccepts(type, value)) {
            warn(type, "numeric codec rejected input; rejecting composition");
            throw new IllegalArgumentException("Invalid numeric component input: "
                                                       + BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type));
        }
    }

    private BigInteger calculate(long first, long second, ComponentPolicy policy) {
        return switch (policy) {
            case AVERAGE -> BigInteger.valueOf(first).add(BigInteger.valueOf(second)).shiftRight(1);
            case MINIMUM -> BigInteger.valueOf(Math.min(first, second));
            case MAXIMUM -> BigInteger.valueOf(Math.max(first, second));
            default -> throw new IllegalArgumentException("Not numeric: " + policy);
        };
    }

    private <T> boolean codecAccepts(DataComponentType<T> type, T value) {
        try {
            var codec = type.codecOrThrow();
            JsonElement encoded = codec.encodeStart(JsonOps.INSTANCE, value).result().orElse(null);
            return encoded != null && codec.parse(JsonOps.INSTANCE, encoded).result()
                                           .filter(value::equals).isPresent();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void warn(DataComponentType<?> type, String problem) {
        warnings.accept(type, problem);
    }

    public record Values<T>(T primary, T inverse) {
    }
}
