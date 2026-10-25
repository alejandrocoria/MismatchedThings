package games.alejandrocoria.mismatchedboots.composition;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.platform.Services;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class AttributeComposer {
    private AttributeComposer() {
    }

    static Result compose(ItemStack first, ItemStack second, AttributeRuleSet rules) {
        return composeModifiers(Services.PLATFORM.getCompositionAttributeModifiers(first),
                                Services.PLATFORM.getCompositionAttributeModifiers(second), rules);
    }

    /** Pure supplied-input entry for frozen optional-integration projections; no stack queries. */
    public static ItemAttributeModifiers composePrimary(
            ItemAttributeModifiers first, ItemAttributeModifiers second, AttributeRuleSet rules) {
        return composeModifiers(first, second, rules).primary();
    }

    private static Result composeModifiers(ItemAttributeModifiers first, ItemAttributeModifiers second, AttributeRuleSet rules) {
        Map<Identifier, AttributeGroup> attributes = new TreeMap<>();
        collect(attributes, first, Side.FIRST);
        collect(attributes, second, Side.SECOND);

        ItemAttributeModifiers.Builder primary = ItemAttributeModifiers.builder();
        ItemAttributeModifiers.Builder inverse = ItemAttributeModifiers.builder();
        attributes.forEach((attributeId, group) -> composeAttribute(
                primary, inverse, rules.policyFor(attributeId), attributeId, group));
        return new Result(primary.build(), inverse.build());
    }

    private static void collect(
            Map<Identifier, AttributeGroup> attributes, ItemAttributeModifiers modifiers, Side side) {
        for (int index = 0; index < modifiers.modifiers().size(); index++) {
            ItemAttributeModifiers.Entry entry = modifiers.modifiers().get(index);
            if (!Double.isFinite(entry.modifier().amount())) {
                throw new IllegalArgumentException(
                        "Non-finite attribute modifier at " + side.serializedName + " index " + index);
            }
            Identifier attributeId = entry.attribute()
                                          .unwrapKey()
                                          .map(key -> key.identifier())
                                          .orElseThrow(() -> new IllegalArgumentException(
                                                  "Attribute modifier uses an unregistered attribute"));
            attributes.computeIfAbsent(attributeId, ignored -> new AttributeGroup())
                      .add(side, entry);
        }
    }

    private static void composeAttribute(
            ItemAttributeModifiers.Builder primary,
            ItemAttributeModifiers.Builder inverse,
            AttributePolicy policy,
            Identifier attributeId,
            AttributeGroup group) {
        if (policy == AttributePolicy.REJECT) {
            throw new IllegalArgumentException("Attribute is rejected by policy: " + attributeId);
        }
        if (policy == AttributePolicy.SEPARATE) {
            copy(primary, group.firstEntries);
            copy(inverse, group.secondEntries);
            return;
        }

        group.identities.forEach((modifierId, identity) -> {
            if (!identity.isNormalizable()) {
                copy(primary, identity.firstEntries);
                copy(inverse, identity.secondEntries);
                return;
            }

            ItemAttributeModifiers.Entry first = identity.firstEntry();
            ItemAttributeModifiers.Entry second = identity.secondEntry();
            ItemAttributeModifiers.Entry metadata = first != null ? first : second;
            AttributeModifier.Operation operation = metadata.modifier().operation();
            double firstContribution = contribution(first, operation);
            double secondContribution = contribution(second, operation);
            double amount = composeAmount(policy, operation, firstContribution, secondContribution);
            if (!Double.isFinite(amount)) {
                throw new IllegalArgumentException(
                        "Non-finite attribute result for " + attributeId + " and " + modifierId);
            }
            amount = canonicalZero(amount);
            if (amount == 0.0D) {
                return;
            }

            AttributeModifier modifier = new AttributeModifier(modifierId, amount, operation);
            add(primary, metadata.attribute(), modifier, metadata);
            add(inverse, metadata.attribute(), modifier, metadata);
        });
    }

    private static double contribution(
            ItemAttributeModifiers.Entry entry, AttributeModifier.Operation operation) {
        if (entry == null) {
            return operation == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL ? 1.0D : 0.0D;
        }
        double amount = entry.modifier().amount();
        if (operation == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            double factor = 1.0D + amount;
            if (!Double.isFinite(factor)) {
                throw new IllegalArgumentException("Non-finite total-multiplication factor");
            }
            return factor;
        }
        return amount;
    }

    private static double composeAmount(
            AttributePolicy policy,
            AttributeModifier.Operation operation,
            double first,
            double second) {
        double result = switch (policy) {
            case AVERAGE -> first * 0.5D + second * 0.5D;
            case COMBINE -> operation == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                            ? first * second
                            : first + second;
            case MINIMUM -> Math.min(first, second);
            case MAXIMUM -> Math.max(first, second);
            case SEPARATE, REJECT -> throw new IllegalStateException("Non-arithmetic attribute policy: " + policy);
        };
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("Non-finite attribute arithmetic result");
        }
        if (operation == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            result -= 1.0D;
        }
        return result;
    }

    private static double canonicalZero(double amount) {
        return amount == 0.0D ? 0.0D : amount;
    }

    private static void copy(
            ItemAttributeModifiers.Builder target, List<ItemAttributeModifiers.Entry> entries) {
        entries.forEach(entry -> add(target, entry.attribute(), entry.modifier(), entry));
    }

    private static void add(
            ItemAttributeModifiers.Builder target,
            Holder<Attribute> attribute,
            AttributeModifier modifier,
            ItemAttributeModifiers.Entry metadata) {
        target.add(attribute, modifier, metadata.slot(), metadata.display());
    }

    record Result(ItemAttributeModifiers primary, ItemAttributeModifiers inverse) {
    }

    private static final class AttributeGroup {
        private final List<ItemAttributeModifiers.Entry> firstEntries = new ArrayList<>();
        private final List<ItemAttributeModifiers.Entry> secondEntries = new ArrayList<>();
        private final Map<Identifier, IdentityGroup> identities = new TreeMap<>();

        private void add(Side side, ItemAttributeModifiers.Entry entry) {
            side.select(firstEntries, secondEntries).add(entry);
            identities.computeIfAbsent(entry.modifier().id(), ignored -> new IdentityGroup())
                      .add(side, entry);
        }
    }

    private static final class IdentityGroup {
        private final List<ItemAttributeModifiers.Entry> firstEntries = new ArrayList<>();
        private final List<ItemAttributeModifiers.Entry> secondEntries = new ArrayList<>();

        private void add(Side side, ItemAttributeModifiers.Entry entry) {
            side.select(firstEntries, secondEntries).add(entry);
        }

        private boolean isNormalizable() {
            if (firstEntries.size() > 1 || secondEntries.size() > 1) {
                return false;
            }
            ItemAttributeModifiers.Entry first = firstEntry();
            ItemAttributeModifiers.Entry second = secondEntry();
            return first == null
                    || second == null
                    || first.modifier().operation() == second.modifier().operation()
                    && first.slot() == second.slot()
                    && first.display().equals(second.display());
        }

        private ItemAttributeModifiers.Entry firstEntry() {
            return firstEntries.isEmpty() ? null : firstEntries.getFirst();
        }

        private ItemAttributeModifiers.Entry secondEntry() {
            return secondEntries.isEmpty() ? null : secondEntries.getFirst();
        }
    }

    private enum Side {
        FIRST("first"),
        SECOND("second");

        private final String serializedName;

        Side(String serializedName) {
            this.serializedName = serializedName;
        }

        private <T> List<T> select(List<T> first, List<T> second) {
            return this == FIRST ? first : second;
        }
    }
}
