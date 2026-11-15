package games.alejandrocoria.mismatchedleggings.test.mysticalrefunds;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import games.alejandrocoria.mismatchedleggings.test.mysticaleffects.NativeAugments;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real damage, inventory insertion and accepted native overflow entities; no candidate refund code. */
public final class RefundChecks {
    private static boolean finished;
    private static int sequence;
    private static final StringBuilder REPORT = new StringBuilder();
    private final RefundProduct product;

    private RefundChecks(RefundProduct product) {
        this.product = product;
    }
    public static boolean enabled() { return Boolean.getBoolean("leggings.test.mysticalRefunds"); }
    public static void tick(MinecraftServer server) {
        if (finished || server.getTickCount() < 20) { return; }
        finished = true;
        try {
            boolean absent = Boolean.getBoolean("leggings.test.maAbsent");
            String graph = System.getProperty("leggings.test.maGraph", "alone");
            require(RefundProduct.BOOTS.installed() == List.of("boots", "current").contains(graph)
                    && RefundProduct.LEGGINGS.installed() == List.of("alone", "current").contains(graph), "Wrong installed MA product graph");
            if (!absent) {
                REPORT.append(SharedRefundWitness.verify(!System.getProperty("leggings.test.maRefundVariant", "normal").equals("flow")));
            } else {
                require(java.util.Arrays.stream(ItemStack.class.getDeclaredMethods()).noneMatch(method -> method.getName().equals("mismatchedthings$maRefundReady")),
                        "Optional absence loaded the MA hook");
                require(RefundChecks.class.getClassLoader().getResource("com/blakebr0/mysticalagriculture/api/util/AugmentUtils.class") == null,
                        "Absent profile retained native MA classes");
            }
            if (graph.equals("core")) {
                var player = player(server);
                var root = new ItemStack(Items.DIAMOND_BOOTS);
                player.setItemSlot(EquipmentSlot.FEET, root);
                destroy(root, player);
                require(root.isEmpty() && delivered(player).isEmpty(), "Unclaimed core-only item refunded or survived break");
                if (!absent) {
                    new RefundChecks(RefundProduct.BOOTS).nativeControl(server, false);
                }
                REPORT.append("Independent core, no armor products, ordinary unclaimed consumption and optional admission passed.\n");
            }
            for (var product : RefundProduct.values()) {
                if (!product.installed()) {
                    continue;
                }
                var checks = new RefundChecks(product);
                REPORT.append("Product ").append(product).append('\n');
                if (absent) {
                    checks.absence(server);
                } else {
                    checks.run(server);
                }
            }
            if (System.getProperty("leggings.test.maRefundVariant", "normal").equals("decline")) {
                REPORT.append("After Leggings declined: ").append(SharedRefundWitness.verify(true));
                new RefundChecks(RefundProduct.BOOTS).checkPair(server,
                        RefundProduct.BOOTS.nativeItem("inferium_leggings", "HEALTH_BOOST_I"), new ItemStack(Items.DIAMOND_BOOTS), false);
            }
            if (!absent && graph.equals("current") && List.of("normal", "compatible").contains(System.getProperty("leggings.test.maRefundVariant", "normal"))) {
                jointRoots(server);
            }
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "refunds.txt"), REPORT.toString());
            SkeletonChecks.passed("server");
        } catch (Throwable failure) { SkeletonChecks.failed("server", failure); }
        finally { RefundControl.reset(null, null); server.halt(false); }
    }

    private void absence(MinecraftServer server) throws Exception {
        var player = player(server);
        var root = product.pair(new ItemStack(product.iron()), new ItemStack(product.diamond())).getFirst();
        player.setItemSlot(product.slot(), root);
        destroy(root, player);
        require(root.isEmpty() && delivered(player).isEmpty(), "Absence broke ordinary wear");
        REPORT.append("Quiet optional absence and ordinary actual break passed.\n");
    }

    private static void jointRoots(MinecraftServer server) throws Exception {
        for (int orientation = 0; orientation < 2; orientation++) {
            var boots = RefundProduct.BOOTS.pair(RefundProduct.BOOTS.nativeItem("awakened_supremium_leggings", "HEALTH_BOOST_I"),
                    new ItemStack(Items.DIAMOND_BOOTS)).get(orientation);
            var leggings = RefundProduct.LEGGINGS.pair(RefundProduct.LEGGINGS.nativeItem("awakened_supremium_leggings", "LUCK_I"),
                    new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
            var player = player(server);
            player.setItemSlot(EquipmentSlot.FEET, boots);
            player.setItemSlot(EquipmentSlot.LEGS, leggings);
            RefundControl.reset(boots, player.getInventory());
            destroy(boots, player);
            var expected = new HashMap<Item, Integer>();
            if (orientation == 0) {
                expected.put(augmentItem("HEALTH_BOOST_I"), 1);
            }
            require(boots.isEmpty() && !leggings.isEmpty() && delivered(player).equals(expected), "Boots break interfered with coequipped leggings");
            RefundControl.reset(leggings, player.getInventory());
            destroy(leggings, player);
            expected.put(augmentItem("LUCK_I"), 1);
            require(leggings.isEmpty() && delivered(player).equals(expected), "Coequipped actual breaks lost or duplicated purchases");
        }
        REPORT.append("Coequipped Boots/Leggings actual breaks retain independent purchase authority in both orientations\n");
        RefundControl.reset(null, null);
    }

    private void run(MinecraftServer server) throws Exception {
        boolean refused = System.getProperty("leggings.test.maRefundVariant", "normal").equals("flow");
        boolean declined = product == RefundProduct.LEGGINGS && System.getProperty("leggings.test.maRefundVariant", "normal").equals("decline");
        if (refused || declined) {
            nativeControl(server, false);
            var player = player(server);
            var root = pair("HEALTH_BOOST_I"); player.setItemSlot(product.slot(), root);
            require(product.selected(root), "Refund refusal disabled independent selection");
            destroy(root, player);
            require(root.isEmpty() && delivered(player).isEmpty(), "Opaque final predicate authorized refund or suppressed ordinary break");
            REPORT.append(declined ? "Registered Leggings receipt declined; independent selection and ordinary break retained.\n"
                    : "Changed final break predicate refused shared refunds; effect reader retained.\n"); return;
        }
        nativeControl(server, false); nativeControl(server, true);
        checkPair(server, product.nativeItem("inferium_leggings", "HEALTH_BOOST_I"), new ItemStack(product.diamond()), false);
        checkPair(server, product.nativeItem("inferium_leggings", "HEALTH_BOOST_I"), product.nativeItem("prudentium_leggings", "LUCK_I"), false);
        checkPair(server, product.nativeItem("awakened_supremium_leggings", "HEALTH_BOOST_I", "STEP_ASSIST"),
                product.nativeItem("awakened_supremium_leggings", "HEALTH_BOOST_V", "SPEED_III"), false);
        checkPair(server, product.nativeItem("awakened_supremium_leggings", "HEALTH_BOOST_I", "HEALTH_BOOST_I"), new ItemStack(product.diamond()), false);
        checkPair(server, product.nativeItem("inferium_leggings"), new ItemStack(product.iron()), false);
        checkPair(server, new ItemStack(product.iron()), new ItemStack(product.diamond()), false);
        checkPair(server, product.nativeItem("inferium_leggings", "LUCK_I"), product.nativeItem("inferium_leggings", "HEALTH_BOOST_I"), true);
        failures(server);
        restoredPurchases(server);
    }

    private void restoredPurchases(MinecraftServer server) throws Exception {
        var roots = product.pair(product.nativeItem("awakened_supremium_leggings", "HEALTH_BOOST_I"),
                product.nativeItem("awakened_supremium_leggings", "HEALTH_BOOST_V", "STEP_ASSIST"));
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (int orientation = 0; orientation < roots.size(); orientation++) {
            var original = roots.get(orientation);
            var encoded = (CompoundTag) ItemStack.CODEC.encodeStart(ops, original).getOrThrow();
            var restored = ItemStack.CODEC.parse(ops, product.archived(encoded)).getOrThrow();
            require(ItemStack.matches(original, restored), "Archive restore changed full root/purchase history");
            var player = player(server);
            player.setItemSlot(product.slot(), restored);
            RefundControl.reset(restored, player.getInventory());
            destroy(restored, player);
            var expected = orientation == 0 ? Map.of(augmentItem("HEALTH_BOOST_I"), 1)
                    : Map.of(augmentItem("HEALTH_BOOST_V"), 1, augmentItem("STEP_ASSIST"), 1);
            require(restored.isEmpty() && delivered(player).equals(expected), "Restored root did not retain physical left-owner purchases");
        }
        REPORT.append("Existing archive codec restores exact roots; subsequent actual breaks retain physical purchases in both orientations (not a mod-removal world matrix).\n");
        RefundControl.reset(null, null);
    }

    private static ServerPlayer player(MinecraftServer server) throws Exception {
        var profile = new GameProfile(UUID.randomUUID(), "ma-refund-" + ++sequence);
        var player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        var loaded = ServerGamePacketListenerImpl.class.getDeclaredMethod("markClientLoaded"); loaded.setAccessible(true); loaded.invoke(player.connection);
        player.setGameMode(GameType.SURVIVAL); player.setPos(0, 80, 0); player.setNoGravity(true);
        server.overworld().getChunkAt(player.blockPosition());
        return player;
    }

    private ItemStack pair(String augment) {
        return product.pair(product.nativeItem("awakened_supremium_leggings", augment), new ItemStack(product.diamond())).getFirst();
    }
    private static Item augmentItem(String name) { return (Item) NativeAugments.invoke(NativeAugments.augment(name), "getItem", new Class<?>[0]); }
    private static void destroy(ItemStack root, ServerPlayer player) {
        root.remove(DataComponents.UNBREAKABLE); root.setDamageValue(root.getMaxDamage() - 1);
        root.hurtAndBreak(2, player.level(), player, item -> {});
    }
    private static void full(ServerPlayer player) {
        for (int i = 0; i < 36; i++) { player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64)); }
    }
    private static List<ItemEntity> drops(ServerPlayer player) {
        return RefundControl.drops.stream().filter(item -> !item.isRemoved() && item.level() == player.level()
                && player.getBoundingBox().inflate(4).intersects(item.getBoundingBox())).toList();
    }
    private static Map<Item, Integer> delivered(ServerPlayer player) {
        var result = new HashMap<Item, Integer>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) { add(result, player.getInventory().getItem(i)); }
        for (var drop : drops(player)) { add(result, drop.getItem()); }
        return result;
    }
    private static void add(Map<Item, Integer> map, ItemStack stack) {
        if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("mysticalagriculture")
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_augment")) {
            map.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
    }
    private static Map<Item, Integer> physical(ItemStack stack) {
        var result = new HashMap<Item, Integer>();
        for (Object augment : (List<?>) NativeAugments.invoke(NativeAugments.type("api.util.AugmentUtils"), "getAugments", new Class<?>[]{ItemStack.class}, stack)) {
            result.merge((Item) NativeAugments.invoke(augment, "getItem", new Class<?>[0]), 1, Integer::sum);
        }
        return result;
    }
    private void nativeControl(MinecraftServer server, boolean overflow) throws Exception {
        var player = player(server); if (overflow) { full(player); }
        var stack = product.nativeItem("inferium_leggings", "HEALTH_BOOST_I"); var expected = physical(stack);
        player.setItemSlot(product.slot(), stack); destroy(stack, player);
        require(stack.isEmpty() && delivered(player).equals(expected), "Native actual-break positive failed empty=" + stack.isEmpty() + " actual=" + delivered(player) + " expected=" + expected + " overflow=" + overflow);
        require(!overflow || !drops(player).isEmpty(), "Native overflow positive absent");
        drops(player).forEach(ItemEntity::discard);
        REPORT.append("Native actual break/inventory positive; overflow=").append(overflow).append('\n');
    }
    private void checkPair(MinecraftServer server, ItemStack left, ItemStack right, boolean overflow) throws Exception {
        var originals = List.of(left.copy(), right.copy());
        var pair = product.pair(left, right); var expected = List.of(physical(left), physical(right));
        for (int orientation = 0; orientation < 2; orientation++) {
            var player = player(server); if (overflow) { full(player); }
            var root = pair.get(orientation);
            player.setItemSlot(product.slot(), root); root.remove(DataComponents.UNBREAKABLE);
            RefundControl.reset(root, player.getInventory());
            root.hurtAndBreak(1, player, product.slot());
            require(delivered(player).isEmpty() && RefundControl.deliveries == 0 && !root.isEmpty(), "Nonterminal wear refunded");
            root.setDamageValue(0); require(delivered(player).isEmpty(), "Repair refunded");
            destroy(root, player);
            require(root.isEmpty() && delivered(player).equals(expected.get(orientation)), "Wrong left-source purchases orientation=" + orientation + " actual=" + delivered(player) + " expected=" + expected.get(orientation));
            int deliveries = RefundControl.deliveries;
            require(deliveries == expected.get(orientation).values().stream().mapToInt(Integer::intValue).sum(), "Early or duplicate delivery");
            root.hurtAndBreak(2, player.level(), player, item -> {});
            require(delivered(player).equals(expected.get(orientation)) && RefundControl.deliveries == deliveries, "Repeated completion refunded twice");
            require(!overflow || expected.get(orientation).isEmpty() || !drops(player).isEmpty(), "Mixed overflow not exercised");
            drops(player).forEach(ItemEntity::discard);
        }
        require(ItemStack.matches(left, originals.get(0)) && ItemStack.matches(right, originals.get(1)), "Original sources mutated");
        REPORT.append("Both orientations: conserved physical purchases ").append(expected).append("; overflow=").append(overflow).append('\n');
        RefundControl.reset(null, null);
    }

    private void failures(MinecraftServer server) throws Exception {
        for (String fault : List.of("cancel", "throwBefore", "throwAfter", "replaceBefore", "replaceAfter", "move", "sourceBefore", "sourceAfter", "damage", "creative", "spectator", "creativeBefore", "spectatorAfter", "delete", "drop", "carried", "count", "preReenter", "postReenter", "deliveryReenter", "otherRoot", "cancelDelivery", "throwDelivery", "lore", "restoreCount", "damageAfter", "ambiguous", "offhand")) {
            var player = player(server); var root = pair("HEALTH_BOOST_I");
            root.remove(DataComponents.UNBREAKABLE); root.setDamageValue(root.getMaxDamage() - 1);
            player.setItemSlot(product.slot(), root); RefundControl.reset(root, player.getInventory());
            boolean expect = Set.of("carried", "preReenter", "postReenter", "deliveryReenter", "otherRoot", "lore", "offhand").contains(fault);
            var mutate = product.sourceMutation(root);
            Runnable after = () -> {};
            switch (fault) {
                case "cancel" -> RefundControl.cancel = true;
                case "throwBefore" -> RefundControl.before = () -> { throw new IllegalStateException("controlled before"); };
                case "throwAfter" -> after = () -> { throw new IllegalStateException("controlled after"); };
                case "replaceBefore" -> RefundControl.before = () -> player.setItemSlot(product.slot(), root.copy());
                case "replaceAfter" -> after = () -> player.setItemSlot(product.slot(), new ItemStack(product.iron()));
                case "move" -> after = () -> { player.setItemSlot(product.slot(), ItemStack.EMPTY); player.getInventory().setItem(0, root); };
                case "sourceBefore" -> RefundControl.before = mutate;
                case "sourceAfter" -> after = mutate;
                case "damage" -> RefundControl.before = () -> root.setDamageValue(0);
                case "creative" -> player.setGameMode(GameType.CREATIVE);
                case "spectator" -> player.setGameMode(GameType.SPECTATOR);
                case "creativeBefore" -> RefundControl.before = () -> player.setGameMode(GameType.CREATIVE);
                case "spectatorAfter" -> after = () -> player.setGameMode(GameType.SPECTATOR);
                case "delete" -> root.setCount(0);
                case "drop" -> player.setItemSlot(product.slot(), ItemStack.EMPTY);
                case "carried" -> { player.setItemSlot(product.slot(), ItemStack.EMPTY); player.getInventory().setItem(0, root); }
                case "count" -> root.setCount(2);
                case "preReenter" -> RefundControl.before = () -> root.hurtAndBreak(2, player.level(), player, item -> {});
                case "postReenter" -> after = () -> root.hurtAndBreak(2, player.level(), player, item -> {});
                case "deliveryReenter" -> RefundControl.delivery = () -> root.hurtAndBreak(2, player.level(), player, item -> {});
                case "otherRoot" -> {
                    var other = pair("LUCK_I"); player.getInventory().setItem(0, other);
                    RefundControl.delivery = () -> destroy(other, player);
                }
                case "cancelDelivery" -> RefundControl.cancelDelivery = true;
                case "throwDelivery" -> RefundControl.throwDelivery = true;
                case "restoreCount" -> after = () -> root.setCount(1);
                case "damageAfter" -> after = () -> root.setDamageValue(0);
                case "ambiguous" -> player.getInventory().setItem(0, root);
                case "offhand" -> {
                    player.setItemSlot(product.slot(), ItemStack.EMPTY);
                    player.setItemSlot(EquipmentSlot.OFFHAND, root);
                }
                case "lore" -> after = () -> root.set(DataComponents.CUSTOM_NAME, Component.literal("unrelated"));
            }
            final Runnable callback = after;
            try { if (!fault.equals("delete")) { root.hurtAndBreak(2, player.level(), player, item -> callback.run()); } }
            catch (IllegalStateException failure) { require(failure.getMessage().startsWith("controlled"), "Unexpected failure " + failure); }
            var expected = expect ? new HashMap<>(Map.of(augmentItem("HEALTH_BOOST_I"), 1)) : new HashMap<Item, Integer>();
            if (fault.equals("otherRoot")) { expected.put(augmentItem("LUCK_I"), 1); }
            require(delivered(player).equals(expected), "Wrong refund under " + fault + ": " + delivered(player));
            if (fault.equals("cancelDelivery") || fault.equals("throwDelivery")) {
                require(root.isEmpty() && RefundControl.deliveries == 1, "Dispatch must occur only once after consumption");
                RefundControl.throwDelivery = RefundControl.cancelDelivery = false;
                root.hurtAndBreak(2, player.level(), player, item -> {});
                require(RefundControl.deliveries == 1 && delivered(player).isEmpty(), "Failed dispatch retried");
            }
            if (fault.equals("cancel") || fault.equals("throwBefore")) {
                RefundControl.reset(root, player.getInventory()); destroy(root, player);
                require(delivered(player).equals(Map.of(augmentItem("HEALTH_BOOST_I"), 1)) && RefundControl.deliveries == 1,
                        "Aborted invocation leaked state into a later genuine break");
            }
            if (fault.equals("throwAfter")) {
                require(root.isEmpty() && RefundControl.deliveries == 0, "Throwing native callback completed a receipt");
                var later = pair("HEALTH_BOOST_I");
                player.setItemSlot(product.slot(), later);
                RefundControl.reset(later, player.getInventory());
                destroy(later, player);
                require(later.isEmpty() && delivered(player).equals(Map.of(augmentItem("HEALTH_BOOST_I"), 1))
                        && RefundControl.deliveries == 1, "Throwing callback leaked into a later genuine break");
            }
            REPORT.append("Adversarial ").append(fault).append(" passed\n");
            RefundControl.reset(null, null);
        }
        malformed(server);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void malformed(MinecraftServer server) throws Exception {
        DataComponentType component = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(net.minecraft.resources.Identifier.parse("mysticalagriculture:equipped_augments"));
        var entry = NativeAugments.type("api.components.AugmentComponent").getConstructor(net.minecraft.resources.Identifier.class, int.class);
        for (String fault : List.of("duplicate", "negative", "outside", "unknown", "tier", "rootCopy", "bare")) {
            var source = product.nativeItem("awakened_supremium_leggings", "HEALTH_BOOST_I");
            var id = net.minecraft.resources.Identifier.parse("mysticalagriculture:health_boost_i");
            switch (fault) {
                case "duplicate" -> source.set(component, List.of(entry.newInstance(id, 0), entry.newInstance(id, 0)));
                case "negative" -> source.set(component, List.of(entry.newInstance(id, -1)));
                case "outside" -> source.set(component, List.of(entry.newInstance(id, 2)));
                case "unknown" -> source.set(component, List.of(entry.newInstance(net.minecraft.resources.Identifier.parse("addon:unknown"), 0)));
                case "tier" -> { source = product.nativeItem("inferium_leggings"); source.set(component, List.of(entry.newInstance(net.minecraft.resources.Identifier.parse("mysticalagriculture:health_boost_v"), 0))); }
                case "rootCopy" -> source = product.nativeItem("inferium_leggings");
            }
            var root = product.pair(source, new ItemStack(product.iron())).getFirst();
            if (fault.equals("bare")) { root.remove(product.dataType()); }
            if (fault.equals("rootCopy")) { root.set(component, product.nativeItem("awakened_supremium_leggings", "HEALTH_BOOST_V").get(component)); }
            var player = player(server); player.setItemSlot(product.slot(), root); destroy(root, player);
            require(root.isEmpty() && delivered(player).isEmpty(), "Malformed/root copy authorized purchases: " + fault);
            REPORT.append("Malformed ").append(fault).append(" refused\n");
        }
    }
}
