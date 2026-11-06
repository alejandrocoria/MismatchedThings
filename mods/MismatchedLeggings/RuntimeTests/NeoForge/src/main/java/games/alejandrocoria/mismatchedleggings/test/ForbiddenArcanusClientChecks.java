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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.ArrayList;
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
    private static ForbiddenModifierPresentation.Scene scene;
    private static List<ItemStack> savedInventory;
    private static List<ItemStack> expectedInventory;
    private static List<CompoundTag> expectedInventoryNbt;
    private static CompoundTag expectedNbt;
    private static int savedAdoptions;

    private ForbiddenArcanusClientChecks() {}

    static void tick(Minecraft client) {
        if (phase == 8) {
            return;
        }
        try {
            require(++ticks < 3000, "Forbidden Arcanus client timeout, phase=" + phase + ", screen=" + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated client window became visible");
            if (phase == 9) {
                if (scene.complete(client)) {
                    client.setScreen(null);
                    if (!ForbiddenBootsChecks.only()) {
                        generation = LeggingsGeometryCache.statistics().generation();
                        previousRenderer = EquippedClient.renderer;
                    }
                    job = client.reloadResourcePacks();
                    phase = 3;
                }
                return;
            }
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
                if (!ForbiddenBootsChecks.only()) { SkeletonChecks.verify(); }
                playerId = client.player.getUUID();
                job = server.submit(() -> {
                    ForbiddenBootsStorage.verify(server);
                    var created = new ArrayList<>(ForbiddenArcanusChecks.samples(server));
                    if (!ForbiddenBootsChecks.only()) {
                        created.addAll(ForbiddenModifierChecks.samples(server));
                        created.addAll(ForbiddenModifierPresentation.installed(server));
                    }
                    created.addAll(ForbiddenBootsChecks.clientSamples(server));
                    return created;
                });
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
                    if (!ForbiddenBootsChecks.only()) { ForbiddenArcanusPresentation.verify(client); }
                    ForbiddenModifierPresentation.verify(client, samples);
                    scene = new ForbiddenModifierPresentation.Scene(samples);
                    client.setScreen(scene);
                    phase = 9;
                }
            } else if (phase == 3 && job.isDone()) {
                job.join();
                if (!ForbiddenBootsChecks.only()) {
                    require(LeggingsGeometryCache.statistics().generation() > generation
                                    && EquippedClient.renderer != previousRenderer,
                            "Real reload did not replace native renderer and invalidate split geometry");
                    ForbiddenArcanusPresentation.verify(client);
                }
                ForbiddenModifierPresentation.verify(client, samples);
                var ordinary = samples.stream().filter(sample -> sample.name().equals(ForbiddenBootsChecks.only()
                                ? "tyr_boots/diamond primary" : "tyr_leggings/diamond primary"))
                        .findFirst().orElseThrow().stack().copy();
                ordinary.set(DataComponents.CUSTOM_NAME, Component.literal("Forbidden Arcanus saved Aureal root"));
                ordinary.setDamageValue(13);
                savedInventory = new ArrayList<>(samples.stream().filter(sample -> sample.name().startsWith("installed "))
                        .map(sample -> sample.stack().copy()).toList());
                savedInventory.add(ordinary);
                var saved = ForbiddenBootsChecks.only() ? samples.stream()
                        .filter(sample -> sample.name().equals("installed boots Aureal dual")).findFirst().orElseThrow().stack().copy()
                        : savedInventory.get(2).copy();
                ForbiddenArcanusPresentation.trim(client, saved);
                previousServer = server;
                previousPlayer = client.player;
                schedule(server, saved, 2);
                phase = 4;
            } else if (phase == 4 && received(client)) {
                savedAdoptions = ForbiddenBootsStorage.adoptions.get();
                backupConfirmed = false;
                client.disconnectWithSavingScreen();
                phase = 5;
            } else if (phase == 6) {
                require(server != previousServer && client.player != previousPlayer && client.player.getUUID().equals(playerId),
                        "Reopen reused an old server/player or changed player identity");
                job = server.submit(() -> {
                    var player = server.getPlayerList().getPlayer(playerId);
                    ForbiddenBootsStorage.verify(server);
                    require(ForbiddenBootsStorage.adoptions.get() == savedAdoptions, "Saved roots were adopted again on native reopen");
                    require(expectedNbt.equals(RecoveryChecks.encode(player.getItemBySlot(ForbiddenArcanusChecks.slot(expected)), server.registryAccess())),
                            "Saved root/provenance did not reopen intact");
                    for (int slot = 0; slot < expectedInventory.size(); slot++) {
                        require(expectedInventoryNbt.get(slot).equals(RecoveryChecks.encode(player.getInventory().getItem(slot), server.registryAccess())),
                                "Saved installed root or source history did not reopen in slot " + slot + ": "
                                        + ForbiddenModifierChecks.changedComponents(expectedInventory.get(slot), player.getInventory().getItem(slot))
                                        + ForbiddenBootsChecks.historyDifference(expectedInventory.get(slot), player.getInventory().getItem(slot)));
                    }
                    // The new world has new damage-type/tag holders. Rebind only the expected oracle;
                    // compare complete persisted NBT first, and never rewrite actual player/client items.
                    var ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
                    expected = ItemStack.CODEC.parse(ops, expectedNbt).getOrThrow();
                    expectedInventory = expectedInventoryNbt.stream().map(nbt -> ItemStack.CODEC.parse(ops, nbt).getOrThrow()).toList();
                    require(player.getAttributeValue(ForbiddenArcanusChecks.attribute()) == 2, "Reopened server lost Aureal");
                });
                settled = 0;
                phase = 7;
            } else if (phase == 7 && received(client)) {
                String rendering = ForbiddenBootsChecks.only()
                        ? "Standalone Boots: native controls, root/source titles and read-only contexts passed before/after real reload;\n"
                        : "Draco/Tyr item textures, native base/trim materials, split waist/legs and tooltips passed before/after real reload;\n";
                Files.writeString(ForbiddenArcanusChecks.report("client"),
                        "Real native equipment packets and client attributes passed for " + samples.size() + " samples;\n"
                        + rendering
                        + "native save/disconnect/reopen replaced server/player, retained UUID, named/damaged/trimmed root/provenance and Aureal;\n"
                        + "inherited/installed single and dual states received through equipment AND inventory packets in both orientations;\n"
                        + "paid flags, scalar projections and complete source/root state survived native save/reopen;\n"
                        + "native translated titles, custom/source names and read-only contexts passed before/after reload;\n"
                        + "native single/dual backgrounds passed real deferred tooltip extraction and rendered frames before reload;\n"
                        + "no client equipment/attribute writes; owner visual/gameplay acceptance remains separate\n");
                SkeletonChecks.passed("client");
                if (ForbiddenBootsChecks.enabled()) {
                    Files.writeString(ForbiddenArcanusChecks.report("boots-storage-client"),
                            "Native container survived initial load and save/disconnect/reopen with exact root/history and no repeated adoption\n");
                    Files.writeString(ForbiddenArcanusChecks.report("boots-client"),
                            "Current and adopted Boots: real feet/inventory packets and complete save/disconnect/reopen passed;\n"
                                    + "native titles/background frames, custom/source names and read-only contexts passed;\n"
                                    + "title assertions repeated after real resource reload with both products installed\n");
                }
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
        expectedInventory = savedInventory == null ? List.of(expected.copy()) : savedInventory;
        settled = 0;
        job = server.submit(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            player.getInventory().clearContent();
            ForbiddenArcanusChecks.equip(player, expected);
            for (int slot = 0; slot < expectedInventory.size(); slot++) {
                player.getInventory().setItem(slot, expectedInventory.get(slot).copy());
            }
            player.inventoryMenu.broadcastChanges();
            expectedNbt = RecoveryChecks.encode(expected, server.registryAccess());
            expectedInventoryNbt = expectedInventory.stream().map(snapshot -> RecoveryChecks.encode(snapshot, server.registryAccess())).toList();
            require(player.getAttributeValue(ForbiddenArcanusChecks.attribute()) == rate, "Server fixture attribute differs from expected");
        });
    }

    private static boolean received(Minecraft client) {
        if (!job.isDone()) {
            return false;
        }
        job.join();
        if (!ItemStack.matches(expected, client.player.getItemBySlot(ForbiddenArcanusChecks.slot(expected)))
                || client.player.getAttributeValue(ForbiddenArcanusChecks.attribute()) != expectedRate) {
            settled = 0;
            return false;
        }
        for (int slot = 0; slot < expectedInventory.size(); slot++) {
            if (!ItemStack.matches(expectedInventory.get(slot), client.player.getInventory().getItem(slot))) {
                settled = 0;
                return false;
            }
        }
        return ++settled >= 4;
    }
}
