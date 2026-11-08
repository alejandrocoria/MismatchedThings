package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.compatibility.undergarden.RogdoriumInfusionService;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;
import quek.undergarden.block.entity.InfuserBlockEntity;
import quek.undergarden.component.RogdoriumInfusion;
import quek.undergarden.component.UndergardenData;
import quek.undergarden.recipe.InfuserInput;
import quek.undergarden.recipe.InfusingRecipe;
import quek.undergarden.registry.UGAttachments;
import quek.undergarden.registry.UGDataComponents;

import java.util.List;
import java.util.UUID;

import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.PRODUCTS;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.history;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.item;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.lower;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.pair;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.recipe;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.recompose;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.sources;
import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Loaded only by the present-native profile; no prototype creation or admission overrides. */
final class RogdoriumNativeStateChecks {
    private RogdoriumNativeStateChecks() {}

    static String verify(MinecraftServer server) throws Exception {
        String variant = System.getProperty("leggings.test.rogdoriumVariant", "normal");
        if (variant.equals("recipe") || variant.equals("menu")) {
            return RogdoriumInfuserChecks.refusal(server, variant);
        }
        var level = server.overworld();
        var biome = level.registryAccess().lookupOrThrow(Registries.BIOME)
                .getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse("undergarden:depths")));
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                level.getChunk(x, z).fillBiomesFromNoise((qx, qy, qz, sampler) -> biome, level.getChunkSource().randomState().sampler());
            }
        }
        var armorRecipe = (InfusingRecipe) server.getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.id().identifier().equals(Identifier.parse("undergarden:armor_infusing")))
                .findFirst().orElseThrow().value();
        var report = new StringBuilder();
        for (String product : PRODUCTS) {
            creation(product);
            var nativeSource = paidNativeSource(level, product);
            var other = item("minecraft:diamond_" + lower(product));
            for (var root : pair(product, nativeSource, other)) {
                require(amount(root) == 600 && maximum(root) == 3600, "Paid infused vanilla creation " + product);
                var before = root.copy();
                require(armorRecipe.matches(new InfuserInput(root, InfusingRecipe.SlotType.ROGDORIUM), level),
                        "Production Infuser admission missing");
                var copied = armorRecipe.assemble(new InfuserInput(root, InfusingRecipe.SlotType.ROGDORIUM));
                require(ItemStack.matches(copied, root), "Native assemble copy changed reservoir/history");
                roundtrip(server, root);
                require(ItemStack.matches(before, root), "Copy/codecs mutated live root");
            }
            preservation(product);
            spending(level, product, nativeSource, other);
            transaction(level, product, nativeSource, other);
            unavailableBinding(product, nativeSource, other);
            RogdoriumInfuserChecks.verify(server, product, armorRecipe, nativeSource, other);
            report.append(product).append(": registered native binding; conservative creation both orientations/vanilla sources; ")
                    .append("exact recomposition including absence/zero/old-projection equality/root-only installation; ")
                    .append("native spending and recipe copy; paid workbench and unpaid invalid/binding refusal; JSON/stream roundtrip passed\n");
        }
        report.append(RogdoriumLifecycleChecks.seed(server));
        return report + "Native Infuser processing/menu/payment/reloads passed; hidden client verifies presentation and saved-world reopening separately\n";
    }

    private static void creation(String product) throws Exception {
        record Case(Integer first, Integer second, Integer expected) {}
        for (var example : List.of(new Case(null, null, null), new Case(0, null, 0), new Case(1201, null, 600),
                new Case(1201, 300, 750), new Case(1201, 1201, 1201))) {
            var first = item("undergarden:froststeel_" + lower(product));
            var second = item("minecraft:diamond_" + lower(product));
            if (example.first != null) { charge(first, example.first, 3600); }
            if (example.second != null) { charge(second, example.second, 3600); }
            var firstBefore = first.copy();
            var secondBefore = second.copy();
            for (var root : pair(product, first, second)) {
                require(example.expected == null ? !root.has(UGDataComponents.ROGDORIUM_INFUSION)
                        : amount(root) == example.expected && maximum(root) == 3600, "Native creation tuple " + product + " " + example);
            }
            require(ItemStack.matches(firstBefore, first) && ItemStack.matches(secondBefore, second), "Creation changed sources");
        }
    }

    private static void preservation(String product) throws Exception {
        var first = item("undergarden:froststeel_" + lower(product));
        charge(first, 1201, 3600);
        var second = item("minecraft:diamond_" + lower(product));
        for (var root : pair(product, first, second)) {
            for (var state : new RogdoriumInfusion[]{null, new RogdoriumInfusion(0, 3600),
                    new RogdoriumInfusion(1201, 3600), new RogdoriumInfusion(2400, 7200)}) {
                if (state == null) { root.remove(UGDataComponents.ROGDORIUM_INFUSION); }
                else { root.set(UGDataComponents.ROGDORIUM_INFUSION, state); }
                root.set(DataComponents.CUSTOM_NAME, Component.literal("live unrelated state"));
                var oldHistory = history(root, product);
                var before = root.copy();
                var sources = sources(root, product);
                charge(sources.getFirst(), 2000, 7200);
                charge(sources.getLast(), 300, 3600);
                var changed = recompose(product, root, sources.getFirst(), sources.getLast());
                require(java.util.Objects.equals(state, changed.get(UGDataComponents.ROGDORIUM_INFUSION)), "Recomposition imported source reserve " + product);
                require(Component.literal("live unrelated state").equals(changed.getCustomName()), "Unrelated live field lost");
                require(!oldHistory.equals(history(changed, product)), "Test did not actually transform source history");
                require(ItemStack.matches(root, before), "Recomposition changed previous root");
            }
        }
        // A root-only installation is not a request to install a container in either immutable source.
        var root = pair(product, second, second).getFirst();
        charge(root, 53, 3600);
        var result = recompose(product, root, second, second);
        require(amount(result) == 53 && sources(result, product).stream().noneMatch(stack -> stack.has(UGDataComponents.ROGDORIUM_INFUSION)),
                "Root-only infusion was lost or imported into sources");
    }

    private static void spending(ServerLevel level, String product, ItemStack source, ItemStack other) throws Exception {
        var root = pair(product, source, other).getFirst();
        var slot = product.equals("Boots") ? EquipmentSlot.FEET : EquipmentSlot.LEGS;
        var nativeControl = source.copy();
        charge(nativeControl, amount(root), maximum(root));
        var player = player(level);
        player.setItemSlot(slot, nativeControl);
        tick(player);
        int nativeSpent = 600 - amount(nativeControl);
        float nativeInfection = player.getData(UGAttachments.UNDERGARDEN_DATA).uthericInfection();
        require(nativeSpent > 0, "Native spending positive control failed");
        var oldHistory = history(root, product);
        player.setItemSlot(slot, root);
        tick(player);
        require(amount(root) == 600 - nativeSpent && oldHistory.equals(history(root, product))
                && Math.abs(nativeInfection - player.getData(UGAttachments.UNDERGARDEN_DATA).uthericInfection()) < .0001,
                "Mixed/native spending or infection diverged");
        var histories = sources(root, product);
        charge(histories.getFirst(), 3600, 3600);
        var refreshed = recompose(product, root, histories.getFirst(), histories.getLast());
        require(amount(refreshed) == amount(root), "Spent root replenished from transformed source");
        // Live writes model refill/removal preservation. Paid mixed refill belongs to B, not this state-only gate.
        charge(root, 3500, 3600);
        require(amount(recompose(product, root, histories.getFirst(), histories.getLast())) == 3500, "Live refill lost");
        root.remove(UGDataComponents.ROGDORIUM_INFUSION);
        require(!recompose(product, root, histories.getFirst(), histories.getLast()).has(UGDataComponents.ROGDORIUM_INFUSION), "Removed reserve resurrected");

        for (int reserve : new int[]{1, 0}) {
            charge(nativeControl, reserve, 3600);
            player.setItemSlot(slot, nativeControl);
            tick(player);
            float expected = player.getData(UGAttachments.UNDERGARDEN_DATA).uthericInfection();
            charge(root, reserve, 3600);
            player.setItemSlot(slot, root);
            tick(player);
            require(amount(root) == amount(nativeControl)
                    && Math.abs(expected - player.getData(UGAttachments.UNDERGARDEN_DATA).uthericInfection()) < .0001,
                    "Final-unit/exhaustion diverged from native");
        }
        charge(root, 10, 3600);
        player.setItemSlot(slot, ItemStack.EMPTY);
        tick(player);
        require(amount(root) == 10, "Unequipped root spent reserve");
        player.setItemSlot(slot, root);
        player.tickCount = 21;
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(player));
        require(amount(root) == 10, "Spent outside the native update interval");
        player.setData(UGAttachments.UNDERGARDEN_DATA, new UndergardenData(20, 0, true));
        player.tickCount = 20;
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(player));
        require(amount(root) == 10 && oldHistory.equals(history(root, product)), "Harmful threshold or immutable history changed");
    }

    private static void transaction(ServerLevel level, String product, ItemStack first, ItemStack second) throws Exception {
        var player = player(level);
        var menu = new WorkbenchMenu(42, player.getInventory());
        menu.getSlot(0).set(first.copy());
        menu.getSlot(1).set(second.copy());
        require(amount(menu.getSlot(2).getItem()) == 600 && amount(menu.getSlot(3).getItem()) == 600, "Wrong paid previews");
        var primary = menu.getSlot(2).safeTake(1, 1, player);
        require(amount(primary) == 600 && menu.isCommitted() && !menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem(), "First output did not pay exactly once");
        require(amount(menu.getSlot(3).safeTake(1, 1, player)) == 600, "Inverse paid result changed");

        var invalid = first.copy();
        charge(invalid, -1, 3600);
        refused(level, product, invalid, second);
    }

    private static void unavailableBinding(String product, ItemStack first, ItemStack second) throws Exception {
        var service = RogdoriumInfusionService.INSTANCE;
        var field = service.getClass().getDeclaredField("binding");
        field.setAccessible(true);
        Object binding = field.get(service);
        require(binding != null, "Native service never acquired its creation binding");
        var root = pair(product, first, second).getFirst();
        try {
            field.set(service, null);
            var input = new WorkbenchInput(first, second);
            require(!recipe(product).assemblePair(input).isComplete(), "Unsupported binding fell back to OWNER");
            var plain = item("minecraft:iron_" + lower(product));
            require(!pair(product, plain, plain).getFirst().has(UGDataComponents.ROGDORIUM_INFUSION), "Unsupported binding blocked ordinary sources");
            var sources = sources(root, product);
            require(java.util.Objects.equals(root.get(UGDataComponents.ROGDORIUM_INFUSION),
                    recompose(product, root, sources.getFirst(), sources.getLast()).get(UGDataComponents.ROGDORIUM_INFUSION)),
                    "Unsupported creation binding prevented exact preservation");
        } finally {
            field.set(service, binding);
        }
    }

    private static void refused(ServerLevel level, String product, ItemStack first, ItemStack second) throws Exception {
        var input = new WorkbenchInput(first, second);
        var firstBefore = first.copy();
        require(!recipe(product).assemblePair(input).isComplete(), "Invalid record produced a paid pair");
        var menu = new WorkbenchMenu(43, player(level).getInventory());
        menu.getSlot(0).set(first.copy());
        menu.getSlot(1).set(second.copy());
        require(!menu.getSlot(2).hasItem() && !menu.getSlot(3).hasItem() && !menu.isCommitted()
                && ItemStack.matches(first, menu.getSlot(0).getItem()) && ItemStack.matches(second, menu.getSlot(1).getItem()),
                "Refused composition changed payment or inputs");
        require(ItemStack.matches(firstBefore, first), "Refusal changed caller input");
    }

    private static ItemStack paidNativeSource(ServerLevel level, String product) {
        var position = new BlockPos(2, 4, 0);
        var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse("undergarden:infuser"));
        level.removeBlock(position, false);
        level.setBlockAndUpdate(position, block.defaultBlockState());
        var infuser = (InfuserBlockEntity) level.getBlockEntity(position);
        infuser.setItem(2, item("undergarden:rogdorium").copyWithCount(2));
        infuser.setItem(0, item("minecraft:iron_" + lower(product)));
        for (int tick = 0; tick < 500 && infuser.getItem(3).isEmpty(); tick++) {
            InfuserBlockEntity.serverTick(level, position, level.getBlockState(position), infuser);
        }
        require(amount(infuser.getItem(3)) == 1200 && infuser.getItem(0).isEmpty() && infuser.getItem(2).isEmpty(), "Native paid source infusion failed");
        return infuser.getItem(3).copy();
    }

    private static void roundtrip(MinecraftServer server, ItemStack root) {
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        require(ItemStack.matches(root, ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, root).getOrThrow()).getOrThrow()), "Persistent tuple roundtrip");
        var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess(), ConnectionType.NEOFORGE);
        try {
            ItemStack.STREAM_CODEC.encode(bytes, root);
            require(ItemStack.matches(root, ItemStack.STREAM_CODEC.decode(bytes)), "Stream tuple roundtrip");
        } finally { bytes.release(); }
    }

    static FakePlayer player(ServerLevel level) {
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "rogdorium_state"));
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        player.setPos(.5, 4, .5);
        return player;
    }

    static void tick(FakePlayer player) {
        player.setData(UGAttachments.UNDERGARDEN_DATA, new UndergardenData(0, 0, true));
        player.tickCount = 20;
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(player));
    }

    static int amount(ItemStack stack) {
        return stack.getOrDefault(UGDataComponents.ROGDORIUM_INFUSION, RogdoriumInfusion.DEFAULT).infusionAmount();
    }

    private static int maximum(ItemStack stack) {
        return stack.getOrDefault(UGDataComponents.ROGDORIUM_INFUSION, RogdoriumInfusion.DEFAULT).infusionMax();
    }

    static void charge(ItemStack stack, int amount, int maximum) {
        stack.set(UGDataComponents.ROGDORIUM_INFUSION, new RogdoriumInfusion(amount, maximum));
    }
}
