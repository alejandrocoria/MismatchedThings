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
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.Future;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearLifecycleTest.armor;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearLifecycleTest.close;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearLifecycleTest.require;

/** Real integrated connection: only immutable JSON expectations cross threads; items/attributes arrive by vanilla packets. */
final class SilentGearClientTest {
    private static int ticks, phase;
    private static boolean finished, magnetComplete;
    private static Future<String> job;
    private static String expected;
    private static int orientation, phaseStarted;
    private static double nativeJump;
    private static int wearerStartTick; // Read/written only by integrated-server jobs, never shared ItemStacks.
    private static final boolean REJOIN = Boolean.getBoolean("mismatchedboots.runtimetest.silentGearRejoin");
    private static final String OUTPUT = REJOIN ? "silent-gear-rejoin" : "silent-gear-client";

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
                client.options.pauseOnLostFocus = false;
                if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) { client.setScreen(null); }
                require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                        && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                        "SG fixture client was not hidden and muted");
            }
            if (REJOIN) { rejoin(client); return; }
            if (phase == 10) {
                if (!magnetComplete) {
                    if (!SilentGearMagnetClientTest.advance(client, false)) { return; }
                    magnetComplete = true;
                }
                if (!SilentGearDefenseClientTest.advance(client, false)) { return; }
                job = server.submit(() -> {
                    var owner = server.getPlayerList().getPlayer(id); var barrel = SilentGearLifecycleTest.barrel(server);
                    var root = barrel.getItem(5).copy(); root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                    owner.setItemSlot(EquipmentSlot.FEET, root); owner.setItemSlot(EquipmentSlot.OFFHAND, barrel.getItem(7).copy());
                    var manifest = new com.google.gson.JsonObject();
                    manifest.addProperty("process", ProcessHandle.current().pid()); manifest.addProperty("uuid", id.toString());
                    String state = snapshot(owner); manifest.add("snapshot", JsonParser.parseString(state));
                    NetworkAcceptanceFiles.write("silent-gear-player-saved.json", manifest.toString());
                    server.getPlayerList().saveAll(); return state;
                });
                phase = 11; return;
            }
            if (phase == 0) {
                job = server.submit(() -> {
                    var owner = server.getPlayerList().getPlayer(id); require(owner != null, "Connected owner missing");
                    owner.setGameMode(GameType.SURVIVAL); owner.setNoGravity(true); owner.setPos(0, 82, 0);
                    owner.getFoodData().setFoodLevel(10); // Do not let regeneration hide the fall-health control.
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
            } else { wearer(client); }
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure(OUTPUT, failure); finished = true; client.stop(); }
    }

    private static void wearer(Minecraft client) throws Exception {
        var server = client.getSingleplayerServer(); var id = client.player.getUUID();
        if (phase == 3) {
            job = server.submit(() -> {
                var owner = server.getPlayerList().getPlayer(id);
                owner.getInventory().clearContent(); owner.removeAllEffects();
                try {
                    // Independent selected native-item callback: no mixed carrier/dispatcher is the oracle.
                    var nativeBoots = new NeoForgeSilentGearTest.NativeApi().boot("refined_iron");
                    nativeBoots.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                    SilentGearLifecycleTest.tick(owner, nativeBoots, 20, EquipmentSlot.MAINHAND);
                } catch (Exception failure) { throw new IllegalStateException(failure); }
                return snapshot(owner);
            });
            phase = 4;
        } else if (phase == 4) {
            if (!jumpEffect(client)) { return; }
            nativeJump = jump(client);
            job = server.submit(() -> {
                var owner = server.getPlayerList().getPlayer(id);
                ItemStack root = SilentGearLifecycleTest.barrel(server).getItem(4 + orientation).copy();
                root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE); // Freeze repair, not wearer effects, during packet comparison.
                owner.setItemSlot(EquipmentSlot.FEET, root); wearerStartTick = owner.tickCount; return snapshot(owner);
            });
            phase = 5; phaseStarted = ticks;
        } else if (phase == 5) {
            if (ticks - phaseStarted < 55) { return; } // The initial native control's 38 ticks cannot mask missing refresh.
            require(jumpEffect(client), "Connected worn Stellar did not refresh its potion");
            close(jump(client), nativeJump, "Connected mixed jump differs from native potion control");
            job = server.submit(() -> {
                var owner = server.getPlayerList().getPlayer(id); var root = owner.getItemBySlot(EquipmentSlot.FEET);
                refreshed(owner);
                owner.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); owner.setItemSlot(EquipmentSlot.OFFHAND, root);
                wearerStartTick = owner.tickCount;
                return snapshot(owner);
            });
            phase = 6; phaseStarted = ticks;
        } else if (phase == 6) {
            if (ticks - phaseStarted < 55) { return; }
            require(jumpEffect(client) && !client.player.hasEffect(MobEffects.SPEED), "Connected held Stellar lost native zero-piece behavior");
            job = server.submit(() -> {
                var owner = server.getPlayerList().getPlayer(id); var root = owner.getItemBySlot(EquipmentSlot.OFFHAND);
                refreshed(owner);
                owner.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY); owner.getInventory().setItem(10, root);
                return snapshot(owner);
            });
            phase = 7; phaseStarted = ticks;
        } else if (phase == 7) {
            if (ticks - phaseStarted < 45) { return; }
            require(!client.player.hasEffect(MobEffects.JUMP_BOOST), "Connected idle Stellar did not expire naturally");
            job = server.submit(() -> {
                var owner = server.getPlayerList().getPlayer(id);
                require(!owner.hasEffect(MobEffects.JUMP_BOOST), "Server idle effect still active");
                var root = SilentGearLifecycleTest.barrel(server).getItem(6 + orientation).copy();
                var data = ModContent.getMixedBootsData(root).orElseThrow();
                var moon = List.of(data.left().source(), data.right().source()).stream()
                        .filter(source -> source.getAttributeModifiers().modifiers().stream()
                                .anyMatch(entry -> entry.attribute().equals(Attributes.GRAVITY))).findFirst().orElseThrow();
                owner.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); float plain = fall(owner);
                owner.setItemSlot(EquipmentSlot.FEET, moon); float nativeLoss = fall(owner);
                owner.setItemSlot(EquipmentSlot.FEET, root); float mixedLoss = fall(owner);
                require(plain > nativeLoss && nativeLoss > 0 && mixedLoss == nativeLoss, "Connected real fall-health controls differ");
                return snapshot(owner);
            });
            phase = 8;
        } else if (phase == 8) {
            ItemStack root = client.player.getItemBySlot(EquipmentSlot.FEET);
            double base = client.player.getAttributeBaseValue(Attributes.GRAVITY), expectedGravity = base;
            for (var entry : root.getAttributeModifiers().modifiers()) {
                if (!entry.attribute().equals(Attributes.GRAVITY)) { continue; }
                expectedGravity = switch (entry.modifier().operation()) {
                    case ADD_VALUE -> expectedGravity + entry.modifier().amount();
                    case ADD_MULTIPLIED_BASE -> expectedGravity + base * entry.modifier().amount();
                    case ADD_MULTIPLIED_TOTAL -> expectedGravity * (1 + entry.modifier().amount());
                };
            }
            if (Math.abs(client.player.getAttributeValue(Attributes.GRAVITY) - expectedGravity) > 1E-6) { return; }
            require(expectedGravity < base, "Moonwalker gravity control is not effective");
            job = server.submit(() -> {
                var owner = server.getPlayerList().getPlayer(id);
                owner.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); return snapshot(owner);
            });
            phase = 9;
        } else if (phase == 9) {
            if (Math.abs(client.player.getAttributeValue(Attributes.GRAVITY) - client.player.getAttributeBaseValue(Attributes.GRAVITY)) > 1E-6) { return; }
            if (++orientation < 2) { phase = 3; return; }
            phase = 10;
        } else {
            if (!jumpEffect(client)) { return; }
            success(client, "Actual integrated connection: existing state/source/armor packets, wear and paid trim/repair; "
                    + "both wearer inverses, native-selected jump control vs real refreshed mixed potion packets/jump, offhand refresh and idle expiry; "
                    + "Moonwalker actual health loss matches native with independent damaging vanilla control, composed gravity and removal packets; "
                    + "native/mixed Magnetic and Greedy ordinary motion, client entity/inventory packets and counted natural pickup, delayed/rejected controls, both hands and selected-to-idle stop; "
                    + "native/mixed ordinary defensive landings, client rebound/sound, health packets and real attacker motion; "
                    + "read-only client queries; actual player inventory including used magnet and SG/JDT defensive roots saved for a separate rejoin JVM\n");
        }
    }

    private static void rejoin(Minecraft client) throws Exception {
        var server = client.getSingleplayerServer(); var id = client.player.getUUID();
        if (phase == 3) {
            if (!magnetComplete) {
                if (!SilentGearMagnetClientTest.advance(client, true)) { return; }
                magnetComplete = true;
            }
            if (!SilentGearDefenseClientTest.advance(client, true)) { return; }
            success(client, "Separate connected-client JVM, same UUID/world: actual player inventory/root/damage/selected copper clocks match saved data; "
                    + "Brilliant recognition, one seeded Stellar heal with unchanged sources/state, normal resumed potion packets then natural idle expiry; "
                    + "saved magnet root/source identity matches before use, ordinary attraction/pickup resumes without recrafting, client motion/counts/delay/rejection and idle stop pass; "
                    + "actual used defensive root matched before unrelated inventory cleanup, then repeats ordinary rebound/sound with one root bill and unchanged source/FE histories\n");
            return;
        }
        if (phase == 0) {
            job = server.submit(() -> {
                var owner = server.getPlayerList().getPlayer(id);
                try {
                    var manifest = JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(
                            System.getProperty("mismatchedboots.runtimetest.markers"), "silent-gear-player-saved.json"))).getAsJsonObject();
                    require(manifest.get("process").getAsLong() != ProcessHandle.current().pid() && manifest.get("uuid").getAsString().equals(id.toString()),
                            "Rejoin reused JVM or changed player identity");
                    require(manifest.get("snapshot").equals(JsonParser.parseString(snapshot(owner))), "Actual player save/rejoin changed frozen roots, damage or clocks");
                    SilentGearDefenseClientTest.captureRejoin(owner);
                    require(net.minecraft.world.entity.monster.piglin.PiglinAi.isWearingSafeArmor(owner), "Rejoined Brilliant missing");
                    var root = owner.getItemBySlot(EquipmentSlot.FEET); var before = root.copy();
                    Object stellar = SilentGearCompatibility.effectiveTraits(root).stream().filter(value -> value.id().getPath().equals("stellar")).findFirst().orElseThrow().nativeInstance();
                    root.remove(DataComponents.UNBREAKABLE); SilentGearWearerFixture.seedStellar(stellar, true);
                    SilentGearLifecycleTest.tick(owner, root, 20, EquipmentSlot.FEET);
                    root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                    require(root.getDamageValue() == before.getDamageValue() - SilentGearTraitSpike.level(stellar), "Player rejoin duplicated repair or reset root damage");
                    before.setDamageValue(root.getDamageValue()); require(ItemStack.matches(before, root), "Rejoin repair changed unrelated data");
                    wearerStartTick = owner.tickCount;
                    return snapshot(owner);
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            phase = 1; phaseStarted = ticks; return;
        }
        if (!received(client)) { return; }
        queries(client);
        if (phase == 1) {
            if (ticks - phaseStarted < 55) { return; }
            require(jumpEffect(client), "Rejoined wearer did not resume normal potion refresh");
            job = server.submit(() -> {
                var owner = server.getPlayerList().getPlayer(id); var root = owner.getItemBySlot(EquipmentSlot.FEET);
                refreshed(owner);
                owner.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); owner.getInventory().setItem(10, root); return snapshot(owner);
            });
            phase = 2; phaseStarted = ticks;
        } else {
            if (ticks - phaseStarted < 45) { return; }
            require(!client.player.hasEffect(MobEffects.JUMP_BOOST), "Rejoined idle effect did not expire");
            phase = 3;
        }
    }

    private static boolean jumpEffect(Minecraft client) {
        var effect = client.player.getEffect(MobEffects.JUMP_BOOST);
        return effect != null && effect.getAmplifier() == 0 && effect.getDuration() > 0 && effect.getDuration() <= 38;
    }

    private static void refreshed(ServerPlayer owner) {
        require(owner.tickCount - wearerStartTick >= 38 && owner.hasEffect(MobEffects.JUMP_BOOST),
                "Wearer was not refreshed through actual unpaused server-player ticks");
    }

    private static double jump(Minecraft client) {
        Vec3 before = client.player.getDeltaMovement();
        try { client.player.setDeltaMovement(Vec3.ZERO); client.player.jumpFromGround(); return client.player.getDeltaMovement().y; }
        finally { client.player.setDeltaMovement(before); }
    }

    private static float fall(ServerPlayer owner) {
        owner.setHealth(owner.getMaxHealth()); owner.setAbsorptionAmount(0); owner.invulnerableTime = 0;
        owner.causeFallDamage(10, 1, owner.damageSources().fall()); return owner.getMaxHealth() - owner.getHealth();
    }

    private static void success(Minecraft client, String report) {
        NetworkAcceptanceFiles.write(OUTPUT + "-report.txt", report);
        NetworkAcceptanceFiles.write(OUTPUT + "-success.txt", "neoforge\n"); finished = true; client.stop();
    }

    private static String snapshot(ServerPlayer owner) {
        owner.inventoryMenu.broadcastChanges();
        var result = new JsonArray(); var ops = owner.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int index = 0; index < 4; index++) {
            ItemStack item = stack(owner, index);
            result.add(item.isEmpty() ? com.google.gson.JsonNull.INSTANCE : ItemStack.CODEC.encodeStart(ops, item).getOrThrow());
        }
        result.add(owner.getHealth());
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
        for (int index = 0; index < 4; index++) {
            ItemStack reference = values.get(index).isJsonNull() ? ItemStack.EMPTY : ItemStack.CODEC.parse(ops, values.get(index)).getOrThrow();
            if (!ItemStack.matches(reference, stack(client.player, index))) { return false; }
        }
        return Math.abs(client.player.getHealth() - values.get(4).getAsFloat()) < 1E-5 && Math.abs(client.player.getAttributeValue(Attributes.ARMOR) - client.player.getAttributeBaseValue(Attributes.ARMOR)
                - armor(client.player.getItemBySlot(EquipmentSlot.FEET))) < 1E-5;
    }

    private static void queries(Minecraft client) {
        for (int index = 0; index < 4; index++) {
            ItemStack stack = stack(client.player, index), before = stack.copy();
            for (int query = 0; query < 3; query++) {
                stack.getAttributeModifiers(); ModContent.getMixedBootsData(stack); stack.get(SilentGearCompatibility.state());
            }
            require(ItemStack.matches(before, stack), "Connected-client query mutated root/source/state");
        }
    }

    private static ItemStack stack(net.minecraft.world.entity.player.Player player, int index) {
        return index == 3 ? player.getInventory().getItem(10) : player.getItemBySlot(
                index == 0 ? EquipmentSlot.MAINHAND : index == 1 ? EquipmentSlot.FEET : EquipmentSlot.OFFHAND);
    }
}
