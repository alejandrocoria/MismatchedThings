package games.alejandrocoria.mismatchedboots.runtimetest.mystical;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class MysticalGuardControl {
    public static int equipmentCalls, armorCalls;

    private MysticalGuardControl() {
    }

    public static ItemStack opaqueSlot(Player player, EquipmentSlot slot) {
        return player.getItemBySlot(slot);
    }
}
