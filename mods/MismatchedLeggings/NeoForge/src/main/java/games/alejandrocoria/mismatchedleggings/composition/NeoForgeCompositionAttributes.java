package games.alejandrocoria.mismatchedleggings.composition;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class NeoForgeCompositionAttributes implements CompositionAttributes {
    @Override
    public ItemAttributeModifiers query(ItemStack source) {
        var result = ItemAttributeModifiers.builder();
        for (var entry : source.copy().getAttributeModifiers().modifiers()) {
            var key = entry.attribute().unwrapKey().orElseThrow(() -> new IllegalArgumentException("Unregistered source attribute"));
            // Runtime event wrappers may not serialize; persist the canonical holder without dropping entry metadata.
            var holder = BuiltInRegistries.ATTRIBUTE.get(key).orElseThrow(() -> new IllegalArgumentException("Unknown source attribute: " + key));
            result.add(holder, entry.modifier(), entry.slot(), entry.display());
        }
        return result.build();
    }
}
