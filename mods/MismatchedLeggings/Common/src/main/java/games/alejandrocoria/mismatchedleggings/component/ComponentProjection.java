package games.alejandrocoria.mismatchedleggings.component;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentRule;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentValueMerger;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ComponentProjection {
    private static final ComponentValueMerger VALUES = new ComponentValueMerger(ComponentProjection::warnOnce);
    private static final Set<String> REPORTED_PROBLEMS = ConcurrentHashMap.newKeySet();

    private ComponentProjection() {
    }

    public static Result project(ItemStack first, ItemStack second, ComponentRuleSet rules) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(rules, "rules");

        DataComponentMap.Builder primary = DataComponentMap.builder();
        DataComponentMap.Builder inverse = DataComponentMap.builder();

        Set<DataComponentType<?>> types = Collections.newSetFromMap(new IdentityHashMap<>());
        types.addAll(first.getComponents().keySet());
        types.addAll(second.getComponents().keySet());
        rules.rules().keySet().forEach(id -> BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(id).ifPresent(types::add));
        types.removeIf(type -> !ComponentRuleSupport.isManaged(type));

        for (DataComponentType<?> type : types) {
            projectType(first, second, rules, type, primary, inverse);
        }

        return new Result(primary.build(), inverse.build(), types);
    }

    private static <T> void projectType(
            ItemStack first,
            ItemStack second,
            ComponentRuleSet rules,
            DataComponentType<T> type,
            DataComponentMap.Builder primary,
            DataComponentMap.Builder inverse) {
        var id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        Values<T> values = projectValues(type, first.get(type), second.get(type), rules.ruleFor(id));
        if (values.primary() != null) {
            primary.set(type, values.primary());
        }
        if (values.inverse() != null) {
            inverse.set(type, values.inverse());
        }
    }

    static <T> Values<T> projectValues(DataComponentType<T> type, T first, T second, ComponentRule rule) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(rule, "rule");
        var resolved = ComponentRuleSupport.resolve(type, rule);
        if (resolved.isEmpty()) {
            return new Values<>(null, null);
        }

        if (!rule.equals(resolved.orElseThrow())) {
            warnOnce(type, "unsupported rule " + rule + "; using default " + resolved.orElseThrow());
            rule = resolved.orElseThrow();
        }

        var values = VALUES.merge(type, first, second, rule);
        return new Values<>(values.primary(), values.inverse());
    }

    private static void warnOnce(DataComponentType<?> type, String problem) {
        String key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type) + ": " + problem;
        if (REPORTED_PROBLEMS.add(key)) {
            LeggingsContent.LOG.warn("Component projection for {}: {}", type, problem);
        }
    }

    /** For a managed type, absence from an orientation's map is an explicit removal. */
    public record Result(DataComponentMap primary, DataComponentMap inverse, Set<DataComponentType<?>> managedTypes) {
        public Result {
            Objects.requireNonNull(primary, "primary");
            Objects.requireNonNull(inverse, "inverse");
            managedTypes = Set.copyOf(managedTypes);
        }
    }

    record Values<T>(T primary, T inverse) {
    }
}
