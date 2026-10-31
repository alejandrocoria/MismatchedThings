package games.alejandrocoria.mismatchedleggings.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import games.alejandrocoria.mismatchedleggings.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedleggings.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedthings.api.composition.AttributePolicy;
import games.alejandrocoria.mismatchedthings.api.config.DynamicConfigPart;
import games.alejandrocoria.mismatchedthings.api.config.EnumConfigEntry;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AttributeRulesConfigPart implements DynamicConfigPart {
    static final String SECTION = "attributes";
    private static final String FALLBACK = "fallback";
    private static final String POLICY = "policy";
    private static final String SECTION_COMMENT = "Attribute modifier composition rules. Available policies: "
            + "average, combine, minimum, maximum, separate, reject.";
    private static final String FALLBACK_COMMENT = "Policy used by valid attribute IDs without an explicit rule.";

    private final EnumConfigEntry<AttributePolicy> fallback = new EnumConfigEntry<>(
            FALLBACK,
            AttributeRuleDefaults.FALLBACK,
            FALLBACK_COMMENT,
            AttributePolicy::fromSerializedName,
            AttributePolicy::serializedName);
    private AttributeRuleSet resolved = AttributeRuleDefaults.ruleSet();

    @Override
    public boolean load(CommentedConfig root) {
        boolean dirty = root.valueMap().keySet().stream().anyMatch(key -> !SECTION.equals(key));
        Object rawSection = root.valueMap().get(SECTION);
        if (!(rawSection instanceof CommentedConfig attributes)) {
            reset();
            return true;
        }

        dirty |= fallback.load(attributes);
        LinkedHashMap<Identifier, AttributePolicy> rules = new LinkedHashMap<>(AttributeRuleDefaults.rules());
        List<Map.Entry<Identifier, AttributePolicy>> customRules = new ArrayList<>();

        for (Map.Entry<String, Object> entry : attributes.valueMap().entrySet()) {
            String rawId = entry.getKey();
            if (FALLBACK.equals(rawId)) {
                continue;
            }

            Identifier id = parseExplicitIdentifier(rawId);
            if (id == null) {
                LeggingsContent.LOG.warn("Discarding invalid attribute ID '{}' from the MismatchedLeggings config", rawId);
                dirty = true;
                continue;
            }
            if (!(entry.getValue() instanceof CommentedConfig category)) {
                LeggingsContent.LOG.warn("Discarding attribute rule '{}' because it is not a TOML table", rawId);
                dirty = true;
                continue;
            }

            boolean categoryDirty = category.valueMap().keySet().stream().anyMatch(key -> !POLICY.equals(key));
            Object rawPolicy = category.valueMap().get(POLICY);
            AttributePolicy policy = rawPolicy instanceof String value
                                     ? AttributePolicy.fromSerializedName(value).orElse(null)
                                     : null;
            if (policy == null) {
                policy = AttributeRuleDefaults.rules().getOrDefault(id, fallback.get());
                categoryDirty = true;
            }
            if (categoryDirty) {
                LeggingsContent.LOG.warn("Cleaning invalid or unknown values from attribute rule '{}'", rawId);
                dirty = true;
            }

            if (rules.containsKey(id)) {
                rules.put(id, policy);
            } else {
                customRules.add(Map.entry(id, policy));
            }
        }

        customRules.sort(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)));
        customRules.forEach(entry -> rules.put(entry.getKey(), entry.getValue()));
        resolved = new AttributeRuleSet(fallback.get(), rules);

        return dirty;
    }

    @Override
    public void save(CommentedConfig root) {
        CommentedConfig attributes = root.createSubConfig();
        fallback.save(attributes);
        for (Map.Entry<Identifier, AttributePolicy> rule : resolved.rules().entrySet()) {
            CommentedConfig category = attributes.createSubConfig();
            category.set(POLICY, rule.getValue().serializedName());
            attributes.set(List.of(rule.getKey().toString()), category);
        }

        root.set(SECTION, attributes);
        root.setComment(SECTION, SECTION_COMMENT);
    }

    @Override
    public void reset() {
        fallback.reset();
        resolved = AttributeRuleDefaults.ruleSet();
    }

    AttributeRuleSet resolved() {
        return resolved;
    }

    private static Identifier parseExplicitIdentifier(String value) {
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':') || separator == value.length() - 1) {
            return null;
        }

        try {
            return Identifier.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
