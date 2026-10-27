package games.alejandrocoria.mismatchedthings.spike.observer;

import games.alejandrocoria.mismatchedthings.spike.core.WorkbenchCore;
import games.alejandrocoria.mismatchedthings.spike.core.WorkbenchMenu;
import games.alejandrocoria.mismatchedthings.spike.core.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.spike.core.WorkbenchRecipe;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class SpikeChecks {
    public static final BlockPos TABLE = new BlockPos(0, 65, 0);
    public static final BlockPos BARREL = new BlockPos(2, 65, 0);
    public static String version;
    public static boolean boots;
    public static boolean addon;
    private static int ticks;
    private static boolean done;

    private SpikeChecks() {
    }

    public static void serverTick(MinecraftServer server) {
        if (!System.getProperty("workbench.spike.phase", "").equals("server") || done || ++ticks < 5) {
            return;
        }

        done = true;
        try {
            String scenario = System.getProperty("workbench.spike.case");
            require(boots == (scenario.equals("boots") || scenario.equals("both")), "Wrong Boots consumer presence");
            require(addon == (scenario.equals("addon") || scenario.equals("both")), "Wrong sibling presence");
            require(version.equals(addon ? "1.1.0" : "1.0.0"), "Wrong selected core: " + version);
            require(WorkbenchCore.initializations == 1, "Core initialized more than once");
            require(WorkbenchCore.contributors() == (boots ? 1 : 0) + (addon ? 1 : 0), "Wrong contributor count");
            String origin = WorkbenchCore.class.getProtectionDomain().getCodeSource().getLocation().toString();
            require(!origin.contains("classes/java/core"), "API leaked from loose compile output");
            boolean extension;
            try {
                Class.forName("games.alejandrocoria.mismatchedthings.spike.core.WorkbenchExtensions");
                extension = true;
            } catch (ClassNotFoundException expected) {
                extension = false;
            }
            require(extension == addon, "The newer-only API does not match core selection");
            var id = Identifier.parse("mismatchedthings:mismatched_workbench");
            require(BuiltInRegistries.BLOCK.containsKey(id) && BuiltInRegistries.ITEM.containsKey(id)
                    && BuiltInRegistries.MENU.containsKey(id) && BuiltInRegistries.RECIPE_TYPE.containsKey(id), "Missing family registry ID");
            require(BuiltInRegistries.ITEM.getValue(id) instanceof BlockItem item
                    && item.getBlock() == BuiltInRegistries.BLOCK.getValue(id), "Wrong block-item linkage");
            require(BuiltInRegistries.RECIPE_TYPE.getValue(id) == WorkbenchRecipe.TYPE, "Wrong station recipe type");
            require(BuiltInRegistries.ITEM.containsKey(Identifier.parse("mismatchedboots:mixed_boots")) == boots,
                    "Packaged Boots presence is not isolated");
            var level = server.overworld();
            var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "spike-actor"), ClientInformation.createDefault());
            var menu = new WorkbenchMenu(1, player.getInventory());
            require(menu.getSlot(0).mayPlace(new ItemStack(Items.IRON_BOOTS)) == boots, "Wrong Boots admission");
            require(menu.getSlot(0).mayPlace(new ItemStack(Items.IRON_HELMET)) == addon, "Wrong sibling admission");
            require(!menu.getSlot(0).mayPlace(new ItemStack(Items.STONE)), "Unrelated input admitted");
            var bootInput = new WorkbenchInput(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS));
            var addonInput = new WorkbenchInput(new ItemStack(Items.IRON_HELMET), new ItemStack(Items.GOLDEN_HELMET));
            require(level.recipeAccess().getRecipeFor(WorkbenchRecipe.TYPE, bootInput, level).isPresent() == boots,
                    "Wrong data-driven Boots recipe presence");
            require(level.recipeAccess().getRecipeFor(WorkbenchRecipe.TYPE, addonInput, level).isPresent() == addon,
                    "Wrong data-driven sibling recipe presence");
            require(level.recipeAccess().getRecipeFor(WorkbenchRecipe.TYPE,
                    new WorkbenchInput(bootInput.left(), addonInput.right()), level).isEmpty(), "Mixed domains matched a recipe");
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    level.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            level.setBlock(TABLE, WorkbenchCore.mismatchedWorkbenchBlock().orElseThrow().defaultBlockState(), 3);
            level.setBlock(BARREL, Blocks.BARREL.defaultBlockState(), 3);
            var barrel = (Container) level.getBlockEntity(BARREL);
            barrel.setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
            if (boots) {
                barrel.setItem(1, level.recipeAccess().getRecipeFor(WorkbenchRecipe.TYPE, bootInput, level).orElseThrow()
                        .value().assemblePair(bootInput).primary());
                write("saved-boots.json", encode(barrel.getItem(1), level));
            }
            if (addon) {
                barrel.setItem(2, level.recipeAccess().getRecipeFor(WorkbenchRecipe.TYPE, addonInput, level).orElseThrow()
                        .value().assemblePair(addonInput).primary());
            }
            write("server.ok", "core=" + version + "\ncontributors=" + WorkbenchCore.contributors()
                    + "\norigin=" + origin + "\nregistry, input, loaded recipes, real Boots composition and saved station seed verified\n");
        } catch (Throwable failure) {
            fail(failure);
        } finally {
            server.halt(false);
        }
    }

    public static String encode(ItemStack stack, Level level) {
        return ItemStack.CODEC.encodeStart(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), stack)
                .getOrThrow().toString();
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void write(String name, String text) {
        try {
            Path directory = Path.of(System.getProperty("workbench.spike.report"));
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(name), text);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static void fail(Throwable failure) {
        var text = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(text));
        write("failure.txt", text.toString());
        failure.printStackTrace();
    }
}
