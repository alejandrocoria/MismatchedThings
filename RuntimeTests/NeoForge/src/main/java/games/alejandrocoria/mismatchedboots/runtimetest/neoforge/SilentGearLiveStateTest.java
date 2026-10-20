package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireAttributesCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearState;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.platform.Services;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.nativeClass;

/** Real item callbacks with independent native working stacks, never the production projection as oracle. */
public final class SilentGearLiveStateTest {
    private static boolean observing;
    private static int recalculations;
    private static final Identifier ARMOR = Identifier.withDefaultNamespace("armor");
    private static final Identifier ARMOR_ID = Identifier.withDefaultNamespace("armor.boots");
    private SilentGearLiveStateTest() {}

    public static void recalculation(ItemStack stack) {
        if (!observing) { return; }
        require(ModContent.getMixedBootsData(stack).isEmpty(), "Production C recalculated a mixed root");
        recalculations++;
    }

    static void run(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report, String variant) throws Exception {
        player.tickCount = 21;
        if (variant.equals("state")) { refusal(player, api, report); return; }
        if (variant.equals("joint")) { joint(player, api, report); return; }
        dynamic(player, api, report);
        oxidation(player, api, report);
    }

    /** Accelerate only the fixture's counters; native ticks/recalculation decide the phase and level. */
    static void fullNativeOxidation(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Exception {
        ItemStack nativeBoot = api.boot("copper");
        var stage = NeoForgeSilentGearTest.NativeApi.component("OXIDATION_STAGE");
        var counter = NeoForgeSilentGearTest.NativeApi.component("OXIDATION_COUNTER");
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


    private static void dynamic(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Exception {
        for (String material : List.of("bone", "quartz")) {
            ItemStack source = api.boot(material);
            ItemStack direct = source.copy(); double nativeHealthy = armor(direct);
            direct.hurtAndBreak(direct.getMaxDamage() / 2, player, EquipmentSlot.FEET);
            close(armor(direct), nativeHealthy, "Native ordinary wear control unexpectedly refreshed");
            ItemStack helper = source.copy();
            nativeClass("util.GearHelper").getMethod("attemptDamage", ItemStack.class, int.class, LivingEntity.class, EquipmentSlot.class)
                    .invoke(null, helper, helper.getMaxDamage() / 2, player, EquipmentSlot.FEET);
            require(Math.abs(armor(helper) - nativeHealthy) > 1E-5, "Native helper refresh control missing");
            for (AttributePolicy policy : List.of(AttributePolicy.AVERAGE, AttributePolicy.MAXIMUM, AttributePolicy.SEPARATE)) {
                var rules = rules(policy);
                var pair = MixedBootsFactory.createPair(source, new ItemStack(Items.DIAMOND_BOOTS), rules, ComponentRulesLifecycle.serverRules());
                for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
                    ItemStack before = root.copy();
                    unchangedTicks(player, root);
                    player.setItemSlot(EquipmentSlot.FEET, root); equipped(player, root);
                    root.hurtAndBreak(root.getMaxDamage() / 2, player, EquipmentSlot.FEET);
                    double stale = armor(root);
                    tick(player, root, 21, EquipmentSlot.FEET);
                    double expected = expectedArmor(root, api, player, policy);
                    close(armor(root), expected, "Configured live armor");
                    equipped(player, root);
                    var data = ModContent.getMixedBootsData(root).orElseThrow();
                    if (policy == AttributePolicy.AVERAGE || policy == AttributePolicy.SEPARATE && api.gear.isInstance(data.left().source().getItem())) {
                        require(Math.abs(stale - expected) > 1E-5, "Live dynamic armor did not change");
                    }
                    unchangedTicks(player, root);
                    int prior = root.getDamageValue();
                    root.enchant(player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
                    player.setItemSlot(EquipmentSlot.FEET, root);
                    player.takeXpDelay = 0; // Synchronous cases do not wait out the prior orb's pickup cooldown.
                    var orb = new ExperienceOrb(EntityType.EXPERIENCE_ORB, player.level()); orb.setValue(5); orb.playerTouch(player);
                    require(root.getDamageValue() < prior, "Real Mending control did not repair");
                    tick(player, root, 21, EquipmentSlot.FEET);
                    close(armor(root), expectedArmor(root, api, player, policy), "Mending armor refresh");
                    equipped(player, root);
                    player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                    equipped(player, ItemStack.EMPTY);
                    if (before.has(DataComponents.ENCHANTMENTS)) { root.set(DataComponents.ENCHANTMENTS, before.get(DataComponents.ENCHANTMENTS)); }
                    else { root.remove(DataComponents.ENCHANTMENTS); }
                    protectedFields(before, root);
                    root.setDamageValue(0); tick(player, root, 21, null);
                    close(armor(root), armor(before), "Healthy armor restore");
                    int maximum = root.getMaxDamage(); root.set(DataComponents.MAX_DAMAGE, maximum + 100);
                    root.setDamageValue(maximum / 2); tick(player, root, 21, null);
                    close(armor(root), expectedArmor(root, api, player, policy), "Changed root maximum proportion");
                    var transformedSources = ModContent.getMixedBootsData(root).orElseThrow();
                    var refreshed = MixedBootsRecomposer.recompose(root, transformedSources.left().source(), transformedSources.right().source());
                    close(armor(refreshed), expectedArmor(refreshed, api, player, AttributePolicy.AVERAGE), "Recomposition did not capture its newly supplied rules");
                    overrides(player, root);
                }
            }
        }
        definitionChange(player, api);
        report.append("PRODUCTION C armor: bone/quartz native ordinary/helper controls; both orientations; average/maximum/separate; actual wear and XP Mending; max changes; selective overrides/recomposition; unchanged ticks/queries do no recalculation or writes\n");
    }

    @SuppressWarnings("unchecked")
    private static void definitionChange(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api) throws Exception {
        SilentGearTraitSpike.initialize(api);
        ItemStack source = api.boot("bone");
        ItemStack root = NeoForgeSilentGearTest.workbench(player, source, new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        root.setDamageValue(root.getMaxDamage() / 2); tick(player, root, 21, null);
        Object trait = SilentGearTraitSpike.traitOf(SilentGearTraitSpike.view(source).get("silentgear:chipping"));
        Object effect = ((List<?>) trait.getClass().getMethod("getEffects").invoke(trait)).getFirst();
        var field = effect.getClass().getDeclaredField("mods"); field.setAccessible(true);
        var original = (Map<Object, Object>) field.get(effect);
        Object armor = ((java.util.function.Supplier<?>) nativeClass("setup.gear.GearProperties").getField("ARMOR").get(null)).get();
        Object replacement = original.get(armor).getClass().getConstructor(float.class, boolean.class, boolean.class).newInstance(-.1f, true, true);
        var changed = new java.util.LinkedHashMap<>(original); changed.put(armor, replacement);
        int prior = recalculations;
        try {
            field.set(effect, Map.copyOf(changed)); // Isolated native definition edit, not a full datapack-reload claim.
            tick(player, root, 21, null);
            require(recalculations > prior, "Changed native armor coefficient did not invalidate input sample");
            close(armor(root), expectedArmor(root, api, player, AttributePolicy.AVERAGE), "Changed native coefficient projection");
            unchangedTicks(player, root);
        } finally { field.set(effect, original); }
        tick(player, root, 21, null);
        close(armor(root), expectedArmor(root, api, player, AttributePolicy.AVERAGE), "Restored native definition projection");
    }

    private static void overrides(ServerPlayer player, ItemStack root) {
        var original = root.get(DataComponents.ATTRIBUTE_MODIFIERS);
        var own = original.modifiers().stream().filter(entry -> entry.attribute().equals(Attributes.ARMOR)).findFirst().orElseThrow();
        var custom = new ArrayList<>(original.modifiers().stream().filter(entry -> !entry.attribute().equals(Attributes.ARMOR)).toList());
        custom.add(new ItemAttributeModifiers.Entry(Attributes.LUCK, new AttributeModifier(Identifier.fromNamespaceAndPath("fixture", "unrelated"),
                .5, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.FEET));
        root.set(DataComponents.CUSTOM_NAME, Component.literal("keep custom state"));
        root.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(custom));
        root.setDamageValue(0); tick(player, root, 21, null);
        require(root.getAttributeModifiers().modifiers().stream().noneMatch(entry -> entry.attribute().equals(Attributes.ARMOR)), "Removed managed armor resurrected");
        require(root.get(SilentGearCompatibility.state()).overrides().contains(SilentGearState.Key.of(own)), "Missing persistent removal marker");
        // Even numerical coincidence must not reclaim an explicitly overridden group.
        custom.add(own); root.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(custom));
        root.setDamageValue(root.getMaxDamage() / 3); tick(player, root, 21, null);
        close(armor(root), own.modifier().amount(), "Explicit added/edit override was reclaimed");
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        ItemStack recomposed = MixedBootsRecomposer.recompose(root, data.left().source(), data.right().source());
        close(armor(recomposed), armor(root), "Recomposition forgot override");
        require(recomposed.getHoverName().equals(root.getHoverName()) && recomposed.getAttributeModifiers().modifiers().stream()
                .anyMatch(entry -> entry.modifier().id().getNamespace().equals("fixture")), "Unrelated metadata/attribute lost");
    }

    private static void oxidation(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Exception {
        var counter = NeoForgeSilentGearTest.NativeApi.component("OXIDATION_COUNTER");
        ItemStack copper = api.boot("copper"); copper.set(counter, 1199);
        ItemStack young = copper.copy(); young.set(counter, 10);
        ItemStack coated = (ItemStack) api.assemble.invoke(api.swapClass.getField("INSTANCE").get(null),
                CraftingInput.of(2, 1, List.of(copper.copy(), api.part("coating", "gold"))));
        ItemStack oxidized = copper.copy();
        tick(player, oxidized, 20, null); tick(player, oxidized, 21, null);
        ItemStack pending = copper.copy(); tick(player, pending, 20, null);
        require(pending.has(NeoForgeSilentGearTest.NativeApi.component("RECALCULATE_FLAG")), "Native pending seed fixture missing");
        for (ItemStack other : List.of(young, coated, oxidized, pending, new ItemStack(Items.DIAMOND_BOOTS))) {
            for (ItemStack root : NeoForgeSilentGearTest.workbench(player, copper, other)) {
                require(!root.has(NeoForgeSilentGearTest.NativeApi.component("RECALCULATE_FLAG")), "Root retained an ambiguous native pending flag");
                ItemStack before = root.copy(); var data = ModContent.getMixedBootsData(root).orElseThrow();
                ItemStack[] refs = {data.left().source(), data.right().source()};
                for (EquipmentSlot selected : List.of(EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
                    tick(player, root, 20, selected); require(ItemStack.matches(before, root), "Selected root advanced oxidation");
                }
                for (int step = 0; step < 4; step++) {
                    tick(player, root, 20, null);
                    for (ItemStack ref : refs) { if (api.gear.isInstance(ref.getItem())) { tick(player, ref, 20, null); tick(player, ref, 21, null); } }
                    assertLanes(root, refs, api);
                    if (step > 0) { continue; }
                    // Accelerated subsequent boundary, independently seeded in both root and native control.
                    var live = root.get(SilentGearCompatibility.state());
                    root.set(SilentGearCompatibility.state(), new SilentGearState(accelerate(live.left()), accelerate(live.right()),
                            live.rules(), live.projection(), live.overrides(), live.sample()));
                    for (ItemStack ref : refs) { if (api.traitLevel(ref, "oxidized") > 0) { ref.set(counter, 599); } }
                }
                protectedFields(before, root);
                unchangedTicks(player, root);
                var saved = root.get(SilentGearCompatibility.state());
                ItemStack repeat = MixedBootsRecomposer.recompose(root, data.left().source(), data.right().source());
                require(repeat.get(SilentGearCompatibility.state()).left().equals(saved.left())
                        && repeat.get(SilentGearCompatibility.state()).right().equals(saved.right()), "Recomposition reset settled phase/counters");
            }
        }
        ItemStack terminal = terminal(api.boot("copper"), api);
        for (ItemStack root : NeoForgeSilentGearTest.workbench(player, terminal, new ItemStack(Items.DIAMOND_BOOTS))) {
            ItemStack before = root.copy();
            tick(player, root, 20, null); tick(player, terminal, 20, null);
            require(ItemStack.matches(before, root), "Explicit terminal native phase advanced");
        }
        report.append("PRODUCTION C oxidation: native-only effect, selected hand/armor pause, idle cadence, unequal/coated/already-oxidized inverse lanes, accelerated repeat boundaries match independent native ticks including Oxidized I reset; immutable sources\n");
    }

    @SuppressWarnings("unchecked")
    private static ItemStack terminal(ItemStack source, NeoForgeSilentGearTest.NativeApi api) throws Exception {
        Object ref = api.resource.getMethod("trait", String.class).invoke(null, "silentgear:oxidized");
        Object definition = ((java.util.function.Supplier<?>) ref).get();
        Object instance = nativeClass("api.traits.TraitInstance").getMethod("of", nativeClass("gear.trait.Trait"), int.class, java.util.Collection.class)
                .invoke(null, definition, 3, List.of());
        Object props = source.get(api.properties);
        var map = new java.util.LinkedHashMap<>((Map<Object, Object>) props.getClass().getMethod("properties").invoke(props));
        Object key = ((java.util.function.Supplier<?>) nativeClass("setup.gear.GearProperties").getField("TRAITS").get(null)).get();
        map.put(key, nativeClass("api.property.TraitListPropertyValue").getConstructor(List.class).newInstance(List.of(instance)));
        source.set(api.properties, props.getClass().getConstructor(Map.class).newInstance(map));
        source.set(NeoForgeSilentGearTest.NativeApi.component("OXIDATION_STAGE"), (byte) 3);
        return source; // Explicit native terminal fixture, not a stock copper acquisition route.
    }

    private static Optional<SilentGearState.Oxidation> accelerate(Optional<SilentGearState.Oxidation> lane) {
        return lane.map(value -> value.phase().equals("oxidized") ? new SilentGearState.Oxidation(value.phase(), value.level(), value.stage(), 599) : value);
    }

    private static void assertLanes(ItemStack root, ItemStack[] refs, NeoForgeSilentGearTest.NativeApi api) throws Exception {
        var state = root.get(SilentGearCompatibility.state());
        var stage = NeoForgeSilentGearTest.NativeApi.component("OXIDATION_STAGE");
        var counter = NeoForgeSilentGearTest.NativeApi.component("OXIDATION_COUNTER");
        var lanes = List.of(state.left(), state.right());
        for (int i = 0; i < 2; i++) {
            if (lanes.get(i).isEmpty()) { continue; }
            var lane = lanes.get(i).orElseThrow();
            require(lane.stage() == ((Number) refs[i].getOrDefault(stage, (byte) 0)).intValue()
                    && lane.counter() == (int) refs[i].getOrDefault(counter, 0)
                    && lane.level() == api.traitLevel(refs[i], lane.phase()), "Production oxidation differs from native lane " + i + ": " + lane);
        }
    }

    private static void joint(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Exception {
        ItemStack jdt = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", "eclipsealloy_boots")));
        JustDireAttributeTestSupport.set(jdt, "stepheight_upgrade_installed", true);
        jdt.set(JustDireResourceTestSupport.energyComponent(), 30_000);
        for (ItemStack root : NeoForgeSilentGearTest.workbench(player, api.boot("bone"), jdt)) {
            require(root.has(JustDireAttributesCompatibility.projections()), "Joint root lacks native JDT table");
            ItemStack before = root.copy();
            for (int damage : List.of(root.getMaxDamage() / 2, 0)) {
                var previousTable = root.get(JustDireAttributesCompatibility.projections());
                root.setDamageValue(damage); tick(player, root, 21, EquipmentSlot.FEET);
                var table = root.get(JustDireAttributesCompatibility.projections());
                for (int i = 0; i < 9; i++) {
                    require(previousTable.states().get(i).modifiers().stream().filter(entry -> !entry.attribute().equals(Attributes.ARMOR)).toList()
                                    .equals(table.states().get(i).modifiers().stream().filter(entry -> !entry.attribute().equals(Attributes.ARMOR)).toList()),
                            "SG refresh rebalanced unrelated frozen JDT groups");
                }
                int afterRefresh = recalculations;
                for (int energy : List.of(0, 30_000)) { for (boolean step : List.of(false, true)) {
                    root.set(JustDireResourceTestSupport.energyComponent(), energy);
                    JustDireAttributeTestSupport.set(root, "stepheight_toggle", step);
                    var data = ModContent.getMixedBootsData(root).orElseThrow();
                    ItemStack a = data.left().source(), b = data.right().source();
                    for (ItemStack source : List.of(a, b)) {
                        if (api.gear.isInstance(source.getItem())) { source.setDamageValue((int) ((long) damage * source.getMaxDamage() / root.getMaxDamage())); recalc(source, player); }
                        else { source.set(JustDireResourceTestSupport.energyComponent(), energy); JustDireAttributeTestSupport.set(source, "stepheight_toggle", step); }
                    }
                    var expected = games.alejandrocoria.mismatchedboots.composition.AttributeComposer.composePrimary(
                            Services.PLATFORM.getCompositionAttributeModifiers(a), Services.PLATFORM.getCompositionAttributeModifiers(b), AttributeRulesLifecycle.serverRules());
                    var actual = root.getAttributeModifiers();
                    close(amount(actual, "armor"), amount(expected, "armor"), "SG+JDT shared armor");
                    close(amount(actual, "step_height"), amount(expected, "step_height"), "SG+JDT Step Assist");
                    require(actual.modifiers().stream().filter(entry -> entry.attribute().equals(Attributes.ARMOR) && entry.modifier().id().equals(ARMOR_ID)).count() == 1,
                            "Duplicate shared armor identity");
                    unchangedTicks(player, root);
                    require(recalculations == afterRefresh, "FE/Step/query caused SG recalculation");
                    require(root.getOrDefault(JustDireResourceTestSupport.energyComponent(), -1) == energy, "Projection spent FE");
                } }
            }
            var toggle = JustDireAttributeTestSupport.<Boolean>component("stepheight_toggle");
            if (before.has(toggle)) { root.set(toggle, before.get(toggle)); } else { root.remove(toggle); }
            root.set(JustDireResourceTestSupport.energyComponent(), before.get(JustDireResourceTestSupport.energyComponent()));
            protectedFields(before, root);
            var modifiers = new ArrayList<>(root.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
                    .filter(entry -> !entry.attribute().equals(Attributes.ARMOR)).toList());
            var baseEntry = root.get(JustDireAttributesCompatibility.projections()).states().getFirst().modifiers().stream()
                    .filter(entry -> entry.attribute().equals(Attributes.ARMOR)).findFirst().orElseThrow();
            modifiers.add(new ItemAttributeModifiers.Entry(baseEntry.attribute(), new AttributeModifier(ARMOR_ID, 23,
                    baseEntry.modifier().operation()), baseEntry.slot(), baseEntry.display()));
            root.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(modifiers)); tick(player, root, 21, null);
            modifiers.removeLast(); modifiers.add(baseEntry); // Deliberate later equality with JDT's baseline.
            root.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(modifiers));
            root.set(JustDireResourceTestSupport.energyComponent(), 30_000);
            close(armor(root), baseEntry.modifier().amount(), "JDT reclaimed SG's recorded override through numerical coincidence");
        }
        report.append("PRODUCTION C joint: both orientations, damaged/repaired bone + powered Eclipse; FE depleted/funded and Step off/on, independent native armor/Step arithmetic, one shared identity, no resource debit or FE-driven SG recalculation\n");
    }

    private static void refusal(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Exception {
        ItemStack copper = api.boot("copper"); copper.set(NeoForgeSilentGearTest.NativeApi.component("OXIDATION_COUNTER"), 1199);
        ItemStack root = NeoForgeSilentGearTest.workbench(player, copper, copper.copy()).getFirst();
        var live = root.get(SilentGearCompatibility.state());
        var invalid = new SilentGearState.Oxidation("polished", 1, 0, 1201);
        root.set(SilentGearCompatibility.state(), new SilentGearState(live.left(), Optional.of(invalid), live.rules(), live.projection(), live.overrides(), live.sample()));
        ItemStack before = root.copy();
        int prior = recalculations;
        for (int repeat = 0; repeat < 2; repeat++) { tick(player, root, 20, null); require(ItemStack.matches(before, root), "Refusal partially advanced first lane/root"); }
        require(recalculations > prior, "Refusal did not first compute the valid native lane transition");
        report.append("PRODUCTION C refusal: first native lane transition computed, second invalid threshold refused; repeated calls retain complete root/clocks/projection with one diagnostic\n");
        String property = "mismatchedboots.runtimetest.silentGearEffectVariant";
        String previous = System.getProperty(property, "");
        try { System.setProperty(property, ""); SilentGearEffectsTest.run(player, api, report); }
        finally { System.setProperty(property, previous); }
    }

    static double expectedArmor(ItemStack root, NeoForgeSilentGearTest.NativeApi api, ServerPlayer player, AttributePolicy policy) throws Exception {
        var data = ModContent.getMixedBootsData(root).orElseThrow(); ItemStack a = data.left().source(), b = data.right().source();
        for (ItemStack source : List.of(a, b)) { if (api.gear.isInstance(source.getItem())) {
            source.setDamageValue((int) ((long) root.getDamageValue() * source.getMaxDamage() / root.getMaxDamage())); recalc(source, player);
        } }
        return switch (policy) { case AVERAGE -> (armor(a) + armor(b)) / 2; case MAXIMUM -> Math.max(armor(a), armor(b)); case SEPARATE -> armor(a); default -> throw new AssertionError(); };
    }

    /** Representative independent writers for another feature's refusal gate, not a second state matrix. */
    static void smoke(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Exception {
        ItemStack root = NeoForgeSilentGearTest.workbench(player, api.boot("bone"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        root.setDamageValue(root.getMaxDamage() / 2); double stale = armor(root);
        tick(player, root, 21, EquipmentSlot.FEET);
        close(armor(root), expectedArmor(root, api, player, AttributePolicy.AVERAGE), "Independent armor refresh");
        require(Math.abs(stale - armor(root)) > 1E-5, "Armor refresh isolation had no dynamic control");
        ItemStack copper = api.boot("copper"); copper.set(NeoForgeSilentGearTest.NativeApi.component("OXIDATION_COUNTER"), 10);
        root = NeoForgeSilentGearTest.workbench(player, copper, new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        int counter = root.get(SilentGearCompatibility.state()).left().orElseThrow().counter();
        var sources = ModContent.getMixedBootsData(root).orElseThrow();
        tick(player, root, 20, null);
        require(root.get(SilentGearCompatibility.state()).left().orElseThrow().counter() == counter + 1
                && ModContent.getMixedBootsData(root).orElseThrow().equals(sources), "Independent oxidation clock/source isolation failed");
        report.append("Live-state isolation: bone armor refresh matches native computation; one copper clock advance, immutable sources\n");
    }
    private static void recalc(ItemStack source, Player player) throws Exception { nativeClass("util.GearData").getMethod("recalculateGearData", ItemStack.class, Player.class).invoke(null, source, player); }
    private static void equipped(ServerPlayer player, ItemStack root) throws Exception {
        var update = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates"); update.setAccessible(true); update.invoke(player);
        close(player.getAttributeValue(Attributes.ARMOR), player.getAttributeBaseValue(Attributes.ARMOR) + (root.isEmpty() ? 0 : armor(root)),
                "Ordinary equipment update did not expose live root armor");
    }
    static AttributeRuleSet rules(AttributePolicy policy) { var map = new java.util.LinkedHashMap<>(AttributeRulesLifecycle.serverRules().rules()); map.put(ARMOR, policy); return new AttributeRuleSet(AttributeRulesLifecycle.serverRules().fallback(), map); }
    private static double armor(ItemStack stack) { return amount(stack.getAttributeModifiers(), "armor"); }
    private static double amount(ItemAttributeModifiers modifiers, String name) { return modifiers.modifiers().stream().filter(entry -> entry.attribute().unwrapKey().orElseThrow().identifier().equals(Identifier.withDefaultNamespace(name))).mapToDouble(entry -> entry.modifier().amount()).sum(); }
    private static void tick(ServerPlayer player, ItemStack stack, int time, EquipmentSlot slot) { player.tickCount = time; observing = true; try { stack.inventoryTick(player.level(), player, slot); } finally { observing = false; } }
    private static void unchangedTicks(ServerPlayer player, ItemStack root) {
        ItemStack before = root.copy(); int prior = recalculations;
        for (int i = 0; i < 3; i++) { tick(player, root, 21, null); root.getAttributeModifiers(); }
        require(ItemStack.matches(before, root) && recalculations == prior, "Unchanged tick/query recalculated or wrote root");
    }
    private static void protectedFields(ItemStack before, ItemStack after) {
        ItemStack a = before.copy(), b = after.copy();
        for (var component : List.of(DataComponents.DAMAGE, DataComponents.ATTRIBUTE_MODIFIERS, SilentGearCompatibility.state(), JustDireAttributesCompatibility.projections())) { a.remove(component); b.remove(component); }
        require(ItemStack.matches(a, b), "C changed protected root/source fields");
    }
    private static void close(double actual, double expected, String label) { require(Math.abs(actual - expected) < 1E-5, label + ": " + actual + " != " + expected); }
    private static void require(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}
