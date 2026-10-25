package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireRefundCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireRefundCredits;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** World/callback faults live only in this fixture, not in production refund state. */
public final class JustDireRefundFaults {
    private static ItemStack root;
    private static ServerPlayer player;
    private static String fault = "none";
    private static boolean reentering;
    private static int callbacks, before, after;
    private static List<Object> mutable;

    private JustDireRefundFaults() {
    }

    public static void before() {
        before++;
        if (root != null && fault.equals("reenter") && !reentering) {
            reentering = true;
            try {
                root.hurtAndBreak(1, player, EquipmentSlot.FEET);
            } finally {
                reentering = false;
            }
        }
    }

    public static void after() {
        after++;
        if (root == null) {
            return;
        }
        switch (fault) {
            case "replace" -> player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
            case "edit" -> root.set(JustDireRefundCompatibility.component(), new JustDireRefundCredits(List.of("earthquake")));
            case "list" -> mutable.clear();
            case "refill" -> root.setCount(1);
            case "throw" -> throw new IllegalStateException("Refund foreign operation fault");
        }
    }

    public static void broken(LivingEntity owner, EquipmentSlot slot) {
        if (owner == player && slot == EquipmentSlot.FEET) {
            callbacks++;
        }
    }

    public static void ordinary(ItemStack stack, int count) {
        stack.shrink(count);
    }

    public static int count(int count) {
        return count;
    }

    public static Operation<Void> operation(Operation<Void> operation) {
        return operation;
    }

    public static LivingEntity entity(LivingEntity owner) {
        return owner;
    }

    static List<String> exercise(MinecraftServer server, String variant) throws Exception {
        var records = new ArrayList<String>();
        var faults = variant.equals("inner") ? List.of("none", "replace", "edit", "list", "refill", "reenter", "throw") : List.of("none");
        for (String selected : faults) {
            root = JustDireRefundBreakTest.mixed("ferricore", "stepheight");
            JustDireRefundBreakTest.last(root);
            player = JustDireRefundBreakTest.equip(server, root);
            callbacks = before = after = 0;
            fault = selected;
            if (fault.equals("list")) {
                mutable = new ArrayList<>();
                mutable.add(Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                                 .getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance("stepheight", 71, false, true));
                JustDireAttributeTestSupport.set(root, "ability_bindings", mutable);
            }
            boolean threw = false;
            try {
                root.hurtAndBreak(2, player, EquipmentSlot.FEET);
            } catch (IllegalStateException failure) {
                if (!fault.equals("throw") || !failure.getMessage().equals("Refund foreign operation fault")) {
                    throw failure;
                }
                threw = true;
            }
            boolean compatible = variant.equals("inner") || variant.equals("outer");
            boolean refunds = compatible && (selected.equals("none") || selected.equals("reenter"));
            JustDireRefundBreakTest.refunds(refunds ? new String[]{"stepheight"} : new String[0]);
            JustDireRefundBreakTest.require(threw == selected.equals("throw"), "Foreign exception swallowed/retried");
            JustDireRefundBreakTest.require(callbacks == (selected.equals("throw") ? 0 : selected.equals("reenter") ? 2 : 1),
                                            "Ordinary break callback duplicated/lost: " + callbacks);
            if (compatible) {
                JustDireRefundBreakTest.require(before == (selected.equals("reenter") ? 2 : 1) && after == before,
                                                "Real forwarding wrapper was not exercised");
            } else {
                JustDireRefundBreakTest.require(before == 0 && after == 0, "Unsafe fixture accidentally loaded a forwarding wrapper");
            }
            JustDireRefundBreakTest.require(root.isEmpty() != selected.equals("refill"), "Foreign count mutation did not run");
            if (selected.equals("replace")) {
                JustDireRefundBreakTest.require(player.getItemBySlot(EquipmentSlot.FEET).is(Items.DIAMOND_BOOTS)
                                                        && player.getItemBySlot(EquipmentSlot.FEET).getDamageValue() == 0,
                                                "Refund refusal touched replacement");
            }
            fault = "none";
            if (selected.equals("refill") || selected.equals("throw")) {
                if (selected.equals("throw")) {
                    root.setCount(1);
                } // Explicit test-only restoration after the foreign exception consumed it.
                root.hurtAndBreak(1, player, EquipmentSlot.FEET);
                JustDireRefundBreakTest.refunds("stepheight");
                JustDireRefundBreakTest.require(root.isEmpty(), "Refill retry remained guarded/stuck");
            }
            records.add(variant + "/" + selected + ": actual consumption, credited drops and vanilla callbacks; bounded forwarding or local refusal");
        }
        root = null;
        player = null;
        mutable = null;
        return records;
    }
}
