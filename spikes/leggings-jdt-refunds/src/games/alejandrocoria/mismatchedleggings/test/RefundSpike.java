package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.test.refundspike.LeggingsWitness;
import games.alejandrocoria.mismatchedleggings.test.refundspike.SharedConsumption;
import games.alejandrocoria.mismatchedleggings.test.refundspike.WitnessSupport;
import games.alejandrocoria.mismatchedleggings.test.mysticaleffects.NativeAugments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Bounded server proof. Leggings credits are explicitly a witness, not shipped support. */
public final class RefundSpike {
    private static final List<String> DROPS = new ArrayList<>();
    private static final StringBuilder EVIDENCE = new StringBuilder();
    private static ItemStack observed;
    private static boolean cancel;
    private static boolean finished;
    private static int callbacks;
    private static Runnable onDrop;

    private RefundSpike() {}

    public static void register(IEventBus bus) {
        LeggingsWitness.initialize(bus);
        if (!fault().equals("absent")) { bus.addListener((RegisterCapabilitiesEvent event) -> WitnessSupport.bind()); }
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (EntityJoinLevelEvent event) -> {
            if (!(event.getEntity() instanceof ItemEntity entity)) { return; }
            var id = BuiltInRegistries.ITEM.getKey(entity.getItem().getItem());
            if (!id.getNamespace().equals("justdirethings") || !id.getPath().startsWith("upgrade_")) { return; }
            require(observed != null && observed.isEmpty(), "Refund preceded actual consumption");
            require(entity.getItem().getCount() == 1, "Fractional or stacked refund");
            DROPS.add(id.getPath().substring("upgrade_".length()));
            if (onDrop != null) {
                var action = onDrop;
                onDrop = null;
                action.run();
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ArmorHurtEvent event) -> {
            if (cancel) { event.setCanceled(true); }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static String fault() { return System.getProperty("refund.spike.fault", ""); }

    private static void tick(MinecraftServer server) {
        if (finished || server.getTickCount() < 20) { return; }
        finished = true;
        try {
            String graph = System.getProperty("refund.spike.graph");
            boolean legs = !graph.equals("boots");
            boolean boots = !graph.equals("legs");
            require(ModList.get().isLoaded("mismatchedleggings") == legs && ModList.get().isLoaded("mismatchedboots") == boots,
                    "Wrong product graph");
            require(ModList.get().isLoaded("justdirethings") != fault().equals("absent"), "Wrong optional graph");
            if (fault().equals("absent")) {
                var root = legs ? MixedLeggingsFactory.createPair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS)).primary()
                        : RefundBootsBridge.pair(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
                direct(server, root, legs, 0);
                expect();
                require(SharedConsumption.acceptedCalls == 0, "Absent JDT enabled hook");
                if (legs) { mysticalOnly(server, true); }
                if (boots) { mysticalOnly(server, false); }
                EVIDENCE.append("Quiet JDT absence; ordinary composition/break callback and both installed MA observers retained\n");
            } else {
                require(ModList.get().isLoaded("mysticalagriculture"), "Actual MA artifact missing");
                var item = BuiltInRegistries.ITEM;
                if (legs) {
                    SharedConsumption.register(item.getValue(Identifier.parse("mismatchedleggings:mixed_leggings")),
                            (root, count, owner, ordinary) -> LeggingsWitness.route(true, root, count, owner, ordinary));
                }
                if (boots) { RefundBootsBridge.register(item.getValue(Identifier.parse("mismatchedboots:mixed_boots"))); }
                boolean ready = readiness("refundspike$ready");
                require(ready != fault().equals("shape"), "Unexpected final shared readiness");
                require(Arrays.stream(ItemStack.class.getDeclaredMethods()).filter(method -> method.getName().contains("refundspike$consume")
                                && method.getReturnType() == void.class && method.getParameterCount() == 6).count() == 1,
                        "Multiple candidate consumption sites");
                require(Arrays.stream(ItemStack.class.getDeclaredMethods()).noneMatch(method -> method.getName().equals("mismatchedboots$justDireRefundReady")),
                        "Old Boots JDT observer remained active");
                for (boolean product : List.of(true, false)) {
                    if (product ? !legs : !boots) { continue; }
                    boolean enabled = ready && !(product && fault().equals("binding"));
                    exercise(server, product, enabled);
                }
                if (ready) { require(SharedConsumption.acceptedCalls > 0, "Candidate dispatcher was bypassed"); }
                EVIDENCE.append("Final shared readiness=").append(ready).append("; accepted product calls=")
                        .append(SharedConsumption.acceptedCalls).append("; MA present in actual transformed ItemStack\n");
                EVIDENCE.append("MA readiness: legs=").append(legs ? readiness("mismatchedleggings$maRefundReady") : "absent")
                        .append("; boots=").append(boots ? readiness("mismatchedboots$maRefundReady") : "absent").append('\n');
                if (ready) {
                    if (legs) {
                        require(readiness("mismatchedleggings$maRefundReady"), "Leggings MA observer regressed");
                        mystical(server, true, !fault().equals("binding"));
                    }
                    if (boots) {
                        require(readiness("mismatchedboots$maRefundReady"), "Boots MA observer regressed");
                        mystical(server, false, true);
                    }
                }
            }
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "spike.txt"), EVIDENCE);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    private static boolean readiness(String name) throws Exception {
        var getter = ItemStack.class.getDeclaredMethod(name);
        getter.setAccessible(true);
        return (boolean) getter.invoke(null);
    }

    private static void exercise(MinecraftServer server, boolean legs, boolean enabled) throws Exception {
        String first = legs ? "walkspeed" : "stepheight";
        String second = legs ? "runspeed" : "jumpboost";
        for (int variant = 0; variant < 3; variant++) {
            var a = nativeStack(legs, "ferricore", first);
            var b = variant == 0 ? new ItemStack(legs ? Items.DIAMOND_LEGGINGS : Items.DIAMOND_BOOTS)
                    : nativeStack(legs, "ferricore", variant == 1 ? first : second);
            var pair = pair(legs, a, b);
            require(credits(legs, pair.get(0)).equals(List.of(first)), "Owner credit missing");
            require(credits(legs, pair.get(1)).equals(variant == 0 ? List.of() : List.of(variant == 1 ? first : second)), "Inverse credit duplicated");
            direct(server, pair.get(0), legs, -1);
            expect(enabled ? new String[]{first} : new String[0]);
            direct(server, pair.get(1), legs, Inventory.SLOT_OFFHAND);
            expect(enabled && variant != 0 ? new String[]{variant == 1 ? first : second} : new String[0]);
        }
        // Actual menu payment is retained; only the Leggings credit boundary is a test witness.
        var root = mixed(legs, "ferricore", first);
        var player = RepairFixtures.player(server);
        var menu = SmithingChecks.ready(player, ItemStack.EMPTY, root, nativeItem("upgrade_" + second));
        require(menu.getSlot(3).hasItem(), "Real root smithing recipe missing");
        var paid = menu.getSlot(3).safeTake(1, 1, player);
        require(!paid.isEmpty() && !menu.getSlot(1).hasItem() && !menu.getSlot(2).hasItem(), "Upgrade not paid");
        if (legs) { WitnessSupport.installed(paid, second); }
        require(credits(legs, paid).containsAll(List.of(first, second)) && credits(legs, paid).size() == 2, "Paid credit not conserved");
        direct(server, paid, legs, 0);
        expect(enabled ? new String[]{first, second} : new String[0]);

        root = mixed(legs, "ferricore", first);
        player = equip(server, root, legs, -1);
        cancel = true;
        try { hit(player, legs); } finally { cancel = false; }
        require(!root.isEmpty(), "Late cancelled wear consumed root");
        expect();
        hit(player, legs);
        require(root.isEmpty(), "Actual equipped wear did not consume");
        expect(enabled ? new String[]{first} : new String[0]);

        root = mixed(legs, "celestigem", first);
        root.set(JustDireDataComponents.FORGE_ENERGY, 1000);
        player = equip(server, root, legs, -1);
        int damage = root.getDamageValue();
        hit(player, legs);
        require(!root.isEmpty() && root.getDamageValue() == damage && root.getOrDefault(JustDireDataComponents.FORGE_ENERGY, 1000) < 1000,
                "Funded wear did not use FE exclusively");
        expect();

        boolean survived = false;
        for (int attempt = 0; attempt < 30 && !survived; attempt++) {
            root = mixed(legs, "ferricore", first);
            root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 255);
            player = equip(server, root, legs, -1);
            root.hurtAndBreak(1, player.level(), player, item -> callbacks++);
            survived = !root.isEmpty();
            expect(!survived && enabled ? new String[]{first} : new String[0]);
        }
        require(survived, "No Unbreaking survival control reached");

        if (enabled) {
            root = mixed(legs, "ferricore", first);
            root.set(JustDireDataComponents.TOOL_ENABLED, false);
            root.set(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.byName(first)), false);
            var configured = com.direwolf20.justdirethings.setup.Config.AVAILABLE_ABILITY_MAP.get(Ability.byName(first));
            boolean previous = configured.get();
            try {
                configured.set(false);
                direct(server, root, legs, 0);
                expect(first);
            } finally {
                configured.set(previous);
            }
            root = mixed(legs, "ferricore", first);
            var reentrant = root;
            player = equip(server, root, legs, -1);
            var owner = player;
            onDrop = () -> reentrant.hurtAndBreak(1, owner.level(), owner, item -> {});
            root.hurtAndBreak(1, player.level(), player, item -> callbacks++);
            expect(first);
            require(callbacks == 1 && onDrop == null, "Reentry duplicated callback or failed to reach drop");
            if (legs) { adversarialReceipts(server, first); }
        }
        EVIDENCE.append(legs ? "Leggings witness" : "Real Boots credits/refund").append(": owner conservation, same/disjoint upgrades, paid smithing, equipped/direct inventory/offhand destruction, late cancellation, Unbreaking, funded FE and reentry passed; enabled=")
                .append(enabled).append('\n');
    }

