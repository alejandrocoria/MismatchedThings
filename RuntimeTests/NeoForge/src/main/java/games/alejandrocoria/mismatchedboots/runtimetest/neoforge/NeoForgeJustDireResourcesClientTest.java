package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleState;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.menu.MismatchedWorkbenchMenu;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchResult;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.Future;

/** Real NeoForge integrated-server packets, with independent synchronized client preview rules. */
final class NeoForgeJustDireResourcesClientTest {
    private static int ticks;
    private static int phase;
    private static boolean finished;
    private static Future<?> job;
    private static MismatchedWorkbenchResult preview;

    private NeoForgeJustDireResourcesClientTest() {}

    static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            if (++ticks > 3_600) { throw new IllegalStateException("Resource client timed out at phase " + phase); }
            if (client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) { return; }
            if (job != null && !job.isDone()) { return; }
            if (job != null) { job.get(); job = null; }
            var player = client.player;
            var server = client.getSingleplayerServer();
            require(server != null, "Integrated server missing");
            if (phase == 0) {
                require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                        && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                        "Resource client was not hidden and muted");
                preview = new MixedBootsWorkbenchRecipe().assemblePreview(new MismatchedWorkbenchInput(
                        NeoForgeJustDireResourcesTest.boots("celestigem", 6_000), new ItemStack(Items.IRON_BOOTS)),
                        AttributeRuleState.current(), ComponentRuleState.current());
                require(preview.isComplete(), "Synchronized client preview failed");
                var id = player.getUUID();
                job = server.submit(() -> {
                    var owner = server.getPlayerList().getPlayer(id);
                    require(owner != null, "Actual server player missing");
                    owner.getInventory().clearContent();
                    var menu = new MismatchedWorkbenchMenu(1, owner.getInventory());
                    menu.getSlot(0).set(NeoForgeJustDireResourcesTest.boots("celestigem", 6_000));
                    menu.getSlot(1).set(new ItemStack(Items.IRON_BOOTS));
                    ItemStack first = menu.getSlot(2).safeTake(1, 1, owner);
                    ItemStack second = menu.getSlot(3).safeTake(1, 1, owner);
                    require(!first.isEmpty() && !second.isEmpty() && !menu.isCommitted(), "Actual server Workbench takes failed");
                    owner.getInventory().setItem(0, first);
                    owner.getInventory().setItem(1, second);
                    owner.inventoryMenu.broadcastChanges();
                });
                phase = 1;
            } else if (phase == 1) {
                ItemStack first = player.getInventory().getItem(0);
                ItemStack second = player.getInventory().getItem(1);
                if (!ModContent.isMixedBoots(first) || !ModContent.isMixedBoots(second)) { return; }
                require(ItemStack.matches(preview.primary(), first) && ItemStack.matches(preview.inverse(), second),
                        "Actual server outputs differed from independently assembled client preview");
                require(JustDireResourceTestSupport.handler(first).getAmountAsInt() == 3_000
                        && JustDireResourceTestSupport.handler(first).getCapacityAsInt() == 10_000, "Client capability initial state differs");
                var id = player.getUUID();
                job = server.submit(() -> {
                    var owner = server.getPlayerList().getPlayer(id);
                    var energy = ItemAccess.forPlayerSlot(owner, 0).getCapability(Capabilities.Energy.ITEM);
                    try (var transaction = Transaction.openRoot()) {
                        require(energy != null && energy.extract(1_267, transaction) == 1_267, "Server slot FE extraction failed");
                        transaction.commit();
                    }
                    owner.inventoryMenu.broadcastChanges();
                });
                phase = 2;
            } else if (phase == 2) {
                ItemStack first = player.getInventory().getItem(0);
                if (JustDireResourceTestSupport.handler(first).getAmountAsInt() != 1_733) { return; }
                require(ItemStack.matches(preview.inverse(), player.getInventory().getItem(1))
                        && ModContent.getMixedBootsData(first).equals(ModContent.getMixedBootsData(preview.primary())),
                        "Actual FE packet changed inverse output or source snapshots");
                var id = player.getUUID();
                job = server.submit(() -> {
                    var owner = server.getPlayerList().getPlayer(id);
                    owner.getInventory().getItem(0).remove(JustDireResourceTestSupport.energyComponent());
                    owner.inventoryMenu.broadcastChanges();
                });
                phase = 3;
            } else {
                ItemStack first = player.getInventory().getItem(0);
                if (first.has(JustDireResourceTestSupport.energyComponent())) { return; }
                require(JustDireResourceTestSupport.handler(first).getAmountAsInt() == 0
                        && ModContent.getMixedBootsData(first).equals(ModContent.getMixedBootsData(preview.primary())),
                        "Absent live FE field was refilled on the real client");
                NetworkAcceptanceFiles.write("just-dire-resources-client-report.txt", "hidden/muted NeoForge client; actual integrated-server Workbench takes; "
                        + "independent synchronized-rule preview equality; production client capability; actual spent/absent FE inventory packets; unchanged inverse/provenance\n");
                NetworkAcceptanceFiles.write("just-dire-resources-client-success.txt", "neoforge\n");
                finished = true;
            }
        } catch (Throwable failure) {
            finished = true;
            NetworkAcceptanceFiles.failure("just-dire-resources-client", failure);
        }
        if (finished) { client.stop(); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }
}
