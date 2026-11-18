package games.alejandrocoria.mismatchedthings.test;

import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class WorkbenchSoundChecks {
    public static final Identifier SOUND_ID = Identifier.parse("mismatchedthings:block.mismatched_workbench.split");
    public static int broadcasts;

    private WorkbenchSoundChecks() {
    }

    public static void verify(ServerPlayer player) {
        WorkbenchChecks.require(BuiltInRegistries.SOUND_EVENT.containsKey(SOUND_ID), "Workbench sound was not registered");
        // Exercise either output as the first result, with ordinary clicks and shift-clicks.
        for (int first : new int[]{2, 3}) {
            for (ContainerInput click : new ContainerInput[]{ContainerInput.PICKUP, ContainerInput.QUICK_MOVE}) {
                broadcasts = 0;
                player.getInventory().clearContent();
                WorkbenchMenu menu = preview(player);
                WorkbenchChecks.require(broadcasts == 0, "Preview played the cutting sound");
                menu.clicked(first, 0, click, player);
                WorkbenchChecks.require(menu.isCommitted() && broadcasts == 1, "First extraction did not play exactly one sound");
                menu.setCarried(ItemStack.EMPTY);
                menu.clicked(first == 2 ? 3 : 2, 0, click, player);
                WorkbenchChecks.require(!menu.isCommitted() && broadcasts == 1, "Second extraction repeated the cutting sound");
                menu.setCarried(ItemStack.EMPTY);
                menu.removed(player);
                WorkbenchChecks.require(broadcasts == 1, "Closing a completed transaction played the cutting sound");
            }
        }

        broadcasts = 0;
        player.getInventory().clearContent();
        WorkbenchMenu unpaid = preview(player);
        unpaid.removed(player);
        WorkbenchChecks.require(broadcasts == 0, "Closing an unpaid preview played the cutting sound");

        WorkbenchMenu paid = preview(player);
        paid.clicked(2, 0, ContainerInput.PICKUP, player);
        WorkbenchChecks.require(paid.isCommitted() && broadcasts == 1, "Paid-close setup did not commit once");
        paid.removed(player);
        WorkbenchChecks.require(broadcasts == 1, "Returning the paid remainder repeated the cutting sound");

        broadcasts = 0;
        player.getInventory().clearContent();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        WorkbenchMenu blocked = preview(player);
        WorkbenchChecks.require(blocked.quickMoveStack(player, 2).isEmpty() && !blocked.isCommitted()
                && broadcasts == 0, "Failed extraction played the cutting sound");
        player.getInventory().clearContent();
        blocked.removed(player);
        WorkbenchChecks.write("sound.ok", "registered positional block sound, both output orders/click modes and silent previews/failed transfers/close passed\n");
    }

    private static WorkbenchMenu preview(ServerPlayer player) {
        var menu = new WorkbenchMenu(500, player.getInventory(), ContainerLevelAccess.create(player.level(), WorkbenchChecks.TABLE));
        var input = WorkbenchRoute.FIXTURE.input();
        menu.getSlot(0).set(input.left());
        menu.getSlot(1).set(input.right());
        WorkbenchChecks.require(menu.getSlot(2).hasItem() && menu.getSlot(3).hasItem(), "Sound test did not prepare both outputs");
        return menu;
    }
}
