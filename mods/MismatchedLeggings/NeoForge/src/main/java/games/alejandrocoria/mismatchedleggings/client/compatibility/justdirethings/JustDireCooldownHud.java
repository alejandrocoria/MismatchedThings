package games.alejandrocoria.mismatchedleggings.client.compatibility.justdirethings;

import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireDecoy;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Rendering only: the native tool view is never equipped or used to settle an action. */
public final class JustDireCooldownHud {
    private JustDireCooldownHud() {}

    public static ItemStack view(ItemStack root, EquipmentSlot slot) {
        return slot == EquipmentSlot.LEGS ? JustDireDecoy.hudView(root) : root;
    }
}
