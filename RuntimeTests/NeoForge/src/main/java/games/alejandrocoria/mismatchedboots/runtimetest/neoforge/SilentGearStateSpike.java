package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.composition.AttributeComposer;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.platform.Services;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.nativeClass;

/** Native state feasibility only. Candidate writes are explicit, not installed item/event support. */
public final class SilentGearStateSpike {
    private static NeoForgeSilentGearStatsTest.NativeApi api;
    private static ItemStack activeRoot;
    private static int nativeRecalculations, forbiddenRecalculations;

    private SilentGearStateSpike() {}

    static void run(ServerPlayer player, NeoForgeSilentGearStatsTest.NativeApi nativeApi, StringBuilder report) throws Exception {
        api = nativeApi;
        clear(player);
        report.append("\nSTATE PROJECTION SPIKE: immutable source snapshots; repair/wear now tested through production B\n");
        dynamicProperties(player, report);
        oxidation(player, report);
        fullNativeOxidation(player, report);
        require(nativeRecalculations > 0 && forbiddenRecalculations == 0, "Missing native projection or mixed-root recalculation entered");
        clear(player);
        report.append("State scope: native recalculation only on disposable native views; no native broken-state inheritance; ")
                .append("root/source metadata checks and registry-aware item codec; not installed ticking or world restart\n");
    }

    /** Accelerate only the fixture's counters; native ticks/recalculation decide the phase and level. */
    private static void fullNativeOxidation(ServerPlayer player, StringBuilder report) throws Exception {
        ItemStack nativeBoot = api.boot("copper");
        var stage = component("OXIDATION_STAGE");
        var counter = component("OXIDATION_COUNTER");
        for (int step = 0; step < 6; step++) {
            boolean polished = api.traitLevel(nativeBoot, "polished") > 0;
            nativeBoot.set(counter, polished ? 1199 : 599);
            player.tickCount = 20;
            nativeBoot.inventoryTick(player.level(), player, null);
            report.append("FULL NATIVE OXIDATION pending step=").append(step)
                    .append(" stage=").append(nativeBoot.getOrDefault(stage, (byte) 0)).append('\n');
            player.tickCount = 21;
            nativeBoot.inventoryTick(player.level(), player, null);
            report.append("FULL NATIVE OXIDATION step=").append(step)
                    .append(" polished=").append(api.traitLevel(nativeBoot, "polished"))
                    .append(" oxidized=").append(api.traitLevel(nativeBoot, "oxidized"))
                    .append(" stage=").append(nativeBoot.getOrDefault(stage, (byte) 0))
                    .append(" counter=").append(nativeBoot.getOrDefault(counter, 0)).append('\n');
        }
        require(api.traitLevel(nativeBoot, "oxidized") == 1 && nativeBoot.getOrDefault(stage, (byte) 0).equals((byte) 1),
                "Pinned native oxidation reset changed; revisit the C progression decision");
        // Independent unseeded timeline rules out the accelerated counters causing the reset.
        nativeBoot = api.boot("copper");
        for (int tick = 1; tick <= 50_000; tick++) {
            player.tickCount = tick;
            nativeBoot.inventoryTick(player.level(), player, null);
            if (tick == 24_001 || tick == 36_001 || tick == 48_001 || tick == 50_000) {
                report.append("NATURAL NATIVE OXIDATION tick=").append(tick)
                        .append(" polished=").append(api.traitLevel(nativeBoot, "polished"))
                        .append(" oxidized=").append(api.traitLevel(nativeBoot, "oxidized"))
                        .append(" stage=").append(nativeBoot.getOrDefault(stage, (byte) 0))
                        .append(" counter=").append(nativeBoot.getOrDefault(counter, 0)).append('\n');
            }
        }
        require(api.traitLevel(nativeBoot, "oxidized") == 1 && nativeBoot.getOrDefault(stage, (byte) 0).equals((byte) 1)
                        && nativeBoot.getOrDefault(counter, 0).equals(100),
                "Unseeded native oxidation differs from the accelerated reset control");
    }


    /** Native copies may recalculate; no mixed stack is admitted to the native gear recalculator. */
    public static void recalculation(ItemStack stack) {
        if (activeRoot == null) { return; }
        if (stack == activeRoot || ModContent.getMixedBootsData(stack).isPresent()) {
            forbiddenRecalculations++;
            throw new AssertionError("State research attempted native recalculation of mixed gear");
        }
        nativeRecalculations++;
    }


