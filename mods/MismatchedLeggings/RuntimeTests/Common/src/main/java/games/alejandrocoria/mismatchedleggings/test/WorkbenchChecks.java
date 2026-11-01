package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedleggings.component.ComponentRuleSet;
import games.alejandrocoria.mismatchedleggings.component.ComponentRules;
import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.network.CompositionRulesPayload;
import games.alejandrocoria.mismatchedleggings.recipe.MixedLeggingsWorkbenchRecipe;
import games.alejandrocoria.mismatchedleggings.test.mixin.RecipeManagerInvoker;
import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.WorkbenchResult;
import games.alejandrocoria.mismatchedthings.api.composition.AttributePolicy;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentNumericKind;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentPolicy;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentRule;
import games.alejandrocoria.mismatchedthings.api.composition.DurabilityPolicy;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Product assertions through the actual core transaction; core internals are test-only. */
public final class WorkbenchChecks {
    public static final BlockPos TABLE = new BlockPos(0, 65, 0);

    private WorkbenchChecks() {
    }

    public static void writeRules(Path directory, String policy) throws Exception {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("mismatchedleggings-components.toml"),
                "[durability]\npolicy = '" + (policy.equals("maximum") ? "separate" : "average")
                        + "'\n[repair_cost]\npolicy = 'maximum'\n[components]\n");
        Files.writeString(directory.resolve("mismatchedleggings-attributes.toml"),
                "[attributes]\nfallback = '" + policy + "'\n[attributes.\"minecraft:armor\"]\npolicy = '" + policy + "'\n");
    }

    public static WorkbenchInput input(boolean boots) {
        var first = new ItemStack(boots ? Items.IRON_BOOTS : Items.IRON_LEGGINGS);
        var second = new ItemStack(boots ? Items.GOLDEN_BOOTS : Items.GOLDEN_LEGGINGS);
        first.setDamageValue(11);
        second.setDamageValue(3);
        return new WorkbenchInput(first, second);
    }

    public static WorkbenchResult assemble(MinecraftServer server, boolean boots) {
        var input = input(boots);
        return server.overworld().recipeAccess().getRecipeFor(MismatchedWorkbench.recipeType(), input, server.overworld())
                .orElseThrow().value().assemblePair(input);
    }

    static void verify(MinecraftServer server) {
        require(server.getCommands().getDispatcher().getRoot().getChild("mismatchedleggings") == null,
                "Leggings unexpectedly registered a config command");
        var rules = CompositionRuleState.server();
        require(rules.attributes().fallback() == AttributePolicy.MAXIMUM
                        && rules.components().durability() == DurabilityPolicy.SEPARATE,
                "Dedicated lifecycle ignored product configuration");
        verifyCodec(server, rules);
        var level = server.overworld();
        var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "leggings-workbench"), ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        require(level.recipeAccess().getRecipeFor(MismatchedWorkbench.recipeType(),
                new WorkbenchInput(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS)), level).isEmpty(),
                "Cross-slot pair matched a recipe");

        for (boolean boots : Boolean.getBoolean("leggings.test.boots") ? List.of(false, true) : List.of(false)) {
            verifyTransactions(server, player, boots);
        }
        verifyRecipeReplacement(server, player);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        level.setBlock(TABLE, MismatchedWorkbench.block().defaultBlockState(), 3);
    }

    private static void verifyTransactions(MinecraftServer server, ServerPlayer player, boolean boots) {
        var expected = assemble(server, boots);
        for (int first : List.of(2, 3)) {
            player.getInventory().clearContent();
            var menu = ready(player, boots);
            require(ItemStack.matches(menu.getSlot(2).getItem(), expected.primary())
                            && ItemStack.matches(menu.getSlot(3).getItem(), expected.inverse()), "Wrong ordered previews");
            menu.clicked(first, 0, ContainerInput.PICKUP, player);
            require(menu.isCommitted() && !menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem(), "First take did not pay once");
            require(ItemStack.matches(menu.getCarried(), first == 2 ? expected.primary() : expected.inverse()), "Pickup changed output");
            menu.setCarried(ItemStack.EMPTY);
            require(!menu.getSlot(0).mayPlace(input(boots).left()), "Paid inverse did not lock input");
            menu.removed(player);
            require(inventory(player).size() == 1 && ItemStack.matches(inventory(player).getFirst(),
                    first == 2 ? expected.inverse() : expected.primary()), "Close lost or duplicated paid inverse");
        }
        player.getInventory().clearContent();
        var unpaid = ready(player, boots);
        unpaid.removed(player);
        require(inventory(player).size() == 2 && contains(player, input(boots).left()) && contains(player, input(boots).right()),
                "Unpaid close failed to return exactly the inputs");
        player.getInventory().clearContent();
        var shift = ready(player, boots);
        require(!shift.quickMoveStack(player, 3).isEmpty() && !shift.quickMoveStack(player, 2).isEmpty(), "Shift-click failed");
        require(!shift.isCommitted() && inventory(player).size() == 2
                && contains(player, expected.primary()) && contains(player, expected.inverse()), "Shift-click did not pay exactly once");
    }

    private static void verifyRecipeReplacement(MinecraftServer server, ServerPlayer player) {
        var manager = server.getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        var pending = ready(player, false);
        var shift = ready(player, false);
        var replacementPending = ready(player, false);
        var paid = ready(player, false);
        paid.clicked(2, 0, ContainerInput.PICKUP, player);
        var frozen = paid.getSlot(3).getItem().copy();
        try {
            // Native apply boundary used by datapack reload, without filesystem/loader-specific conditions.
            applyRecipes(server, original.stream().filter(holder -> !(holder.value() instanceof MixedLeggingsWorkbenchRecipe)).toList());
            pending.clicked(2, 0, ContainerInput.PICKUP, player);
            require(pending.getCarried().isEmpty() && !pending.getSlot(2).hasItem() && pending.getSlot(0).hasItem(),
                    "Removed recipe allowed an unpaid stale pickup");
            require(shift.quickMoveStack(player, 3).isEmpty() && shift.getSlot(1).hasItem(), "Removed recipe allowed shift-click payment");
            require(ItemStack.matches(frozen, paid.getSlot(3).getItem()), "Recipe removal changed a paid inverse");

            var replacement = new ArrayList<RecipeHolder<?>>();
            original.forEach(holder -> replacement.add(holder.value() instanceof MixedLeggingsWorkbenchRecipe
                    ? new RecipeHolder<>(holder.id(), new MixedLeggingsWorkbenchRecipe()) : holder));
            applyRecipes(server, replacement);
            require(!replacementPending.quickMoveStack(player, 2).isEmpty(), "Replaced loaded recipe could not craft");
            require(ItemStack.matches(frozen, paid.getSlot(3).safeTake(1, 1, player)), "Replacement changed the paid inverse");
        } finally {
            applyRecipes(server, original);
        }
    }

    static void applyRecipes(MinecraftServer server, List<RecipeHolder<?>> recipes) {
        ((RecipeManagerInvoker) server.getRecipeManager()).leggings$apply(
                RecipeMap.create(recipes), server.getResourceManager(), Profiler.get());
        server.getRecipeManager().finalizeRecipeLoading(server.overworld().enabledFeatures());
    }

    private static void verifyCodec(MinecraftServer server, CompositionRules rules) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            var declarations = new LinkedHashMap<>(rules.components().declarations());
            var effective = new LinkedHashMap<>(rules.components().components().rules());
            var charge = Identifier.parse("leggings_skeleton_test:charge");
            var absent = Identifier.parse("absent_mod:charge");
            var numeric = ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.LONG);
            declarations.put(charge, numeric);
            effective.put(charge, numeric);
            declarations.put(absent, numeric);
            rules = new CompositionRules(rules.attributes(), new ComponentRules(rules.components().durability(),
                    rules.components().repairCost(), declarations, new ComponentRuleSet(effective)));
            CompositionRulesPayload.STREAM_CODEC.encode(buffer, new CompositionRulesPayload(rules));
            var decoded = CompositionRulesPayload.STREAM_CODEC.decode(buffer).rules();
            require(rules.equals(decoded) && !buffer.isReadable(), "Complete rule snapshot did not round-trip");
            buffer.clear();
            CompositionRulesPayload.STREAM_CODEC.encode(buffer, new CompositionRulesPayload(rules));
            buffer.writeByte(1);
            var before = CompositionRuleState.preview();
            boolean rejected = false;
            try {
                CompositionRuleState.installPreview(CompositionRulesPayload.STREAM_CODEC.decode(buffer).rules());
            } catch (io.netty.handler.codec.DecoderException expected) {
                rejected = true;
            }
            require(rejected && CompositionRuleState.preview().equals(before), "Malformed snapshot installed partial rules");

            rejectSnapshot(buffer, bytes -> {
                bytes.writeUtf("average");
                bytes.writeVarInt(-1);
            });
            rejectSnapshot(buffer, bytes -> {
                bytes.writeUtf("average");
                bytes.writeVarInt(2);
                for (int index = 0; index < 2; index++) {
                    bytes.writeUtf("minecraft:armor");
                    bytes.writeUtf("maximum");
                }
            });
            rejectSnapshot(buffer, bytes -> {
                bytes.writeUtf("average");
                bytes.writeVarInt(0);
                bytes.writeUtf("average");
                bytes.writeUtf("maximum");
                bytes.writeVarInt(1);
                bytes.writeUtf("minecraft:equippable");
                bytes.writeUtf("owner");
                bytes.writeByte(0);
            });
        } finally {
            buffer.release();
        }
    }

    private static void rejectSnapshot(RegistryFriendlyByteBuf buffer, Consumer<RegistryFriendlyByteBuf> write) {
        buffer.clear();
        write.accept(buffer);
        var before = CompositionRuleState.preview();
        boolean rejected = false;
        try {
            CompositionRuleState.installPreview(CompositionRulesPayload.STREAM_CODEC.decode(buffer).rules());
        } catch (io.netty.handler.codec.DecoderException expected) {
            rejected = true;
        }
        require(rejected && CompositionRuleState.preview().equals(before), "Invalid rules escaped atomic validation");
    }

    private static WorkbenchMenu ready(ServerPlayer player, boolean boots) {
        var menu = new WorkbenchMenu(300, player.getInventory());
        var input = input(boots);
        require(menu.getSlot(0).mayPlace(input.left()) && menu.getSlot(1).mayPlace(input.right()), "Product admission missing");
        menu.getSlot(0).set(input.left());
        menu.getSlot(1).set(input.right());
        require(menu.getSlot(2).hasItem() && menu.getSlot(3).hasItem(), "Loaded recipe produced no preview");
        return menu;
    }

    public static List<ItemStack> inventory(ServerPlayer player) {
        return player.getInventory().getNonEquipmentItems().stream().filter(stack -> !stack.isEmpty()).toList();
    }

    public static boolean contains(ServerPlayer player, ItemStack expected) {
        return inventory(player).stream().anyMatch(stack -> ItemStack.matches(stack, expected));
    }
}
