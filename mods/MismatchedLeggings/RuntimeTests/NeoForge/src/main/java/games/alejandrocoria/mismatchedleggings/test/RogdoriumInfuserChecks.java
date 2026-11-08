package games.alejandrocoria.mismatchedleggings.test;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import quek.undergarden.block.entity.InfuserBlockEntity;
import quek.undergarden.inventory.InfuserMenu;
import quek.undergarden.recipe.InfuserInput;
import quek.undergarden.recipe.InfusingRecipe;
import quek.undergarden.registry.UGDataComponents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.RogdoriumNativeStateChecks.amount;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumNativeStateChecks.charge;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumNativeStateChecks.player;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.PRODUCTS;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.item;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.lower;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.pair;
import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real native machine/menu transactions and recipe reloads, not direct private infusion calls. */
final class RogdoriumInfuserChecks {
    private RogdoriumInfuserChecks() {}

    static void verify(MinecraftServer server, String product, InfusingRecipe recipe, ItemStack source, ItemStack other) throws Exception {
        var level = server.overworld();
        var wrongType = Arrays.stream(InfusingRecipe.SlotType.values()).filter(type -> type != InfusingRecipe.SlotType.ROGDORIUM)
                .findFirst().orElseThrow();
        for (var root : pair(product, source, other)) {
            root.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("unchanged live root"));
            // 1200 and absence reproduce the former OWNER/uncharged-inverse layouts from these paid source histories.
            for (Integer reserve : new Integer[]{null, 0, 600, 1200, 3500}) {
                if (reserve == null) {
                    root.remove(UGDataComponents.ROGDORIUM_INFUSION);
                } else {
                    charge(root, reserve, 3600);
                }
                int initial = amount(root);
                int fuel = initial == 3500 ? 6 : 2;
                var before = root.copy();
                var machine = ready(level, root, fuel);
                process(level, machine);
                var expected = root.copy();
                charge(expected, Math.min(3600, initial + fuel * 600), 3600);
                require(ItemStack.matches(expected, machine.getItem(3)) && machine.getItem(0).isEmpty()
                        && machine.getItem(2).isEmpty(), "Native paid mixed infusion changed history/state/payment " + product);
                require(ItemStack.matches(root, before), "Native infusion changed caller history");
                require(!recipe.matches(new InfuserInput(root, wrongType), level), "Wrong fuel slot type gained admission");
            }
            menu(level, root, true, true);
            reloads(server, product, root);
        }
        negatives(server, product, pair(product, source, other).getFirst());
        // Native positives must not depend on either product adapter.
        menu(level, item("minecraft:iron_" + lower(product)), true, true);
    }

    static String refusal(MinecraftServer server, String variant) throws Exception {
        var level = server.overworld();
        var recipe = armor(server);
        for (String product : PRODUCTS) {
            var source = item("minecraft:iron_" + lower(product));
            var other = item("minecraft:diamond_" + lower(product));
            var root = pair(product, source, other).getFirst();
            boolean processing = !variant.equals("recipe");
            require(recipe.matches(new InfuserInput(source, InfusingRecipe.SlotType.ROGDORIUM), level),
                    "Guard refusal changed native armor");
            require(recipe.matches(new InfuserInput(root, InfusingRecipe.SlotType.ROGDORIUM), level) == processing,
                    "Wrong local processing refusal");
            menu(level, root, !variant.equals("menu"), processing);
            var machine = ready(level, root, 2);
            process(level, machine);
            if (processing) {
                require(amount(machine.getItem(3)) == 1200 && machine.getItem(2).isEmpty(), "Menu refusal disabled processing");
            } else {
                unchanged(machine, root, 2);
            }
        }
        return "Isolated " + variant + " guard refusal preserves native armor and the other local extension\n";
    }

    private static void negatives(MinecraftServer server, String product, ItemStack root) {
        var level = server.overworld();
        var raw = RecoveryChecks.encode(root, server.registryAccess());
        var key = "mismatched" + lower(product) + ":mixed_" + lower(product) + "_data";
        var missing = raw.copy();
        missing.getCompoundOrEmpty("components").getCompoundOrEmpty(key).getCompoundOrEmpty("left")
                .putString("id", "removed_undergarden:missing_" + lower(product));
        var suspended = RecoveryChecks.decode(missing, server.registryAccess());
        var malformed = raw.copy();
        malformed.getCompoundOrEmpty("components").getCompoundOrEmpty(key).putString("left", "malformed");
        var quarantined = RecoveryChecks.decode(malformed, server.registryAccess());
        require(suspended.has(BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(
                Identifier.parse("mismatched" + lower(product) + ":suspended_" + lower(product)))), "No suspended negative fixture");
        for (var negative : List.of(item("mismatched" + lower(product) + ":mixed_" + lower(product)),
                suspended, quarantined, root.copyWithCount(2), item("minecraft:stone"))) {
            require(!armor(server).matches(new InfuserInput(negative, InfusingRecipe.SlotType.ROGDORIUM), level),
                    "Invalid root gained armor recipe admission: " + negative);
            // Native container insertion clamps overstacked armor to one; test count-two admission before that normalization.
            if (negative.getCount() == 1) {
                var machine = ready(level, negative, 2);
                process(level, machine);
                unchanged(machine, negative, 2);
            }
            menu(level, negative, false, false);
        }
    }

    private static void reloads(MinecraftServer server, String product, ItemStack root) {
        var manager = server.getRecipeManager();
        var recipes = List.copyOf(manager.getRecipes());
        var original = recipes.stream().filter(holder -> holder.id().identifier().equals(Identifier.parse("undergarden:armor_infusing")))
                .findFirst().orElseThrow();
        var level = server.overworld();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonElement encoded = Recipe.CODEC.encodeStart(ops, original.value()).getOrThrow();
        try {
            var removed = recipes.stream().filter(holder -> holder != original).toList();
            WorkbenchChecks.applyRecipes(server, removed);
            var noRecipe = ready(level, root, 2);
            process(level, noRecipe);
            unchanged(noRecipe, root, 2);
            menu(level, root, true, false); // Placement deliberately does not promise processing availability.

            var changed = encoded.deepCopy().getAsJsonObject();
            changed.addProperty("input", "minecraft:iron_" + lower(product));
            var narrowed = Recipe.CODEC.parse(ops, changed).getOrThrow();
            replace(server, recipes, original, narrowed);
            require(!armor(server).matches(new InfuserInput(root, InfusingRecipe.SlotType.ROGDORIUM), level),
                    "Changed ingredient inherited old armor proof");
            var wrongRecipe = ready(level, root, 2);
            process(level, wrongRecipe);
            unchanged(wrongRecipe, root, 2);
            menu(level, root, true, false);

            var equivalent = encoded.deepCopy().getAsJsonObject();
            equivalent.addProperty("infusing_time", 17);
            equivalent.addProperty("experience", 0.25);
            var replacement = Recipe.CODEC.parse(ops, equivalent).getOrThrow();
            require(replacement != original.value(), "Replacement must be a new recipe instance");
            replace(server, recipes, original, replacement);
            var refreshed = ready(level, root, 1);
            for (int tick = 0; tick < 16; tick++) {
                InfuserBlockEntity.serverTick(level, refreshed.getBlockPos(), refreshed.getBlockState(), refreshed);
            }
            require(refreshed.getItem(3).isEmpty() && refreshed.getItem(2).getCount() == 1, "Native replacement time ignored");
            InfuserBlockEntity.serverTick(level, refreshed.getBlockPos(), refreshed.getBlockState(), refreshed);
            require(amount(refreshed.getItem(3)) == Math.min(3600, amount(root) + 600)
                    && refreshed.getItem(2).isEmpty(), "Equivalent reloaded armor recipe lost admission");
        } finally {
            WorkbenchChecks.applyRecipes(server, recipes);
        }
    }

    private static void replace(MinecraftServer server, List<RecipeHolder<?>> recipes, RecipeHolder<?> original, Recipe<?> replacement) {
        var changed = new ArrayList<RecipeHolder<?>>(recipes);
        changed.remove(original);
        changed.add(new RecipeHolder<>(original.id(), replacement));
        WorkbenchChecks.applyRecipes(server, changed);
    }

    private static InfusingRecipe armor(MinecraftServer server) {
        return (InfusingRecipe) server.getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.id().identifier().equals(Identifier.parse("undergarden:armor_infusing")))
                .findFirst().orElseThrow().value();
    }

    private static void menu(ServerLevel level, ItemStack input, boolean accepted, boolean processing) {
        var machine = ready(level, ItemStack.EMPTY, 0);
        var player = player(level);
        var menu = new InfuserMenu(0, player.getInventory(), machine, new SimpleContainerData(2));
        require(menu.getSlot(0).mayPlace(input), "Native permissive placement changed");
        require(!menu.getSlot(2).mayPlace(item("minecraft:diamond")) && !menu.getSlot(3).mayPlace(input),
                "Fuel or output slot restrictions changed");
        player.getInventory().setItem(9, input.copy());
        menu.quickMoveStack(player, 4);
        require(accepted ? ItemStack.matches(machine.getItem(0), input) : machine.getItem(0).isEmpty(), "Native quick-move admission");
        if (accepted) {
            machine.setItem(2, item("undergarden:rogdorium"));
            process(level, machine);
            if (processing) {
                require(amount(machine.getItem(3)) == Math.min(3600, amount(input) + 600)
                        && machine.getItem(2).isEmpty(), "Paid output after menu transfer");
                menu.quickMoveStack(player, 3);
                require(machine.getItem(3).isEmpty() && player.getInventory().contains(stack ->
                        stack.is(input.getItem()) && amount(stack) == Math.min(3600, amount(input) + 600)), "Output extraction lost paid item");
            } else {
                unchanged(machine, input, 1);
            }
        }
    }

    private static InfuserBlockEntity ready(ServerLevel level, ItemStack input, int fuel) {
        var position = new BlockPos(5, 4, 0);
        var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse("undergarden:infuser"));
        level.removeBlock(position, false);
        level.setBlockAndUpdate(position, block.defaultBlockState());
        var machine = (InfuserBlockEntity) level.getBlockEntity(position);
        machine.setItem(2, fuel == 0 ? ItemStack.EMPTY : item("undergarden:rogdorium").copyWithCount(fuel));
        machine.setItem(0, input.copy());
        return machine;
    }

    private static void process(ServerLevel level, InfuserBlockEntity machine) {
        for (int tick = 0; tick < 500 && machine.getItem(3).isEmpty(); tick++) {
            InfuserBlockEntity.serverTick(level, machine.getBlockPos(), machine.getBlockState(), machine);
        }
    }

    private static void unchanged(InfuserBlockEntity machine, ItemStack input, int fuel) {
        require(ItemStack.matches(machine.getItem(0), input) && machine.getItem(2).getCount() == fuel && machine.getItem(3).isEmpty(),
                "Refused machine changed input/fuel/output: input=" + input + "; now=" + machine.getItem(0)
                        + "; fuel=" + machine.getItem(2) + "; output=" + machine.getItem(3));
    }
}
