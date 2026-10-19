package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.Future;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearLifecycleTest.armor;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearLifecycleTest.close;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearLifecycleTest.require;

/** Real integrated connection: only immutable JSON expectations cross threads; items/attributes arrive by vanilla packets. */
final class SilentGearClientTest {
    private static int ticks, phase;
    private static boolean finished;
    private static Future<String> job;
    private static String expected;

    private SilentGearClientTest() {}

    static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 3_600, "Silent Gear client timeout at phase " + phase);
            if (client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) { return; }
            if (job != null) {
                if (!job.isDone()) { return; }
                expected = job.get(); job = null;
            }
            var server = client.getSingleplayerServer(); require(server != null, "Integrated server missing");
            var id = client.player.getUUID();
            if (phase == 0) {
                require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                        && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                        "SG fixture client was not hidden and muted");
                job = server.submit(() -> {
                    var owner = server.getPlayerList().getPlayer(id); require(owner != null, "Connected owner missing");
                    owner.setGameMode(GameType.SURVIVAL); owner.setNoGravity(true); owner.setPos(0, 82, 0);
                    owner.getInventory().clearContent(); owner.getInventory().setSelectedSlot(0);
                    var barrel = SilentGearLifecycleTest.barrel(server);
                    owner.setItemSlot(EquipmentSlot.MAINHAND, barrel.getItem(0).copy());
                    owner.setItemSlot(EquipmentSlot.FEET, barrel.getItem(2).copy());
                    return snapshot(owner);
                });
                phase = 1; return;
            }
            if (!received(client)) { return; }
            queries(client);
            if (phase == 1) {
                job = server.submit(() -> {
                    var owner = server.getPlayerList().getPlayer(id);
                    ItemStack root = owner.getItemBySlot(EquipmentSlot.FEET);
                    var history = ModContent.getMixedBootsData(root);
                    root.hurtAndBreak(17, owner, EquipmentSlot.FEET);
                    SilentGearLifecycleTest.tick(owner, root, 21, EquipmentSlot.FEET);
                    nativeArmor(owner, root);
                    require(history.equals(ModContent.getMixedBootsData(root)), "Wear changed synchronized sources");
                    ItemStack copper = owner.getItemBySlot(EquipmentSlot.MAINHAND);
                    owner.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    owner.setItemSlot(EquipmentSlot.OFFHAND, copper);
                    ItemStack before = copper.copy();
                    SilentGearLifecycleTest.tick(owner, copper, 20, EquipmentSlot.OFFHAND);
                    require(ItemStack.matches(before, copper), "Offhand move progressed selected state");
                    owner.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                    owner.getInventory().setItem(7, copper);
                    SilentGearLifecycleTest.tick(owner, copper, 20, null);
                    var old = before.get(SilentGearCompatibility.state()); var live = copper.get(SilentGearCompatibility.state());
                    require(live.left().orElseThrow().counter() == old.left().orElseThrow().counter() + 1
                            && live.right().orElseThrow().counter() == old.right().orElseThrow().counter() + 1,
                            "Inventory move reset or missed live clocks");
                    owner.getInventory().setItem(7, ItemStack.EMPTY);
                    owner.setItemSlot(EquipmentSlot.MAINHAND, copper); // Freeze while ordinary packets are in flight.
                    return snapshot(owner);
                });
                phase = 2;
            } else if (phase == 2) {
                job = server.submit(() -> {
                    var owner = server.getPlayerList().getPlayer(id);
                    ItemStack root = owner.getItemBySlot(EquipmentSlot.FEET);
                    owner.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                    ItemStack trimmed = SilentGearLifecycleTest.trim(owner, root, Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE);
                    ItemStack beforeRepair = trimmed.copy(); // The real menu consumes its input stack.
                    ItemStack repaired = SilentGearLifecycleTest.repair(owner, trimmed);
                    require(repaired.getDamageValue() < beforeRepair.getDamageValue(), "Client lifecycle repair did not pay/repair");
                    require(ModContent.getMixedBootsData(repaired).equals(ModContent.getMixedBootsData(beforeRepair)), "Repair changed source snapshots");
                    SilentGearLifecycleTest.tick(owner, repaired, 21, EquipmentSlot.FEET);
                    nativeArmor(owner, repaired);
                    owner.setItemSlot(EquipmentSlot.FEET, repaired); return snapshot(owner);
                });
                phase = 3;
            } else {
                NetworkAcceptanceFiles.write("silent-gear-client-report.txt", "Actual integrated connection from saved world: full item/state/source packets; "
                        + "native-computed armor and vanilla equipment attributes before wear, after wear and after paid trim/repair; "
                        + "hand/offhand/idle moves preserve and advance unequal clocks once; client queries are read-only\n");
                NetworkAcceptanceFiles.write("silent-gear-client-success.txt", "neoforge\n"); finished = true; client.stop();
            }
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure("silent-gear-client", failure); finished = true; client.stop(); }
    }

    private static String snapshot(ServerPlayer owner) {
        owner.inventoryMenu.broadcastChanges();
        var result = new JsonArray(); var ops = owner.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.FEET)) {
            result.add(ItemStack.CODEC.encodeStart(ops, owner.getItemBySlot(slot)).getOrThrow());
        }
        return result.toString();
    }

    private static void nativeArmor(ServerPlayer owner, ItemStack root) {
        try {
            close(armor(root), SilentGearLiveStateTest.expectedArmor(root, new NeoForgeSilentGearTest.NativeApi(), owner,
                    AttributePolicy.AVERAGE), "Connected server native armor oracle");
        } catch (Exception failure) { throw new IllegalStateException("Native armor control failed", failure); }
    }

    private static boolean received(Minecraft client) {
        var values = JsonParser.parseString(expected).getAsJsonArray();
        var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int index = 0; index < 2; index++) {
            ItemStack reference = ItemStack.CODEC.parse(ops, values.get(index)).getOrThrow();
            if (!ItemStack.matches(reference, client.player.getItemBySlot(index == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.FEET))) { return false; }
        }
        return Math.abs(client.player.getAttributeValue(Attributes.ARMOR) - client.player.getAttributeBaseValue(Attributes.ARMOR)
                - armor(client.player.getItemBySlot(EquipmentSlot.FEET))) < 1E-5;
    }

    private static void queries(Minecraft client) {
        for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.FEET)) {
            ItemStack stack = client.player.getItemBySlot(slot), before = stack.copy();
            for (int query = 0; query < 3; query++) {
                stack.getAttributeModifiers(); ModContent.getMixedBootsData(stack); stack.get(SilentGearCompatibility.state());
            }
            require(ItemStack.matches(before, stack), "Connected-client query mutated root/source/state");
        }
    }
}