    private static void adversarialReceipts(MinecraftServer server, String name) {
        for (String mode : List.of("skip", "partial", "copy", "move", "replace", "edit", "restore")) {
            var root = mixed(true, "ferricore", name);
            var player = equip(server, root, true, 0);
            root.setDamageValue(root.getMaxDamage());
            if (mode.equals("partial")) { root.setCount(2); }
            LeggingsWitness.route(true, root, 1, player, arguments -> {
                if (mode.equals("skip")) { return null; }
                if (mode.equals("copy")) { root.copy().shrink(1); return null; }
                if (mode.equals("move")) { player.getInventory().setItem(0, ItemStack.EMPTY); player.getInventory().setItem(1, root); }
                if (mode.equals("replace")) { player.getInventory().setItem(0, root.copy()); }
                if (mode.equals("edit")) { root.set(JustDireDataComponents.FORGE_ENERGY, 99); }
                root.shrink(1);
                if (mode.equals("restore")) { root.setCount(1); }
                return null;
            });
            expect();
        }
        var root = mixed(true, "ferricore", name);
        var player = equip(server, root, true, 0);
        root.setDamageValue(root.getMaxDamage());
        LeggingsWitness.route(true, root, 1, player, arguments -> {
            LeggingsWitness.route(true, root, 1, player, nested -> { root.shrink(1); return null; });
            return null;
        });
        expect(name);
        EVIDENCE.append("Direct witness receipt negatives: skip/partial/copy/move/replacement/state edit/count restoration; pre-consumption reentry refunds once\n");
    }

