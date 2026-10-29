package games.alejandrocoria.mismatchedboots.runtimetest.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.replaymod.core.ReplayMod;
import com.replaymod.recording.Setting;
import com.replaymod.replay.ReplayModReplay;
import games.alejandrocoria.mismatchedboots.client.mixin.ItemStackRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.mixin.LayerRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("UnstableApiUsage")
public final class MediaClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        JsonArray catalog = readCatalog();
        AtomicReference<Path> replayFolder = new AtomicReference<>();
        context.runOnClient(client -> {
            assertUnattended(client);
            client.getWindow().setWindowed(854, 480);
            client.options.guiScale().set(2);
            client.options.renderDistance().set(5);
            client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            var settings = ReplayMod.instance.getSettingsRegistry();
            settings.set(Setting.RECORD_SINGLEPLAYER, true);
            settings.set(Setting.AUTO_START_RECORDING, true);
            settings.set(Setting.RENAME_DIALOG, false);
            try {
                replayFolder.set(ReplayMod.instance.folders.getReplayFolder());
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            for (var definition : catalog) {
                for (var texture : definition.getAsJsonObject().getAsJsonArray("textures")) {
                    var original = Identifier.parse(texture.getAsString());
                    var resource = Identifier.fromNamespaceAndPath(original.getNamespace(),
                            "textures/entity/equipment/humanoid/" + original.getPath() + ".png");
                    if (client.getResourceManager().getResource(resource).isEmpty()) {
                        throw new AssertionError("Media equipment texture not loaded: " + resource);
                    }
                }
            }
        });

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            installDatapack(world);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var commands = server.getCommands();
                commands.performPrefixedCommand(server.createCommandSourceStack().withEntity(player),
                                                "function mismatchedmedia:all");
            });
            context.waitTicks(2);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var commands = server.getCommands();
                var boots = catalogBoots(player.getInventory().getNonEquipmentItems());
                verifyCatalog(boots, catalog);
                var neptunium = findBoot(boots, "neptunium");
                var unobtainium = findBoot(boots, "unobtainium");
                for (ItemStack boot : boots) {
                    String id = photoId(boot);
                    var composed = MixedBootsFactory.createPair(boot, neptunium);
                    assertAppearance(composed.primary(), id, "neptunium");
                    assertAppearance(composed.inverse(), "neptunium", id);
                }
                var pair = MixedBootsFactory.createPair(neptunium, unobtainium);
                assertAppearance(pair.primary(), "neptunium", "unobtainium");
                assertAppearance(pair.inverse(), "unobtainium", "neptunium");
                player.setItemSlot(EquipmentSlot.FEET, pair.primary());
                player.setItemSlot(EquipmentSlot.OFFHAND, pair.inverse());
                commands.performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
                commands.performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
            });
            world.getClientLevel().waitForChunksRender();
            context.waitFor(client -> client.player != null && ModContent.isMixedBoots(
                    client.player.getItemBySlot(EquipmentSlot.FEET)));
            context.waitTicks(80);
            context.runOnClient(client -> {
                assertUnattended(client);
                assertAppearance(client.player.getItemBySlot(EquipmentSlot.FEET), "neptunium", "unobtainium");
                var boots = catalogBoots(client.player.getInventory().getNonEquipmentItems());
                verifyCatalog(boots, catalog);
                for (ItemStack boot : boots) {
                    var state = new ItemStackRenderState();
                    client.getItemModelResolver().updateForTopItem(
                            state, boot, ItemDisplayContext.GUI, client.level, client.player, 0);
                    var access = (ItemStackRenderStateAccessor) (Object) state;
                    if (access.mismatchedboots$getActiveLayerCount() == 0) {
                        throw new AssertionError("Media inventory model did not resolve: " + photoId(boot));
                    }
                    for (int index = 0; index < access.mismatchedboots$getActiveLayerCount(); index++) {
                        var layer = (LayerRenderStateAccessor) (Object) access.mismatchedboots$getLayers()[index];
                        if (layer.mismatchedboots$getQuads().isEmpty()
                                || layer.mismatchedboots$getQuads().stream().anyMatch(quad -> !quad.materialInfo()
                                        .sprite().contents().name().getNamespace().equals("mismatchedmedia"))) {
                            throw new AssertionError("Media icon has missing or non-photo textures: " + photoId(boot));
                        }
                    }
                }
            });
            context.takeScreenshot("media-equipped");
        }

        AtomicReference<Path> recording = new AtomicReference<>();
        context.waitFor(client -> {
            try (var files = Files.list(replayFolder.get())) {
                files.filter(path -> path.getFileName().toString().endsWith(".mcpr"))
                     .findFirst().ifPresent(recording::set);
                return recording.get() != null;
            } catch (IOException exception) {
                return false;
            }
        }, 1200);
        context.runOnClient(client -> {
            try {
                client.options.setCameraType(CameraType.FIRST_PERSON);
                ReplayModReplay.instance.startReplay(recording.get().toFile());
                var replay = ReplayModReplay.instance.getReplayHandler();
                replay.getReplaySender().setSyncModeAndWait();
                replay.getReplaySender().sendPacketsTill(replay.getReplayDuration() - 1000);
                replay.getReplaySender().setReplaySpeed(0);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
        context.waitFor(client -> findRecordedPlayer(client) != null, 600);
        context.runOnClient(client -> {
            assertUnattended(client);
            LivingEntity player = findRecordedPlayer(client);
            assertAppearance(player.getItemBySlot(EquipmentSlot.FEET), "neptunium", "unobtainium");
            var camera = ReplayModReplay.instance.getReplayHandler().getCameraEntity();
            camera.setPos(player.getX(), player.getY() + 1.1, player.getZ() + 3);
            camera.setYRot(180);
            camera.setXRot(10);
            client.options.hideGui = true;
            client.setScreen(null);
            // Fabric's screenshot helper waits for ticks. Resume only its client clock
            // after checking the paused replay; the synchronous sender stays at our frame.
            ReplayModReplay.instance.getReplayHandler().getReplaySender().setReplaySpeed(1);
        });
        context.waitTicks(2);
        context.takeScreenshot("media-replay-equipped");
        context.runOnClient(client -> {
            try {
                ReplayModReplay.instance.getReplayHandler().endReplay();
                Files.writeString(Path.of("media-client.ok"), "media-client-passed\n");
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    private static JsonArray readCatalog() {
        try (var reader = Files.newBufferedReader(Path.of("catalog.json"))) {
            return JsonParser.parseReader(reader).getAsJsonArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static List<ItemStack> catalogBoots(List<ItemStack> inventory) {
        return inventory.stream().filter(stack -> stack.has(DataComponents.CONTAINER))
                        .flatMap(stack -> stack.get(DataComponents.CONTAINER).nonEmptyItemCopyStream()).toList();
    }

    private static void verifyCatalog(List<ItemStack> boots, JsonArray catalog) {
        if (boots.size() != catalog.size()) {
            throw new AssertionError("Media catalog expected " + catalog.size() + " boots, got " + boots.size());
        }
        for (var entry : catalog) {
            String id = entry.getAsJsonObject().get("id").getAsString();
            assertSource(findBoot(boots, id), id);
        }
    }

    private static ItemStack findBoot(List<ItemStack> boots, String id) {
        return boots.stream().filter(stack -> photoId(stack).equals(id)).findFirst().orElseThrow();
    }

    private static String photoId(ItemStack stack) {
        return stack.get(DataComponents.ITEM_MODEL).getPath().replaceFirst("_boots$", "");
    }

    private static void installDatapack(TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            try {
                Files.copy(Path.of("mismatchedmedia-datapack.zip"),
                           server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("mismatchedmedia.zip"));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            server.getPackRepository().reload();
            var packs = new ArrayList<>(server.getPackRepository().getSelectedIds());
            packs.add("file/mismatchedmedia.zip");
            server.reloadResources(packs).join();
        });
    }

    private static LivingEntity findRecordedPlayer(Minecraft client) {
        if (client.level == null) {
            return null;
        }
        for (var entity : client.level.entitiesForRendering()) {
            if (entity != client.player && entity instanceof LivingEntity living
                    && ModContent.isMixedBoots(living.getItemBySlot(EquipmentSlot.FEET))) {
                return living;
            }
        }
        return null;
    }

    private static void assertAppearance(ItemStack mixed, String left, String right) {
        var data = ModContent.getMixedBootsData(mixed).orElseThrow();
        assertSource(data.left().source(), left);
        assertSource(data.right().source(), right);
    }

    private static void assertSource(ItemStack source, String name) {
        var equipment = source.get(DataComponents.EQUIPPABLE);
        if (!source.is(Items.IRON_BOOTS) || equipment == null
                || !equipment.assetId().orElseThrow().identifier().toString().equals("mismatchedmedia:" + name)
                || !Identifier.fromNamespaceAndPath("mismatchedmedia", name + "_boots")
                              .equals(source.get(DataComponents.ITEM_MODEL))) {
            throw new AssertionError("Photo source appearance was lost: " + source);
        }
    }

    private static void assertUnattended(Minecraft client) {
        if (GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) != GLFW.GLFW_FALSE
                || client.options.getSoundSourceVolume(SoundSource.MASTER) != 0.0F) {
            throw new AssertionError("Media acceptance client must stay hidden and muted");
        }
    }
}
