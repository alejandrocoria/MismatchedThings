package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.menu.MismatchedWorkbenchMenu;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Native forge/transfer calls and real menu takes, followed by independent world reload; no candidate adapter. */
final class NeoVitaeCompositionLifecycleTest {
    private static final BlockPos BARREL = new BlockPos(0, 80, 0);
    private static final BlockPos FORGE = new BlockPos(2, 80, 0);

    private NeoVitaeCompositionLifecycleTest() {
    }

    static void save(MinecraftServer server) throws Exception {
        var api = new NativeApi();
        var player = player(server);
        ItemStack diamond = api.infuse(server, new ItemStack(Items.DIAMOND_BOOTS), "spiritus_gem_petty");
        diamond = api.bloodMending(server, diamond);
        ItemStack iron = api.infuse(server, new ItemStack(Items.IRON_BOOTS), "spiritus_gem_common");
        require(api.fill(diamond, api.raw, 40, true) == 40 && api.fill(iron, api.raw, 100, true) == 100, "Native source fill failed");
        var compatible = workbench(player, diamond, iron);
        for (ItemStack root : compatible) {
            api.tuple(root, 1024, 70, api.raw);
            require(root.has(api.blood), "Inherited mark missing");
        }
        ItemStack live = compatible.getFirst();
        var provenance = ModContent.getMixedBootsData(live).orElseThrow();
        ItemStack beforeSimulation = live.copy();
        require(api.drain(live, api.raw, 70, false) == 70 && ItemStack.matches(beforeSimulation, live), "Native drain simulation mutated root");
        require(api.drain(live, api.raw, 1000, true) == 70 && api.fill(live, api.ruina, 12.5, true) == 12.5,
                "Native depletion/type switch failed");
        require(provenance.equals(ModContent.getMixedBootsData(live).orElseThrow()), "Transfer changed provenance");
        NeoForgeNeoVitaeCompositionTest.decorate(server.overworld(), live);
        ItemStack beforeTake = live.copy();
        ItemStack trimmed = smithing(server, player, live, Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, Items.AMETHYST_SHARD);
        api.same(beforeTake, trimmed, "paid trim");
        var trimmedData = ModContent.getMixedBootsData(trimmed).orElseThrow();
        require(trimmedData.left().source().has(net.minecraft.core.component.DataComponents.TRIM)
                        && trimmedData.right().source().has(net.minecraft.core.component.DataComponents.TRIM), "Paid trim missed a source");
        beforeTake = trimmed.copy();
        ItemStack upgraded = smithing(server, player, trimmed, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Items.NETHERITE_INGOT);
        api.same(beforeTake, upgraded, "paid tier");
        var tier = ModContent.getMixedBootsData(upgraded).orElseThrow();
        require(tier.left().source().is(Items.NETHERITE_BOOTS) && tier.right().source().is(Items.IRON_BOOTS)
                        && tier.left().source().get(api.amount) == 40 && tier.right().source().get(api.amount) == 100,
                "Tier take changed source balances or wrong item");
        require(api.drain(compatible.get(1), api.raw, 1000, true) == 70, "Empty control did not drain");

        ItemStack raw = api.infuse(server, new ItemStack(Items.DIAMOND_BOOTS), "spiritus_gem_petty");
        ItemStack ruina = api.infuse(server, new ItemStack(Items.IRON_BOOTS), "spiritus_gem_common");
        api.fill(raw, api.raw, 40, true);
        api.fill(ruina, api.ruina, 100, true);
        var conflicting = workbench(player, raw, ruina);
        api.tuple(conflicting.getFirst(), 1024, 40, api.raw);
        api.tuple(conflicting.get(1), 1024, 100, api.ruina);
        beforeSimulation = conflicting.getFirst().copy();
        require(api.fill(conflicting.getFirst(), api.ruina, 20, true) == 0
                        && ItemStack.matches(beforeSimulation, conflicting.getFirst()), "Native conflicting fill converted a resource");
        require(api.drain(conflicting.getFirst(), api.raw, 7.25, true) == 7.25, "Native fractional drain failed");

        var plain = workbench(player, new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.IRON_BOOTS));
        ItemStack later = api.infuse(server, plain.getFirst(), "spiritus_gem_greater");
        later = api.bloodMending(server, later);
        require(api.fill(later, api.ruina, 13.125, true) == 13.125, "Later native root fill failed");
        beforeTake = later.copy();
        ItemStack laterTier = smithing(server, player, later, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Items.NETHERITE_INGOT);
        api.same(beforeTake, laterTier, "later-infused paid tier");
        api.tuple(laterTier, 4096, 13.125, api.ruina);
        require(!plain.get(1).has(api.capacity) && !plain.get(1).has(api.blood), "Plain inverse acquired state");

