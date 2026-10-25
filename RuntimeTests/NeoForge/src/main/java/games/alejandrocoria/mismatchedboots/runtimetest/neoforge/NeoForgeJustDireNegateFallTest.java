package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Native payment controls and real production mixed falls; no alternate ability or wallet. */
public final class NeoForgeJustDireNegateFallTest {
    private static boolean finished;
    private static int cases;
    private static final StringBuilder report = new StringBuilder();
    private static ServerPlayer observed;
    private static int fallEvents;
    private static double finalDistance;

    private NeoForgeJustDireNegateFallTest() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NeoForgeJustDireNegateFallTest::observeFall);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void observeFall(LivingFallEvent event) {
        if (event.getEntity() == observed) {
            fallEvents++;
            finalDistance = event.getDistance();
        }
    }

    private static void tick(MinecraftServer server) {
        if (finished) {
            return;
        }
        finished = true;
        try {
            Class<?> ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
            Object negate = ability.getField("NEGATEFALLDAMAGE").get(null);
            require((int) ability.getMethod("getFeCost").invoke(negate) == 50, "Native base cost changed; revise characterization");
            String guard = System.getProperty("mismatchedboots.runtimetest.justDireNegateGuard", "");
            if (guard.isEmpty()) {
                nativeMatrix(server);
                mixedMatrix(server);
                rejectedDebit(server);
            } else {
                guarded(server, guard);
            }
            NetworkAcceptanceFiles.write("just-dire-negate-fall-report.txt", cases
                    + (guard.isEmpty() ? " native/mixed cases; event bus; survival actual health; partial payment; "
                                         + "authoritative game modes; Unbreaking; equipped production FE; actual debit rollback; sources/wear unchanged\n"
                                       : " guard cases; variant=" + guard + "; real final permissions, native original, actual health, warning counts and isolation\n") + report);
            NetworkAcceptanceFiles.write("just-dire-negate-fall-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("just-dire-negate-fall", failure);
        } finally {
            observed = null;
            server.halt(false);
        }
    }

    private static void nativeMatrix(MinecraftServer server) throws ReflectiveOperationException {
        Fall unprotected = fall(player(server, GameType.SURVIVAL), 9);
        require(unprotected.distance == 9 && unprotected.loss == 6, "Survival control is immune or does not take expected real fall damage");
        record("unprotected survival control", "distance=9 healthLoss=6; connected fixture marked client-loaded");
        for (String tier : List.of("celestigem", "eclipsealloy")) {
            for (int charge : new int[]{0, 49, 50, 51}) {
                nativeFall(server, tier + " threshold " + charge, boots(tier, charge), 9,
                           charge >= 50 ? 0 : 9, charge >= 50 ? 0 : charge, GameType.SURVIVAL);
            }
            nativeFall(server, tier + " full bill", boots(tier, 1_000), 9, 0, 550, GameType.SURVIVAL);
        }
        for (double distance : new double[]{0, .9, 2.5, 4.5}) {
            int remaining = switch ((int) distance) {
                case 2 -> 900;
                case 4 -> 800;
                default -> 1_000;
            };
            nativeFall(server, "fractional distance " + distance, boots("celestigem", 1_000), distance,
                       0, remaining, GameType.SURVIVAL);
        }
        nativeFall(server, "zero bill still needs base FE", boots("celestigem", 49), .9, .9, 49, GameType.SURVIVAL);
        for (int level : new int[]{1, 3, 10, 12}) {
            ItemStack boots = boots("celestigem", 1_000);
            boots.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), level);
            int remaining = switch (level) {
                case 1 -> 595;
                case 3 -> 685;
                default -> 1_000;
            };
            nativeFall(server, "Unbreaking " + level, boots, 9, 0, remaining, GameType.SURVIVAL);
        }
        ItemStack discounted = boots("celestigem", 49);
        discounted.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 10);
        nativeFall(server, "discount does not lower eligibility", discounted, 9, 9, 49, GameType.SURVIVAL);
        ItemStack rounded = boots("celestigem", 1_000);
        rounded.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 1);
        nativeFall(server, "discounted fractional bill", rounded, 4.5, 0, 820, GameType.SURVIVAL);
        ItemStack unbreakable = boots("celestigem", 1_000);
        unbreakable.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        nativeFall(server, "native Unbreakable still pays FE", unbreakable, 9, 0, 550, GameType.SURVIVAL);
        ItemStack feather = boots("celestigem", 1_000);
        feather.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FEATHER_FALLING), 4);
        nativeFall(server, "Feather Falling does not discount FE", feather, 9, 0, 550, GameType.SURVIVAL);

        for (GameType mode : List.of(GameType.CREATIVE, GameType.SPECTATOR)) {
            for (int charge : new int[]{0, 49, 50}) {
                nativeFall(server, mode + " threshold " + charge, boots("celestigem", charge), 9,
                           charge >= 50 ? 0 : 9, charge, mode);
            }
        }
        for (String disabled : List.of("tool_enabled", "negatefalldamage_toggle", "negatefalldamage_upgrade_installed")) {
            ItemStack boots = boots("celestigem", 1_000);
            JustDireAttributeTestSupport.set(boots, disabled, false);
            nativeFall(server, disabled + " false", boots, 9, 9, 1_000, GameType.SURVIVAL);
        }
        Map<?, ?> flags = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config")
                                           .getField("AVAILABLE_ABILITY_MAP").get(null);
        ModConfigSpec.BooleanValue enabled = flags.entrySet().stream()
                                                  .filter(entry -> ((Enum<?>) entry.getKey()).name().equals("NEGATEFALLDAMAGE"))
                                                  .map(entry -> (ModConfigSpec.BooleanValue) entry.getValue()).findFirst().orElseThrow();
        boolean previous = enabled.get();
        try {
            enabled.set(false);
            nativeFall(server, "native config disabled", boots("celestigem", 1_000), 9, 9, 1_000, GameType.SURVIVAL);
        } finally {
            enabled.set(previous);
        }

        ItemStack jump = boots("celestigem", 50);
        JustDireAttributeTestSupport.set(jump, "jumpboost_upgrade_installed", true);
        JustDireAttributeTestSupport.set(jump, "jumpboost_value", 2);
        nativeFall(server, "Negate before Jump", jump, 9, 0, 0, GameType.SURVIVAL);
        jump = jump.copy();
        jump.set(JustDireResourceTestSupport.energyComponent(), 49);
        nativeFall(server, "rejected Negate permits native Jump", jump, 9, 7, 49, GameType.SURVIVAL);
        for (EquipmentSlot hand : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
            ServerPlayer player = player(server, GameType.SURVIVAL);
            ItemStack boots = boots("celestigem", 50);
            player.setItemSlot(EquipmentSlot.FEET, boots);
            player.setItemSlot(hand, new ItemStack(BuiltInRegistries.ITEM.getValue(
                    Identifier.fromNamespaceAndPath("justdirethings", "blazejet_wand"))));
            Fall result = fall(player, 9);
            require(result.distance == 0 && result.loss == 0 && energy(boots).getAmountAsInt() == 50,
                    "Native hand protection did not precede Negate debit: " + hand + " distance=" + result.distance
                            + " loss=" + result.loss + " FE=" + energy(boots).getAmountAsInt()
                            + " held=" + player.getItemBySlot(hand));
            record("hand precedence " + hand, "distance=0 FE=50 healthLoss=0");
        }
    }

    private static void nativeFall(MinecraftServer server, String label, ItemStack boots, double distance,
                                   double expectedDistance, int expectedEnergy, GameType mode) {
        ServerPlayer player = player(server, mode);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        ItemStack before = boots.copy();
        Fall actual = fall(player, distance);
        require(actual.distance == expectedDistance && energy(boots).getAmountAsInt() == expectedEnergy,
                label + ": distance=" + actual.distance + " FE=" + energy(boots).getAmountAsInt());
        if (mode == GameType.SURVIVAL) {
            ServerPlayer control = player(server, GameType.SURVIVAL);
            ItemStack iron = new ItemStack(Items.IRON_BOOTS);
            if (boots.has(DataComponents.ENCHANTMENTS)) {
                iron.set(DataComponents.ENCHANTMENTS, boots.get(DataComponents.ENCHANTMENTS));
            }
            control.setItemSlot(EquipmentSlot.FEET, iron);
            float expectedLoss = fall(control, expectedDistance).loss;
            require(expectedDistance <= 3 || expectedLoss > 0, label + ": ordinary long-fall control took no damage");
            require(actual.loss == expectedLoss, label + ": wrong actual survival health loss " + actual.loss + " expected=" + expectedLoss);
        }
        unchangedExceptEnergy(before, boots, label);
        record(label, "distance=" + actual.distance + " FE=" + expectedEnergy + " healthLoss=" + actual.loss
                + (mode == GameType.SURVIVAL ? " physical fall" : " event-bus mode control, not physical fall"));
    }

    private static void rejectedDebit(MinecraftServer server) {
        ItemStack original = boots("celestigem", 100);
        original.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE), 1);
        nativeFall(server, "native cursed boots still pay", original, 9, 0, 0, GameType.SURVIVAL);
        try {
            JustDireNegateGuardPlugin.rejectDebit = true;
            for (int charge : new int[]{50, 1_000}) {
                ItemStack jump = NeoForgeJustDireJumpTest.boots("blazegold", true);
                ItemStack root = MixedBootsFactory.createPair(boots("celestigem", charge * 2), jump).primary();
                JustDireAttributeTestSupport.set(root, "jumpboost_value", 2);
                JustDireNegateGuardPlugin.rejectedAmount = 0;
                nativeFall(server, "unexpected extraction rolls back FE=" + charge, root, 9, 7, charge, GameType.SURVIVAL);
                require(JustDireNegateGuardPlugin.rejectedAmount == Math.min(450, charge), "Failure control did not execute a real FE extraction");
            }
        } finally {
            JustDireNegateGuardPlugin.rejectDebit = false;
        }
    }

    private static void mixedMatrix(MinecraftServer server) throws ReflectiveOperationException {
        for (String tier : List.of("celestigem", "eclipsealloy")) {
            for (boolean inverse : new boolean[]{false, true}) {
                for (int charge : new int[]{0, 49, 50, 51, 1_000}) {
                    ItemStack root = mixed(tier, charge, inverse);
                    nativeFall(server, "mixed " + tier + " inverse=" + inverse + " FE=" + charge, root, 9,
                               charge >= 50 ? 0 : 9, charge >= 50 ? Math.max(0, charge - 450) : charge, GameType.SURVIVAL);
                }
            }
        }
        ItemStack two = MixedBootsFactory.createPair(boots("celestigem", 1_000), boots("eclipsealloy", 1_000)).primary();
        nativeFall(server, "two authorizing sources pay once", two, 9, 0, 550, GameType.SURVIVAL);
        for (double distance : new double[]{0, .9, 2.5, 4.5}) {
            nativeFall(server, "mixed fractional " + distance, mixed("celestigem", 1_000, false), distance,
                       0, 1_000 - 50 * (int) distance, GameType.SURVIVAL);
        }
        for (int level : new int[]{1, 3, 10, 12}) {
            ItemStack root = mixed("celestigem", 1_000, false);
            root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), level);
            nativeFall(server, "mixed root Unbreaking " + level, root, 9, 0,
                       level == 1 ? 595 : level == 3 ? 685 : 1_000, GameType.SURVIVAL);
        }
        ItemStack root = mixed("eclipsealloy", 50, true);
        root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE), 1);
        root.setDamageValue(23);
        nativeFall(server, "mixed cursed root pays without armor removal", root, 9, 0, 0, GameType.SURVIVAL);
        nativeFall(server, "same depleted root no longer protects", root, 9, 9, 0, GameType.SURVIVAL);
        for (GameType mode : List.of(GameType.CREATIVE, GameType.SPECTATOR)) {
            for (int charge : new int[]{49, 50}) {
                nativeFall(server, "mixed " + mode + " FE=" + charge, mixed("celestigem", charge, false), 9,
                           charge >= 50 ? 0 : 9, charge, mode);
            }
        }
        for (String control : List.of("tool_enabled", "negatefalldamage_toggle")) {
            root = mixed("celestigem", 1_000, false);
            JustDireAttributeTestSupport.set(root, control, false);
            nativeFall(server, "mixed root " + control + " disabled", root, 9, 9, 1_000, GameType.SURVIVAL);
        }
        ItemStack source = boots("celestigem", 2_000);
        JustDireAttributeTestSupport.set(source, "negatefalldamage_toggle", false);
        root = MixedBootsFactory.createPair(source, new ItemStack(Items.IRON_BOOTS)).primary();
        JustDireAttributeTestSupport.set(root, "negatefalldamage_toggle", true);
        nativeFall(server, "live root toggle overrides frozen source control", root, 9, 0, 550, GameType.SURVIVAL);
        root = mixed("celestigem", 1_000, false);
        root.remove(BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.fromNamespaceAndPath("justdirethings", "tool_enabled"))
                                                         .orElseThrow()
                                                         .value());
        root.remove(BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.fromNamespaceAndPath("justdirethings", "negatefalldamage_toggle"))
                                                         .orElseThrow()
                                                         .value());
        nativeFall(server, "absent root controls use native enabled defaults", root, 9, 0, 550, GameType.SURVIVAL);

        source = boots("celestigem", 2_000);
        JustDireAttributeTestSupport.set(source, "negatefalldamage_upgrade_installed", false);
        root = MixedBootsFactory.createPair(source, new ItemStack(Items.IRON_BOOTS)).primary();
        JustDireAttributeTestSupport.set(root, "negatefalldamage_upgrade_installed", true);
        nativeFall(server, "root-only installation authorizes eligible tier", root, 9, 0, 550, GameType.SURVIVAL);
        ItemStack forged = new ItemStack(Items.IRON_BOOTS);
        JustDireAttributeTestSupport.set(forged, "negatefalldamage_upgrade_installed", true);
        root = MixedBootsFactory.createPair(source, forged).primary();
        nativeFall(server, "ineligible source installation grants nothing", root, 9, 9, 1_000, GameType.SURVIVAL);

        Map<?, ?> flags = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config").getField("AVAILABLE_ABILITY_MAP").get(null);
        ModConfigSpec.BooleanValue enabled = flags.entrySet().stream().filter(entry -> ((Enum<?>) entry.getKey()).name().equals("NEGATEFALLDAMAGE"))
                                                  .map(entry -> (ModConfigSpec.BooleanValue) entry.getValue()).findFirst().orElseThrow();
        boolean previous = enabled.get();
        try {
            enabled.set(false);
            nativeFall(server, "mixed native config veto", mixed("celestigem", 1_000, false), 9, 9, 1_000, GameType.SURVIVAL);
        } finally {
            enabled.set(previous);
        }
        for (int charge : new int[]{49, 50}) {
            ItemStack jump = NeoForgeJustDireJumpTest.boots("blazegold", true);
            JustDireAttributeTestSupport.set(jump, "jumpboost_value", 2);
            root = MixedBootsFactory.createPair(boots("celestigem", charge * 2), jump).primary();
            JustDireAttributeTestSupport.set(root, "jumpboost_value", 2);
            nativeFall(server, "mixed Negate/Jump precedence FE=" + charge, root, 9,
                       charge == 50 ? 0 : 7, charge == 50 ? 0 : 49, GameType.SURVIVAL);
        }
        for (EquipmentSlot hand : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
            ServerPlayer player = player(server, GameType.SURVIVAL);
            root = mixed("celestigem", 50, false);
            player.setItemSlot(EquipmentSlot.FEET, root);
            ItemStack before = root.copy();
            player.setItemSlot(hand,
                               new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", "blazejet_wand"))));
            Fall result = fall(player, 9);
            require(result.distance == 0 && result.loss == 0 && ItemStack.matches(before, root), "Hand precedence charged mixed root " + hand);
            record("mixed hand precedence " + hand, "distance=0 FE=50; whole equipped root unchanged");
        }
        for (double distance : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Integer.MAX_VALUE}) {
            ServerPlayer player = player(server, GameType.SURVIVAL);
            root = mixed("celestigem", 1_000, false);
            player.setItemSlot(EquipmentSlot.FEET, root);
            ItemStack before = root.copy();
            LivingFallEvent event = new LivingFallEvent(player, distance, 1);
            NeoForge.EVENT_BUS.post(event);
            require(Double.compare(event.getDistance(), distance) == 0 && ItemStack.matches(before, root),
                    "Exceptional distance spent FE or protected " + distance);
            record("unsupported distance " + distance, "event and whole root unchanged; bounded diagnostic");
        }
        for (boolean invalidCount : new boolean[]{false, true}) {
            root = mixed("celestigem", 1_000, false);
            if (invalidCount) {
                root.setCount(2);
            } else {
                root.remove(games.alejandrocoria.mismatchedboots.content.ModContent.mixedBootsDataType().orElseThrow());
            }
            ServerPlayer player = player(server, GameType.SURVIVAL);
            player.setItemSlot(EquipmentSlot.FEET, root);
            ItemStack before = root.copy();
            Fall result = fall(player, 9);
            require(result.distance == 9 && result.loss == 6 && ItemStack.matches(before, root), "Invalid mixed root protected or changed");
            record("invalid mixed " + (invalidCount ? "count" : "data"), "distance=9 healthLoss=6; whole root unchanged");
        }
        root = mixed("celestigem", 1_000, false);
        var mob = new net.minecraft.world.entity.monster.zombie.Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, server.overworld());
        mob.setItemSlot(EquipmentSlot.FEET, root);
        ItemStack before = root.copy();
        LivingFallEvent event = new LivingFallEvent(mob, 9, 1);
        NeoForge.EVENT_BUS.post(event);
        require(event.getDistance() == 9 && ItemStack.matches(before, root), "Non-player acquired mixed Negate");
        record("non-player scope", "actual native event leaves distance=9 and root unchanged");
    }

    private static ItemStack mixed(String tier, int charge, boolean inverse) {
        var pair = MixedBootsFactory.createPair(boots(tier, charge * 2), new ItemStack(Items.IRON_BOOTS));
        return inverse ? pair.inverse() : pair.primary();
    }

    private static void guarded(MinecraftServer server, String guard) throws ReflectiveOperationException {
        boolean jumpReady = !guard.equals("missingFall");
        boolean negateReady = guard.equals("compatible") || guard.equals("apiMissing") || guard.equals("capabilityMissing");
        Class<?> handler = Class.forName("com.direwolf20.justdirethings.common.events.LivingEntityEvents");
        for (String name : List.of("mismatchedboots$justDireJumpReady", "mismatchedboots$justDireNegateFallReady")) {
            var getter = handler.getDeclaredMethod(name);
            getter.setAccessible(true);
            require((boolean) getter.invoke(null) == (name.contains("Jump") ? jumpReady : negateReady),
                    "Unexpected final permission " + guard + " " + name);
        }
        nativeFall(server, "guard " + guard + " native original", boots("celestigem", 1_000), 9,
                   0, guard.equals("consumerChanged") ? 500 : 550, GameType.SURVIVAL);
        ItemStack jump = NeoForgeJustDireJumpTest.boots("blazegold", true);
        ItemStack root = MixedBootsFactory.createPair(boots("celestigem", 2_000), jump).primary();
        JustDireAttributeTestSupport.set(root, "jumpboost_value", 2);
        for (int repeat = 0; repeat < 3; repeat++) {
            ServerPlayer player = player(server, GameType.SURVIVAL);
            player.setItemSlot(EquipmentSlot.FEET, root);
            ItemStack before = root.copy();
            Fall actual = fall(player, 9);
            double distance = guard.equals("compatible") ? 0 : jumpReady ? 7 : 9;
            require(actual.distance == distance && actual.loss == (distance == 0 ? 0 : distance == 7 ? 4 : 6), "Guard actual fall/health " + guard);
            if (guard.equals("compatible")) {
                require(energy(root).getAmountAsInt() == 550, "Compatible wrapper double/missed debit");
                unchangedExceptEnergy(before, root, "guard compatible");
                // Keep all three repetitions eligible, independently of partial payment.
                root.set(JustDireResourceTestSupport.energyComponent(), 1_000);
            } else {
                require(ItemStack.matches(before, root), "Denied Negate changed root/source/wear " + guard);
            }
            record("guard " + guard + " repeat=" + repeat,
                   "distance=" + actual.distance + " healthLoss=" + actual.loss + "; one event; no rejected debit");
        }
        ServerPlayer movement = player(server, GameType.SURVIVAL);
        movement.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        movement.jumpFromGround();
        double ordinaryJump = movement.getDeltaMovement().y;
        movement.setItemSlot(EquipmentSlot.FEET, jump);
        movement.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        movement.jumpFromGround();
        double nativeJump = movement.getDeltaMovement().y;
        movement.setItemSlot(EquipmentSlot.FEET, root);
        movement.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        movement.jumpFromGround();
        require(Math.abs(movement.getDeltaMovement().y - (jumpReady ? nativeJump : ordinaryJump)) < 1e-7, "Independent Jump movement lost " + guard);
        if (guard.equals("capabilityMissing")) {
            require(energy(root) == null, "Capability failure did not affect actual production provider");
        } else {
            require(energy(root).getAmountAsInt() == 1_000, "Negate guard disabled independent live storage");
            var charged = root.getAttributeModifiers();
            root.set(JustDireResourceTestSupport.energyComponent(), 0);
            require(!charged.equals(root.getAttributeModifiers()), "Negate guard disabled independent live armor selection");
        }
        var poweredOnly = MixedBootsFactory.createPair(NeoForgeJustDireJumpTest.poweredBoots("celestigem", 10), new ItemStack(Items.IRON_BOOTS))
                                           .primary();
        movement.setItemSlot(EquipmentSlot.FEET, poweredOnly);
        movement.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        var poweredBefore = poweredOnly.copy();
        movement.jumpFromGround();
        int poweredValue = jumpReady && !guard.equals("capabilityMissing") ? 3 : 0;
        require(Math.abs(movement.getDeltaMovement().y - ordinaryJump - poweredValue / 7.5f) < 1e-7
                        && ItemStack.matches(poweredBefore, poweredOnly), "Independent powered Jump/capability refusal lost " + guard);
        if (guard.equals("compatible")) {
            require(JustDireJumpGuardPlugin.jumpReads > 0 && JustDireJumpGuardPlugin.fallReads > 0, "Real compatible wrappers did not execute");
        }
        record("guard " + guard + " isolation", "native original; final permissions; paired movement; "
                + (guard.equals("capabilityMissing")
                   ? "actual missing capability denies powered Jump, retains passive Jump and causes no payment/protection"
                   : "production FE/live armor/powered Jump remain independent"));
    }

    private static Fall fall(ServerPlayer player, double distance) {
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        observed = player;
        fallEvents = 0;
        finalDistance = Double.NaN;
        try {
            if (player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL) {
                player.causeFallDamage(distance, 1, player.damageSources().fall());
            } else {
                // Creative/spectator physical falls bypass LivingFallEvent; test the native consumer via its actual bus.
                NeoForge.EVENT_BUS.post(new LivingFallEvent(player, distance, 1));
            }
            require(fallEvents == 1, "Expected one actual native fall event, got " + fallEvents);
            return new Fall(finalDistance, player.getMaxHealth() - player.getHealth());
        } finally {
            observed = null;
        }
    }

    static ServerPlayer player(MinecraftServer server, GameType mode) {
        ServerPlayer player = new ServerPlayer(server, server.overworld(),
                                               new GameProfile(UUID.randomUUID(), "negate-test"), ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                                         CommonListenerCookie.createInitial(player.getGameProfile(), false));
        // Newly constructed listeners have a load timeout; ServerPlayer rejects real damage until it clears.
        player.connection.markClientLoaded();
        player.setGameMode(mode);
        require(player.gameMode.getGameModeForPlayer() == mode && player.gameMode() == mode, "Fixture did not set authoritative game mode");
        return player;
    }

    static ItemStack boots(String tier, int charge) {
        ItemStack boots = NeoForgeJustDireResourcesTest.boots(tier, charge);
        JustDireAttributeTestSupport.set(boots, "negatefalldamage_upgrade_installed", true);
        return boots;
    }

    private static EnergyHandler energy(ItemStack stack) {
        return JustDireResourceTestSupport.handler(stack);
    }

    private static void unchangedExceptEnergy(ItemStack before, ItemStack after, String label) {
        ItemStack expected = before.copy();
        ItemStack actual = after.copy();
        expected.remove(JustDireResourceTestSupport.energyComponent());
        actual.remove(JustDireResourceTestSupport.energyComponent());
        require(ItemStack.matches(expected, actual), label + ": changed components/count/provenance/wear outside FE");
    }

    private static void record(String label, String details) {
        cases++;
        report.append(label).append(": ").append(details).append('\n');
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Fall(double distance, float loss) {
    }
}
