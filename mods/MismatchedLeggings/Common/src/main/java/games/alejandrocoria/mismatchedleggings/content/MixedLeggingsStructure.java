package games.alejandrocoria.mismatchedleggings.content;

import games.alejandrocoria.mismatchedleggings.data.LeggingHalf;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.Optional;

/** Product-owned structural fields only; numerical composition belongs to the factory. */
public final class MixedLeggingsStructure {
    public enum State { BARE, MALFORMED, SUSPENDED, VALID }

    private MixedLeggingsStructure() {
    }

    public static boolean isMixedItem(ItemStack stack) {
        return stack != null && stack.getItem() instanceof MixedLeggingsItem;
    }

    public static State classify(ItemStack stack) {
        if (!isMixedItem(stack) || stack.isEmpty() || stack.getCount() != 1 || ItemStack.validateStrict(stack).isError()) {
            return State.MALFORMED;
        }
        if (LeggingsContent.suspendedLeggingsData(stack).isPresent()) {
            return State.SUSPENDED;
        }
        var data = stack.get(LeggingsContent.mixedLeggingsDataType());
        if (data == null) {
            return stack.has(DataComponents.EQUIPPABLE) ? State.MALFORMED : State.BARE;
        }
        if (!LeggingHalf.isValidSource(data.left().source()) || !LeggingHalf.isValidSource(data.right().source())) {
            return State.MALFORMED;
        }
        return stack.getMaxStackSize() == 1
                && LeggingsContent.MIXED_LEGGINGS_ID.equals(stack.get(DataComponents.ITEM_MODEL))
                && Component.translatable("item.mismatchedleggings.mixed_leggings").equals(stack.get(DataComponents.ITEM_NAME))
                && projectEquipment(data).equals(stack.get(DataComponents.EQUIPPABLE)) ? State.VALID : State.MALFORMED;
    }

    /** Strict consumer boundary: data on an unrelated item is not a mixed pair. */
    public static Optional<MixedLeggingsData> validData(ItemStack stack) {
        return classify(stack) == State.VALID ? Optional.of(stack.get(LeggingsContent.mixedLeggingsDataType())) : Optional.empty();
    }

    /** Inactive history may stay worn, but a bare or quarantined root cannot invent an armor slot. */
    public static boolean canEquip(ItemStack stack) {
        var state = classify(stack);
        if (state == State.VALID) {
            return true;
        }
        return state == State.SUSPENDED && LeggingsContent.suspendedLeggingsData(stack)
                .filter(data -> !data.quarantined() && data.projection().equals(SuspendedLeggingsData.equipmentProjection()))
                .isPresent()
                && Equippable.builder(EquipmentSlot.LEGS).setDamageOnHurt(false).build()
                        .equals(stack.get(DataComponents.EQUIPPABLE));
    }

    /** Called only after the factory has projected owner-effective components. */
    public static void apply(ItemStack root, MixedLeggingsData data) {
        if (!isMixedItem(root) || root.getCount() != 1) {
            throw new IllegalArgumentException("Structural fields require a single mixed leggings root");
        }
        root.set(DataComponents.MAX_STACK_SIZE, 1);
        root.set(DataComponents.ITEM_NAME, Component.translatable("item.mismatchedleggings.mixed_leggings"));
        root.set(DataComponents.ITEM_MODEL, LeggingsContent.MIXED_LEGGINGS_ID);
        root.set(DataComponents.EQUIPPABLE, projectEquipment(data));
        root.set(LeggingsContent.mixedLeggingsDataType(), data);
        ItemStack.validateStrict(root).getOrThrow();
    }

    public static Equippable projectEquipment(MixedLeggingsData data) {
        var owner = data.left().source().get(DataComponents.EQUIPPABLE);
        var other = data.right().source().get(DataComponents.EQUIPPABLE);
        var sound = owner.equipSound();
        // Sound identity, not item namespace: retain the selected holder and its custom range.
        if ("minecraft".equals(sound.value().location().getNamespace())
                && !"minecraft".equals(other.equipSound().value().location().getNamespace())) {
            sound = other.equipSound();
        }
        return new Equippable(EquipmentSlot.LEGS, sound, owner.assetId(), Optional.empty(), owner.allowedEntities(),
                owner.dispensable(), owner.swappable(), owner.damageOnHurt(), owner.equipOnInteract(), owner.canBeSheared(), owner.shearingSound());
    }
}
