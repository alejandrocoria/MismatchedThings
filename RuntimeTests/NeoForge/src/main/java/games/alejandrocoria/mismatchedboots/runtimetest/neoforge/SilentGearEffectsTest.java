package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCompatibility;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Random;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.level;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.nativeClass;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.traitOf;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.view;

/** Production inventory/wear dispatch, native controls and independent seeded probability oracle. */
public final class SilentGearEffectsTest {
    private static boolean observing;
    private static int recalculations;
    private static Random random;
    private SilentGearEffectsTest() {}

    static void run(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Exception {
        SilentGearTraitSpike.initialize(api);
        clear(player);
        var field = Class.forName("net.silentchaos512.lib.util.MathUtils").getDeclaredField("RANDOM");
        field.setAccessible(true); random = (Random) field.get(null);
        ItemStack calibration = api.boot("iron"); boolean witnessed = false;
        observing = true;
        try { nativeClass("util.GearData").getMethod("recalculateGearData", ItemStack.class, net.minecraft.world.entity.player.Player.class).invoke(null, calibration, player); }
        catch (java.lang.reflect.InvocationTargetException failure) { witnessed = failure.getCause() instanceof AssertionError && recalculations == 1; }
        finally { observing = false; }
        require(witnessed, "Production recalculation witness did not intercept its positive control"); recalculations = 0;
        String variant = System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", "");
        if (!variant.isEmpty()) {
            for (String name : List.of("SelfRepairTraitEffect", "DurabilityTraitEffect")) {
                require(Boolean.TRUE.equals(nativeClass("gear.trait.effect." + name).getMethod("sgtest$modified").invoke(null)), "Competing native modification was not installed");
            }
            if (!variant.equals("compatible")) { refusal(player, api, variant, report); return; }
        }
        report.append("\nPRODUCTION B: variant=").append(variant.isEmpty() ? "live" : variant).append('\n');
        for (String[] sample : List.of(new String[]{"amethyst", "renew"}, new String[]{"refined_iron", "stellar"})) {
            ItemStack source = api.boot(sample[0]);
            Object instance = view(source).get("silentgear:" + sample[1]);
            Object effect = effect(instance, "SelfRepairTraitEffect");
            float chance = chance(effect) * level(instance);
            for (boolean activate : List.of(true, false)) {
                long seed = seed(chance, activate);
                ItemStack nativeBoot = source.copy(); nativeBoot.setDamageValue(12);
                random.setSeed(seed); nativeBoot.inventoryTick(player.level(), player, null);
                require(nativeBoot.getDamageValue() == (activate ? 12 - level(instance) : 12), "Native repair amount/probability differs");
                for (ItemStack root : pair(player, source, new ItemStack(Items.DIAMOND_BOOTS))) {
                    root.setDamageValue(12); ItemStack before = root.copy();
                    random.setSeed(seed); observe(() -> root.inventoryTick(player.level(), player, null));
                    require(root.getDamageValue() == nativeBoot.getDamageValue(), "Production repair differs from native control");
                    unchanged(before, root);
                }
            }
            for (String mode : List.of("duplicate", "equipped", "healthy", "creative", "unbreakable", "wrong-tick", "no-state")) {
                ItemStack root = pair(player, source, mode.equals("duplicate") ? source : new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
                root.setDamageValue(mode.equals("healthy") ? 0 : 12);
                player.setGameMode(mode.equals("creative") ? GameType.CREATIVE : GameType.SURVIVAL);
                player.tickCount = mode.equals("wrong-tick") ? 21 : 20;
                if (mode.equals("unbreakable")) { root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE); }
                if (mode.equals("no-state")) { root.remove(SilentGearCompatibility.state()); }
                ItemStack before = root.copy();
                random.setSeed(seed(chance, true)); observe(() -> root.inventoryTick(player.level(), player,
                        mode.equals("equipped") ? EquipmentSlot.FEET : null));
                int expected = mode.equals("duplicate") || mode.equals("equipped") ? 12 - level(instance) : before.getDamageValue();
                require(root.getDamageValue() == expected, "Production repair eligibility/max-level differs: " + mode);
                unchanged(before, root);
                clear(player);
            }
            report.append("B REPAIR ").append(sample[1]).append(": native tick vs both mixed orientations, seeded success/failure; duplicate, equipped/inventory, healthy/creative/Unbreakable/cadence/absent state\n");
        }
        for (String[] sample : List.of(new String[]{"iron", "malleable"}, new String[]{"diamond", "brittle"},
                new String[]{"tyrian_steel", "sturdy"}, new String[]{"iron", "flexible"}, new String[]{"iron", "bending"})) {
            ItemStack source = api.boot(sample[0]);
            if (sample[1].equals("flexible")) {
                source = (ItemStack) api.assemble.invoke(api.swapClass.getField("INSTANCE").get(null),
                        net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(source, api.part("lining", "wool"))));
            }
            if (sample[1].equals("bending")) { source = bending(source, api); }
            Object instance = view(source).get("silentgear:" + sample[1]);
            Object effect = effect(instance, "DurabilityTraitEffect");
            int scale = sample[1].equals("brittle") || sample[1].equals("bending") ? 1 : -1;
            // Flexible lining also has Malleable: isolate the selected computed native instance for this definition control.
            if (sample[1].equals("flexible")) { source = only(source, instance, api); }
            for (boolean activate : List.of(true, false)) {
                long seed = seed(chance(effect) * level(instance), activate);
                ItemStack control = source.copy(); control.setDamageValue(10);
                random.setSeed(seed); control.hurtAndBreak(3, player, EquipmentSlot.FEET);
                require(control.getDamageValue() == 13 + (activate ? scale : 0), "Native wear control differs: " + sample[1]);
                for (ItemStack root : pair(player, source, new ItemStack(Items.DIAMOND_BOOTS))) {
                    root.setDamageValue(10); ItemStack before = root.copy(); random.setSeed(seed);
                    observe(() -> root.hurtAndBreak(3, player, EquipmentSlot.FEET));
                    require(root.getDamageValue() == control.getDamageValue(), "Actual mixed damageItem differs: " + sample[1]);
                    unchanged(before, root);
                }
            }
            report.append("B WEAR ").append(sample[1]).append(": native hurtAndBreak and both mixed orientations; success/failure\n");
        }
        combined(player, api, report);
        ItemStack renewSource = api.boot("amethyst"), stellarSource = api.boot("refined_iron");
        long bothRepair = 0;
        for (; bothRepair < 100_000; bothRepair++) {
            var oracle = new Random(bothRepair);
            if (oracle.nextDouble() < .072F && oracle.nextDouble() < .02F) { break; }
        }
        require(bothRepair < 100_000, "Missing two-effect repair seed");
        for (ItemStack root : pair(player, renewSource, stellarSource)) {
            root.setDamageValue(12); ItemStack before = root.copy(); random.setSeed(bothRepair);
            observe(() -> root.inventoryTick(player.level(), player, null));
            require(root.getDamageValue() == 7, "Distinct repair effects did not commit their one combined heal"); unchanged(before, root);
        }
        report.append("B REPAIR combined: Renew IV + Stellar I, both inverse roots heal five once, one post-callback commit\n");
        ordering(player, api, report);
        clear(player);
        require(recalculations == 0, "Production native gear recalculation was entered");
        report.append("B mutation witness: zero native recalculations during all production callbacks; sources/metadata immutable\n");
    }