        server.overworld().setBlock(BARREL, Blocks.BARREL.defaultBlockState(), 3);
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(BARREL);
        require(barrel != null, "Missing persistence barrel");
        var roots = List.of(upgraded, compatible.get(1), conflicting.getFirst(), conflicting.get(1), laterTier, plain.get(1));
        var saved = new JsonArray();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int index = 0; index < roots.size(); index++) {
            barrel.setItem(index, roots.get(index));
            saved.add(ItemStack.CODEC.encodeStart(ops, roots.get(index)).getOrThrow());
            // Native components in a vanilla slot packet, not a connected-client or reconnect claim.
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            try {
                ClientboundContainerSetSlotPacket.STREAM_CODEC.encode(buffer,
                                                                      new ClientboundContainerSetSlotPacket(1, 0, index, roots.get(index)));
                var decoded = ClientboundContainerSetSlotPacket.STREAM_CODEC.decode(buffer);
                require(decoded.getSlot() == index && ItemStack.matches(roots.get(index), decoded.getItem()), "Slot packet lost complete root");
            } finally {
                buffer.release();
            }
        }
        barrel.setChanged();
        var manifest = new JsonObject();
        manifest.addProperty("process", ProcessHandle.current().pid());
        manifest.add("roots", saved);
        NetworkAcceptanceFiles.write("neo-vitae-composition-saved.json", manifest.toString());
        NetworkAcceptanceFiles.write("neo-vitae-composition-lifecycle-report.txt",
                                     "3 real Workbench transactions/6 outputs; native forge infusion and Blood Mending with ingredient/fuel payment; "
                                             + "native fill/drain/simulation/type rejection/deplete/refill; 3 paid smithing takes; "
                                             + "6 complete roots saved in world barrel (compatible, zero, two conflicting, later-infused, absent); "
                                             + "vanilla slot-packet codec round trips with native components, not connected-client acceptance\n");
    }

    static void restart(MinecraftServer server) throws Exception {
        var api = new NativeApi();
        var manifest = JsonParser.parseString(Files.readString(Path.of(System.getProperty("mismatchedboots.runtimetest.markers"),
                                                                       "neo-vitae-composition-saved.json"))).getAsJsonObject();
        require(manifest.get("process").getAsLong() != ProcessHandle.current().pid(), "Restart did not use another JVM");
        server.overworld().getChunk(0, 0);
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(BARREL);
        require(barrel != null, "World restart lost barrel");
        var saved = manifest.getAsJsonArray("roots");
        require(saved.size() == 6, "Incomplete independent expectations");
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int index = 0; index < saved.size(); index++) {
            ItemStack expected = ItemStack.CODEC.parse(ops, saved.get(index)).getOrThrow();
            require(ItemStack.matches(expected, barrel.getItem(index)), "World restart changed root/provenance " + index);
        }
        var player = player(server);
        ItemStack live = barrel.removeItemNoUpdate(0);
        var history = ModContent.getMixedBootsData(live).orElseThrow();
        require(api.drain(live, api.ruina, .125, true) == .125, "Native post-restart drain failed");
        require(history.equals(ModContent.getMixedBootsData(live).orElseThrow()), "Post-restart transfer mutated history");
        ItemStack beforeTake = live.copy();
        ItemStack trimmed = smithing(server, player, live, Items.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, Items.GOLD_INGOT);
        api.same(beforeTake, trimmed, "post-restart paid trim");
        barrel.setItem(0, trimmed);
        ItemStack empty = barrel.getItem(1);
        require(empty.get(api.amount) == 0 && empty.has(api.capacity) && api.fill(empty, api.ruina, .5, true) == .5,
                "Empty installed container lost type-switch support");
        ItemStack absent = barrel.removeItemNoUpdate(5);
        require(!absent.has(api.capacity) && !absent.has(api.amount) && !absent.has(api.type), "Absent container resurrected");
        ItemStack installed = api.infuse(server, absent, "spiritus_gem_petty");
        api.tuple(installed, 64, 0, api.raw);
        barrel.setItem(5, installed);
        barrel.setChanged();
        NetworkAcceptanceFiles.write("neo-vitae-composition-restart-report.txt",
                                     "second JVM reloads actual world barrel; six independent complete-stack/provenance comparisons before mutation; "
                                             + "native drain, paid trim, empty refill/type switch and later native infusion still work\n");
    }

    private static List<ItemStack> workbench(ServerPlayer player, ItemStack first, ItemStack second) {
        var menu = new MismatchedWorkbenchMenu(1, player.getInventory());
        ItemStack beforeFirst = first.copy(), beforeSecond = second.copy();
        menu.getSlot(0).set(first);
        menu.getSlot(1).set(second);
        ItemStack primaryPreview = menu.getSlot(2).getItem().copy(), inversePreview = menu.getSlot(3).getItem().copy();
        require(!primaryPreview.isEmpty() && !inversePreview.isEmpty() && !menu.isCommitted(), "Missing Workbench previews");
        var result = new ArrayList<ItemStack>();
        result.add(menu.getSlot(2).safeTake(1, 1, player));
        require(menu.isCommitted() && menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty()
                        && ItemStack.matches(inversePreview, menu.getSlot(3).getItem()), "First take did not pay once for both outputs");
        result.add(menu.getSlot(3).safeTake(1, 1, player));
        require(ItemStack.matches(primaryPreview, result.getFirst()) && ItemStack.matches(inversePreview, result.get(1))
                        && menu.getSlot(2).safeTake(1, 1, player).isEmpty() && menu.getSlot(3).safeTake(1, 1, player).isEmpty(),
                "Workbench changed results or allowed another unpaid take");
        for (int index = 0; index < 2; index++) {
            var data = ModContent.getMixedBootsData(result.get(index)).orElseThrow();
            require(ItemStack.matches(data.left().source(), index == 0 ? beforeFirst : beforeSecond)
                            && ItemStack.matches(data.right().source(), index == 0 ? beforeSecond : beforeFirst),
                    "Workbench lost original provenance");
        }
        return result;
    }

    private static ServerPlayer player(MinecraftServer server) {
        // The native advancement/research listeners send packets; NeoForge's offline test player has a no-op transport.
        var player = FakePlayerFactory.getMinecraft(server.overworld());
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static ItemStack smithing(MinecraftServer server, ServerPlayer player, ItemStack root, Item template, Item addition) {
        var menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.create(server.overworld(), BlockPos.ZERO));
        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(new ItemStack(template));
        menu.getSlot(SmithingMenu.BASE_SLOT).set(root);
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(addition));
        ItemStack preview = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
        menu.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
        require(!preview.isEmpty() && ItemStack.matches(preview, menu.getCarried())
                        && menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty()
                        && menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty()
                        && menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty(), "Paid smithing menu take failed");
        ItemStack result = menu.getCarried();
        menu.setCarried(ItemStack.EMPTY);
        return result;
    }

    /** Reflection binds only pinned native methods; all matching, progress, assembly and debits run upstream. */
    private static final class NativeApi {
        final DataComponentType<Double> capacity = NeoForgeNeoVitaeCompositionTest.component("spiritus_max");
        final DataComponentType<Double> amount = NeoForgeNeoVitaeCompositionTest.component("spiritus_amount");
        final DataComponentType<Object> type = NeoForgeNeoVitaeCompositionTest.component("spiritus_type");
        final DataComponentType<Boolean> blood = NeoForgeNeoVitaeCompositionTest.component("blood_mending");
        final Object raw = type.codec().parse(JsonOps.INSTANCE, new com.google.gson.JsonPrimitive("raw")).getOrThrow();
        final Object ruina = type.codec().parse(JsonOps.INSTANCE, new com.google.gson.JsonPrimitive("ruina")).getOrThrow();
        final Method fill, drain, maximum, tick, getSlot, setSlot;
        final Class<?> forgeClass;

        NativeApi() throws ReflectiveOperationException {
            var spiritus = Class.forName("com.breakinblocks.neovitae.common.datacomponent.SpiritusType");
            var helper = Class.forName("com.breakinblocks.neovitae.spiritus.SpiritusHelper");
            fill = helper.getMethod("fillSpiritus", ItemStack.class, spiritus, double.class, boolean.class);
            drain = helper.getMethod("drainSpiritus", ItemStack.class, spiritus, double.class, boolean.class);
            maximum = helper.getMethod("resolveMaxSpiritus", ItemStack.class);
            forgeClass = Class.forName("com.breakinblocks.neovitae.common.blockentity.HellfireForgeBlockEntity");
            tick = forgeClass.getMethod("tick", Level.class, BlockPos.class, BlockState.class, forgeClass);
            var inventory = Class.forName(forgeClass.getName() + "$Inv");
            getSlot = inventory.getMethod("getStackInSlot", int.class);
            setSlot = inventory.getMethod("setStackInSlot", int.class, ItemStack.class);
        }

        double fill(ItemStack root, Object aspect, double value, boolean execute) throws ReflectiveOperationException {
            return (double) fill.invoke(null, root, aspect, value, execute);
        }

        double drain(ItemStack root, Object aspect, double value, boolean execute) throws ReflectiveOperationException {
            return (double) drain.invoke(null, root, aspect, value, execute);
        }

        ItemStack infuse(MinecraftServer server, ItemStack target, String gem) throws Exception {
            var provenance = ModContent.getMixedBootsData(target);
            double expectedCapacity = (double) maximum.invoke(null, item(gem));
            ItemStack result = forge(server, List.of(target, item(gem)), 0);
            tuple(result, expectedCapacity, 0, raw);
            require(provenance.equals(ModContent.getMixedBootsData(result)), "Native infusion changed mixed provenance");
            return result;
        }

        ItemStack bloodMending(MinecraftServer server, ItemStack target) throws Exception {
            var provenance = ModContent.getMixedBootsData(target);
            ItemStack result = forge(server, List.of(target, item("tabula_robur"), new ItemStack(Items.LAPIS_LAZULI),
                                                     new ItemStack(Items.NETHER_WART)), 200);
            require(Boolean.TRUE.equals(result.get(blood)), "Native Blood Mending installation failed");
            require(provenance.equals(ModContent.getMixedBootsData(result)), "Native Blood Mending changed mixed provenance");
            return result;
        }

        ItemStack forge(MinecraftServer server, List<ItemStack> ingredients, double cost) throws Exception {
            var level = server.overworld();
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("neovitae", "hellfire_forge"));
            require(block != Blocks.AIR, "Missing native forge block");
            level.setBlock(FORGE, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(FORGE, block.defaultBlockState(), 3);
            Object forge = level.getBlockEntity(FORGE);
            require(forgeClass.isInstance(forge), "Missing native forge entity");
            Object inventory = forgeClass.getField("inv").get(forge);
            for (int index = 0; index < ingredients.size(); index++) {
                setSlot.invoke(inventory, index, ingredients.get(index).copy());
            }
            ItemStack fuel = item("spiritus_gem_common");
            double full = (double) maximum.invoke(null, fuel);
            require(fill(fuel, raw, full, true) == full, "Could not seed full native fuel");
            setSlot.invoke(inventory, 4, fuel);
            // Drive the unmodified native tick to completion; do not set progress or assemble/pay in the fixture.
            for (int step = 0; step <= 100 && stack(inventory, 5).isEmpty(); step++) {
                tick.invoke(null, level, FORGE, level.getBlockState(FORGE), forge);
            }
            ItemStack result = stack(inventory, 5).copy();
            require(!result.isEmpty() && stack(inventory, 4).get(amount) == full - cost, "Native forge did not complete/pay expected fuel");
            for (int index = 0; index < 4; index++) {
                require(stack(inventory, index).isEmpty(), "Forge did not consume one ingredient");
            }
            setSlot.invoke(inventory, 5, ItemStack.EMPTY);
            setSlot.invoke(inventory, 4, ItemStack.EMPTY);
            return result;
        }

        private ItemStack stack(Object inventory, int slot) throws ReflectiveOperationException {
            return (ItemStack) getSlot.invoke(inventory, slot);
        }

        void tuple(ItemStack root, double expectedCapacity, double expectedAmount, Object expectedType) {
            require(Double.valueOf(expectedCapacity).equals(root.get(capacity)) && Double.valueOf(expectedAmount).equals(root.get(amount))
                            && expectedType.equals(root.get(type)), "Unexpected native lifecycle tuple");
        }

        void same(ItemStack before, ItemStack after, String operation) {
            NeoForgeNeoVitaeCompositionTest.sameLiveState(before, after, capacity, amount, type, blood, operation);
        }

        private static ItemStack item(String name) {
            return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("neovitae", name)).orElseThrow());
        }
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalStateException(message);
        }
    }
}
