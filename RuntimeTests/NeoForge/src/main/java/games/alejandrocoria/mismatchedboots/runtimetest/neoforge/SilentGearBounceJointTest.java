package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Real registered SG/JDT handlers; observation only, no alternate action, payment or ordering. */
public final class SilentGearBounceJointTest {
    private static LivingFallEvent observed;
    private static final List<String> trace = new ArrayList<>();
    private static Boolean sgFirst;
    private SilentGearBounceJointTest() {}

    public static void sgWear() { if (observed != null) { trace.add("sg-wear"); } }

    static void run(ServerPlayer owner, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Exception {
        SilentGearTraitSpike.initialize(api);
        var field = Class.forName("net.silentchaos512.lib.util.MathUtils").getDeclaredField("RANDOM"); field.setAccessible(true); var random = (Random) field.get(null);
        var sg = SilentGearBounceTest.lined(api.boot("crimson_iron"), api, owner);
        var nativeBoots = NeoForgeJustDireNegateFallTest.boots("eclipsealloy", 0);
        for (var root : NeoForgeSilentGearTest.workbench(owner, sg, nativeBoots)) {
            var record = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown").getConstructor(String.class, int.class, boolean.class);
            var binding = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding").getConstructor(String.class, int.class, boolean.class, boolean.class);
            JustDireAttributeTestSupport.set(root, "ability_cooldowns", List.of(record.newInstance("groundstomp", 37, false)));
            JustDireAttributeTestSupport.set(root, "ability_bindings", List.of(binding.newInstance("groundstomp", 72, false, true)));
            for (String mode : List.of("funded", "partial", "exhausted", "disabled", "master-off")) {
                int charge = mode.equals("exhausted") ? 0 : mode.equals("partial") ? 50 : 2_000;
                root.set(JustDireResourceTestSupport.energyComponent(), 0);
                if (charge > 0) { try (var transaction = Transaction.openRoot()) { require(JustDireResourceTestSupport.handler(root).insert(charge, transaction) == charge, "Joint root recharge refused"); transaction.commit(); } }
                JustDireAttributeTestSupport.set(root, "tool_enabled", !mode.equals("master-off"));
                JustDireAttributeTestSupport.set(root, "negatefalldamage_toggle", !mode.equals("disabled")); root.setDamageValue(10);
                equip(owner, root); var before = root.copy(); random.setSeed(0); var event = post(owner, 12);
                if (sgFirst == null) {
                    // Calibrate once from the funded outcome; suppression below independently proves JDT is live.
                    boolean sgOutcome = event.isCanceled() && event.getDistance() == 12 && SilentGearBounceTest.wearCalls == 1
                            && JustDireResourceTestSupport.handler(root).getAmountAsInt() == charge;
                    boolean jdt = !event.isCanceled() && event.getDistance() == 0 && SilentGearBounceTest.wearCalls == 0
                            && JustDireResourceTestSupport.handler(root).getAmountAsInt() == charge - 600;
                    require(sgOutcome != jdt, "Registered protections double-charged or neither activated: " + trace); sgFirst = sgOutcome;
                    report.append("C registered order inferred from exact funded payment/cancellation + independent live JDT controls: ").append(sgFirst ? "SG before JDT" : "JDT before SG").append('\n');
                }
                boolean negate = !sgFirst && (mode.equals("funded") || mode.equals("partial"));
                int remaining = negate ? Math.max(0, charge - 600) : charge;
                var expected = before.copy(); expected.set(JustDireResourceTestSupport.energyComponent(), remaining); expected.setDamageValue(10 + (negate ? 0 : 3));
                require(ItemStack.matches(expected, root) && event.isCanceled() == !negate && event.getDistance() == (negate ? 0 : 12)
                        && SilentGearBounceTest.wearCalls == (negate ? 0 : 1), "SG/JDT fall changed policy or double-charged " + mode + ": " + trace);
                report.append("C joint ").append(mode).append(" FE ").append(charge).append("->").append(remaining)
                        .append(" wear=").append(root.getDamageValue() - 10).append(" distance=").append(event.getDistance()).append(" trace=").append(trace).append('\n');
                // Prove JDT is actually live, not merely absent/disabled while SG wins registration order.
                var control = before.copy(); equip(owner, control); owner.setShiftKeyDown(true);
                var jdt = post(owner, 12); boolean usable = mode.equals("funded") || mode.equals("partial");
                var controlExpected = before.copy(); controlExpected.set(JustDireResourceTestSupport.energyComponent(), usable ? Math.max(0, charge - 600) : charge);
                require(!jdt.isCanceled() && jdt.getDistance() == (usable ? 0 : 12) && ItemStack.matches(control, controlExpected)
                        && SilentGearBounceTest.wearCalls == 0, "Suppressed Bounce lost live JDT control " + mode + ": " + trace);
                report.append("C suppressed Bounce / live Negate ").append(mode).append(" FE ").append(charge).append("->")
                        .append(JustDireResourceTestSupport.handler(control).getAmountAsInt()).append(" distance=").append(jdt.getDistance()).append(" wear=0\n");
                owner.setShiftKeyDown(false); equip(owner, root);
            }
            var before = root.copy(); var canceled = new LivingFallEvent(owner, 12, 1); canceled.setCanceled(true); observed = canceled; trace.clear();
            try { NeoForge.EVENT_BUS.post(canceled); } finally { observed = null; }
            require(ItemStack.matches(before, root) && trace.isEmpty(), "Pre-canceled joint fall gained work");
            before = root.copy(); var zero = post(owner, 0);
            require(!zero.isCanceled() && ItemStack.matches(before, root) && SilentGearBounceTest.wearCalls == 0, "Zero-distance joint fall gained a payment");
        }
        for (var root : NeoForgeSilentGearTest.workbench(owner, sg, NeoForgeJustDireJumpTest.boots("ferricore", true))) {
            root.setDamageValue(10); equip(owner, root); var before = root.copy(); random.setSeed(0); var event = post(owner, 12);
            boolean jdtFirst = !sgFirst; int bill = jdtFirst ? 2 : 3;
            var expected = before.copy(); expected.setDamageValue(10 + bill);
            require(event.isCanceled() && event.getDistance() == (jdtFirst ? 11 : 12) && ItemStack.matches(expected, root)
                    && SilentGearBounceTest.wearCalls == 1 && SilentGearBounceTest.requestedWear == bill, "Passive Jump/Bounce did not respect native order: " + trace);
            report.append("C joint passive Jump distance=").append(event.getDistance()).append(" wear=").append(bill).append(" trace=").append(trace).append('\n');
            var control = before.copy(); equip(owner, control); owner.setShiftKeyDown(true); var jdt = post(owner, 12);
            require(!jdt.isCanceled() && jdt.getDistance() == 11 && ItemStack.matches(before, control)
                    && SilentGearBounceTest.wearCalls == 0, "Suppressed Bounce lost passive Jump");
            report.append("C suppressed Bounce / live passive Jump distance=11 wear=0\n");
            owner.setShiftKeyDown(false);
        }
        owner.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        report.append("C joint complete root/source/FE/control/binding/cooldown snapshots preserved except the one authorized bill; no priority change or replacement handlers\n");
    }

    private static LivingFallEvent post(ServerPlayer owner, double distance) {
        var event = new LivingFallEvent(owner, distance, 1); observed = event; trace.clear(); SilentGearBounceTest.wearCalls = 0;
        try { NeoForge.EVENT_BUS.post(event); return event; } finally { observed = null; }
    }
    private static void equip(ServerPlayer owner, ItemStack root) { for (var slot : EquipmentSlot.values()) { owner.setItemSlot(slot, ItemStack.EMPTY); } owner.setDeltaMovement(0, 0, 0); owner.setItemSlot(EquipmentSlot.FEET, root); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
