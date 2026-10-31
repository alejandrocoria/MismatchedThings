package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.NeoForgeCompositionAttributes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/** Confirms the production adapter snapshots native effective modifiers, not just stored components. */
final class NeoForgeCompositionChecks {
    static void register() {
        NeoForge.EVENT_BUS.addListener((ItemAttributeModifierEvent event) -> {
            if (event.getItemStack().is(Items.IRON_LEGGINGS) && event.getItemStack().has(CompositionFixture.CHARGE)) {
                // Mutation must affect only the adapter's defensive query copy.
                event.getItemStack().set(DataComponents.REPAIR_COST, 99);
                event.addModifier(Attributes.LUCK, new AttributeModifier(Identifier.parse("leggings_skeleton_test:effective"),
                        6, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.LEGS);
            }
        });
        CompositionChecks.effectiveAttributeExpected = true;
        CompositionChecks.nativeQueryCheck = source -> {
            var before = source.copy();
            var modifiers = new NeoForgeCompositionAttributes().query(source);
            SkeletonChecks.require(ItemStack.matches(source, before), "Native adapter mutated its caller's stack");
            SkeletonChecks.require(modifiers.modifiers().stream().anyMatch(entry -> entry.attribute() == Attributes.LUCK
                            && entry.modifier().id().equals(Identifier.parse("leggings_skeleton_test:effective"))),
                    "Native query lost the event modifier/canonical holder");
        };
    }
}
