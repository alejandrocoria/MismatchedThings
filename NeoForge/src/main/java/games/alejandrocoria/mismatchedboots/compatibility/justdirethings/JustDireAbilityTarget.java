package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A live action location, not a permission or an equipped-state substitute. Never follows a moved/equal stack. */
public record JustDireAbilityTarget(ServerPlayer player, int slot, ItemStack root) {
    public static final int FEET_SLOT = EquipmentSlot.FEET.getIndex(Inventory.INVENTORY_SIZE);

    public boolean valid() {
        return present()
                && player.containerMenu == player.inventoryMenu && JustDireSettingsCompatibility.mixed(root);
    }

    public static boolean supportedSlot(int slot) {
        return slot >= 0 && slot < Inventory.INVENTORY_SIZE || slot == FEET_SLOT || slot == Inventory.SLOT_OFFHAND;
    }

    public boolean equipped() { return slot == FEET_SLOT && player.getItemBySlot(EquipmentSlot.FEET) == root; }

    void hurtAndBreak(int amount) {
        root.hurtAndBreak(amount, player.level(), player, this::broken);
    }

    /** Identity only: also works after consumption and does not impose action-menu rules on ordinary refunds. */
    public boolean present() {
        return supportedSlot(slot) && slot < player.getInventory().getContainerSize() && player.getInventory().getItem(slot) == root;
    }

    private void broken(Item item) {
        // Shrink/refund callbacks may already have replaced the slot or changed selected hotbar.
        // Empty consumed roots still have identity; never clean up a replacement's location effects.
        EquipmentSlot equipment = !present() ? null : slot == FEET_SLOT ? EquipmentSlot.FEET
                : slot == Inventory.SLOT_OFFHAND ? EquipmentSlot.OFFHAND
                : slot == player.getInventory().getSelectedSlot() ? EquipmentSlot.MAINHAND : null;
        if (equipment != null) { player.onEquippedItemBroken(item, equipment); }
        else { player.awardStat(Stats.ITEM_BROKEN.get(item)); }
    }
}