    private static void combined(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Exception {
        for (ItemStack root : pair(player, api.boot("iron"), api.boot("diamond"))) {
            for (boolean brittle : List.of(false, true)) {
                for (boolean malleable : List.of(false, true)) {
                    long seed = 0;
                    for (; seed < 100_000; seed++) {
                        var oracle = new Random(seed);
                        if ((oracle.nextDouble() < .5) == brittle && (oracle.nextDouble() < .5) == malleable) { break; }
                    }
                    require(seed < 100_000, "Missing combined roll seed");
                    for (int incoming : List.of(1, 3)) {
                        root.setDamageValue(10); ItemStack before = root.copy(); random.setSeed(seed);
                        observe(() -> root.hurtAndBreak(incoming, player, EquipmentSlot.FEET));
                        require(root.getDamageValue() == 10 + incoming + (brittle ? 1 : 0) - (malleable ? 1 : 0), "Combined production wear/order differs");
                        unchanged(before, root);
                    }
                }
            }
        }
        report.append("B WEAR combined: four independent seeded outcomes, both orientations, one root debit\n");
    }

    private static void ordering(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Exception {
        ItemStack source = api.boot("iron");
        Object instance = view(source).get("silentgear:malleable"), effect = effect(instance, "DurabilityTraitEffect");
        long seed = seed(chance(effect) * level(instance), true);
        for (ItemStack root : pair(player, source, new ItemStack(Items.DIAMOND_BOOTS))) {
            root.setDamageValue(10); random.setSeed(seed); observe(() -> root.hurtAndBreak(1, player, EquipmentSlot.FEET));
            require(root.getDamageValue() == 10, "One-point wear was not reduced to zero");
            observe(() -> root.hurtAndBreak(0, player, EquipmentSlot.FEET));
            require(root.getDamageValue() == 10, "Zero wear changed damage");
            var unbreaking = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
            ItemStack ordinary = new ItemStack(Items.DIAMOND_BOOTS); ordinary.enchant(unbreaking, 3); ordinary.setDamageValue(10);
            root.enchant(unbreaking, 3);
            player.level().getRandom().setSeed(184); ordinary.hurtAndBreak(2, player, EquipmentSlot.FEET);
            player.level().getRandom().setSeed(184); random.setSeed(seed);
            observe(() -> root.hurtAndBreak(3, player, EquipmentSlot.FEET));
            require(root.getDamageValue() == ordinary.getDamageValue(), "Unbreaking did not process adjusted wear exactly once");
            root.remove(DataComponents.ENCHANTMENTS); root.setDamageValue(10);
            root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE); root.hurtAndBreak(3, player, EquipmentSlot.FEET);
            require(root.getDamageValue() == 10, "Unbreakable was bypassed"); root.remove(DataComponents.UNBREAKABLE);
            player.setGameMode(GameType.CREATIVE); root.hurtAndBreak(3, player, EquipmentSlot.FEET);
            require(root.getDamageValue() == 10, "Creative wear exemption was bypassed"); clear(player);
            root.setDamageValue(root.getMaxDamage() - 1); random.setSeed(seed);
            observe(() -> root.hurtAndBreak(4, player, EquipmentSlot.FEET));
            require(root.isEmpty(), "Mixed inherited native non-destructive broken state");
        }
        ItemStack nativeBroken = source.copy(); nativeBroken.setDamageValue(nativeBroken.getMaxDamage() - 1);
        random.setSeed(seed); nativeBroken.hurtAndBreak(4, player, EquipmentSlot.FEET);
        require(!nativeBroken.isEmpty(), "Native broken-survival positive control disappeared");
        ItemStack worn = pair(player, source, new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        // FakePlayer is sufficient for item callbacks, not a real connected-player damage control.
        player.level().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        ServerPlayer actor = NeoForgeJustDireNegateFallTest.player(player.level().getServer(), GameType.SURVIVAL);
        ItemStack ordinaryArmor = new ItemStack(Items.DIAMOND_BOOTS);
        actor.setItemSlot(EquipmentSlot.FEET, ordinaryArmor);
        var attack = actor.damageSources().mobAttack(new net.minecraft.world.entity.monster.zombie.Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, actor.level()));
        require(actor.hurtServer(actor.level(), attack, 12) && ordinaryArmor.getDamageValue() == 3, "Ordinary real armor-hit control differs");
        actor.invulnerableTime = 0; actor.setHealth(actor.getMaxHealth());
        actor.setItemSlot(EquipmentSlot.FEET, worn);
        random.setSeed(seed);
        observe(() -> require(actor.hurtServer(actor.level(), attack, 12), "Fixture player rejected real armor hit"));
        require(worn.getDamageValue() == 2, "Actual armor damage path differs: " + worn.getDamageValue());
        actor.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        clear(player);
        report.append("B lifecycle: zero/small wear, vanilla Unbreaking after native adjustment, creative/Unbreakable, normal mixed destruction vs native survival, actual armor hurt dispatch\n");
    }

    private static void refusal(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, String variant, StringBuilder report) throws Exception {
        ItemStack repairSource = api.boot("amethyst"), wearSource = api.boot("iron");
        Object renew = view(repairSource).get("silentgear:renew"), malleable = view(wearSource).get("silentgear:malleable");
        for (int repeat = 0; repeat < 2; repeat++) {
            ItemStack root = pair(player, repairSource, wearSource).getFirst(); root.setDamageValue(12);
            random.setSeed(seed(chance(effect(renew, "SelfRepairTraitEffect")) * level(renew), true));
            observe(() -> root.inventoryTick(player.level(), player, null));
            require(root.getDamageValue() == (variant.equals("repair") ? 12 : 12 - level(renew)), "Independent repair refusal differs");
            int before = root.getDamageValue(); random.setSeed(seed(chance(effect(malleable, "DurabilityTraitEffect")) * level(malleable), true));
            observe(() -> root.hurtAndBreak(3, player, EquipmentSlot.FEET));
            require(root.getDamageValue() == before + (variant.equals("wear") ? 3 : 2), "Independent wear refusal differs");
        }
        report.append("PRODUCTION B: variant=").append(variant).append("; refused local effect twice, other effect stays functional, no native recalculation\n");
    }

    public static void ignoredNativeRepair(ItemStack stack, int amount, LivingEntity entity, InteractionHand hand) {}
    public static void recalculation(ItemStack stack) {
        if (observing) { recalculations++; throw new AssertionError("Production B entered native gear recalculation"); }
    }
    private static void observe(Runnable action) { observing = true; try { action.run(); } finally { observing = false; } }
    private static Object effect(Object instance, String type) throws Exception {
        return ((List<?>) traitOf(instance).getClass().getMethod("getEffects").invoke(traitOf(instance))).stream()
                .filter(value -> value.getClass().getSimpleName().equals(type)).findFirst().orElseThrow();
    }
    private static float chance(Object effect) throws Exception {
        var field = effect.getClass().getDeclaredField("activationChance"); field.setAccessible(true); return field.getFloat(effect);
    }
    private static long seed(float chance, boolean activate) {
        for (long seed = 0; seed < 100_000; seed++) { if ((new Random(seed).nextDouble() < chance) == activate) { return seed; } }
        throw new AssertionError("No bounded native roll control");
    }
    private static ItemStack bending(ItemStack source, NeoForgeSilentGearTest.NativeApi api) throws Exception {
        var ref = api.resource.getMethod("trait", String.class).invoke(null, "silentgear:bending");
        Class<?> conditions = nativeClass("api.traits.ITraitCondition");
        Object instance = nativeClass("api.traits.TraitInstance").getMethod("of", api.resource, int.class, Array.newInstance(conditions, 0).getClass())
                .invoke(null, ref, 1, Array.newInstance(conditions, 0));
        return only(source, instance, api);
    }
    @SuppressWarnings("unchecked")
    private static ItemStack only(ItemStack source, Object instance, NeoForgeSilentGearTest.NativeApi api) throws Exception {
        ItemStack copy = source.copy(); Object props = copy.get(api.properties);
        var values = new java.util.HashMap<>((java.util.Map<Object, Object>) props.getClass().getMethod("properties").invoke(props));
        Object key = ((java.util.function.Supplier<?>) nativeClass("setup.gear.GearProperties").getField("TRAITS").get(null)).get();
        values.put(key, nativeClass("api.property.TraitListPropertyValue").getConstructor(List.class).newInstance(List.of(instance)));
        copy.set(api.properties, props.getClass().getConstructor(java.util.Map.class).newInstance(values));
        return copy;
    }
    private static List<ItemStack> pair(ServerPlayer player, ItemStack first, ItemStack second) { return NeoForgeSilentGearTest.workbench(player, first, second); }
    private static void unchanged(ItemStack before, ItemStack after) {
        var a = before.copy(); var b = after.copy(); a.remove(DataComponents.DAMAGE); b.remove(DataComponents.DAMAGE);
        require(ItemStack.matches(a, b), "Production B changed sources or unrelated root metadata");
    }
    private static void clear(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL); player.removeAllEffects(); player.tickCount = 20;
        for (EquipmentSlot slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
