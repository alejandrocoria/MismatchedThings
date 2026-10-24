package games.alejandrocoria.mismatchedboots.runtimetest.mystical;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-only world/purchase fixture and observation of real connected movement. */
public final class MysticalLifecycle {
    static final BlockPos BARREL = new BlockPos(4, 80, 4);
    static volatile UUID playerId;
    static volatile boolean recordJump;
    static volatile double serverPeak;
    private static boolean seeded;

    private MysticalLifecycle() {}
    public static String phase() { return System.getProperty("mismatchedboots.test.mysticalLifecycle", ""); }
    public static boolean enabled() { return !phase().isEmpty(); }

    public static void tick(MinecraftServer server) {
        if (phase().equals("seed")) {
            if (seeded) { return; }
            seeded = true;
            try {
                var recipe = new MixedBootsWorkbenchRecipe();
                var first = recipe.assemblePair(new MismatchedWorkbenchInput(
                        MysticalAugmentsProbe.item("awakened_supremium_boots", "HEALTH_BOOST_III", "STEP_ASSIST"), new ItemStack(Items.DIAMOND_BOOTS)));
                var second = recipe.assemblePair(new MismatchedWorkbenchInput(
                        MysticalAugmentsProbe.item("awakened_supremium_boots", "JUMP_BOOST_III", "NO_FALL_DAMAGE"),
                        MysticalAugmentsProbe.item("prudentium_boots", "LUCK_I")));
                require(first.isComplete() && second.isComplete(), "Actual workbench recipe rejected fixtures");
                var roots = List.of(first.primary(), first.inverse(), second.primary(), second.inverse());
                server.overworld().setBlock(BARREL, Blocks.BARREL.defaultBlockState(), 3);
                var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(BARREL);
                for (int i = 0; i < roots.size(); i++) {
                    var root = roots.get(i);
                    root.set(DataComponents.CUSTOM_NAME, Component.literal("MA lifecycle " + i));
                    root.setDamageValue(3);
                    barrel.setItem(i, root);
                }
                barrel.setChanged();
                var manifest = new JsonObject(); manifest.addProperty("process", ProcessHandle.current().pid());
                manifest.add("roots", encode(server, roots));
                NetworkAcceptanceFiles.write("mystical-world.json", manifest.toString());
                NetworkAcceptanceFiles.write("mystical-seed-report.txt", "Four recipe-produced named/used inverse roots saved in an actual world barrel\n");
                NetworkAcceptanceFiles.write("mystical-seed-success.txt", "passed\n");
            } catch (Throwable failure) { NetworkAcceptanceFiles.failure("mystical-seed", failure); }
            finally { server.halt(false); }
        } else if (recordJump && playerId != null) {
            var owner = server.getPlayerList().getPlayer(playerId);
            if (owner != null) { serverPeak = Math.max(serverPeak, owner.getY() - 81); }
        }
    }

    static List<ItemStack> worldRoots(MinecraftServer server) {
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(BARREL);
        require(barrel != null, "Restart lost world barrel");
        var roots = new ArrayList<ItemStack>();
        for (int i = 0; i < 4; i++) { roots.add(barrel.getItem(i).copy()); }
        return roots;
    }
    static JsonArray encode(MinecraftServer server, List<ItemStack> roots) {
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var result = new JsonArray();
        roots.forEach(root -> result.add(ItemStack.CODEC.encodeStart(ops, root).getOrThrow()));
        return result;
    }
    static JsonObject read(String name) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(System.getProperty("mismatchedboots.runtimetest.markers"), name))).getAsJsonObject();
    }
    static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