    private static void mysticalOnly(MinecraftServer server, boolean legs) throws Exception {
        require(readiness(legs ? "mismatchedleggings$maRefundReady" : "mismatchedboots$maRefundReady"), "JDT absence disabled MA");
        var source = NativeAugments.item(legs ? "awakened_supremium_leggings" : "awakened_supremium_boots", "LUCK_I");
        var other = new ItemStack(legs ? Items.DIAMOND_LEGGINGS : Items.DIAMOND_BOOTS);
        var root = legs ? MixedLeggingsFactory.createPair(source, other).primary() : RefundBootsBridge.pair(source, other).getFirst();
        var player = equip(server, root, legs, -1);
        root.hurtAndBreak(1, player.level(), player, item -> callbacks++);
        var refundItem = (net.minecraft.world.item.Item) NativeAugments.invoke(NativeAugments.augment("LUCK_I"), "getItem", new Class<?>[0]);
        int delivered = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.is(refundItem)) { delivered += stack.getCount(); }
        }
        require(root.isEmpty() && callbacks == 1 && delivered == 1, "Real MA refund changed without JDT");
        expect();
    }

    private static void mystical(MinecraftServer server, boolean legs, boolean enabled) {
        String upgrade = legs ? "walkspeed" : "stepheight";
        var source = NativeAugments.item(legs ? "awakened_supremium_leggings" : "awakened_supremium_boots", "LUCK_I");
        var roots = pair(legs, source, nativeStack(legs, "ferricore", upgrade));
        var refundItem = (net.minecraft.world.item.Item) NativeAugments.invoke(NativeAugments.augment("LUCK_I"), "getItem", new Class<?>[0]);
        for (int orientation = 0; orientation < 2; orientation++) {
            var root = roots.get(orientation);
            var player = equip(server, root, legs, -1);
            root.hurtAndBreak(1, player.level(), player, item -> callbacks++);
            require(root.isEmpty() && callbacks == 1, "Joint MA/JDT ordinary break changed");
            int delivered = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                var stack = player.getInventory().getItem(slot);
                if (stack.is(refundItem)) { delivered += stack.getCount(); }
            }
            require(delivered == (orientation == 0 ? 1 : 0), "MA owner refund lost/duplicated across shared hook");
            expect(enabled && orientation == 1 ? new String[]{upgrade} : new String[0]);
        }
        EVIDENCE.append(legs ? "Leggings" : "Boots").append(" MA+JDT sources: real augment inventory delivery and JDT owner drop both conserved\n");
    }

    private static List<ItemStack> pair(boolean legs, ItemStack first, ItemStack second) {
        if (!legs) { return RefundBootsBridge.pair(first, second); }
        var pair = MixedLeggingsFactory.createPair(first, second);
        WitnessSupport.creation(pair.primary(), first);
        WitnessSupport.creation(pair.inverse(), second);
        return List.of(pair.primary(), pair.inverse());
    }

    private static List<String> credits(boolean legs, ItemStack root) {
        return legs ? WitnessSupport.credits(root) : RefundBootsBridge.credits(root);
    }

    private static ItemStack mixed(boolean legs, String tier, String... names) {
        return pair(legs, nativeStack(legs, tier, names), new ItemStack(legs ? Items.DIAMOND_LEGGINGS : Items.DIAMOND_BOOTS)).getFirst();
    }

    private static ItemStack nativeStack(boolean legs, String tier, String... names) {
        var source = nativeItem(tier + (legs ? "_leggings" : "_boots"));
        for (String name : names) { source.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.byName(name)), true); }
        return source;
    }

    private static ItemStack nativeItem(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", name)).orElseThrow().value());
    }

    private static ServerPlayer equip(MinecraftServer server, ItemStack root, boolean legs, int slot) {
        var player = RepairFixtures.player(server);
        observed = root;
        callbacks = 0;
        DROPS.clear();
        root.setDamageValue(root.getMaxDamage() - 1);
        if (slot < 0) { player.setItemSlot(legs ? EquipmentSlot.LEGS : EquipmentSlot.FEET, root); }
        else { player.getInventory().setItem(slot, root); }
        return player;
    }

    private static void direct(MinecraftServer server, ItemStack root, boolean legs, int slot) {
        var player = equip(server, root, legs, slot);
        root.hurtAndBreak(1, player.level(), player, item -> callbacks++);
        require(root.isEmpty() && callbacks == 1, "Ordinary consumption/callback changed");
    }

    private static void hit(ServerPlayer player, boolean legs) {
        CommonHooks.onArmorHurt(player.damageSources().generic(), new EquipmentSlot[]{legs ? EquipmentSlot.LEGS : EquipmentSlot.FEET}, 2, player);
    }

    private static void expect(String... names) {
        require(DROPS.stream().sorted().toList().equals(List.of(names).stream().sorted().toList()), "Refunds=" + DROPS + "; expected=" + List.of(names));
        DROPS.clear();
    }
}
