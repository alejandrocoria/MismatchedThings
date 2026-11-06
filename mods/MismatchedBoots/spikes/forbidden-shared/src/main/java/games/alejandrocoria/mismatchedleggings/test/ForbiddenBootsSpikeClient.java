package games.alejandrocoria.mismatchedleggings.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Hidden/muted native old-world loading, equipment packets and actual save/reopen; no client item writes. */
public final class ForbiddenBootsSpikeClient {
    private static int phase;
    private static int ticks;
    private static int settled;
    private static int savedAdoptions;
    private static boolean backupConfirmed;
    private static CompletableFuture<?> job;
    private static List<ItemStack> expected;
    private static MinecraftServer previous;

    private ForbiddenBootsSpikeClient() {}

    public static void setup(FMLClientSetupEvent event) {
        require(ForbiddenBootsCandidate.nativeApplications == 0, "Optional native API ran before client world loading");
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> tick(Minecraft.getInstance()));
    }

    private static void tick(Minecraft client) {
        if (phase == 5) { return; }
        try {
            require(++ticks < 2400, "Forbidden Boots client timeout: " + phase + ", " + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated spike client became visible");
            if (client.player == null && client.screen instanceof BackupConfirmScreen backup && !backupConfirmed) {
                backupConfirmed = true;
                backup.children().stream().filter(child -> child instanceof Button button
                                && button.getMessage().equals(Component.translatable("selectWorld.backupJoinSkipButton")))
                        .map(Button.class::cast).findFirst().orElseThrow().onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                return;
            }
            if (phase == 2 && client.player == null && client.level == null && client.getSingleplayerServer() == null) {
                phase = 3;
                client.createWorldOpenFlows().openWorld("skeleton", () -> client.setScreen(new TitleScreen()));
                return;
            }
            if (client.player == null || client.level == null || client.screen != null || client.getOverlay() != null) { return; }
            var server = client.getSingleplayerServer();
            if (phase == 0) {
                var playerId = client.player.getUUID();
                job = server.submit(() -> {
                    try { ForbiddenBootsSpike.verifyLoaded(server, false); }
                    catch (Exception failure) { throw new AssertionError("Old-world load failed", failure); }
                    require(ForbiddenBootsCandidate.adoptions > 0, "No real native persistent load was converted");
                    var barrel = ForbiddenBootsSpike.storage(server);
                    var roots = List.of(barrel.getItem(1).copy(), barrel.getItem(3).copy());
                    var player = server.getPlayerList().getPlayer(playerId);
                    player.getInventory().clearContent();
                    player.setItemSlot(EquipmentSlot.FEET, roots.getFirst().copy());
                    player.getInventory().setItem(0, roots.getLast().copy());
                    player.inventoryMenu.broadcastChanges();
                    return roots;
                });
                phase = 1;
            } else if (phase == 1 && job.isDone()) {
                @SuppressWarnings("unchecked")
                var roots = (List<ItemStack>) job.join();
                expected = roots;
                if (!received(client)) { return; }
                for (var stack : List.of(client.player.getItemBySlot(EquipmentSlot.FEET), client.player.getInventory().getItem(0))) {
                    var before = stack.copy();
                    for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                        stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
                    }
                    require(ItemStack.matches(before, stack), "Old native tooltip mutated adopted state");
                }
                savedAdoptions = ForbiddenBootsCandidate.adoptions;
                previous = server;
                backupConfirmed = false;
                client.disconnectWithSavingScreen();
                phase = 2;
            } else if (phase == 3) {
                require(server != previous, "Native reopen reused old server");
                var playerId = client.player.getUUID();
                job = server.submit(() -> {
                    try { ForbiddenBootsSpike.verifyLoaded(server, true); }
                    catch (Exception failure) { throw new AssertionError("Converted-world reopen failed", failure); }
                    require(ForbiddenBootsCandidate.adoptions == savedAdoptions,
                            "Normal save did not write adopted state; the second load migrated roots again");
                    var player = server.getPlayerList().getPlayer(playerId);
                    require(ItemStack.matches(expected.getFirst(), player.getItemBySlot(EquipmentSlot.FEET))
                            && ItemStack.matches(expected.getLast(), player.getInventory().getItem(0)), "Player persistence lost adopted roots");
                });
                settled = 0;
                phase = 4;
            } else if (phase == 4 && received(client)) {
                Files.writeString(ForbiddenBootsSpike.report("client"),
                        "Actual published Boots save loaded through persistent native codec; container conversion preceded item access;\n"
                        + "real inventory/equipment packets, read-only tooltips and native save/reopen preserved flags and full live/source state;\n"
                        + "no repeated adoption, tooltip writes, client item writes, inventory-tick migration or screenshots\n");
                Files.writeString(ForbiddenArcanusChecks.report("client"), "Bounded legacy candidate, not final shared API acceptance\n");
                SkeletonChecks.passed("client");
                phase = 5;
                client.stop();
            }
        } catch (Throwable failure) {
            phase = 5;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static boolean received(Minecraft client) {
        if (!job.isDone()) { return false; }
        job.join();
        if (!ItemStack.matches(expected.getFirst(), client.player.getItemBySlot(EquipmentSlot.FEET))
                || !ItemStack.matches(expected.getLast(), client.player.getInventory().getItem(0))) {
            settled = 0;
            return false;
        }
        return ++settled >= 4;
    }
}
