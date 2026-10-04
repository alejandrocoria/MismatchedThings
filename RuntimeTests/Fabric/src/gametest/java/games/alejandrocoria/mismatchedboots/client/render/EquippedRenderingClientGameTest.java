package games.alejandrocoria.mismatchedboots.client.render;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPatterns;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class EquippedRenderingClientGameTest implements FabricClientGameTest {
    private static final DyedItemColor LEFT_DYE = new DyedItemColor(0x3366CC);

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            configureWorld(singleplayer);
            singleplayer.getClientLevel().waitForChunksRender();

            verifyStandardSplit(context, singleplayer);
            verifyVisualComponents(context, singleplayer);
            verifyFallbackAndRecovery(context, singleplayer);
            captureAdultPoses(context, singleplayer);
            captureHumanoidMatrix(context, singleplayer);
        }
    }

    private static void configureWorld(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "difficulty normal");
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.setPose(Pose.STANDING);
            player.setGlowingTag(true);
            player.setItemSlot(EquipmentSlot.FEET, mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        });
    }

    private static void verifyStandardSplit(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        context.runOnClient(client -> {
            EquippedRenderingProbe.reset();
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
        equip(singleplayer, mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        context.waitFor(client -> hasSplit(
                Items.IRON_BOOTS, EquipmentAssets.IRON,
                Items.GOLDEN_BOOTS, EquipmentAssets.GOLD));
        context.runOnClient(client -> assertOnlySplitSourceViews(true));

        context.runOnClient(client -> EquippedRenderingProbe.reset());
        equip(singleplayer, mixed(Items.GOLDEN_BOOTS, Items.IRON_BOOTS));
        context.waitFor(client -> hasSplit(
                Items.GOLDEN_BOOTS, EquipmentAssets.GOLD,
                Items.IRON_BOOTS, EquipmentAssets.IRON));
    }

    private static void verifyVisualComponents(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        final ArmorTrim[] expectedTrim = new ArmorTrim[1];
        singleplayer.getServer().runOnServer(server -> {
            ArmorTrim trim = new ArmorTrim(
                    server.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL)
                            .getOrThrow(TrimMaterials.QUARTZ),
                    server.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN)
                            .getOrThrow(TrimPatterns.COAST));
            expectedTrim[0] = trim;
            ItemStack left = new ItemStack(Items.LEATHER_BOOTS);
            left.set(DataComponents.DYED_COLOR, LEFT_DYE);
            left.set(DataComponents.TRIM, trim);
            ItemStack mixed = MixedBootsFactory.createPair(left, new ItemStack(Items.DIAMOND_BOOTS)).primary();
            mixed.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.setGlowingTag(false);
            player.setItemSlot(EquipmentSlot.FEET, mixed);
        });
        context.runOnClient(client -> EquippedRenderingProbe.reset());
        context.waitFor(client -> calls().stream().anyMatch(call ->
                call.side() == MixedBootsEquipmentRenderer.Side.LEFT
                        && call.item() == Items.LEATHER_BOOTS
                        && call.asset().equals(EquipmentAssets.LEATHER)
                        && call.foil()
                        && LEFT_DYE.equals(call.dye())
                        && expectedTrim[0].equals(call.trim()))
                && calls().stream().anyMatch(call ->
                call.side() == MixedBootsEquipmentRenderer.Side.RIGHT
                        && call.item() == Items.DIAMOND_BOOTS
                        && call.asset().equals(EquipmentAssets.DIAMOND)
                        && call.foil()));
        context.runOnClient(client -> {
            ItemStack equipped = client.player.getItemBySlot(EquipmentSlot.FEET);
            ItemStack storedLeft = ModContent.getMixedBootsData(equipped).orElseThrow().left().source();
            if (storedLeft.hasFoil()) {
                throw new AssertionError("Root glint was written into the stored left snapshot");
            }
            if (!LEFT_DYE.equals(storedLeft.get(DataComponents.DYED_COLOR))
                    || !expectedTrim[0].equals(storedLeft.get(DataComponents.TRIM))) {
                throw new AssertionError("Equipped rendering mutated source visual components");
            }
        });
        context.waitTicks(3);
        context.takeScreenshot("phase6-dye-trim-glint");
    }

    private static void verifyFallbackAndRecovery(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        ItemStack unsupported = new ItemStack(Items.GOLDEN_BOOTS);
        unsupported.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build());
        context.runOnClient(client -> EquippedRenderingProbe.reset());
        equip(singleplayer, MixedBootsFactory.createPair(
                new ItemStack(Items.IRON_BOOTS), unsupported).primary());
        context.waitFor(client -> calls().stream().anyMatch(call ->
                call.side() == null
                        && call.mixedStack()
                        && call.asset().equals(EquipmentAssets.IRON)));
        context.runOnClient(client -> EquippedRenderingProbe.reset());
        context.waitFor(client -> calls().stream().anyMatch(call ->
                call.side() == null
                        && call.mixedStack()
                        && call.asset().equals(EquipmentAssets.IRON)));
        context.runOnClient(client -> {
            if (calls().stream().anyMatch(call -> call.side() != null)) {
                throw new AssertionError("Unsupported equipment rendered a partial split before fallback");
            }
        });

        context.runOnClient(client -> EquippedRenderingProbe.reset());
        equip(singleplayer, mixed(Items.DIAMOND_BOOTS, Items.LEATHER_BOOTS));
        context.waitFor(client -> hasSplit(
                Items.DIAMOND_BOOTS, EquipmentAssets.DIAMOND,
                Items.LEATHER_BOOTS, EquipmentAssets.LEATHER));
        context.runOnClient(client -> EquippedRenderingProbe.reset());
        context.waitFor(client -> hasSplit(
                Items.DIAMOND_BOOTS, EquipmentAssets.DIAMOND,
                Items.LEATHER_BOOTS, EquipmentAssets.LEATHER));
        context.runOnClient(client -> assertOnlySplitSourceViews(false));
    }

    private static void captureAdultPoses(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        equip(singleplayer, mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.setPose(Pose.STANDING);
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), 0.0F, 10.0F);
        });
        context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(4);
        context.takeScreenshot("phase6-adult-front");

        context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.waitTicks(4);
        context.takeScreenshot("phase6-adult-rear");

        context.runOnClient(client -> client.options.keyUp.setDown(true));
        context.waitTicks(3);
        context.takeScreenshot("phase6-adult-walking");
        context.runOnClient(client -> client.options.keyUp.setDown(false));

        context.waitTicks(4);
        context.runOnClient(client -> {
            client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            client.options.keyShift.setDown(true);
        });
        context.waitTicks(4);
        context.takeScreenshot("phase6-adult-crouching");
        context.runOnClient(client -> client.options.keyShift.setDown(false));
    }

    private static void captureHumanoidMatrix(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> spawnHumanoids(server.overworld(),
                server.getPlayerList().getPlayers().getFirst()));
        context.runOnClient(client -> {
            EquippedRenderingProbe.reset();
            client.options.setCameraType(CameraType.FIRST_PERSON);
        });
        context.waitFor(client -> hasSplit(
                Items.IRON_BOOTS, EquipmentAssets.IRON,
                Items.GOLDEN_BOOTS, EquipmentAssets.GOLD)
                && hasSplit(
                Items.GOLDEN_BOOTS, EquipmentAssets.GOLD,
                Items.DIAMOND_BOOTS, EquipmentAssets.DIAMOND));
        context.runOnClient(client -> {
            if (calls().stream().anyMatch(EquippedRenderingProbe.Call::mixedStack)) {
                throw new AssertionError("A nearby humanoid used the complete-owner path unexpectedly");
            }
        });
        context.waitTicks(8);
        context.takeScreenshot("phase6-humanoids-front");

        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.connection.teleport(player.getX() + 5.0, player.getY(), player.getZ() + 4.0, 90.0F, 5.0F);
        });
        context.waitTicks(6);
        context.takeScreenshot("phase6-humanoids-side");

        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.connection.teleport(player.getX() - 5.0, player.getY(), player.getZ() + 4.0, 180.0F, 5.0F);
        });
        context.waitTicks(6);
        context.takeScreenshot("phase6-humanoids-rear");
    }

    private static void spawnHumanoids(ServerLevel level, ServerPlayer player) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ() + 4.0;

        ArmorStand stand = new ArmorStand(level, x - 1.1, y, z);
        stand.setYRot(180.0F);
        stand.setItemSlot(EquipmentSlot.FEET, mixed(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        level.addFreshEntity(stand);

        Zombie baby = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
        if (baby == null) {
            throw new AssertionError("Could not create the phase 6 baby humanoid");
        }
        baby.setPos(x + 1.1, y, z);
        baby.setYRot(180.0F);
        baby.setBaby(true);
        baby.setNoAi(true);
        baby.setPersistenceRequired();
        baby.setItemSlot(EquipmentSlot.FEET, mixed(Items.GOLDEN_BOOTS, Items.DIAMOND_BOOTS));
        level.addFreshEntity(baby);
    }

    private static void equip(TestSingleplayerContext singleplayer, ItemStack stack) {
        singleplayer.getServer().runOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().setItemSlot(EquipmentSlot.FEET, stack));
    }

    private static ItemStack mixed(Item left, Item right) {
        return MixedBootsFactory.createPair(new ItemStack(left), new ItemStack(right)).primary();
    }

    private static boolean hasSplit(
            Item leftItem,
            ResourceKey<EquipmentAsset> leftAsset,
            Item rightItem,
            ResourceKey<EquipmentAsset> rightAsset) {
        List<EquippedRenderingProbe.Call> calls = calls();
        return calls.stream().anyMatch(call ->
                call.side() == MixedBootsEquipmentRenderer.Side.LEFT
                        && call.item() == leftItem
                        && call.asset().equals(leftAsset))
                && calls.stream().anyMatch(call ->
                call.side() == MixedBootsEquipmentRenderer.Side.RIGHT
                        && call.item() == rightItem
                        && call.asset().equals(rightAsset));
    }

    private static void assertOnlySplitSourceViews(boolean expectOutline) {
        List<EquippedRenderingProbe.Call> calls = calls();
        if (calls.stream().anyMatch(EquippedRenderingProbe.Call::mixedStack)) {
            throw new AssertionError("Standard mixed equipment also rendered the complete owner appearance");
        }
        if (calls.stream().noneMatch(call -> call.side() == MixedBootsEquipmentRenderer.Side.LEFT)
                || calls.stream().noneMatch(call -> call.side() == MixedBootsEquipmentRenderer.Side.RIGHT)) {
            throw new AssertionError("Standard mixed equipment did not submit both split sides");
        }
        List<Integer> outlineColors = calls.stream()
                .filter(call -> call.side() != null)
                .map(EquippedRenderingProbe.Call::outlineColor)
                .distinct()
                .toList();
        if (outlineColors.size() != 1 || expectOutline && outlineColors.getFirst() == 0) {
            throw new AssertionError("Split equipment did not preserve the entity outline color");
        }
    }

    private static List<EquippedRenderingProbe.Call> calls() {
        return EquippedRenderingProbe.snapshot();
    }
}