    private static void dynamicProperties(ServerPlayer player, StringBuilder report) throws Exception {
        for (String[] sample : List.of(new String[]{"bone", "chipping"}, new String[]{"quartz", "crushing"})) {
            ItemStack source = api.boot(sample[0]); double healthy = armor(source);
            int half = source.getMaxDamage() / 2;
            ItemStack direct = source.copy(); direct.hurtAndBreak(half, player, EquipmentSlot.FEET);
            double afterDirect = armor(direct);
            ItemStack helper = source.copy(); attempt(helper, half, player);
            double afterHelper = armor(helper);
            require(sample[1].equals("chipping") ? afterHelper < healthy : afterHelper > healthy, "Native damage property did not change");
            attempt(helper, -half, player); close(armor(helper), healthy, "Native repair refresh");
            report.append("DYNAMIC native ").append(sample[1]).append(" armor healthy=").append(healthy)
                    .append(" ordinary wear=").append(afterDirect).append(" helper wear=").append(afterHelper)
                    .append(" repaired=").append(armor(helper)).append('\n');
            for (ItemStack root : pair(player, source, new ItemStack(Items.DIAMOND_BOOTS))) {
                ItemStack before = root.copy();
                root.hurtAndBreak(root.getMaxDamage() / 2, player, EquipmentSlot.FEET);
                close(armor(root), armor(before), "Unadapted mixed stat should remain its creation snapshot");
                for (int denominator : List.of(2, 1)) {
                    root.setDamageValue(denominator == 2 ? root.getMaxDamage() / 2 : 0);
                    activeRoot = root;
                    try {
                        var data = ModContent.getMixedBootsData(root).orElseThrow();
                        ItemStack left = projectWear(data.left().source(), root, player), right = projectWear(data.right().source(), root, player);
                        root.set(DataComponents.ATTRIBUTE_MODIFIERS, AttributeComposer.composePrimary(normalize(left), normalize(right), AttributeRulesLifecycle.serverRules()));
                        close(armor(root), (armor(left) + armor(right)) / 2, "Projected armor arithmetic");
                        if (denominator == 2) { require(Math.abs(armor(root) - armor(before)) > 1E-5, "Mixed damage projection did not refresh"); }
                        else { close(armor(root), armor(before), "Projected repaired armor"); }
                        unchangedExcept(before, root, DataComponents.DAMAGE, DataComponents.ATTRIBUTE_MODIFIERS);
                    } finally { activeRoot = null; }
                }
            }
        }
        // Reusing a native recalculator on a mixed root is deliberately refused before it can overwrite components.
        ItemStack root = pair(player, api.boot("bone"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst(), before = root.copy();
        activeRoot = root;
        try {
            boolean caught = false;
            try { recalculate(root, player); } catch (InvocationTargetException failure) { caught = failure.getCause() instanceof AssertionError; }
            require(caught && forbiddenRecalculations == 1 && ItemStack.matches(before, root), "Mixed recalculation tripwire did not protect data");
        } finally { activeRoot = null; forbiddenRecalculations = 0; }
        report.append("DYNAMIC candidate maps live root wear ratio into disposable native copies, composes only attributes; max/model/equipment/history preserved\n");
    }

    private static void oxidation(ServerPlayer player, StringBuilder report) throws Exception {
        DataComponentType<Object> stage = component("OXIDATION_STAGE"), counter = component("OXIDATION_COUNTER");
        ItemStack copper = api.boot("copper");
        copper.set(counter, 1199); // Native Polished's transition threshold, not a fabricated trait list.
        ItemStack coated = (ItemStack) api.assemble.invoke(api.swapClass.getField("INSTANCE").get(null),
                CraftingInput.of(2, 1, List.of(copper.copy(), api.part("coating", "gold"))));
        require(!coated.isEmpty(), "Native coating control failed");
        ItemStack younger = copper.copy(); younger.set(counter, 10);
        record Scenario(ItemStack second, int activeLanes, String name) {}
        for (var scenario : List.of(new Scenario(new ItemStack(Items.DIAMOND_BOOTS), 1, "vanilla"),
                new Scenario(coated, 1, "coated"), new Scenario(copper.copy(), 2, "aligned copper"),
                new Scenario(younger, 1, "unequal clocks"))) {
            for (ItemStack root : pair(player, copper, scenario.second())) {
                ItemStack before = root.copy();
                var data = ModContent.getMixedBootsData(root).orElseThrow();
                ItemStack[] reference = {data.left().source(), data.right().source()};
                for (int step = 0; step < 2; step++) {
                    player.tickCount = 20 * (step + 1);
                    for (ItemStack nativeBoot : reference) {
                        if (api.gear.isInstance(nativeBoot.getItem())) { nativeBoot.inventoryTick(player.level(), player, null); }
                    }
                    activeRoot = root;
                    try {
                        for (int side = 0; side < 2; side++) {
                            ItemStack original = side == 0 ? data.left().source() : data.right().source();
                            if (!api.gear.isInstance(original.getItem())) { continue; }
                            ItemStack working = original.copy();
                            CompoundTag state = lane(root, side);
                            if (!state.isEmpty()) { working.set(stage, (byte) state.getIntOr("stage", 0)); working.set(counter, state.getIntOr("counter", 0)); }
                            recalculate(working, player);
                            working.inventoryTick(player.level(), player, null);
                            var next = new CompoundTag();
                            next.putInt("stage", ((Number) working.getOrDefault(stage, (byte) 0)).intValue());
                            next.putInt("counter", ((Number) working.getOrDefault(counter, 0)).intValue());
                            saveLane(root, side, next);
                            require(working.getOrDefault(stage, (byte) 0).equals(reference[side].getOrDefault(stage, (byte) 0))
                                    && working.getOrDefault(counter, 0).equals(reference[side].getOrDefault(counter, 0)), "Root-owned oxidation diverged from native");
                        }
                        unchangedExcept(before, root, DataComponents.CUSTOM_DATA);
                    } finally { activeRoot = null; }
                    if (step == 0) {
                        var ops = player.level().registryAccess().createSerializationContext(JsonOps.INSTANCE);
                        root = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, root).getOrThrow()).getOrThrow();
                    }
                }
                int activeLanes = 0;
                for (int side = 0; side < 2; side++) { if (lane(root, side).getIntOr("stage", 0) > 0) { activeLanes++; } }
                require(activeLanes == scenario.activeLanes(), "Oxidation/coating lane count differs");
                report.append("OXIDATION ").append(scenario.name()).append(" lanes=").append(activeLanes)
                        .append("; native Polished transition/coating pause and item-codec continuation match\n");
            }
        }
        ItemStack held = copper.copy(), idle = copper.copy(); player.tickCount = 20;
        held.inventoryTick(player.level(), player, EquipmentSlot.FEET);
        idle.inventoryTick(player.level(), player, null);
        require(((Number) held.getOrDefault(counter, 0)).intValue() == 1199
                && ((Number) idle.getOrDefault(stage, (byte) 0)).intValue() > 0, "Native selected/idle oxidation control differs");
        report.append("OXIDATION native copies update per-source root-owned lanes; no chosen production schema, source edits or full native item loop on root\n");
    }

