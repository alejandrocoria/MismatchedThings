package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.clear;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.component;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.fall;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.install;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.item;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.measure;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.paidPair;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.require;

/** Small persisted-world fixture using the existing barrel / separate-JVM acceptance pattern. */
final class AetherIILifecycleTest {
    private static final BlockPos BARREL = new BlockPos(4, 80, 4);
    private static boolean finished;

    private AetherIILifecycleTest() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (finished) { return; }
            finished = true;
            String phase = System.getProperty("mismatchedboots.runtimetest.aetherIILifecycle");
            String output = "aether-ii-" + phase;
            try {
                var server = event.getServer();
                var owner = FakePlayerFactory.getMinecraft(server.overworld());
                owner.setGameMode(GameType.SURVIVAL); owner.setNoGravity(true);
                if (phase.equals("save")) { save(server, owner); }
                else {
                    var manifest = read("aether-ii-world.json");
                    require(manifest.get("process").getAsLong() != ProcessHandle.current().pid(), "Restart reused save JVM");
                    var roots = roots(server);
                    compare(server, roots, manifest.getAsJsonArray("roots"));
                    var report = new StringBuilder("Different JVM: six complete damaged/named world roots matched before callbacks\n");
                    check(owner, roots, report);
                    NetworkAcceptanceFiles.write(output + "-report.txt", report.toString());
                }
                NetworkAcceptanceFiles.write(output + "-success.txt", "neoforge\n");
            } catch (Throwable failure) { NetworkAcceptanceFiles.failure(output, failure); }
            finally { event.getServer().halt(false); }
        });
    }

    private static void save(MinecraftServer server, ServerPlayer owner) throws Exception {
        var api = new AetherIICompatibilityTest.NativeApi();
        var nativeCharmed = install(owner, api, item("neptune_boots"), "health", 0, true);
        // Used source damage is historical; only the root receives subsequent wear.
        nativeCharmed.setDamageValue(9);
        var roots = new ArrayList<>(paidPair(owner, nativeCharmed, new ItemStack(Items.DIAMOND_BOOTS)));
        roots.addAll(paidPair(owner, item("sentry_boots"), new ItemStack(Items.DIAMOND_BOOTS)));
        roots.addAll(paidPair(owner, item("gravitite_boots"), new ItemStack(Items.DIAMOND_BOOTS)));
        var report = new StringBuilder();
        for (int i = 0; i < roots.size(); i++) {
            roots.get(i).set(DataComponents.CUSTOM_NAME, Component.literal("Aether used root " + i));
            roots.get(i).hurtAndBreak(7, owner, EquipmentSlot.FEET);
            require(roots.get(i).getDamageValue() >= 7, "Save missed root wear");
        }
        check(owner, roots, report);
        server.overworld().setBlock(BARREL, Blocks.BARREL.defaultBlockState(), 3);
        var barrel = barrel(server);
        for (int i = 0; i < roots.size(); i++) { barrel.setItem(i, roots.get(i)); }
        barrel.setChanged();
        var manifest = new JsonObject(); manifest.addProperty("process", ProcessHandle.current().pid());
        manifest.add("roots", encode(server, roots));
        NetworkAcceptanceFiles.write("aether-ii-world.json", manifest.toString());
        NetworkAcceptanceFiles.write("aether-ii-save-report.txt", "Six paid used roots: Health/diamond, Sentry/diamond, Gravitite/diamond inverses\n" + report);
    }

    static List<ItemStack> roots(MinecraftServer server) {
        var barrel = barrel(server);
        var roots = new ArrayList<ItemStack>();
        for (int i = 0; i < 6; i++) { roots.add(barrel.getItem(i)); }
        return roots;
    }

    private static BarrelBlockEntity barrel(MinecraftServer server) {
        return (BarrelBlockEntity) server.overworld().getBlockEntity(BARREL);
    }

    static JsonArray encode(MinecraftServer server, List<ItemStack> roots) {
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var values = new JsonArray();
        for (var root : roots) { values.add(ItemStack.CODEC.encodeStart(ops, root).getOrThrow()); }
        return values;
    }

    static void compare(MinecraftServer server, List<ItemStack> roots, JsonArray expected) {
        require(roots.size() == 6 && expected.size() == 6, "Incomplete Aether lifecycle roots");
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int i = 0; i < 6; i++) {
            require(ItemStack.matches(roots.get(i), ItemStack.CODEC.parse(ops, expected.get(i)).getOrThrow()),
                    "Persistence changed complete root/history/damage/attributes " + i);
        }
    }

    static JsonObject read(String file) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(System.getProperty("mismatchedboots.runtimetest.markers"), file))).getAsJsonObject();
    }

    /** Reuse native attribute/menu probes; no duplicate ability engine or synthetic modifier oracle. */
    static void check(ServerPlayer owner, List<ItemStack> roots, StringBuilder report) throws Exception {
        var server = owner.level().getServer();
        var before = encode(server, roots);
        var api = new AetherIICompatibilityTest.NativeApi();
        for (int i = 0; i < 6; i++) {
            clear(owner);
            var root = roots.get(i);
            require(!root.has(component("charms")), "Lifecycle restored live root charm holders");
            if (i < 2) {
                measure(owner, root, Attributes.MAX_HEALTH, 1, "persisted Health " + i, report);
                install(owner, api, root, "health", 0, false);
            } else if (i < 4) {
                measure(owner, root, Attributes.FALL_DAMAGE_MULTIPLIER, -.375, "persisted Sentry " + i, report);
                fall(owner, root, 7.99, true); fall(owner, root, 8, false);
            } else {
                owner.setItemSlot(EquipmentSlot.HEAD, item("gravitite_helmet"));
                owner.setItemSlot(EquipmentSlot.CHEST, item("gravitite_chestplate"));
                owner.setItemSlot(EquipmentSlot.FEET, root);
                require(api.count(owner, item("gravitite_boots").get(component("armor_set"))) == 3,
                        "Restarted Gravitite lost third piece");
                fall(owner, root, 7.99, true);
                owner.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                require(!api.ability(owner, item("gravitite_boots").get(component("armor_set"))), "Two pieces activated after restart");
            }
        }
        clear(owner); compare(server, roots, before);
        report.append("Both inverses: snapshot-only Health +1, rejected Forge installation, Sentry -.375 and 7.99/8, Gravitite 3/2 counts/fall; unchanged full roots\n");
    }
}
