package games.alejandrocoria.mismatchedboots.composition;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.platform.Services;
import games.alejandrocoria.mismatchedthings.api.composition.AttributeMerger;
import games.alejandrocoria.mismatchedthings.api.composition.AttributePolicy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class AttributeComposer {
    private AttributeComposer() {
    }

    static Result compose(ItemStack first, ItemStack second, AttributeRuleSet rules) {
        return composeModifiers(Services.PLATFORM.getCompositionAttributeModifiers(first),
                                Services.PLATFORM.getCompositionAttributeModifiers(second), rules);
    }

    /** Pure supplied-input entry for frozen optional-integration projections; no stack queries. */
    public static ItemAttributeModifiers composePrimary(ItemAttributeModifiers first, ItemAttributeModifiers second, AttributeRuleSet rules) {
        return composeModifiers(first, second, rules).primary();
    }

    private static Result composeModifiers(ItemAttributeModifiers first, ItemAttributeModifiers second, AttributeRuleSet rules) {
        var result = AttributeMerger.merge(first, second, id -> AttributePolicy.valueOf(rules.policyFor(id).name()));
        return new Result(result.primary(), result.inverse());
    }

    record Result(ItemAttributeModifiers primary, ItemAttributeModifiers inverse) {
    }
}
