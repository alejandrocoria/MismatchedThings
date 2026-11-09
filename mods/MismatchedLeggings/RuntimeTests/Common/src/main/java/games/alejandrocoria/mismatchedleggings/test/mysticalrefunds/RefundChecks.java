package games.alejandrocoria.mismatchedleggings.test.mysticalrefunds;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture.MysticalAugmentsCompatibility;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.data.LeggingHalf;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import games.alejandrocoria.mismatchedleggings.test.mysticaleffects.NativeAugments;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real damage, inventory insertion and accepted native overflow entities; no candidate refund code. */
public final class RefundChecks {
    private static boolean finished;
    private static int sequence;
    private static final StringBuilder REPORT = new StringBuilder();
    private RefundChecks() {}
    public static boolean enabled() { return Boolean.getBoolean("leggings.test.mysticalRefunds"); }
    public static void tick(MinecraftServer server) {
        if (finished || server.getTickCount() < 20) { return; }
        finished = true;
        try {
            if (Boolean.getBoolean("leggings.test.maAbsent")) {
                var player = player(server);
                var root = MixedLeggingsFactory.createPair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS)).primary();
                player.setItemSlot(EquipmentSlot.LEGS, root); destroy(root, player);
                require(root.isEmpty() && delivered(player).isEmpty(), "Absence broke ordinary wear");
                REPORT.append("Quiet optional absence and ordinary actual break passed.\n");
            } else { run(server); }
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "refunds.txt"), REPORT.toString());
            SkeletonChecks.passed("server");
        } catch (Throwable failure) { SkeletonChecks.failed("server", failure); }
        finally { RefundControl.reset(null, null); server.halt(false); }
    }

    private static void run(MinecraftServer server) throws Exception {
        boolean refused = System.getProperty("leggings.test.maRefundVariant", "normal").equals("flow");
        verifyTransform(refused);
        if (refused) {
            nativeControl(server, false);
            var player = player(server);
            var root = pair("HEALTH_BOOST_I"); player.setItemSlot(EquipmentSlot.LEGS, root);
            require(!MysticalAugmentsCompatibility.selectedIds(root).isEmpty(), "Refund refusal disabled independent selection");
            destroy(root, player);
            require(root.isEmpty() && delivered(player).isEmpty(), "Opaque final predicate authorized refund or suppressed ordinary break");
            REPORT.append("Changed final break predicate refused locally; effect reader retained.\n"); return;
        }
        nativeControl(server, false); nativeControl(server, true);
        checkPair(server, NativeAugments.item("inferium_leggings", "HEALTH_BOOST_I"), new ItemStack(Items.DIAMOND_LEGGINGS), false);
        checkPair(server, NativeAugments.item("inferium_leggings", "HEALTH_BOOST_I"), NativeAugments.item("prudentium_leggings", "LUCK_I"), false);
        checkPair(server, NativeAugments.item("awakened_supremium_leggings", "HEALTH_BOOST_I", "STEP_ASSIST"),
                NativeAugments.item("awakened_supremium_leggings", "HEALTH_BOOST_V", "SPEED_III"), false);
        checkPair(server, NativeAugments.item("awakened_supremium_leggings", "HEALTH_BOOST_I", "HEALTH_BOOST_I"), new ItemStack(Items.DIAMOND_LEGGINGS), false);
        checkPair(server, NativeAugments.item("inferium_leggings"), new ItemStack(Items.IRON_LEGGINGS), false);
        checkPair(server, new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS), false);
        checkPair(server, NativeAugments.item("inferium_leggings", "LUCK_I"), NativeAugments.item("inferium_leggings", "HEALTH_BOOST_I"), true);
        failures(server);
        if (!System.getProperty("leggings.test.maGraph", "alone").equals("alone")) { boots(server); }
    }

    private static void verifyTransform(boolean refused) throws Exception {
        var marker = ItemStack.class.getDeclaredMethod("mismatchedleggings$maRefundReady"); marker.setAccessible(true);
        require(marker.invoke(null).equals(!refused), "Final observer readiness mismatch");
        var node = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(Files.readAllBytes(Path.of(".mixin.out/class/net/minecraft/world/item/ItemStack.class"))).accept(node, 0);
        int priority = System.getProperty("leggings.test.maOrder", "before").equals("before") ? 1100 : 900;
        var observed = new HashMap<String, Integer>();
        for (var method : node.methods) {
            if (method.visibleAnnotations == null) { continue; }
            for (var annotation : method.visibleAnnotations) {
                if (!annotation.desc.endsWith("/MixinMerged;") || annotation.values == null) { continue; }
                int owner = annotation.values.indexOf("mixin") + 1; int rank = annotation.values.indexOf("priority") + 1;
                if (owner > 0 && rank > 0) { observed.put(annotation.values.get(owner).toString(), (Integer) annotation.values.get(rank)); }
            }
        }
        String prefix = "games.alejandrocoria.";
        require(Objects.equals(observed.get(prefix + "mismatchedleggings.mixin.compatibility.mysticalagriculture.MysticalRefundMixin"), priority), "Observer relative priority not exercised");
        if (!System.getProperty("leggings.test.maGraph", "alone").equals("alone")) {
            require(Objects.equals(observed.get(prefix + "mismatchedboots.mixin.compatibility.mysticalagriculture.MysticalRefundMixin"), 1000), "Boots router priority changed");
        }
        REPORT.append("Actual exported observer priority=").append(priority).append("; final readiness=").append(!refused).append('\n');
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

    private static ItemStack pair(String augment) {
        return MixedLeggingsFactory.createPair(NativeAugments.item("awakened_supremium_leggings", augment), new ItemStack(Items.DIAMOND_LEGGINGS)).primary();
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
    private static void nativeControl(MinecraftServer server, boolean overflow) throws Exception {
        var player = player(server); if (overflow) { full(player); }
        var stack = NativeAugments.item("inferium_leggings", "HEALTH_BOOST_I"); var expected = physical(stack);
        player.setItemSlot(EquipmentSlot.LEGS, stack); destroy(stack, player);
        require(stack.isEmpty() && delivered(player).equals(expected), "Native actual-break positive failed empty=" + stack.isEmpty() + " actual=" + delivered(player) + " expected=" + expected + " overflow=" + overflow);
        require(!overflow || !drops(player).isEmpty(), "Native overflow positive absent");
        drops(player).forEach(ItemEntity::discard);
        REPORT.append("Native actual break/inventory positive; overflow=").append(overflow).append('\n');
    }
    private static void checkPair(MinecraftServer server, ItemStack left, ItemStack right, boolean overflow) throws Exception {
        var originals = List.of(left.copy(), right.copy());
        var pair = MixedLeggingsFactory.createPair(left, right); var expected = List.of(physical(left), physical(right));
        for (int orientation = 0; orientation < 2; orientation++) {
            var player = player(server); if (overflow) { full(player); }
            var root = orientation == 0 ? pair.primary() : pair.inverse();
            player.setItemSlot(EquipmentSlot.LEGS, root); root.remove(DataComponents.UNBREAKABLE);
            RefundControl.reset(root, player.getInventory());
            root.hurtAndBreak(1, player, EquipmentSlot.LEGS);
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

    private static void failures(MinecraftServer server) throws Exception {
        for (String fault : List.of("cancel", "throwBefore", "throwAfter", "replaceBefore", "replaceAfter", "move", "sourceBefore", "sourceAfter", "damage", "creative", "spectator", "creativeBefore", "spectatorAfter", "delete", "drop", "carried", "count", "preReenter", "postReenter", "deliveryReenter", "otherRoot", "cancelDelivery", "throwDelivery", "lore")) {
            var player = player(server); var root = pair("HEALTH_BOOST_I");
            root.remove(DataComponents.UNBREAKABLE); root.setDamageValue(root.getMaxDamage() - 1);
            player.setItemSlot(EquipmentSlot.LEGS, root); RefundControl.reset(root, player.getInventory());
            boolean expect = Set.of("carried", "preReenter", "postReenter", "deliveryReenter", "otherRoot", "lore").contains(fault);
            var history = root.get(LeggingsContent.mixedLeggingsDataType());
            Runnable after = () -> {};
            switch (fault) {
                case "cancel" -> RefundControl.cancel = true;
                case "throwBefore" -> RefundControl.before = () -> { throw new IllegalStateException("controlled before"); };
                case "throwAfter" -> after = () -> { throw new IllegalStateException("controlled after"); };
                case "replaceBefore" -> RefundControl.before = () -> player.setItemSlot(EquipmentSlot.LEGS, root.copy());
                case "replaceAfter" -> after = () -> player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                case "move" -> after = () -> { player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY); player.getInventory().setItem(0, root); };
                case "sourceBefore" -> RefundControl.before = () -> mutate(root, history);
                case "sourceAfter" -> after = () -> mutate(root, history);
                case "damage" -> RefundControl.before = () -> root.setDamageValue(0);
                case "creative" -> player.setGameMode(GameType.CREATIVE);
                case "spectator" -> player.setGameMode(GameType.SPECTATOR);
                case "creativeBefore" -> RefundControl.before = () -> player.setGameMode(GameType.CREATIVE);
                case "spectatorAfter" -> after = () -> player.setGameMode(GameType.SPECTATOR);
                case "delete" -> root.setCount(0);
                case "drop" -> player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                case "carried" -> { player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY); player.getInventory().setItem(0, root); }
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
            REPORT.append("Adversarial ").append(fault).append(" passed\n");
            RefundControl.reset(null, null);
        }
        malformed(server);
    }

    private static void mutate(ItemStack root, MixedLeggingsData data) {
        root.set(LeggingsContent.mixedLeggingsDataType(), new MixedLeggingsData(new LeggingHalf(NativeAugments.item("awakened_supremium_leggings", "LUCK_I")), data.right()));
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void malformed(MinecraftServer server) throws Exception {
        DataComponentType component = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(net.minecraft.resources.Identifier.parse("mysticalagriculture:equipped_augments"));
        var entry = NativeAugments.type("api.components.AugmentComponent").getConstructor(net.minecraft.resources.Identifier.class, int.class);
        for (String fault : List.of("duplicate", "negative", "outside", "unknown", "tier", "rootCopy", "bare")) {
            var source = NativeAugments.item("awakened_supremium_leggings", "HEALTH_BOOST_I");
            var id = net.minecraft.resources.Identifier.parse("mysticalagriculture:health_boost_i");
            switch (fault) {
                case "duplicate" -> source.set(component, List.of(entry.newInstance(id, 0), entry.newInstance(id, 0)));
                case "negative" -> source.set(component, List.of(entry.newInstance(id, -1)));
                case "outside" -> source.set(component, List.of(entry.newInstance(id, 2)));
                case "unknown" -> source.set(component, List.of(entry.newInstance(net.minecraft.resources.Identifier.parse("addon:unknown"), 0)));
                case "tier" -> { source = NativeAugments.item("inferium_leggings"); source.set(component, List.of(entry.newInstance(net.minecraft.resources.Identifier.parse("mysticalagriculture:health_boost_v"), 0))); }
                case "rootCopy" -> source = NativeAugments.item("inferium_leggings");
            }
            var root = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS)).primary();
            if (fault.equals("bare")) { root.remove(LeggingsContent.mixedLeggingsDataType()); }
            if (fault.equals("rootCopy")) { root.set(component, NativeAugments.item("awakened_supremium_leggings", "HEALTH_BOOST_V").get(component)); }
            var player = player(server); player.setItemSlot(EquipmentSlot.LEGS, root); destroy(root, player);
            require(root.isEmpty() && delivered(player).isEmpty(), "Malformed/root copy authorized purchases: " + fault);
            REPORT.append("Malformed ").append(fault).append(" refused\n");
        }
    }
    private static void boots(MinecraftServer server) throws Exception {
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null,
                NativeAugments.item("awakened_supremium_boots", "HEALTH_BOOST_I", "HEALTH_BOOST_I"), new ItemStack(Items.DIAMOND_BOOTS));
        var player = player(server);
        for (String orientation : List.of("primary", "inverse")) {
            var root = (ItemStack) pair.getClass().getMethod(orientation).invoke(pair);
            player.getInventory().clearContent(); player.setItemSlot(EquipmentSlot.FEET, root);
            var leggings = pair("LUCK_I"); player.setItemSlot(EquipmentSlot.LEGS, leggings);
            RefundControl.reset(root, player.getInventory()); destroy(root, player);
            var expected = orientation.equals("primary") ? new HashMap<>(Map.of(augmentItem("HEALTH_BOOST_I"), 2)) : new HashMap<Item, Integer>();
            require(root.isEmpty() && !leggings.isEmpty() && delivered(player).equals(expected), "Boots actual break/refund failed " + orientation);
            RefundControl.reset(leggings, player.getInventory()); destroy(leggings, player);
            expected.put(augmentItem("LUCK_I"), 1);
            require(leggings.isEmpty() && delivered(player).equals(expected), "Joint actual breaks lost/duplicated purchases " + orientation);
        }
        REPORT.append("Current/unchanged published Boots: both actual breaks and duplicate physical purchases retained\n");
        RefundControl.reset(null, null);
    }
}
