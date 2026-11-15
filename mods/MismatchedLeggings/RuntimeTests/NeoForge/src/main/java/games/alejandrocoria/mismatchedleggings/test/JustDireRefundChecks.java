package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.test.mysticaleffects.NativeAugments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Actual packaged initialization/refunds; no candidate registration, local credits or patched JARs. */
final class JustDireRefundChecks {
    private static final StringBuilder REPORT = new StringBuilder();
    private static ItemStack observed;
    private static int jdtDrops;
    private static int augmentDrops;
    private static int callbacks;
    private static boolean cancel;
    private static boolean finished;

    private JustDireRefundChecks() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (EntityJoinLevelEvent event) -> {
            if (!(event.getEntity() instanceof ItemEntity entity)) {
                return;
            }
            var id = BuiltInRegistries.ITEM.getKey(entity.getItem().getItem());
            boolean jdt = id.getNamespace().equals("justdirethings") && id.getPath().startsWith("upgrade_");
            boolean augment = id.getNamespace().equals("mysticalagriculture") && id.getPath().endsWith("_augment");
            if (!jdt && !augment) {
                return;
            }
            require(observed != null && observed.isEmpty(), "Refund preceded actual consumption");
            require(entity.getItem().getCount() == 1, "Unexpected refund count");
            if (jdt) {
                jdtDrops++;
            } else {
                augmentDrops++;
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ArmorHurtEvent event) -> {
            if (cancel) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static String fault() {
        return System.getProperty("leggings.test.refundFault", "");
    }

    private static void tick(MinecraftServer server) {
        if (finished || server.getTickCount() < 20) {
            return;
        }
        finished = true;
        try {
            String graph = System.getProperty("leggings.test.refundGraph");
            boolean legs = graph.equals("legs") || graph.equals("both");
            boolean boots = graph.equals("boots") || graph.equals("both");
            boolean jdt = !fault().equals("jdtAbsent");
            boolean ma = !fault().equals("maAbsent");
            require(ModList.get().isLoaded("mismatchedleggings") == legs && ModList.get().isLoaded("mismatchedboots") == boots
                            && ModList.get().isLoaded("justdirethings") == jdt && ModList.get().isLoaded("mysticalagriculture") == ma,
                    "Wrong packaged product/optional graph");
            require(ModList.get().getMods().stream().filter(mod -> mod.getModId().equals("mismatchedthings")).count() == 1, "Multiple selected cores");
            require(Arrays.stream(ItemStack.class.getDeclaredMethods()).noneMatch(method -> method.getName().equals("mismatchedboots$justDireRefundReady")),
                    "Old Boots JDT hook still present");
            if (jdt) {
                require(ready("mismatchedthings$justDireRefundReady") != fault().equals("shape"), "Unexpected shared final permission");
                require(Arrays.stream(ItemStack.class.getDeclaredMethods()).filter(method -> method.getName().contains("mismatchedthings$justDireRefund")
                                && method.getReturnType() == void.class && method.getParameterCount() == 6).count() == 1,
                        "Expected one core-owned JDT consumption observer");
            } else {
                require(Arrays.stream(ItemStack.class.getDeclaredMethods()).noneMatch(method -> method.getName().equals("mismatchedthings$justDireRefundReady")),
                        "Absent JDT loaded the optional observer");
            }
            if (legs) {
                require(BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.parse("mismatchedleggings:jdt_refundable_upgrades")).isEmpty(),
                        "Block A unexpectedly introduced Leggings JDT credits");
            }
            if (graph.equals("core")) {
                var root = new ItemStack(Items.DIAMOND_BOOTS);
                destroy(server, root, false, false);
                require(jdtDrops == 0 && augmentDrops == 0, "Unclaimed core-only item refunded");
                REPORT.append("Independent core; one verified observer and ordinary unclaimed consumption/callback\n");
            }
            for (boolean leggings : List.of(true, false)) {
                if (leggings ? !legs : !boots) {
                    continue;
                }
                if (ma && !fault().equals("shape")) {
                    require(ready(leggings ? "mismatchedleggings$maRefundReady" : "mismatchedboots$maRefundReady"), "MA final observer regressed");
                }
                var vanilla = new ItemStack(leggings ? Items.DIAMOND_LEGGINGS : Items.DIAMOND_BOOTS);
                if (jdt) {
                    var source = nativeJdt(leggings);
                    var roots = pair(leggings, source, vanilla);
                    for (var root : roots) {
                        require(root.getOrDefault(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.byName(leggings ? "walkspeed" : "stepheight")), false),
                                "Hook migration changed installed capability");
                    }
                    for (int orientation = 0; orientation < 2; orientation++) {
                        destroy(server, roots.get(orientation), leggings, false);
                        require(jdtDrops == (!leggings && orientation == 0 && !fault().equals("shape") ? 1 : 0), "Wrong owner JDT refund");
                    }
                    cancelledAndFunded(server, leggings);
                }
                if (ma && !fault().equals("shape")) {
                    for (boolean overflow : List.of(false, true)) {
                        var source = NativeAugments.item(leggings ? "awakened_supremium_leggings" : "awakened_supremium_boots", "LUCK_I");
                        var roots = pair(leggings, source, jdt ? nativeJdt(leggings) : vanilla);
                        var refund = (Item) NativeAugments.invoke(NativeAugments.augment("LUCK_I"), "getItem", new Class<?>[0]);
                        for (int orientation = 0; orientation < 2; orientation++) {
                            var player = destroy(server, roots.get(orientation), leggings, overflow);
                            int inventory = 0;
                            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                                var stack = player.getInventory().getItem(slot);
                                if (stack.is(refund)) {
                                    inventory += stack.getCount();
                                }
                            }
                            require(inventory + augmentDrops == (orientation == 0 ? 1 : 0), "MA purchase lost/duplicated");
                            require(augmentDrops == (overflow && orientation == 0 ? 1 : 0), "MA overflow route not exercised");
                            require(jdtDrops == (jdt && !leggings && orientation == 1 ? 1 : 0), "MA/JDT owner credits interfered");
                        }
                    }
                    if (jdt && !leggings) {
                        paidBootsWithBothRefunds(server);
                    }
                }
                REPORT.append(leggings ? "Leggings" : "Boots").append(": real initialized product; owner JDT drops, cancellation/funded FE and MA inventory/overflow checked where present\n");
            }
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "refunds.txt"), REPORT);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    private static boolean ready(String name) throws Exception {
        var getter = ItemStack.class.getDeclaredMethod(name);
        getter.setAccessible(true);
        return (boolean) getter.invoke(null);
    }

    private static void paidBootsWithBothRefunds(MinecraftServer server) throws Exception {
        var source = NativeAugments.item("awakened_supremium_boots", "LUCK_I");
        var root = pair(false, source, nativeJdt(false)).getFirst();
        var player = RepairFixtures.player(server);
        var upgrade = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:upgrade_jumpboost")));
        var menu = SmithingChecks.ready(player, ItemStack.EMPTY, root, upgrade);
        var paid = menu.getSlot(3).safeTake(1, 1, player);
        require(!paid.isEmpty() && !menu.getSlot(1).hasItem() && !menu.getSlot(2).hasItem(), "Joint-credit upgrade not actually paid");
        observed = paid;
        callbacks = 0;
        jdtDrops = 0;
        augmentDrops = 0;
        paid.setDamageValue(paid.getMaxDamage() - 1);
        player.getInventory().setItem(0, paid);
        paid.hurtAndBreak(1, player.level(), player, item -> callbacks++);
        var refund = (Item) NativeAugments.invoke(NativeAugments.augment("LUCK_I"), "getItem", new Class<?>[0]);
        int returned = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.is(refund)) {
                returned += stack.getCount();
            }
        }
        require(paid.isEmpty() && callbacks == 1 && jdtDrops == 1 && returned + augmentDrops == 1,
                "Paid Boots inventory destruction lost one of its simultaneous JDT/MA refunds");
        REPORT.append("Boots: a paid root owning both credit kinds refunds both after actual inventory destruction\n");
    }

    private static List<ItemStack> pair(boolean leggings, ItemStack first, ItemStack second) throws Exception {
        if (leggings) {
            var pair = MixedLeggingsFactory.createPair(first, second);
            return List.of(pair.primary(), pair.inverse());
        }
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, first, second);
        return List.of((ItemStack) pair.getClass().getMethod("primary").invoke(pair), (ItemStack) pair.getClass().getMethod("inverse").invoke(pair));
    }

    private static ItemStack nativeJdt(boolean leggings) {
        var root = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:ferricore_" + (leggings ? "leggings" : "boots"))));
        root.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.byName(leggings ? "walkspeed" : "stepheight")), true);
        return root;
    }

    private static ServerPlayer equip(MinecraftServer server, ItemStack root, boolean leggings) {
        var player = RepairFixtures.player(server);
        observed = root;
        callbacks = 0;
        jdtDrops = 0;
        augmentDrops = 0;
        root.setDamageValue(root.getMaxDamage() - 1);
        player.setItemSlot(leggings ? EquipmentSlot.LEGS : EquipmentSlot.FEET, root);
        return player;
    }

    private static ServerPlayer destroy(MinecraftServer server, ItemStack root, boolean leggings, boolean overflow) {
        var player = equip(server, root, leggings);
        if (overflow) {
            for (int slot = 0; slot < 36; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            }
        }
        root.hurtAndBreak(1, player.level(), player, item -> callbacks++);
        require(root.isEmpty() && callbacks == 1, "Ordinary break/callback changed");
        return player;
    }

    private static void cancelledAndFunded(MinecraftServer server, boolean leggings) throws Exception {
        var other = new ItemStack(leggings ? Items.DIAMOND_LEGGINGS : Items.DIAMOND_BOOTS);
        var root = pair(leggings, nativeJdt(leggings), other).getFirst();
        var player = equip(server, root, leggings);
        cancel = true;
        try {
            hit(player, leggings);
        } finally {
            cancel = false;
        }
        require(!root.isEmpty() && jdtDrops == 0, "Late cancelled wear consumed/refunded root");
        hit(player, leggings);
        require(root.isEmpty() && jdtDrops == (!leggings && !fault().equals("shape") ? 1 : 0), "Actual equipped wear/refund changed");
        var powered = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:celestigem_" + (leggings ? "leggings" : "boots"))));
        powered.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.byName(leggings ? "walkspeed" : "stepheight")), true);
        root = pair(leggings, powered, other).getFirst();
        root.set(JustDireDataComponents.FORGE_ENERGY, 1000);
        player = equip(server, root, leggings);
        int damage = root.getDamageValue();
        hit(player, leggings);
        require(!root.isEmpty() && root.getDamageValue() == damage && root.getOrDefault(JustDireDataComponents.FORGE_ENERGY, 1000) < 1000
                && jdtDrops == 0, "Shared refund refusal changed funded FE wear");
    }

    private static void hit(ServerPlayer player, boolean leggings) {
        CommonHooks.onArmorHurt(player.damageSources().generic(), new EquipmentSlot[]{leggings ? EquipmentSlot.LEGS : EquipmentSlot.FEET}, 2, player);
    }
}
