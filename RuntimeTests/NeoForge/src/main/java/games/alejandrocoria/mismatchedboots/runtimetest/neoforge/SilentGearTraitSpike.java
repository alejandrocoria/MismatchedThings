package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/** Research-only native contexts and local consumer hooks; never an installed compatibility adapter. */
public final class SilentGearTraitSpike {
    private static NeoForgeSilentGearStatsTest.NativeApi api;
    private static boolean active;
    private static int setQueries;
    private static int recalculations;
    private static final Class<?> TRAIT = nativeClass("gear.trait.Trait");
    private static final Class<?> INSTANCE = nativeClass("api.traits.TraitInstance");
    private static final Class<?> CONTEXT = nativeClass("api.traits.TraitActionContext");
    private static final Class<?> EFFECT = nativeClass("api.traits.TraitEffect");

    private SilentGearTraitSpike() {}

    /** Share native fixture helpers without requiring execution of the unrelated wearer proof. */
    static void initialize(NeoForgeSilentGearStatsTest.NativeApi nativeApi) { api = nativeApi; }

    static void run(ServerPlayer player, NeoForgeSilentGearStatsTest.NativeApi nativeApi, StringBuilder report) throws Exception {
        initialize(nativeApi);
        report.append("\nTRAIT REUSE SPIKE (scoped research hooks, not production)\n");
        numericControls(player, report);
        ItemStack gold = api.boot("gold"), stellar = api.boot("refined_iron"), moon = api.boot("azure_silver");
        for (ItemStack root : pair(player, gold, stellar)) {
            ItemStack before = root.copy();
            var traits = view(root);
            require(level(traits.get("silentgear:brilliant")) > 0 && level(traits.get("silentgear:stellar")) > 0,
                    "Cross-source view lost a native trait");
            ItemStack carrier = carrier(root, traits);
            require(carrier.getItem().makesPiglinsNeutral(carrier, player)
                    && !root.getItem().makesPiglinsNeutral(root, player), "Brilliant reuse/native root control differs");
            require(ItemStack.matches(before, root), "Brilliant changed root/sources");
        }
        report.append("Brilliant: both cross-source orientations; native carrier callback=true, production root=false; unchanged roots/sources\n");

        Method fall = nativeClass("event.GearEvents").getMethod("onLivingFall", LivingFallEvent.class);
        for (ItemStack root : pair(player, moon, new ItemStack(Items.DIAMOND_BOOTS))) {
            clear(player); player.setItemSlot(EquipmentSlot.FEET, root);
            ItemStack before = root.copy();
            int nativeLevel = api.traitLevel(moon, "moonwalker");
            var baseline = new LivingFallEvent(player, 10, 1);
            fall.invoke(null, baseline);
            close(baseline.getDistance(), 10, "Unadapted mixed fall control");
            active = true;
            try {
                var candidate = new LivingFallEvent(player, 10, 1);
                fall.invoke(null, candidate);
                close(candidate.getDistance(), 10 * (1 + nativeLevel * -0.15F), "Native Moonwalker event");
                require(!candidate.isCanceled() && ItemStack.matches(before, root), "Moonwalker mutated root or ran Bounce");
                report.append("Moonwalker native event: level=").append(nativeLevel).append(" distance=")
                        .append(candidate.getDistance()).append("; unadapted=10; native handler reused\n");
            } finally { active = false; }
        }
        clear(player); player.setItemSlot(EquipmentSlot.FEET, moon);
        var nativeFall = new LivingFallEvent(player, 10, 1); fall.invoke(null, nativeFall);
        close(nativeFall.getDistance(), 10 * (1 + api.traitLevel(moon, "moonwalker") * -0.15F), "Native fall positive control");
        ItemStack iron = api.boot("iron");
        int lowerLevel = api.traitLevel(gold, "malleable"), higherLevel = api.traitLevel(iron, "malleable");
        require(lowerLevel > 0 && lowerLevel < higherLevel, "Native unequal-level control did not differ");
        for (ItemStack duplicate : pair(player, gold, iron)) {
            require(level(view(duplicate).get("silentgear:malleable")) == higherLevel, "Unequal native levels did not select max");
        }
        report.append("Native Malleable levels ").append(lowerLevel).append(" / ").append(higherLevel)
                .append(" select max, not sum, in both mixed orientations; no wear callback executed\n");

        ItemStack chest = api.armor("chestplate", "chestplate_plates", "refined_iron");
        ItemStack helmet = api.armor("helmet", "helmet_plates", "refined_iron");
        ItemStack legs = api.armor("leggings", "legging_plates", "refined_iron");
        // Actual native callback from a different slot must also see mixed FEET.
        for (ItemStack root : pair(player, stellar, new ItemStack(Items.DIAMOND_BOOTS))) {
            setCase(player, root, chest, helmet, legs, false, false, 3, false, report);
            setCase(player, root, chest, helmet, legs, true, false, 4, false, report);
            setCase(player, root, chest, helmet, legs, true, true, 4, false, report);
            setCase(player, root, chest, ItemStack.EMPTY, ItemStack.EMPTY, true, true, 2, false, report);
            setCase(player, root, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, true, true, 1, false, report);
            setCase(player, root, chest, helmet, legs, true, true, 4, true, report);
        }
        for (ItemStack root : pair(player, stellar, stellar)) {
            require(level(view(root).get("silentgear:stellar")) == api.traitLevel(stellar, "stellar"), "Duplicate levels were added");
            setCase(player, root, chest, ItemStack.EMPTY, ItemStack.EMPTY, true, true, 2, false, report);
        }
        clear(player); player.setItemSlot(EquipmentSlot.FEET, stellar);
        player.setItemSlot(EquipmentSlot.CHEST, chest); player.setItemSlot(EquipmentSlot.HEAD, helmet); player.setItemSlot(EquipmentSlot.LEGS, legs);
        dispatch(player, chest, view(chest).get("silentgear:stellar"), chest.copy(), true);
        require(player.getEffect(MobEffects.SPEED).getAmplifier() == 2
                && player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() == 3, "Native full-set positive control differs");

        // Mixed context is genuinely not accepted by the unchanged native Wielder callback.
        ItemStack root = pair(player, stellar, new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        Object instance = view(root).get("silentgear:stellar"), effect = wielder(instance);
        Method tick = EFFECT.getMethod("inventoryTick", CONTEXT, Level.class, Entity.class, boolean.class);
        boolean refusedCast = false;
        try { tick.invoke(effect, context(player, instance, root), player.level(), player, true); }
        catch (InvocationTargetException failure) { refusedCast = failure.getCause() instanceof ClassCastException; }
        require(refusedCast, "Native Wielder unexpectedly accepted the mixed item context");
        report.append("Native Wielder(root) refuses with ClassCastException; ephemeral native carrier + local set count succeeds\n");

        // Calibrate the observer, then prove routed calls never enter native recalculation.
        active = true;
        try {
            boolean observed = false;
            try { nativeClass("util.GearData").getMethod("recalculateGearData", ItemStack.class, Player.class).invoke(null, stellar.copy(), player); }
            catch (InvocationTargetException failure) { observed = failure.getCause() instanceof AssertionError; }
            require(observed && recalculations == 1, "Native recalculation witness did not run");
        } finally { active = false; }
        recalculations = 0;
        setCase(player, root, chest, helmet, legs, true, true, 4, false, report);
        require(setQueries > 0 && recalculations == 0, "Native consumer was not exercised or recalculated gear");
        clear(player);
        report.append("Stellar dispatch: native Wielder only; native SelfRepair explicitly refused; no root/carrier/source changes, zero native recalculations\n")
                .append("Scope does not establish safe Renew/Bounce-wear/oxidation/damage-property mutation or production guards\n");
    }

    /** Scoped replacement of this one private native counter, preserving native checks outside FEET. */
    public static Integer setCount(Object context, Object type, Player player) throws Exception {
        if (!active || type != ((Supplier<?>) nativeClass("setup.gear.GearTypes").getField("ARMOR").get(null)).get()) { return null; }
        Object trait = CONTEXT.getMethod("trait").invoke(context);
        int count = 0;
        Class<?> armor = nativeClass("api.item.GearArmor");
        for (EquipmentSlot slot : EquipmentSlotGroup.ARMOR) {
            ItemStack stack = player.getItemBySlot(slot);
            boolean has;
            if (slot == EquipmentSlot.FEET && ModContent.getMixedBootsData(stack).isPresent()) {
                has = view(stack).values().stream().anyMatch(instance -> traitOf(instance) == trait);
            } else {
                has = armor.isInstance(stack.getItem())
                        && (boolean) api.helper.getMethod("hasTrait", ItemInstance.class, TRAIT).invoke(null, stack, trait);
            }
            if (has) { count++; }
        }
        setQueries++;
        return count;
    }

    /** Delegate native armor/Curios first; add only mixed FEET's Moonwalker contribution. */
    public static int fallLevel(Player player, Object resource, int original) throws Exception {
        if (!active || !resource.getClass().getMethod("getId").invoke(resource).toString().equals("silentgear:moonwalker")) { return original; }
        return Math.max(original, level(view(player.getItemBySlot(EquipmentSlot.FEET)).get("silentgear:moonwalker")));
    }

    public static void recalculation(ItemStack stack) {
        if (active) {
            recalculations++;
            throw new AssertionError("Research pure-effect scope entered native recalculation: " + stack.getItem());
        }
    }

    private static void setCase(ServerPlayer player, ItemStack root, ItemStack chest, ItemStack head, ItemStack legs,
                                boolean candidate, boolean mixedContext, int count, boolean unselected, StringBuilder report) throws Exception {
        clear(player); player.tickCount = 20;
        root = root.copy(); root.setDamageValue(30);
        player.setItemSlot(EquipmentSlot.FEET, root); player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.setItemSlot(EquipmentSlot.HEAD, head); player.setItemSlot(EquipmentSlot.LEGS, legs);
        var before = new ArrayList<ItemStack>();
        for (EquipmentSlot slot : EquipmentSlotGroup.ARMOR) { before.add(player.getItemBySlot(slot).copy()); }
        Object instance = view(mixedContext ? root : chest).get("silentgear:stellar");
        ItemStack carrier = mixedContext ? carrier(root, view(root)) : chest.copy();
        ItemStack carrierBefore = carrier.copy();
        active = candidate;
        try {
            int blocked = dispatch(player, mixedContext ? root : chest, instance, carrier, !unselected);
            require(blocked == 1, "Stellar's self-repair was not refused independently");
            int speed = unselected ? 0 : Math.max(0, count - 1), jump = unselected ? 0 : count;
            require((player.getEffect(MobEffects.SPEED) == null ? 0 : player.getEffect(MobEffects.SPEED).getAmplifier() + 1) == speed,
                    "Stellar speed count differs: " + count);
            require((player.getEffect(MobEffects.JUMP_BOOST) == null ? 0 : player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() + 1) == jump,
                    "Stellar jump count differs: " + count);
            int index = 0;
            for (EquipmentSlot slot : EquipmentSlotGroup.ARMOR) { require(ItemStack.matches(before.get(index++), player.getItemBySlot(slot)), "Equipment/root/source mutation"); }
            require(ItemStack.matches(carrierBefore, carrier), "Pure native callback mutated carrier");
            report.append("Stellar: candidate=").append(candidate).append(" context=").append(mixedContext ? "mixed-view" : "native-chest")
                    .append(" pieces=").append(count).append(" selected=").append(!unselected)
                    .append(" speed=").append(speed).append(" jump=").append(jump).append(" refusedSelfRepair=1\n");
        } finally { active = false; }
    }

    /** Select concrete proven wearer effects, not the whole trait (Stellar mixes repair and potions). */
    private static int dispatch(ServerPlayer player, ItemStack root, Object instance, ItemStack carrier, boolean selected) throws Exception {
        Object ctx = context(player, instance, carrier);
        var effects = (List<?>) TRAIT.getMethod("getEffects").invoke(traitOf(instance));
        int refused = 0, called = 0;
        for (Object effect : effects) {
            if (!effect.getClass().getName().equals("net.silentchaos512.gear.gear.trait.effect.WielderEffectTraitEffect")) { refused++; continue; }
            EFFECT.getMethod("inventoryTick", CONTEXT, Level.class, Entity.class, boolean.class).invoke(effect, ctx, player.level(), player, selected);
            called++;
        }
        require(called == 1 && root != carrier, "Missing native effect or original root was passed as carrier");
        return refused;
    }

    /** The sources' already-computed levels are merged by ID using max, only as a research policy. */
    static Map<String, Object> view(ItemStack stack) throws Exception {
        var result = new TreeMap<String, Object>();
        var mixed = ModContent.getMixedBootsData(stack);
        List<ItemStack> sources = mixed.isPresent() ? List.of(mixed.get().left().source(), mixed.get().right().source()) : List.of(stack);
        for (ItemStack source : sources) {
            for (Object instance : (List<?>) api.helper.getMethod("getTraits", ItemInstance.class).invoke(null, source)) {
                String id = INSTANCE.getMethod("getTraitId").invoke(instance).toString();
                if (level(instance) > level(result.get(id))) { result.put(id, instance); }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    static ItemStack carrier(ItemStack root, Map<String, Object> traits) throws Exception {
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        ItemStack source = api.gear.isInstance(data.left().source().getItem()) ? data.left().source() : data.right().source();
        require(api.gear.isInstance(source.getItem()), "Missing genuine native carrier");
        ItemStack carrier = source.copy();
        Object props = carrier.get(api.properties);
        var values = new java.util.HashMap<>((Map<Object, Object>) props.getClass().getMethod("properties").invoke(props));
        Object key = ((Supplier<?>) nativeClass("setup.gear.GearProperties").getField("TRAITS").get(null)).get();
        values.put(key, nativeClass("api.property.TraitListPropertyValue").getConstructor(List.class).newInstance(List.copyOf(traits.values())));
        carrier.set(api.properties, props.getClass().getConstructor(Map.class).newInstance(values));
        return carrier;
    }

    static Object context(Player player, Object instance, ItemStack stack) throws Exception {
        return CONTEXT.getConstructor(Player.class, INSTANCE, ItemStack.class).newInstance(player, instance, stack);
    }
    private static Object wielder(Object instance) throws Exception {
        return ((List<?>) TRAIT.getMethod("getEffects").invoke(traitOf(instance))).stream()
                .filter(effect -> effect.getClass().getSimpleName().equals("WielderEffectTraitEffect")).findFirst().orElseThrow();
    }
    static Object traitOf(Object instance) {
        try { return INSTANCE.getMethod("getTrait").invoke(instance); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    static int level(Object instance) throws Exception {
        return instance == null ? 0 : (int) INSTANCE.getMethod("getLevel").invoke(instance);
    }
    private static List<ItemStack> pair(ServerPlayer player, ItemStack first, ItemStack second) {
        return NeoForgeSilentGearStatsTest.workbench(player, first, second);
    }
    static Class<?> nativeClass(String name) {
        try { return Class.forName("net.silentchaos512.gear." + name); }
        catch (ClassNotFoundException failure) { throw new AssertionError(failure); }
    }
    private static void clear(ServerPlayer player) {
        player.removeAllEffects();
        for (EquipmentSlot slot : List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) { player.setItemSlot(slot, ItemStack.EMPTY); }
        player.tickCount = 20;
    }

    private static void numericControls(ServerPlayer player, StringBuilder report) throws Exception {
        Method update = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates"); update.setAccessible(true);
        for (String[] sample : List.of(new String[]{"azure_electrum", "movement_speed"},
                new String[]{"azure_silver", "gravity"}, new String[]{"feather", "safe_fall_distance"})) {
            ItemStack nativeBoot = api.boot(sample[0]);
            var attribute = BuiltInRegistries.ATTRIBUTE.get(Identifier.withDefaultNamespace(sample[1])).orElseThrow();
            var nativeEntries = nativeBoot.getAttributeModifiers().modifiers().stream()
                    .filter(entry -> entry.attribute().equals(attribute) && entry.slot().test(EquipmentSlot.FEET)).toList();
            // Upstream builds trait entries twice. Preserve this finding instead of pretending the source was normalized.
            require(nativeEntries.size() == 2 && nativeEntries.getFirst().equals(nativeEntries.getLast()),
                    "Pinned native duplicate control changed: " + sample[1] + "; entries=" + nativeEntries);
            var nativeEntry = nativeEntries.getFirst();
            require(nativeEntry.modifier().amount() != 0, "Zero native attribute control");
            var subjects = new ArrayList<ItemStack>(); subjects.add(nativeBoot);
            subjects.addAll(pair(player, nativeBoot, new ItemStack(Items.DIAMOND_BOOTS)));
            for (int index = 0; index < subjects.size(); index++) {
                ItemStack subject = subjects.get(index), before = subject.copy();
                clear(player); update.invoke(player);
                double base = player.getAttributeValue(attribute), expectedAmount = nativeEntry.modifier().amount() / (index == 0 ? 1 : 2);
                var entries = subject.getAttributeModifiers().modifiers().stream()
                        .filter(entry -> entry.attribute().equals(attribute) && entry.slot().test(EquipmentSlot.FEET)).toList();
                require(entries.size() == (index == 0 ? 2 : 1), "Normalized production modifier count differs");
                for (var entry : entries) {
                    require(entry.modifier().operation() == nativeEntry.modifier().operation(), "Modifier operation differs");
                    close(entry.modifier().amount(), expectedAmount, "Normalized trait amount");
                }
                double expected = switch (nativeEntry.modifier().operation()) {
                    case ADD_VALUE -> base + expectedAmount;
                    case ADD_MULTIPLIED_BASE -> base * (1 + expectedAmount);
                    default -> throw new AssertionError("Unexpected sample operation");
                };
                player.setItemSlot(EquipmentSlot.FEET, subject); update.invoke(player);
                close(player.getAttributeValue(attribute), expected, "Equipped trait attribute");
                require(ItemStack.matches(before, subject), "Equipment update changed source/root");
                report.append("TRAIT ATTRIBUTE ").append(sample[1]).append(" subject=").append(index)
                        .append(" operation=").append(nativeEntry.modifier().operation()).append(" amount=").append(expectedAmount)
                        .append(" rawFeetEntries=").append(entries.size()).append(" equipped=").append(expected)
                        .append(" native duplicates apply once; both production outputs compose normalized contributions\n");
            }
        }
        clear(player); update.invoke(player);
    }

    private static void close(double actual, double expected, String label) {
        require(Math.abs(actual - expected) < 1E-6, label + ": " + actual + " != " + expected);
    }
    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