    private static ItemStack projectWear(ItemStack source, ItemStack root, ServerPlayer player) throws Exception {
        ItemStack working = source.copy();
        if (api.gear.isInstance(working.getItem())) {
            int mapped = (int) ((long) root.getDamageValue() * working.getMaxDamage() / root.getMaxDamage());
            working.set(DataComponents.DAMAGE, mapped); recalculate(working, player);
        }
        return working;
    }
    private static ItemAttributeModifiers normalize(ItemStack stack) {
        var builder = ItemAttributeModifiers.builder();
        Services.PLATFORM.getCompositionAttributeModifiers(stack).modifiers().stream().distinct()
                .forEach(entry -> builder.add(entry.attribute(), entry.modifier(), entry.slot(), entry.display()));
        return builder.build();
    }
    private static double armor(ItemStack stack) {
        return stack.getAttributeModifiers().modifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.ARMOR) && entry.slot().test(EquipmentSlot.FEET))
                .mapToDouble(entry -> entry.modifier().amount()).sum();
    }
    private static void attempt(ItemStack stack, int amount, ServerPlayer player) throws Exception {
        nativeClass("util.GearHelper").getMethod("attemptDamage", ItemStack.class, int.class, LivingEntity.class, EquipmentSlot.class)
                .invoke(null, stack, amount, player, EquipmentSlot.FEET);
    }
    private static void recalculate(ItemStack stack, ServerPlayer player) throws Exception {
        nativeClass("util.GearData").getMethod("recalculateGearData", ItemStack.class, Player.class).invoke(null, stack, player);
    }
    private static List<ItemStack> pair(ServerPlayer player, ItemStack first, ItemStack second) {
        return NeoForgeSilentGearStatsTest.workbench(player, first, second);
    }
    private static DataComponentType<Object> component(String field) throws ReflectiveOperationException {
        return NeoForgeSilentGearStatsTest.NativeApi.component(field);
    }
    private static CompoundTag lane(ItemStack root, int side) {
        return root.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty("sg_state_spike_" + side);
    }
    private static void saveLane(ItemStack root, int side, CompoundTag value) {
        var tag = root.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put("sg_state_spike_" + side, value); root.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    private static void unchangedExcept(ItemStack before, ItemStack after, DataComponentType<?>... allowed) {
        ItemStack a = before.copy(), b = after.copy();
        for (var type : allowed) { a.remove(type); b.remove(type); }
        require(ItemStack.matches(a, b), "Candidate changed protected root fields or original sources");
    }
    private static void clear(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL); player.removeAllEffects(); player.tickCount = 20;
        for (EquipmentSlot slot : List.of(EquipmentSlot.FEET, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) { player.setItemSlot(slot, ItemStack.EMPTY); }
    }
    private static void close(double actual, double expected, String label) {
        require(Math.abs(actual - expected) < 1E-5, label + ": " + actual + " != " + expected);
    }
    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
