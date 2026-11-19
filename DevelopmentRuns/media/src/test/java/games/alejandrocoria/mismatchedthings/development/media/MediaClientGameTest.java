package games.alejandrocoria.mismatchedthings.development.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.replaymod.core.ReplayMod;
import com.replaymod.recording.Setting;
import com.replaymod.replay.ReplayModReplay;
import games.alejandrocoria.mismatchedboots.client.mixin.ItemStackRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.mixin.LayerRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
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
                            "textures/entity/equipment/" + definition.getAsJsonObject().get("layer").getAsString()
                                    + "/" + original.getPath() + ".png");
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
                var armor = catalogArmor(player.getInventory().getNonEquipmentItems());
                verifyCatalog(armor, catalog);
                verifyBoxes(player.getInventory().getNonEquipmentItems(), catalog);
                for (ItemStack item : armor) {
                    String kind = photoKind(item);
                    String id = photoId(item);
                    var neptunium = findArmor(armor, kind, "neptunium");
                    if (kind.equals("boots")) {
                        var composed = MixedBootsFactory.createPair(item, neptunium);
                        assertAppearance(composed.primary(), kind, id, "neptunium");
                        assertAppearance(composed.inverse(), kind, "neptunium", id);
                    } else {
                        var composed = MixedLeggingsFactory.createPair(item, neptunium);
                        assertAppearance(composed.primary(), kind, id, "neptunium");
                        assertAppearance(composed.inverse(), kind, "neptunium", id);
                    }
                }
                var pair = MixedBootsFactory.createPair(findArmor(armor, "boots", "neptunium"),
                        findArmor(armor, "boots", "unobtainium"));
                var leggings = MixedLeggingsFactory.createPair(findArmor(armor, "leggings", "neptunium"),
                        findArmor(armor, "leggings", "unobtainium"));
                player.setItemSlot(EquipmentSlot.FEET, pair.primary());
                player.setItemSlot(EquipmentSlot.LEGS, leggings.primary());
                player.setItemSlot(EquipmentSlot.OFFHAND, pair.inverse());
                var previousHand = player.getMainHandItem().copy();
                player.setItemSlot(EquipmentSlot.MAINHAND, leggings.inverse());
                if (!player.getInventory().add(previousHand)) {
                    throw new AssertionError("Media setup had no room to preserve the selected catalog box");
                }
                commands.performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
                commands.performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
            });
            world.getClientLevel().waitForChunksRender();
            context.waitFor(client -> client.player != null && ModContent.isMixedBoots(
                    client.player.getItemBySlot(EquipmentSlot.FEET))
                    && client.player.getItemBySlot(EquipmentSlot.LEGS).has(LeggingsContent.mixedLeggingsDataType()));
            context.waitTicks(80);
            context.runOnClient(client -> {
                assertUnattended(client);
                assertAppearance(client.player.getItemBySlot(EquipmentSlot.FEET), "boots", "neptunium", "unobtainium");
                assertAppearance(client.player.getItemBySlot(EquipmentSlot.LEGS), "leggings", "neptunium", "unobtainium");
                assertAppearance(client.player.getItemBySlot(EquipmentSlot.MAINHAND), "leggings", "unobtainium", "neptunium");
                assertAppearance(client.player.getItemBySlot(EquipmentSlot.OFFHAND), "boots", "unobtainium", "neptunium");
                var armor = catalogArmor(client.player.getInventory().getNonEquipmentItems());
                verifyCatalog(armor, catalog);
                verifyBoxes(client.player.getInventory().getNonEquipmentItems(), catalog);
                for (ItemStack item : armor) {
                    var state = new ItemStackRenderState();
                    client.getItemModelResolver().updateForTopItem(
                            state, item, ItemDisplayContext.GUI, client.level, client.player, 0);
                    var access = (ItemStackRenderStateAccessor) (Object) state;
                    if (access.mismatchedboots$getActiveLayerCount() == 0) {
                        throw new AssertionError("Media inventory model did not resolve: " + item);
                    }
                    for (int index = 0; index < access.mismatchedboots$getActiveLayerCount(); index++) {
                        var layer = (LayerRenderStateAccessor) (Object) access.mismatchedboots$getLayers()[index];
                        if (layer.mismatchedboots$getQuads().isEmpty()
                                || layer.mismatchedboots$getQuads().stream().anyMatch(quad -> !quad.materialInfo()
                                        .sprite().contents().name().getNamespace().equals("mismatchedmedia"))) {
                            throw new AssertionError("Media icon has missing or non-photo textures: " + item);
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
            assertAppearance(player.getItemBySlot(EquipmentSlot.FEET), "boots", "neptunium", "unobtainium");
            assertAppearance(player.getItemBySlot(EquipmentSlot.LEGS), "leggings", "neptunium", "unobtainium");
            assertAppearance(player.getItemBySlot(EquipmentSlot.MAINHAND), "leggings", "unobtainium", "neptunium");
            assertAppearance(player.getItemBySlot(EquipmentSlot.OFFHAND), "boots", "unobtainium", "neptunium");
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

    private static List<ItemStack> catalogArmor(List<ItemStack> inventory) {
        return inventory.stream().filter(stack -> stack.has(DataComponents.CONTAINER))
                        .flatMap(stack -> stack.get(DataComponents.CONTAINER).nonEmptyItemCopyStream()).toList();
    }

    private static void verifyCatalog(List<ItemStack> armor, JsonArray catalog) {
        if (armor.size() != catalog.size()) {
            throw new AssertionError("Media catalog expected " + catalog.size() + " pieces, got " + armor.size());
        }
        for (var entry : catalog) {
            String id = entry.getAsJsonObject().get("id").getAsString();
            String kind = entry.getAsJsonObject().get("kind").getAsString();
            assertSource(findArmor(armor, kind, id), kind, id);
        }
    }

    private static void verifyBoxes(List<ItemStack> inventory, JsonArray catalog) {
        var boxes = inventory.stream().filter(stack -> stack.has(DataComponents.CONTAINER)).toList();
        long expectedBoxes = catalog.asList().stream().map(entry -> entry.getAsJsonObject().get("mod").getAsString()).distinct().count();
        if (boxes.size() != expectedBoxes) {
            throw new AssertionError("Expected one media box per mod");
        }
        for (ItemStack box : boxes) {
            var contents = box.get(DataComponents.CONTAINER).allItemsCopyStream().toList();
            int bootCount = (int) contents.stream().filter(stack -> stack.is(Items.IRON_BOOTS)).count();
            int firstLeggingsRow = (bootCount + 8) / 9 * 9;
            var first = contents.stream().filter(stack -> !stack.isEmpty()).findFirst().orElseThrow();
            var firstDefinition = catalog.asList().stream().map(entry -> entry.getAsJsonObject())
                    .filter(entry -> entry.get("id").getAsString().equals(photoId(first))
                            && entry.get("kind").getAsString().equals(photoKind(first)))
                    .findFirst().orElseThrow();
            String mod = firstDefinition.get("mod").getAsString();
            int expectedItems = 0;
            for (var entry : catalog) {
                var definition = entry.getAsJsonObject();
                if (!definition.get("mod").getAsString().equals(mod)) {
                    continue;
                }
                int slot = definition.get("boxSlot").getAsInt();
                String kind = definition.get("kind").getAsString();
                assertSource(contents.get(slot), kind, definition.get("id").getAsString());
                if (kind.equals("leggings") && slot < firstLeggingsRow) {
                    throw new AssertionError("Media leggings did not begin on their own row");
                }
                expectedItems++;
            }
            if (contents.stream().filter(stack -> !stack.isEmpty()).count() != expectedItems) {
                throw new AssertionError("Media box contains another mod's pieces");
            }
        }
    }

    private static ItemStack findArmor(List<ItemStack> armor, String kind, String id) {
        return armor.stream().filter(stack -> photoKind(stack).equals(kind) && photoId(stack).equals(id))
                .findFirst().orElseThrow();
    }

    private static String photoId(ItemStack stack) {
        return stack.get(DataComponents.ITEM_MODEL).getPath().replaceFirst("_(boots|leggings)$", "");
    }

    private static String photoKind(ItemStack stack) {
        return stack.is(Items.IRON_BOOTS) ? "boots" : "leggings";
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
                    && ModContent.isMixedBoots(living.getItemBySlot(EquipmentSlot.FEET))
                    && living.getItemBySlot(EquipmentSlot.LEGS).has(LeggingsContent.mixedLeggingsDataType())) {
                return living;
            }
        }
        return null;
    }

    private static void assertAppearance(ItemStack mixed, String kind, String left, String right) {
        if (kind.equals("boots")) {
            var data = ModContent.getMixedBootsData(mixed).orElseThrow();
            assertSource(data.left().source(), kind, left);
            assertSource(data.right().source(), kind, right);
        } else {
            var data = mixed.get(LeggingsContent.mixedLeggingsDataType());
            if (data == null) {
                throw new AssertionError("Mixed leggings lost their source data");
            }
            assertSource(data.left().source(), kind, left);
            assertSource(data.right().source(), kind, right);
        }
    }

    private static void assertSource(ItemStack source, String kind, String name) {
        var equipment = source.get(DataComponents.EQUIPPABLE);
        var item = kind.equals("boots") ? Items.IRON_BOOTS : Items.IRON_LEGGINGS;
        var slot = kind.equals("boots") ? EquipmentSlot.FEET : EquipmentSlot.LEGS;
        if (!source.is(item) || equipment == null || equipment.slot() != slot
                || !equipment.assetId().orElseThrow().identifier().toString().equals("mismatchedmedia:" + name)
                || !Identifier.fromNamespaceAndPath("mismatchedmedia", name + "_" + kind)
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
