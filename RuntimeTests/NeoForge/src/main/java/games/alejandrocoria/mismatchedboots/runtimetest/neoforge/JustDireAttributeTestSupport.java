package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.composition.AttributeComposer;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.platform.Services;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import java.util.ArrayList;

/** Native-input controls and a competing transformation only; no production projection oracle. */
public final class JustDireAttributeTestSupport {
    public static boolean earlierOverride;
    private JustDireAttributeTestSupport() {}
    static ItemAttributeModifiers composed(ItemStack left, ItemStack right, AttributeRuleSet rules) {
        return AttributeComposer.composePrimary(Services.PLATFORM.getCompositionAttributeModifiers(left),
                Services.PLATFORM.getCompositionAttributeModifiers(right), rules);
    }
    public static ItemAttributeModifiers earlier(ItemStack root, ItemAttributeModifiers defaults) {
        if (!earlierOverride || !(root.getItem() instanceof MixedBootsItem)) { return defaults; }
        var result = new ArrayList<ItemAttributeModifiers.Entry>();
        for (var entry : defaults.modifiers()) {
            if (BuiltInRegistries.ATTRIBUTE.getKey(entry.attribute().value()).equals(Identifier.withDefaultNamespace("armor"))) {
                result.add(new ItemAttributeModifiers.Entry(entry.attribute(),
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(entry.modifier().id(), 17,
                                entry.modifier().operation()), entry.slot(), entry.display()));
            } else { result.add(entry); }
        }
        return new ItemAttributeModifiers(java.util.List.copyOf(result));
    }
    @SuppressWarnings("unchecked") static <T> void set(ItemStack stack, String path, T value) {
        stack.set((DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value(), value);
    }
    @SuppressWarnings("unchecked") static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value();
    }
}
