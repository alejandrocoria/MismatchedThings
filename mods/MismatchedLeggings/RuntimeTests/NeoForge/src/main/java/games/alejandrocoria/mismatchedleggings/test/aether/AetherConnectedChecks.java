package games.alejandrocoria.mismatchedleggings.test.aether;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.failed;
import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.passed;
import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Old suspended equipment crosses real chunk storage and ordinary player packets, not a test decoder. */
public final class AetherConnectedChecks {
    private static final BlockPos STORAGE = new BlockPos(0, 65, 0);
    private static int ticks;
    private static int phase;
    private static boolean initialized;
    private static boolean finished;

    private AetherConnectedChecks() {}

    public static String stage() { return System.getProperty("leggings.test.aetherConnected", ""); }
    public static Path report(String name) { return Path.of(System.getProperty("leggings.test.report"), name); }
    private static Path baseline(String name) { return Path.of(System.getProperty("leggings.test.aetherConnectedBaseline"), name); }

    public static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer()) { return; }
        try {
            require(++ticks < 4800, "Aether connected server timed out at " + phase);
            if (ticks < 20) { return; }
            if (!initialized) {
                boolean seed = stage().equals("seed");
                require(ModList.get().isLoaded("aether_ii") != seed, "Wrong native removal/reinstallation graph");
                require(ModList.get().isLoaded("mismatchedboots"), "Packaged sibling missing");
                server.overworld().getChunkAt(STORAGE);
                var frozen = AetherPersistenceChecks.frozen();
                if (seed) {
                    server.overworld().setBlock(STORAGE, Blocks.BARREL.defaultBlockState(), 3);
                    for (int i = 0; i < frozen.size(); i++) {
                        barrel(server).setItem(i, AetherPersistenceChecks.decode(server, frozen.get(i)));
                    }
                    barrel(server).setChanged();
                    Files.writeString(baseline("seed-pid.txt"), Long.toString(ProcessHandle.current().pid()));
                    finish(server);
                    return;
                }
                require(Long.parseLong(Files.readString(baseline("seed-pid.txt"))) != ProcessHandle.current().pid(), "Seed JVM reused");
                // Read the block entity loaded from disk, never replace it with newly decoded expectations.
                for (int i = 0; i < frozen.size(); i++) {
                    require(AetherPersistenceChecks.encode(server, barrel(server).getItem(i))
                            .equals(AetherPersistenceChecks.expected(frozen.get(i), true)), "Stored old root failed complete recovery: " + i);
                }
                initialized = true;
                Files.writeString(report("ready.txt"), "Old container restored; dedicated server ready\n");
            }

            var players = server.getPlayerList().getPlayers();
            if (phase == 4) {
                if (Files.exists(report("client.ok")) && players.isEmpty()) { finish(server); }
                return;
            }
            if (players.isEmpty()) { return; }
            var player = players.getFirst();
            if (phase == 0) {
                if (stage().equals("reconnect")) {
                    require(snapshot(player).equals(JsonParser.parseString(Files.readString(baseline("player.json")))),
                            "Saved player items changed on restart/reconnect");
                    require(setCount(player) == 3, "Saved equipment lost native set recognition");
                    // Let the real native player tick restore transient effects before changing equipment.
                    if (Math.abs(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - .2) > 1E-6) { return; }
                    Files.writeString(report("rejoined.ok"), "Complete saved player state and native Burrukai effect restored before test callbacks\n");
                }
                player.getInventory().clearContent();
                for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
                player.setGameMode(GameType.SURVIVAL);
                player.setInvulnerable(true);
                player.setNoGravity(true);
                player.connection.teleport(.5, 68, .5, 0, 0);
                for (int i = 0; i < 12; i++) { player.getInventory().setItem(i, barrel(server).getItem(i).copy()); }
                player.setItemSlot(EquipmentSlot.HEAD, item("burrukai_plate_helmet"));
                equip(player, 2, 8);
                publish(player, "first");
                phase = 1;
            } else if (phase == 1 && Files.exists(report("first.ok"))) {
                equip(player, 3, 9);
                publish(player, "inverse");
                phase = 2;
            } else if (phase == 2 && Files.exists(report("inverse.ok"))) {
                require(snapshot(player).equals(readSnapshot("inverse")), "Observation changed equipped roots");
                // Frozen overrides deliberately differ from item families; use ordinary native-family
                // controls for presentation rather than expecting new semantics for custom set overrides.
                var bootsPair = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory")
                        .getMethod("createPair", ItemStack.class, ItemStack.class)
                        .invoke(null, item("burrukai_plate_boots"), new ItemStack(Items.DIAMOND_BOOTS));
                player.setItemSlot(EquipmentSlot.FEET, (ItemStack) bootsPair.getClass().getMethod("primary").invoke(bootsPair));
                player.setItemSlot(EquipmentSlot.LEGS, MixedLeggingsFactory.createPair(
                        item("burrukai_plate_leggings"), new ItemStack(Items.DIAMOND_LEGGINGS)).primary());
                verifyNative(player);
                publish(player, "native");
                phase = 3;
            } else if (phase == 3 && Files.exists(report("native.ok"))) {
                equip(player, 3, 9);
                Files.writeString(baseline("player.json"), snapshot(player).toString());
                Files.writeString(report("finish.txt"), "Both old orientations and current-family presentation controls accepted; old equipment restored for reconnect\n");
                phase = 4;
            }
        } catch (Throwable failure) {
            finished = true;
            failed("server", failure);
            server.halt(false);
        }
    }

    private static void equip(ServerPlayer player, int feet, int legs) throws Exception {
        player.setItemSlot(EquipmentSlot.FEET, barrel(player.level().getServer()).getItem(feet).copy());
        player.setItemSlot(EquipmentSlot.LEGS, barrel(player.level().getServer()).getItem(legs).copy());
        verifyNative(player);
    }

    private static void verifyNative(ServerPlayer player) throws Exception {
        Class.forName("com.aetherteam.aetherii.item.equipment.armor.abilities.BurrukaiPlateArmor")
                .getMethod("updatePlayerAttributes", PlayerTickEvent.Pre.class).invoke(null, new PlayerTickEvent.Pre(player));
        require(setCount(player) == 3 && Math.abs(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - .2) < 1E-6,
                "Recovered mixed slots failed native Burrukai count/effect");
    }

    public static int setCount(LivingEntity player) throws Exception {
        var tag = TagKey.create(Registries.ITEM, Identifier.parse("aether_ii:armor/burrukai_plate"));
        return (int) Class.forName("com.aetherteam.aetherii.item.equipment.EquipmentUtil")
                .getMethod("getArmorCount", LivingEntity.class, TagKey.class).invoke(null, player, tag);
    }

    public static JsonArray snapshot(Player player) {
        var result = new JsonArray();
        var inventory = player.getInventory();
        var ops = player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        // Item/equipment packets may arrive in different ticks; empty slots mean not ready, not a codec failure.
        for (int i = 0; i < 12; i++) { result.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, inventory.getItem(i)).getOrThrow()); }
        for (var slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.FEET, EquipmentSlot.LEGS}) {
            result.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, player.getItemBySlot(slot)).getOrThrow());
        }
        return result;
    }

    public static JsonArray readSnapshot(String name) throws Exception {
        return JsonParser.parseString(Files.readString(report(name + ".json"))).getAsJsonArray();
    }

    private static ItemStack item(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.parse("aether_ii:" + name)).orElseThrow());
    }

    private static void publish(ServerPlayer player, String name) throws Exception {
        player.inventoryMenu.broadcastChanges();
        Files.writeString(report(name + ".json"), snapshot(player).toString());
    }

    private static BarrelBlockEntity barrel(MinecraftServer server) {
        var barrel = server.overworld().getBlockEntity(STORAGE);
        require(barrel instanceof BarrelBlockEntity, "Persisted old-data barrel missing");
        return (BarrelBlockEntity) barrel;
    }

    private static void finish(MinecraftServer server) throws Exception {
        server.saveEverything(true, true, true);
        Files.writeString(report("state.txt"), "Real saved barrel: 12 old roots; stage=" + stage() + "; JVM=" + ProcessHandle.current().pid() + "\n");
        passed("server");
        finished = true;
        server.halt(false);
    }
}
