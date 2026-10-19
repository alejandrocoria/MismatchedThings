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
import net.minecraft.util.Unit;
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
import net.minecraft.world.level.Level;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Random;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.carrier;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.context;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.level;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.nativeClass;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.traitOf;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.view;

/** Native state feasibility only. Candidate writes are explicit, not installed item/event support. */
public final class SilentGearStateSpike {
    private static NeoForgeSilentGearStatsTest.NativeApi api;
    private static ItemStack activeRoot, repairCarrier;
    private static int repairCalls, nativeRecalculations, forbiddenRecalculations;
    private static Random nativeRandom;
    private static final Class<?> EFFECT = nativeClass("api.traits.TraitEffect");
    private static final Class<?> CONTEXT = nativeClass("api.traits.TraitActionContext");

    private SilentGearStateSpike() {}

    static void run(ServerPlayer player, NeoForgeSilentGearStatsTest.NativeApi nativeApi, StringBuilder report) throws Exception {
        api = nativeApi;
        var randomField = Class.forName("net.silentchaos512.lib.util.MathUtils").getDeclaredField("RANDOM");
        randomField.setAccessible(true); nativeRandom = (Random) randomField.get(null);
        clear(player);
        report.append("\nSTATE SPIKE: provisional ordinary mixed destruction, immutable source snapshots\n");
        repairs(player, report);
        wear(player, report);
        dynamicProperties(player, report);
        oxidation(player, report);
        require(nativeRecalculations > 0 && forbiddenRecalculations == 0, "Missing native projection or mixed-root recalculation entered");
        clear(player);
        report.append("State scope: native recalculation only on disposable native views; no native broken-state inheritance; ")
                .append("root/source metadata checks and registry-aware item codec; not installed ticking or world restart\n");
    }

    /** Intercept only the exact transient carrier, after native repair chance/amount calculation. */
    public static boolean repair(ItemStack stack, int amount, LivingEntity entity) {
        if (activeRoot == null || stack != repairCarrier) { return false; }
        require(amount < 0, "Repair boundary received positive wear");
        repairCalls++;
        if (!activeRoot.isEmpty() && !activeRoot.has(DataComponents.UNBREAKABLE)
                && (!(entity instanceof Player player) || !player.getAbilities().instabuild)) {
            activeRoot.setDamageValue(Math.max(0, activeRoot.getDamageValue() + amount));
        }
        return true;
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

    private static void repairs(ServerPlayer player, StringBuilder report) throws Exception {
        for (String[] sample : List.of(new String[]{"amethyst", "renew"}, new String[]{"refined_iron", "stellar"})) {
            ItemStack source = api.boot(sample[0]);
            Object instance = view(source).get("silentgear:" + sample[1]), effect = effect(instance, "SelfRepairTraitEffect");
            int lvl = level(instance);
            float chance = chance(effect);
            for (boolean success : List.of(true, false)) {
                long seed = seed(chance * lvl, success);
                ItemStack nativeBoot = source.copy(); nativeBoot.setDamageValue(12);
                player.tickCount = 20; nativeRandom.setSeed(seed);
                tickEffect(effect, player, instance, nativeBoot, false);
                require(nativeBoot.getDamageValue() == (success ? 12 - lvl : 12), "Native repair chance/amount control differs");
                for (ItemStack root : pair(player, source, new ItemStack(Items.DIAMOND_BOOTS))) {
                    root.setDamageValue(12);
                    ItemStack before = root.copy(), proxy = carrier(root, view(root)), proxyBefore = proxy.copy();
                    activeRoot = root; repairCarrier = proxy; repairCalls = 0;
                    try {
                        nativeRandom.setSeed(seed); tickEffect(effect, player, instance, proxy, false);
                        require(root.getDamageValue() == nativeBoot.getDamageValue() && repairCalls == (success ? 1 : 0), "Root repair differs or ran twice");
                        unchangedExcept(before, root, DataComponents.DAMAGE);
                        require(ItemStack.matches(proxyBefore, proxy), "Repair mutated its carrier");
                    } finally { activeRoot = null; repairCarrier = null; }
                }
            }
            ItemStack root = pair(player, source, source).getFirst();
            Object merged = view(root).get("silentgear:" + sample[1]);
            require(level(merged) == lvl, "Duplicate repair trait levels added");
            Object selectedEffect = effect(merged, "SelfRepairTraitEffect");
            for (String mode : List.of("duplicate", "healthy", "creative", "unbreakable", "wrong-tick")) {
                root.setDamageValue(mode.equals("healthy") ? 0 : 12);
                if (mode.equals("unbreakable")) { root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE); }
                player.setGameMode(mode.equals("creative") ? GameType.CREATIVE : GameType.SURVIVAL);
                player.tickCount = mode.equals("wrong-tick") ? 21 : 20;
                ItemStack before = root.copy(); activeRoot = root; repairCarrier = carrier(root, view(root));
                try {
                    nativeRandom.setSeed(seed(chance * lvl, true));
                    tickEffect(selectedEffect, player, merged, repairCarrier, false);
                    require(root.getDamageValue() == (mode.equals("duplicate") ? 12 - lvl : before.getDamageValue()), "Repair boundary differs: " + mode);
                    unchangedExcept(before, root, DataComponents.DAMAGE);
                } finally { activeRoot = null; repairCarrier = null; root.remove(DataComponents.UNBREAKABLE); }
            }
            player.setGameMode(GameType.SURVIVAL);
            report.append("REPAIR ").append(sample[1]).append(" level=").append(lvl).append(" chance=").append(chance * lvl)
                    .append(" per 20-player-tick attempt; seeded success/failure, inventory, inverse, duplicate, healthy, creative, Unbreakable and cadence controls\n");
        }
    }

