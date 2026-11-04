package games.alejandrocoria.mismatchedleggings.test;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Observe authoritative state only after a real result-click packet has returned. */
public final class SmithingReloadPackets {
    private static volatile int armedContainer = -1;
    public static volatile Snapshot last;

    private SmithingReloadPackets() {
    }

    static void arm(int container) {
        last = null;
        armedContainer = container;
    }

    public static void capture(ServerPlayer player, int container, int slot) {
        if (armedContainer != container || slot != SmithingMenu.RESULT_SLOT
                || !(player.containerMenu instanceof SmithingMenu menu) || menu.containerId != container) {
            return;
        }
        last = new Snapshot(List.of(menu.getSlot(0).getItem().copy(), menu.getSlot(1).getItem().copy(),
                menu.getSlot(2).getItem().copy()), menu.getSlot(3).getItem().copy(), menu.getCarried().copy(),
                player.getInventory().getNonEquipmentItems().stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList());
        armedContainer = -1;
    }

    public record Snapshot(List<ItemStack> inputs, ItemStack result, ItemStack carried, List<ItemStack> inventory) {
    }
}
