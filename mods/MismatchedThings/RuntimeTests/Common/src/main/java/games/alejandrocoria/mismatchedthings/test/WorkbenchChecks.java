package games.alejandrocoria.mismatchedthings.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class WorkbenchChecks {
    public static final BlockPos TABLE = new BlockPos(0, 65, 0);
    public static final BlockPos BARREL = new BlockPos(2, 65, 0);
    public static final boolean RECIPES = Boolean.getBoolean("workbench.test.recipes");
    private static int ticks;
    private static boolean done;

    private WorkbenchChecks() {
    }

    public static void serverTick(MinecraftServer server) {
        if (!System.getProperty("workbench.test.phase", "").equals("server") || done || ++ticks < 5) {
            return;
        }
        done = true;
        try {
            Identifier id = Identifier.parse("mismatchedthings:mismatched_workbench");
            require(BuiltInRegistries.BLOCK.containsKey(id) && BuiltInRegistries.ITEM.containsKey(id)
                            && BuiltInRegistries.MENU.containsKey(id) && BuiltInRegistries.RECIPE_TYPE.containsKey(id),
                    "Missing literal family registry key");
            require(MismatchedWorkbench.item() instanceof BlockItem item && item.getBlock() == MismatchedWorkbench.block(),
                    "Block item points at another block");
            require(BuiltInRegistries.RECIPE_TYPE.getValue(id) == MismatchedWorkbench.recipeType(), "Wrong recipe type");
            require(!BuiltInRegistries.ITEM.containsKey(Identifier.parse("mismatchedboots:mixed_boots")), "Boots leaked into core run");
            require(!BuiltInRegistries.BLOCK.containsKey(Identifier.parse("mismatchedboots:mismatched_workbench")), "Legacy station present");
            String origin = MismatchedWorkbench.class.getProtectionDomain().getCodeSource().getLocation().toString();
            require(origin.contains(".jar"), "Core was loaded from loose classes, not the packaged artifact: " + origin);

            var level = server.overworld();
            require(MismatchedWorkbench.block().defaultBlockState().is(BlockTags.MINEABLE_WITH_AXE), "Missing axe tag");
            var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "workbench-actor"), ClientInformation.createDefault());
            var menu = new WorkbenchMenu(1, player.getInventory());
            require(menu.getSlot(0).mayPlace(new ItemStack(Items.IRON_HELMET)) == RECIPES, "Wrong contributor admission");
            require(!menu.getSlot(0).mayPlace(new ItemStack(Items.STONE)), "Unrelated input accepted");
            var input = new WorkbenchInput(new ItemStack(Items.IRON_HELMET), new ItemStack(Items.GOLDEN_HELMET));
            require(level.recipeAccess().getRecipeFor(MismatchedWorkbench.recipeType(), input, level).isPresent() == RECIPES,
                    "Wrong loaded recipe presence");
            require(level.recipeAccess().getRecipeFor(MismatchedWorkbench.recipeType(),
                    new WorkbenchInput(input.left(), new ItemStack(Items.STONE)), level).isEmpty(), "Invalid pair matched");
            menu.getSlot(0).set(input.left());
            require(menu.getSlot(2).getItem().isEmpty(), "Partial input created a preview");
            menu.getSlot(1).set(input.right());
            require(menu.getSlot(2).hasItem() == RECIPES && menu.getSlot(3).hasItem() == RECIPES, "Wrong pair preview");

            CraftingInput crafting = CraftingInput.of(3, 3, List.of(
                    new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.SHEARS), new ItemStack(Items.SPRUCE_PLANKS),
                    new ItemStack(Items.BIRCH_PLANKS), new ItemStack(Items.DIAMOND), new ItemStack(Items.JUNGLE_PLANKS),
                    new ItemStack(Items.ACACIA_PLANKS), ItemStack.EMPTY, new ItemStack(Items.DARK_OAK_PLANKS)));
            var acquisition = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, crafting, level).orElseThrow();
            require(acquisition.id().identifier().equals(id) && acquisition.value().assemble(crafting).is(MismatchedWorkbench.item()),
                    "Family shaped recipe is absent or produces another item");
            require(server.getAdvancements().get(Identifier.parse("mismatchedthings:recipes/decorations/mismatched_workbench")) != null,
                    "Missing acquisition advancement");

            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    level.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            level.setBlock(TABLE, MismatchedWorkbench.block().defaultBlockState(), 3);
            require(level.getBlockEntity(TABLE) == null, "Workbench acquired persistent private inventory");
            level.setBlock(BARREL, Blocks.BARREL.defaultBlockState(), 3);
            ((Container) level.getBlockEntity(BARREL)).setItem(0, new ItemStack(MismatchedWorkbench.item()));
            write("server.ok", "literal registries, standalone artifact, acquisition/tag, admission, loaded recipes and persistence seed passed\n");
        } catch (Throwable failure) {
            fail(failure);
        } finally {
            server.halt(false);
        }
    }

    public static String encode(ItemStack stack, Level level) {
        return ItemStack.CODEC.encodeStart(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), stack).getOrThrow().toString();
    }

    public static void verifyCloseCleanup(ServerPlayer player) {
        player.getInventory().clearContent();
        var preview = new WorkbenchMenu(400, player.getInventory());
        preview.getSlot(0).set(new ItemStack(Items.IRON_HELMET));
        preview.getSlot(1).set(new ItemStack(Items.GOLDEN_HELMET));
        preview.removed(player);
        require(player.getInventory().countItem(Items.IRON_HELMET) == 1
                && player.getInventory().countItem(Items.GOLDEN_HELMET) == 1, "Preview close lost or duplicated input items");
        require(player.getInventory().getNonEquipmentItems().stream().filter(stack -> !stack.isEmpty())
                .noneMatch(stack -> stack.getHoverName().getString().startsWith("Fixture")), "Preview close returned unpaid outputs");

        player.getInventory().clearContent();
        var committed = new WorkbenchMenu(401, player.getInventory());
        committed.getSlot(0).set(new ItemStack(Items.IRON_HELMET));
        committed.getSlot(1).set(new ItemStack(Items.GOLDEN_HELMET));
        require(!committed.quickMoveStack(player, 2).isEmpty() && committed.isCommitted(), "First output failed to commit");
        require(!committed.getSlot(0).hasItem() && !committed.getSlot(1).hasItem(), "Inputs were not paid");
        committed.removed(player);
        var inventory = player.getInventory().getNonEquipmentItems();
        require(inventory.stream().filter(stack -> !stack.isEmpty()).count() == 2, "Paid close duplicated or lost items");
        require(inventory.stream().anyMatch(stack -> stack.getHoverName().getString().equals("Fixture primary"))
                && inventory.stream().anyMatch(stack -> stack.getHoverName().getString().equals("Fixture inverse")),
                "Paid close did not preserve both results");
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void write(String name, String text) {
        try {
            Path directory = Path.of(System.getProperty("workbench.test.report"));
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(name), text);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static void fail(Throwable failure) {
        StringWriter text = new StringWriter();
        failure.printStackTrace(new PrintWriter(text));
        write("failure.txt", text.toString());
        failure.printStackTrace();
    }
}
