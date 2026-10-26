package games.alejandrocoria.mismatchedboots.component;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Shared policy validation for projection and configuration. */
public final class ComponentRuleSupport {
    private static final Set<DataComponentType<?>> FIXED_TYPES = Set.of(
            DataComponents.MAX_STACK_SIZE,
            DataComponents.MAX_DAMAGE,
            DataComponents.DAMAGE,
            DataComponents.REPAIR_COST,
            DataComponents.CUSTOM_NAME,
            DataComponents.ITEM_NAME,
            DataComponents.ITEM_MODEL,
            DataComponents.ENCHANTMENTS,
            DataComponents.ATTRIBUTE_MODIFIERS,
            DataComponents.EQUIPPABLE,
            DataComponents.REPAIRABLE);

    private ComponentRuleSupport() {
    }

    public static boolean isManaged(DataComponentType<?> type) {
        Objects.requireNonNull(type, "type");
        return !FIXED_TYPES.contains(type) && ModContent.mixedBootsDataType().filter(type::equals).isEmpty()
                && !ModContent.SUSPENDED_BOOTS_ID.equals(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type));
    }

    /** Empty means a fixed/dedicated field; unsupported rules resolve to the component's default. */
    public static Optional<ComponentRule> resolve(DataComponentType<?> type, ComponentRule requested) {
        Objects.requireNonNull(requested, "requested");
        if (!isManaged(type)) {
            return Optional.empty();
        }
        if (supports(type, requested)) {
            return Optional.of(requested);
        }

        return Optional.of(ComponentRuleDefaults.ruleSet()
                                                .ruleFor(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type)));
    }

    private static boolean supports(DataComponentType<?> type, ComponentRule rule) {
        return switch (rule.policy()) {
            case OWNER, OMIT, SAME, SHARE -> true;
            case INTERSECTION -> type == DataComponents.DAMAGE_RESISTANT;
            case AVERAGE, MINIMUM, MAXIMUM -> type == DataComponents.ENCHANTABLE
                                              ? rule.number() == null
                                              : !ComponentRuleDefaults.rules().containsKey(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type))
                                                && rule.number() != null && !type.isTransient();
        };
    }
}
