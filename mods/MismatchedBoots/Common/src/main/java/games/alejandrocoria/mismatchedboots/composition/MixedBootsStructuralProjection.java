package games.alejandrocoria.mismatchedboots.composition;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.Optional;

/** Canonical output fields only; admission, source data and final validation stay with callers. */
final class MixedBootsStructuralProjection {
    private MixedBootsStructuralProjection() {
    }

    static void applyIdentity(ItemStack root) {
        root.set(DataComponents.MAX_STACK_SIZE, 1);
        root.set(DataComponents.ITEM_NAME, Component.translatable("item.mismatchedboots.mixed_boots"));
        root.set(DataComponents.ITEM_MODEL, ModContent.MIXED_BOOTS_ID);
    }

    static Equippable projectEquippable(ItemStack owner, ItemStack companion) {
        Equippable source = owner.get(DataComponents.EQUIPPABLE);
        Equippable other = companion.get(DataComponents.EQUIPPABLE);
        if (source == null || other == null) {
            throw new IllegalArgumentException("Mixed boots sources are not feet-equippable");
        }

        var sound = source.equipSound();
        // Classify the sound, not the item: supports native/inline holders and keeps the chosen holder's range intact.
        if ("minecraft".equals(sound.value().location().getNamespace())
                && !"minecraft".equals(other.equipSound().value().location().getNamespace())) {
            sound = other.equipSound();
        }

        return new Equippable(
                EquipmentSlot.FEET,
                sound,
                source.assetId(),
                Optional.empty(),
                source.allowedEntities(),
                source.dispensable(),
                source.swappable(),
                source.damageOnHurt(),
                source.equipOnInteract(),
                source.canBeSheared(),
                source.shearingSound());
    }
}
