package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.client.render.LeggingsGeometryCache;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real equipment packets and disconnect/save/reopen, with no client equipment or attribute writes. */
final class ForbiddenArcanusClientChecks {
    private static int ticks;
    private static int phase;
    private static int index;
    private static int settled;
    private static boolean backupConfirmed;
    private static List<ForbiddenArcanusChecks.Sample> samples;
    private static CompletableFuture<?> job;
    private static ItemStack expected;
    private static double expectedRate;
    private static MinecraftServer previousServer;
    private static Object previousPlayer;
    private static UUID playerId;
    private static long generation;
    private static EquipmentLayerRenderer previousRenderer;

    private ForbiddenArcanusClientChecks() {}

    static void tick(Minecraft client) {
        if (phase == 8) {
            return;
        }
        try {
            require(++ticks < 3000, "Forbidden Arcanus client timeout, phase=" + phase + ", screen=" + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated client window became visible");
            if (client.player == null && client.screen instanceof BackupConfirmScreen backup && !backupConfirmed) {
                backupConfirmed = true;
                backup.children().stream().filter(child -> child instanceof Button button
                                && button.getMessage().equals(Component.translatable("selectWorld.backupJoinSkipButton")))
                        .map(Button.class::cast).findFirst().orElseThrow().onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                return;
            }
            if (phase == 5 && client.player == null && client.level == null && client.getSingleplayerServer() == null) {
                phase = 6;
                client.createWorldOpenFlows().openWorld("skeleton", () -> client.setScreen(new TitleScreen()));
                return;
            }
            if (client.player == null || client.level == null || client.getOverlay() != null || client.screen != null) {
                return;
            }
            var server = client.getSingleplayerServer();
            if (phase == 0) {
                SkeletonChecks.verify();
                playerId = client.player.getUUID();
                job = server.submit(ForbiddenArcanusChecks::samples);
                phase = 1;
            } else if (phase == 1 && job.isDone()) {
                @SuppressWarnings("unchecked")
                var created = (List<ForbiddenArcanusChecks.Sample>) job.join();
                samples = created;
                schedule(server, samples.getFirst().stack(), samples.getFirst().rate());
                phase = 2;
            } else if (phase == 2 && received(client)) {
                if (++index < samples.size()) {
                    var next = samples.get(index);
                    schedule(server, next.stack(), next.rate());
                } else {
                    ForbiddenArcanusPresentation.verify(client);
                    generation = LeggingsGeometryCache.statistics().generation();
                    previousRenderer = EquippedClient.renderer;
                    job = client.reloadResourcePacks();
                    phase = 3;
                }
            } else if (phase == 3 && job.isDone()) {
                job.join();
                require(LeggingsGeometryCache.statistics().generation() > generation
                                && EquippedClient.renderer != previousRenderer,
                        "Real reload did not replace native renderer and invalidate split geometry");
                ForbiddenArcanusPresentation.verify(client);
                var saved = samples.stream().filter(sample -> sample.name().equals("tyr_leggings/diamond primary"))
                        .findFirst().orElseThrow().stack().copy();
                saved.set(DataComponents.CUSTOM_NAME, Component.literal("Forbidden Arcanus saved Aureal root"));
                saved.setDamageValue(13);
                ForbiddenArcanusPresentation.trim(client, saved);
                previousServer = server;
                previousPlayer = client.player;
                schedule(server, saved, 2);
                phase = 4;
            } else if (phase == 4 && received(client)) {
                backupConfirmed = false;
                client.disconnectWithSavingScreen();
                phase = 5;
            } else if (phase == 6) {
                require(server != previousServer && client.player != previousPlayer && client.player.getUUID().equals(playerId),
                        "Reopen reused an old server/player or changed player identity");
                job = server.submit(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    require(ItemStack.matches(expected, player.getItemBySlot(EquipmentSlot.LEGS)), "Saved root/provenance did not reopen intact");
                    require(player.getAttributeValue(ForbiddenArcanusChecks.attribute()) == 2, "Reopened server lost Aureal");
                });
                settled = 0;
                phase = 7;
            } else if (phase == 7 && received(client)) {
                Files.writeString(ForbiddenArcanusChecks.report("client"),
                        "Real native equipment packets and client attributes passed for " + samples.size() + " samples;\n"
                        + "Draco/Tyr item textures, native base/trim materials, split waist/legs and tooltips passed before/after real reload;\n"
                        + "native save/disconnect/reopen replaced server/player, retained UUID, named/damaged/trimmed root/provenance and Aureal;\n"
                        + "no client equipment/attribute writes, screenshot or optional modifier acceptance\n");
                SkeletonChecks.passed("client");
                phase = 8;
                client.stop();
            }
        } catch (Throwable failure) {
            phase = 8;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static void schedule(MinecraftServer server, ItemStack stack, double rate) {
        expected = stack.copy();
        expectedRate = rate;
        settled = 0;
        job = server.submit(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            player.getInventory().clearContent();
            ForbiddenArcanusChecks.equip(player, expected);
            player.inventoryMenu.broadcastChanges();
            require(player.getAttributeValue(ForbiddenArcanusChecks.attribute()) == rate, "Server fixture attribute differs from expected");
        });
    }

    private static boolean received(Minecraft client) {
        if (!job.isDone()) {
            return false;
        }
        job.join();
        if (!ItemStack.matches(expected, client.player.getItemBySlot(EquipmentSlot.LEGS))
                || client.player.getAttributeValue(ForbiddenArcanusChecks.attribute()) != expectedRate) {
            settled = 0;
            return false;
        }
        return ++settled >= 4;
    }
}
