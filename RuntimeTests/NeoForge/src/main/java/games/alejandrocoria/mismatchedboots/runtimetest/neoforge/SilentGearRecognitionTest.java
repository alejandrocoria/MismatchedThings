package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearRecognition;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Production-only item callbacks + registered native fall event. No research recognition is enabled. */
public final class SilentGearRecognitionTest {
    public static int queryContribution;
    private static boolean observing;
    private static int recalculations;
    private SilentGearRecognitionTest() {}

    public static void recalculation(ItemStack stack) {
        if (observing) { recalculations++; throw new AssertionError("Recognition entered native recalculation: " + stack.getItem()); }
    }

    static void run(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report, String variant) throws Exception {
        try { test(player, api, report, variant); }
        finally { observing = false; queryContribution = 0; clear(player); }
    }

    private static void test(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report, String variant) throws Exception {
        SilentGearTraitSpike.initialize(api); // Fixture construction helpers only, not its run/recognition hooks.
        boolean brilliant = !variant.equals("recognition-brilliant"), moonwalker = !variant.equals("recognition-moonwalker");
        Class<?> nativeArmor = SilentGearTraitSpike.nativeClass("item.gear.GearArmorItem"), events = SilentGearTraitSpike.nativeClass("event.GearEvents");
        require(proof(nativeArmor, "mismatchedboots$sgBrilliantReady") == brilliant, "Final Brilliant proof differs");
        require(proof(events, "mismatchedboots$sgMoonReady") == moonwalker, "Final Moonwalker proof differs");
        if (!variant.equals("recognition")) {
            require((boolean) nativeArmor.getMethod("sgtest$recognitionChanged").invoke(null)
                    && (boolean) events.getMethod("sgtest$recognitionChanged").invoke(null), "Competing transform did not run");
        }
        ItemStack gold = api.boot("gold"), moon = api.boot("azure_silver"), diamond = new ItemStack(Items.DIAMOND_BOOTS);
        ItemStack witness = api.boot("iron"); observing = true;
        try {
            boolean observed = false;
            try { SilentGearTraitSpike.nativeClass("util.GearData").getMethod("recalculateGearData", ItemStack.class, net.minecraft.world.entity.player.Player.class).invoke(null, witness, player); }
            catch (InvocationTargetException expected) { observed = expected.getCause() instanceof AssertionError; }
            require(observed && recalculations == 1, "Recalculation witness was not calibrated");
        } finally { observing = false; recalculations = 0; }
        clear(player); require(!PiglinAi.isWearingSafeArmor(player), "Naked piglin control failed");
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS)); require(PiglinAi.isWearingSafeArmor(player), "Vanilla gold control failed");
        player.setItemSlot(EquipmentSlot.FEET, gold); require(PiglinAi.isWearingSafeArmor(player), "Independent native Brilliant control failed");
        for (ItemStack second : List.of(diamond, api.boot("iron"), gold)) {
            for (ItemStack root : NeoForgeSilentGearTest.workbench(player, gold, second)) {
                clear(player); player.setItemSlot(EquipmentSlot.FEET, root);
                var before = equipment(player); observing = true;
                try {
                    require(root.getItem().makesPiglinsNeutral(root, player) == brilliant, "Production Brilliant callback differs");
                    require(PiglinAi.isWearingSafeArmor(player) == brilliant, "Actual piglin consumer differs");
                    require(SilentGearRecognition.brilliant(root, player, true), "Already-true ordinary callback was lost");
                    unchanged(player, before);
                } finally { observing = false; }
                clear(player); player.setItemSlot(EquipmentSlot.MAINHAND, root);
                require(!PiglinAi.isWearingSafeArmor(player), "Held boots counted as worn safe armor");
                invalidBrilliant(player, root);
            }
        }
        report.append("B Brilliant: native/vanilla piglin consumer controls; both paid orientations, SG/vanilla + SG/non-Brilliant + duplicate SG; ordinary true retained; held/invalid/plain roots neutral; ready=").append(brilliant).append('\n');

        clear(player); player.setItemSlot(EquipmentSlot.FEET, moon);
        fall(player, 10, api.traitLevel(moon, "moonwalker"), false);
        for (ItemStack second : List.of(diamond, moon)) {
            for (ItemStack root : NeoForgeSilentGearTest.workbench(player, moon, second)) {
                clear(player); player.setItemSlot(EquipmentSlot.FEET, root);
                int level = api.traitLevel(moon, "moonwalker");
                var before = equipment(player); observing = true;
                try {
                    fall(player, 10, moonwalker ? level : 0, false);
                    fall(player, 3, 0, false); fall(player, 10, 0, true);
                    unchanged(player, before);
                } finally { observing = false; }
                for (int otherLevel : List.of(2, 5)) {
                    ItemStack helmet = trait(api.armor("helmet", "helmet_plates", "iron"), api, "moonwalker", otherLevel);
                    clear(player); player.setItemSlot(EquipmentSlot.HEAD, helmet);
                    fall(player, 10, otherLevel, false); // Native armor positive, independently of our mixed view.
                    player.setItemSlot(EquipmentSlot.FEET, root); before = equipment(player); observing = true;
                    try { fall(player, 10, moonwalker ? Math.max(level, otherLevel) : otherLevel, false); unchanged(player, before); }
                    finally { observing = false; }
                }
                clear(player); player.setItemSlot(EquipmentSlot.MAINHAND, root); fall(player, 10, 0, false);
                ItemStack invalid = root.copy(); invalid.setCount(2); player.setItemSlot(EquipmentSlot.FEET, invalid); fall(player, 10, 0, false);
                invalid = root.copy(); invalid.remove(DataComponents.EQUIPPABLE); player.setItemSlot(EquipmentSlot.FEET, invalid); fall(player, 10, 0, false);
                invalid = root.copy(); invalid.remove(ModContent.mixedBootsDataType().orElseThrow()); player.setItemSlot(EquipmentSlot.FEET, invalid); fall(player, 10, 0, false);
            }
        }
        // Controlled source-property instances, not stock survival materials or a separate compatibility engine.
        ItemStack low = trait(moon, api, "moonwalker", 2), high = trait(moon, api, "moonwalker", 5);
        for (var root : NeoForgeSilentGearTest.workbench(player, low, high)) {
            clear(player); player.setItemSlot(EquipmentSlot.FEET, root); fall(player, 10, moonwalker ? 5 : 0, false);
        }
        ItemStack bounce = trait(api.boot("iron"), api, "bounce", 1);
        clear(player); player.setItemSlot(EquipmentSlot.FEET, bounce);
        var nativeBounce = new LivingFallEvent(player, 10, 1); NeoForge.EVENT_BUS.post(nativeBounce);
        require(nativeBounce.isCanceled(), "Native Bounce positive control failed");
        for (var root : NeoForgeSilentGearTest.workbench(player, moon, bounce)) {
            clear(player); player.setItemSlot(EquipmentSlot.FEET, root); var before = equipment(player); observing = true;
            try { fall(player, 10, moonwalker ? 4 : 0, false); unchanged(player, before); }
            finally { observing = false; }
        }
        if (variant.equals("recognition-compatible")) {
            var root = NeoForgeSilentGearTest.workbench(player, moon, diamond).getFirst();
            for (int contribution : List.of(2, 5)) {
                queryContribution = contribution; clear(player); player.setItemSlot(EquipmentSlot.FEET, root);
                fall(player, 10, Math.max(4, contribution), false);
                clear(player); fall(player, 10, contribution, false);
            }
            queryContribution = 0;
            report.append("Controlled optional-query contribution 2/5 preserved through compatible result chaining (Curios not installed)\n");
        }
        clear(player); player.setItemSlot(EquipmentSlot.FEET, diamond); fall(player, 10, 0, false);
        require(recalculations == 0, "Production recognition recalculated native gear");
        for (var root : NeoForgeSilentGearTest.workbench(player, api.boot("refined_iron"), diamond)) {
            clear(player); player.tickCount = 20; player.setItemSlot(EquipmentSlot.FEET, root);
            root.inventoryTick(player.level(), player, EquipmentSlot.FEET);
            require(player.hasEffect(net.minecraft.world.effect.MobEffects.JUMP_BOOST), "Independent C Wielder dispatch regressed");
        }
        // Each refused feature leaves the other and the completed repair/live-state paths available.
        SilentGearEffectsTest.smoke(player, api, report);
        SilentGearLiveStateTest.smoke(player, api, report);
        report.append("B Moonwalker: registered native event, one native reduction; native/vanilla, either/duplicate/unequal sources, higher/lower native armor; cancellation/short falls, held/invalid/plain roots; native Bounce remains unrecognized on mixed roots; ready=").append(moonwalker)
                .append("; immutable equipment/sources, zero native recalculations; existing repair/live state pass\n");
    }

    private static void invalidBrilliant(ServerPlayer player, ItemStack root) {
        ItemStack invalid = root.copy(); invalid.setCount(2);
        require(!invalid.getItem().makesPiglinsNeutral(invalid, player), "Stacked root gained Brilliant");
        invalid = root.copy(); invalid.remove(DataComponents.EQUIPPABLE);
        require(!invalid.getItem().makesPiglinsNeutral(invalid, player), "Non-FEET root gained Brilliant");
        invalid = root.copy(); invalid.remove(ModContent.mixedBootsDataType().orElseThrow());
        require(!invalid.getItem().makesPiglinsNeutral(invalid, player), "Plain root gained Brilliant");
    }

    private static void fall(ServerPlayer player, double distance, int level, boolean canceled) {
        var event = new LivingFallEvent(player, distance, 1); event.setCanceled(canceled);
        var velocity = player.getDeltaMovement(); NeoForge.EVENT_BUS.post(event);
        double expected = level == 0 ? distance : distance * (1 + level * -0.15F);
        require(Math.abs(event.getDistance() - expected) < 1E-6 && event.isCanceled() == canceled && player.getDeltaMovement().equals(velocity),
                "Fall behavior differs: expected=" + expected + " actual=" + event.getDistance() + " canceled=" + event.isCanceled());
    }

    @SuppressWarnings("unchecked")
    private static ItemStack trait(ItemStack stack, NeoForgeSilentGearTest.NativeApi api, String name, int level) throws Exception {
        ItemStack copy = stack.copy();
        Object ref = api.resource.getMethod("trait", String.class).invoke(null, "silentgear:" + name);
        Class<?> instance = SilentGearTraitSpike.nativeClass("api.traits.TraitInstance");
        Object nativeTrait = instance.getMethod("of", SilentGearTraitSpike.nativeClass("gear.trait.Trait"), int.class, java.util.Collection.class)
                .invoke(null, ((Supplier<?>) ref).get(), level, List.of());
        Object props = copy.get(api.properties);
        var values = new HashMap<>((Map<Object, Object>) props.getClass().getMethod("properties").invoke(props));
        Object key = ((Supplier<?>) SilentGearTraitSpike.nativeClass("setup.gear.GearProperties").getField("TRAITS").get(null)).get();
        values.put(key, SilentGearTraitSpike.nativeClass("api.property.TraitListPropertyValue").getConstructor(List.class).newInstance(List.of(nativeTrait)));
        copy.set(api.properties, props.getClass().getConstructor(Map.class).newInstance(values)); return copy;
    }
    private static void clear(ServerPlayer player) {
        player.removeAllEffects(); player.setDeltaMovement(0, 0, 0);
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
    }
    private static List<ItemStack> equipment(ServerPlayer player) {
        var result = new ArrayList<ItemStack>(); for (var slot : EquipmentSlot.values()) { result.add(player.getItemBySlot(slot).copy()); } return result;
    }
    private static void unchanged(ServerPlayer player, List<ItemStack> before) {
        int i = 0; for (var slot : EquipmentSlot.values()) { require(ItemStack.matches(before.get(i++), player.getItemBySlot(slot)), "Recognition changed gear/source state"); }
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
    private static boolean proof(Class<?> owner, String name) throws Exception {
        var method = owner.getDeclaredMethod(name); method.setAccessible(true); return (boolean) method.invoke(null);
    }
}
