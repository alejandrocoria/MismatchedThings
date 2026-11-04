package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real datapack reload with an open menu: mixed previews must reject stale packet takes. */
final class SmithingReloadChecks {
    private static final String PACK = "file/leggings-smithing-reload";
    private static List<String> originalPacks;
    private static CompletableFuture<Void> job;
    private static int testCase;
    private static int stage;
    private static ItemStack before;
    private static ItemStack after;
    private static List<ItemStack> inputs;

    private SmithingReloadChecks() {
    }

    static boolean tick(Minecraft client) {
        if (job != null) {
            if (!job.isDone()) {
                return false;
            }
            job.join();
            job = null;
        }
        var server = client.getSingleplayerServer();
        switch (stage) {
            case 0 -> {
                require(Boolean.getBoolean("leggings.test.boots") && !Boolean.getBoolean("leggings.test.previousBoots"),
                        "Reload regression requires both current products");
                job = server.submit(() -> prepare(server));
                stage = 1;
            }
            case 1 -> {
                if (!ItemStack.matches(before, client.player.containerMenu.getSlot(3).getItem())) {
                    return false;
                }
                job = server.submit(() -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    int container = player.containerMenu.containerId;
                    if (!mode().equals("unchanged")) {
                        reload(server, mode());
                    }
                    require(player.containerMenu instanceof SmithingMenu && player.containerMenu.containerId == container,
                            "Native resource reload closed/replaced the observed menu");
                    // Do not touch ingredients or call createResult: observe native reload behavior.
                    after = player.containerMenu.getSlot(3).getItem().copy();
                    player.containerMenu.broadcastChanges();
                    SmithingReloadPackets.arm(container);
                });
                stage = 2;
            }
            case 2 -> {
                if (!ItemStack.matches(after, client.player.containerMenu.getSlot(3).getItem())) {
                    return false;
                }
                client.gameMode.handleContainerInput(client.player.containerMenu.containerId, 3, 0,
                        shift() ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP, client.player);
                stage = 3;
            }
            case 3 -> {
                var observed = SmithingReloadPackets.last;
                if (observed == null || !synchronizedState(client, observed)) {
                    return false;
                }
                record(observed);
                if (++testCase == 24) {
                    job = server.submit(() -> server.reloadResources(originalPacks).join());
                    stage = 4;
                } else {
                    stage = 0;
                }
            }
            case 4 -> {
                return true;
            }
            default -> throw new AssertionError("Unknown reload probe stage: " + stage);
        }
        return false;
    }

    private static void prepare(MinecraftServer server) {
        reload(server, "baseline");
        var player = server.getPlayerList().getPlayers().getFirst();
        var menu = player.containerMenu;
        menu.setCarried(ItemStack.EMPTY);
        player.getInventory().clearContent();
        for (int slot = 0; slot < 4; slot++) {
            menu.getSlot(slot).set(ItemStack.EMPTY);
        }
        var root = switch (product()) {
            case "leggings" -> MixedLeggingsFactory.createPair(new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.IRON_LEGGINGS)).primary();
            case "boots" -> boots();
            default -> new ItemStack(Items.DIAMOND_BOOTS);
        };
        inputs = List.of(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2), root.copy(), new ItemStack(Items.AMETHYST_SHARD, 2));
        for (int slot = 0; slot < 3; slot++) {
            menu.getSlot(slot).set(inputs.get(slot).copy());
        }
        before = menu.getSlot(3).getItem().copy();
        require(!before.isEmpty(), "Missing baseline native smithing preview: " + product());
        menu.broadcastChanges();
    }

    private static ItemStack boots() {
        try {
            var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
            var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class)
                    .invoke(null, new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.IRON_BOOTS));
            return (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot create current Boots reload sample", failure);
        }
    }

    private static void reload(MinecraftServer server, String mode) {
        if (originalPacks == null) {
            originalPacks = List.copyOf(server.getPackRepository().getSelectedIds());
        }
        var selected = new ArrayList<>(originalPacks);
        if (!mode.equals("removed")) {
            var pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("leggings-smithing-reload");
            var format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
            try {
                var recipes = pack.resolve("data/leggings_skeleton_test/recipe");
                Files.createDirectories(recipes);
                Files.writeString(pack.resolve("pack.mcmeta"), """
                        {"pack":{"description":"Smithing reload regression","min_format":[%d,%d],"max_format":[%d,%d]}}
                        """.formatted(format.major(), format.minor(), format.major(), format.minor()));
                for (String slot : List.of("boots", "leggings")) {
                    Files.writeString(recipes.resolve("reload_" + slot + ".json"), """
                            {"type":"minecraft:smithing_transform","template":"minecraft:netherite_upgrade_smithing_template",
                            "base":"minecraft:diamond_%s","addition":"minecraft:amethyst_shard","result":{"id":"minecraft:%s_%s"}}
                            """.formatted(slot, mode.equals("replaced") ? "netherite" : "golden", slot));
                }
            } catch (Exception failure) {
                throw new IllegalStateException("Cannot stage disposable reload recipes", failure);
            }
            server.getPackRepository().reload();
            selected.add(PACK);
        }
        server.reloadResources(selected).join();
        long found = server.getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.id().identifier().toString().startsWith("leggings_skeleton_test:reload_")).count();
        require(found == (mode.equals("removed") ? 0 : 2), "Datapack recipe discovery/removal failed");
        if (!mode.equals("removed")) {
            server.getRecipeManager().getRecipes().stream()
                    .filter(holder -> holder.id().identifier().toString().startsWith("leggings_skeleton_test:reload_"))
                    .forEach(holder -> {
                        boolean boots = holder.id().identifier().getPath().endsWith("boots");
                        var base = new ItemStack(boots ? Items.DIAMOND_BOOTS : Items.DIAMOND_LEGGINGS);
                        var input = new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                                base, new ItemStack(Items.AMETHYST_SHARD));
                        var target = boots ? Items.GOLDEN_BOOTS : Items.GOLDEN_LEGGINGS;
                        if (mode.equals("replaced")) {
                            target = boots ? Items.NETHERITE_BOOTS : Items.NETHERITE_LEGGINGS;
                        }
                        require(((SmithingRecipe) holder.value()).assemble(input).is(target),
                                "Filesystem reload retained the wrong recipe body: " + holder.id());
                    });
        }
    }

    private static boolean synchronizedState(Minecraft client, SmithingReloadPackets.Snapshot observed) {
        var menu = client.player.containerMenu;
        for (int slot = 0; slot < 3; slot++) {
            if (!ItemStack.matches(menu.getSlot(slot).getItem(), observed.inputs().get(slot))) {
                return false;
            }
        }
        if (!ItemStack.matches(menu.getSlot(3).getItem(), observed.result())
                || !ItemStack.matches(menu.getCarried(), observed.carried())) {
            return false;
        }
        var inventory = client.player.getInventory().getNonEquipmentItems().stream().filter(stack -> !stack.isEmpty()).toList();
        if (inventory.size() != observed.inventory().size()) {
            return false;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (!ItemStack.matches(inventory.get(slot), observed.inventory().get(slot))) {
                return false;
            }
        }
        return true;
    }

    private static void record(SmithingReloadPackets.Snapshot observed) {
        var received = shift() ? observed.inventory() : observed.carried().isEmpty() ? List.<ItemStack>of() : List.of(observed.carried());
        boolean spent = observed.inputs().get(1).isEmpty();
        boolean paidOld = received.stream().anyMatch(stack -> ItemStack.matches(stack, before));
        if (spent) {
            require(received.size() == 1 && observed.inputs().get(0).getCount() == 1 && observed.inputs().get(2).getCount() == 1,
                    "Unexpected partial/duplicate smithing payment: " + product());
        } else {
            require(received.isEmpty(), "Rejected extraction still delivered an item");
            for (int slot = 0; slot < 3; slot++) {
                require(ItemStack.matches(inputs.get(slot), observed.inputs().get(slot)), "Rejected extraction spent input " + slot);
            }
        }
        if (mode().equals("unchanged")) {
            require(spent && paidOld, "Unchanged-recipe positive control failed: " + product());
        } else if (!product().equals("native")) {
            require(!spent && received.isEmpty(), "Mixed preview survived recipe reload: " + product() + "/" + mode());
        }
        String preview = after.isEmpty() ? "empty" : ItemStack.matches(before, after) ? "retained" : "updated";
        System.out.println("SMITHING_RELOAD product=" + product() + " reload=" + mode() + " take=" + (shift() ? "shift" : "normal")
                + " preview=" + preview + " outcome=" + (!spent ? "blocked" : paidOld ? "paid-preview" : "paid-updated")
                + " received=" + received.stream().map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).toList());
    }

    private static String product() {
        return List.of("boots", "leggings", "native").get(testCase / 8);
    }

    private static String mode() {
        return List.of("unchanged", "removed", "replaced", "reloaded").get(testCase % 8 / 2);
    }

    private static boolean shift() {
        return testCase % 2 != 0;
    }
}
