package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Map;

/** Native threshold controls and opt-in mixed query feasibility through real production movement/fall routing. */
public final class NeoForgeJustDirePoweredJumpTest {
    private static boolean finished;
    private static int cases;
    private static ServerPlayer observed;
    private static double distance;
    private static final StringBuilder report = new StringBuilder();

    private NeoForgeJustDirePoweredJumpTest() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingFallEvent event) -> {
            if (event.getEntity() == observed) { distance = event.getDistance(); }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer()));
    }

    private static void run(MinecraftServer server) {
        if (finished) { return; } finished = true;
        String variant = System.getProperty("mismatchedboots.runtimetest.justDirePoweredJumpVariant", "live");
        try {
            JustDirePoweredJumpSpike.bind();
            require(NeoForgeJustDireJumpTest.fall(NeoForgeJustDireJumpTest.player(server), 9) == 6, "Survival health control is immune");
            if (variant.equals("live")) { matrix(server); }
            else {
                check(server, boots("eclipsealloy", 5), 5);
                var root = pair("eclipsealloy", "iron"); charge(root, 5); check(server, root, 0);
                root = pair("eclipsealloy", "ferricore"); charge(root, 5);
                check(server, root, variant.equals("control") ? 1 : 0);
                require(JustDirePoweredJumpSpike.queries == 0, "Unflagged control or denied paired hooks used the candidate");
            }
            NetworkAcceptanceFiles.write("just-dire-powered-jump-report.txt", cases + " native/mixed movement and real-health cases; variant="
                    + variant + "; queries=" + JustDirePoweredJumpSpike.queries + "\n" + report
                    + "test-only value candidate; existing production movement/fall/Negate dispatch; no production powered-Jump acceptance\n");
            NetworkAcceptanceFiles.write("just-dire-powered-jump-success.txt", variant + "\n");
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure("just-dire-powered-jump", failure); }
        finally { observed = null; server.halt(false); }
    }

    private static void matrix(MinecraftServer server) throws ReflectiveOperationException {
        for (String tier : List.of("celestigem", "eclipsealloy")) {
            int value = tier.equals("celestigem") ? 3 : 5;
            for (int energy : new int[]{0, 4, 5}) {
                check(server, boots(tier, energy), energy == 5 ? value : 0);
                var pair = MixedBootsFactory.createPair(boots(tier, 0), new ItemStack(Items.IRON_BOOTS));
                for (var root : List.of(pair.primary(), pair.inverse())) {
                    charge(root, energy); check(server, root, energy == 5 ? value : 0);
                }
            }
        }
        report.append("native/mixed 0/4/5 FE boundaries, both powered tiers/orientations; jump + three real falls do not spend FE/wear\n");
        var both = MixedBootsFactory.createPair(boots("celestigem", 0), boots("eclipsealloy", 0));
        for (var root : List.of(both.primary(), both.inverse())) { charge(root, 5); check(server, root, 5); }
        for (String passive : List.of("ferricore", "blazegold")) {
            var pair = MixedBootsFactory.createPair(boots("eclipsealloy", 0), NeoForgeJustDireJumpTest.boots(passive, true));
            for (var root : List.of(pair.primary(), pair.inverse())) {
                JustDireAttributeTestSupport.set(root, "jumpboost_value", 5);
                for (int energy : new int[]{5, 4, 5}) {
                    charge(root, energy); check(server, root, energy == 5 ? 5 : passive.equals("ferricore") ? 1 : 2);
                    require(root.get(JustDireAttributeTestSupport.<Integer>component("jumpboost_value")) == 5, "Fallback rewrote the root slider");
                }
            }
        }
        report.append("strongest usable maximum once; powered depletion uses the independently eligible passive tier, recharge restores 5 without rewriting controls\n");
        var root = pair("eclipsealloy", "iron"); charge(root, 5);
        for (String flag : List.of("tool_enabled", "jumpboost_toggle", "jumpboost_upgrade_installed")) {
            JustDireAttributeTestSupport.set(root, flag, false); check(server, root, 0);
            JustDireAttributeTestSupport.set(root, flag, true); check(server, root, 5);
        }
        JustDireAttributeTestSupport.set(root, "jumpboost_value", 999); check(server, root, 5);
        JustDireAttributeTestSupport.set(root, "jumpboost_value", -99); check(server, root, 1);
        root = pair("celestigem", "iron"); charge(root, 5);
        JustDireAttributeTestSupport.set(root, "jumpboost_value", 5); check(server, root, 3);
        root.remove(JustDireAttributeTestSupport.component("jumpboost_value")); check(server, root, 3);
        root.setDamageValue(root.getMaxDamage()); check(server, root, 3);
        charge(root, 4); root.setDamageValue(0); check(server, root, 0);
        var nativeWorn = boots("celestigem", 5); nativeWorn.setDamageValue(nativeWorn.getMaxDamage()); check(server, nativeWorn, 3);
        var historical = MixedBootsFactory.createPair(boots("eclipsealloy", 10_000), new ItemStack(Items.IRON_BOOTS)).primary();
        charge(historical, 0); check(server, historical, 0); charge(historical, 5); check(server, historical, 5);
        root = pair("eclipsealloy", "ferricore"); charge(root, 4);
        root.setDamageValue(root.getMaxDamage()); check(server, root, 0);
        root.setDamageValue(root.getMaxDamage() - 1); check(server, root, 1);
        root.setCount(2); check(server, root, 0);
        check(server, new ItemStack(root.getItem()), 0);
        report.append("root installation/controls; absent/native slider defaults and clamps; powered ignores ordinary wear but never falls back to durability itself; frozen FE is not live FE; invalid roots reject\n");
        Map<?, ?> flags = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config").getField("AVAILABLE_ABILITY_MAP").get(null);
        var enabled = (ModConfigSpec.BooleanValue) flags.entrySet().stream().filter(e -> ((Enum<?>) e.getKey()).name().equals("JUMPBOOST"))
                .findFirst().orElseThrow().getValue();
        boolean previous = enabled.get();
        try { enabled.set(false); check(server, boots("eclipsealloy", 5), 0); root = pair("eclipsealloy", "iron"); charge(root, 5); check(server, root, 0); }
        finally { enabled.set(previous); }
        for (boolean water : new boolean[]{true, false}) {
            for (var stack : List.of(boots("eclipsealloy", 5), fundedPair())) {
                var player = NeoForgeJustDireJumpTest.player(server, water, !water);
                player.jumpFromGround(); double baseline = player.getDeltaMovement().y;
                player.setDeltaMovement(Vec3.ZERO); player.setItemSlot(EquipmentSlot.FEET, stack);
                var before = stack.copy(); player.jumpFromGround();
                require(player.getDeltaMovement().y == baseline && NeoForgeJustDireJumpTest.fall(player, 9) == 1
                        && ItemStack.matches(before, stack), "Native water/gliding jump-only veto or no-payment fall differs");
                cases++;
            }
        }
        report.append("native config veto and water/gliding suppression apply to jump, not the fall companion\n");
        for (int energy : new int[]{4, 5, 49, 50, 600}) {
            for (var stack : List.of(boots("eclipsealloy", energy), fundedPair())) {
                charge(stack, energy); JustDireAttributeTestSupport.set(stack, "negatefalldamage_upgrade_installed", true);
                var before = stack.copy(); var player = NeoForgeJustDireJumpTest.player(server);
                player.setItemSlot(EquipmentSlot.FEET, stack); observed = player; distance = Double.NaN;
                float loss = NeoForgeJustDireJumpTest.fall(player, 12);
                require(distance == (energy >= 50 ? 0 : energy >= 5 ? 7 : 12)
                        && loss == (energy >= 50 ? 0 : energy >= 5 ? 4 : 9), "Native/mixed Negate/Jump precedence or real health differs: " + energy);
                before.set(JustDireResourceTestSupport.energyComponent(), energy >= 50 ? Math.max(0, energy - 600) : energy);
                require(ItemStack.matches(before, stack), "Extra Jump bill/state mutation beside Negate"); cases++;
            }
        }
        var handRoot = fundedPair(); var player = NeoForgeJustDireJumpTest.player(server);
        player.setItemSlot(EquipmentSlot.FEET, handRoot);
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", "blazejet_wand"))));
        var before = handRoot.copy(); require(NeoForgeJustDireJumpTest.fall(player, 12) == 0 && ItemStack.matches(before, handRoot), "AIRBURST early return changed");
        report.append("actual 12-block health and observed final distance: Negate wins at 50 FE even with partial bill; otherwise 5-FE Jump applies without payment; native hand AIRBURST still returns first\n");
    }

    private static void check(MinecraftServer server, ItemStack stack, int value) {
        try { NeoForgeJustDireJumpTest.check(server, stack, value); cases++; }
        catch (AssertionError failure) {
            int observedQueries = JustDirePoweredJumpSpike.queries;
            throw new AssertionError("Case " + cases + "; queries=" + observedQueries + "; " + JustDirePoweredJumpSpike.lastQuery, failure);
        }
    }
    static ItemStack boots(String tier, int energy) { var stack = NeoForgeJustDireJumpTest.boots(tier, true); charge(stack, energy); return stack; }
    static ItemStack pair(String powered, String passive) {
        return MixedBootsFactory.createPair(boots(powered, 0), passive.equals("iron") ? new ItemStack(Items.IRON_BOOTS)
                : NeoForgeJustDireJumpTest.boots(passive, true)).primary();
    }
    private static ItemStack fundedPair() { var root = pair("eclipsealloy", "iron"); charge(root, 5); return root; }
    static void charge(ItemStack root, int energy) { JustDireAttributeTestSupport.set(root, "forge_energy", energy); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