    private static void wear(ServerPlayer player, StringBuilder report) throws Exception {
        for (String[] sample : List.of(new String[]{"iron", "malleable"}, new String[]{"diamond", "brittle"})) {
            ItemStack source = api.boot(sample[0]);
            Object instance = view(source).get("silentgear:" + sample[1]), effect = effect(instance, "DurabilityTraitEffect");
            int lvl = level(instance), scale = sample[1].equals("malleable") ? -1 : 1;
            float chance = chance(effect);
            for (boolean success : List.of(true, false)) {
                long seed = seed(chance * lvl, success);
                ItemStack nativeBoot = source.copy(); nativeBoot.setDamageValue(10);
                nativeRandom.setSeed(seed); nativeBoot.hurtAndBreak(3, player, EquipmentSlot.FEET);
                int expected = 3 + (success ? scale : 0);
                require(nativeBoot.getDamageValue() == 10 + expected, "Native wear probability/scale differs");
                for (ItemStack root : pair(player, source, new ItemStack(Items.DIAMOND_BOOTS))) {
                    root.setDamageValue(10); ItemStack before = root.copy();
                    activeRoot = root;
                    try {
                        nativeRandom.setSeed(seed);
                        // Native wear contexts intentionally have no player, as in GearHelper.damageItem.
                        int amount = (int) EFFECT.getMethod("onDurabilityDamage", CONTEXT, int.class)
                                .invoke(effect, context(null, instance, carrier(root, view(root))), 3);
                        require(amount == expected, "Candidate native wear account differs");
                        root.hurtAndBreak(amount, player, EquipmentSlot.FEET);
                        require(root.getDamageValue() == nativeBoot.getDamageValue(), "Root did not spend native wear exactly once");
                        unchangedExcept(before, root, DataComponents.DAMAGE);
                    } finally { activeRoot = null; }
                }
            }
            report.append("WEAR ").append(sample[1]).append(" level=").append(lvl).append(" chance=").append(chance * lvl)
                    .append("; native hurtAndBreak and callback delta match on both orientations, seeded activation/nonactivation\n");
        }
        for (ItemStack root : pair(player, api.boot("iron"), api.boot("diamond"))) {
            var traits = view(root);
            for (boolean brittle : List.of(false, true)) {
                for (boolean malleable : List.of(false, true)) {
                    long chosen = 0;
                    for (; chosen < 100_000; chosen++) {
                        Random oracle = new Random(chosen);
                        if ((oracle.nextDouble() < 0.5) == brittle && (oracle.nextDouble() < 0.5) == malleable) { break; }
                    }
                    require(chosen < 100_000, "Missing combined wear roll control");
                    ItemStack proxy = carrier(root, traits); proxy.setDamageValue(10);
                    nativeRandom.setSeed(chosen); proxy.hurtAndBreak(3, player, EquipmentSlot.FEET);
                    int expected = 3 + (brittle ? 1 : 0) - (malleable ? 1 : 0), amount = 3;
                    root.setDamageValue(10); ItemStack before = root.copy(); activeRoot = root;
                    try {
                        nativeRandom.setSeed(chosen);
                        for (String id : List.of("silentgear:brittle", "silentgear:malleable")) {
                            Object instance = traits.get(id);
                            amount = (int) EFFECT.getMethod("onDurabilityDamage", CONTEXT, int.class)
                                    .invoke(effect(instance, "DurabilityTraitEffect"), context(null, instance, carrier(root, traits)), amount);
                        }
                        require(amount == expected && proxy.getDamageValue() == 10 + expected, "Combined native wear/candidate/oracle differ");
                        root.hurtAndBreak(amount, player, EquipmentSlot.FEET);
                        require(root.getDamageValue() == 10 + expected, "Combined root wear paid more than once");
                        unchangedExcept(before, root, DataComponents.DAMAGE);
                    } finally { activeRoot = null; }
                }
            }
        }
        report.append("WEAR mixed iron/diamond: four activation combinations, both orientations; shared native carrier and independent seeded oracle match; stable ID order is research policy\n");
        ItemStack nativeBoot = api.boot("iron");
        nativeBoot.setDamageValue(nativeBoot.getMaxDamage() - 2);
        nativeRandom.setSeed(seed(0.5F, false)); nativeBoot.hurtAndBreak(4, player, EquipmentSlot.FEET);
        require(!nativeBoot.isEmpty(), "Native non-destructive broken control disappeared");
        for (ItemStack root : pair(player, api.boot("iron"), new ItemStack(Items.DIAMOND_BOOTS))) {
            root.setDamageValue(root.getMaxDamage() - 1); activeRoot = root;
            try { root.hurtAndBreak(4, player, EquipmentSlot.FEET); require(root.isEmpty(), "Mixed inherited native survival at break"); }
            finally { activeRoot = null; }
        }
        report.append("BREAK native iron survives exhaustion; both mixed orientations still disappear through ordinary hurtAndBreak\n");
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
    private static Object effect(Object instance, String name) throws Exception {
        require(instance != null, "Missing native trait instance for " + name);
        return ((List<?>) traitOf(instance).getClass().getMethod("getEffects").invoke(traitOf(instance))).stream()
                .filter(effect -> effect.getClass().getSimpleName().equals(name)).findFirst().orElseThrow();
    }
    private static float chance(Object effect) throws Exception {
        var field = effect.getClass().getDeclaredField("activationChance"); field.setAccessible(true); return field.getFloat(effect);
    }
    private static long seed(float chance, boolean activate) {
        for (long seed = 0; seed < 100_000; seed++) { if ((new Random(seed).nextDouble() < chance) == activate) { return seed; } }
        throw new AssertionError("No bounded probability control");
    }
    private static void tickEffect(Object effect, ServerPlayer player, Object instance, ItemStack stack, boolean selected) throws Exception {
        EFFECT.getMethod("inventoryTick", CONTEXT, Level.class, net.minecraft.world.entity.Entity.class, boolean.class)
                .invoke(effect, context(player, instance, stack), player.level(), player, selected);
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
