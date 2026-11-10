package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearState;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsRecomposer;
import games.alejandrocoria.mismatchedleggings.composition.NeoForgeCompositionAttributes;
import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedthings.api.composition.AttributeMerger;
import games.alejandrocoria.mismatchedthings.api.composition.AttributePolicy;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.neoforged.fml.ModList;
import net.silentchaos512.gear.api.property.TraitListPropertyValue;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.core.component.GearPropertiesData;
import net.silentchaos512.gear.setup.SgDataComponents;
import net.silentchaos512.gear.setup.gear.GearProperties;
import net.silentchaos512.gear.util.GearData;
import net.silentchaos512.gear.util.TraitHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real product callbacks, independent native controls and accelerated (fixture-only) oxidation clocks. */
public final class LiveStateChecks {
    private static boolean observing;
    private static int recalculations;

    private LiveStateChecks() {}

    public static void recalculation(ItemStack stack) {
        if (observing) {
            require(!MixedLeggingsStructure.isMixedItem(stack), "C recalculated a live mixed root");
            recalculations++;
        }
    }

    static void run(ServerPlayer player, StringBuilder report) throws Exception {
        player.tickCount = 21;
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
        dynamic(player, report);
        oxidation(player, report);
        ownership(player, report);
        if (ModList.get().isLoaded("mismatchedboots")) { sibling(player, report); }
        if (!System.getProperty("leggings.test.sgLiveVariant", "").isEmpty()) { refusal(player, report); }
    }

    private static void dynamic(ServerPlayer player, StringBuilder report) throws Exception {
        for (String material : List.of("bone", "quartz")) {
            var source = FoundationChecks.armor(material);
            double healthy = amount(source.getAttributeModifiers());
            var nativeControl = source.copy();
            nativeControl.hurtAndBreak(nativeControl.getMaxDamage() / 2, player, EquipmentSlot.LEGS);
            close(amount(nativeControl.getAttributeModifiers()), healthy, "Native ordinary wear staleness");
            GearData.recalculateGearData(nativeControl, player);
            require(Math.abs(amount(nativeControl.getAttributeModifiers()) - healthy) > 1E-5, "Native explicit refresh control missing");
            for (var policy : List.of(AttributePolicy.AVERAGE, AttributePolicy.MAXIMUM, AttributePolicy.SEPARATE)) {
                var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.DIAMOND_LEGGINGS), rules(policy));
                for (var root : List.of(pair.primary(), pair.inverse())) {
                    unchanged(player, root);
                    var protectedRoot = root.copy();
                    var unrelated = new ItemAttributeModifiers.Entry(Attributes.ARMOR_TOUGHNESS,
                            new AttributeModifier(Identifier.parse("test:owned-refresh"), 13, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.LEGS);
                    var entries = new ArrayList<>(root.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers());
                    entries.add(unrelated);
                    root.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(entries));
                    root.hurtAndBreak(root.getMaxDamage() / 2, player, EquipmentSlot.LEGS);
                    double stale = amount(root.getAttributeModifiers());
                    tick(player, root, 21, EquipmentSlot.LEGS);
                    close(amount(root.getAttributeModifiers()), expected(root), "Configured live wear projection " + material + '/' + policy);
                    require(root.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().contains(unrelated), "Owned SG refresh replaced unrelated modifier");
                    if (policy == AttributePolicy.AVERAGE) { require(Math.abs(stale - expected(root)) > 1E-5, "Wear failed to refresh"); }
                    equipped(player, root);
                    unchanged(player, root);
                    // Real XP-orb Mending changes only live root wear, then the next tick restores armor.
                    root.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
                    player.setItemSlot(EquipmentSlot.LEGS, root);
                    player.takeXpDelay = 0;
                    var orb = new ExperienceOrb(EntityType.EXPERIENCE_ORB, player.level());
                    orb.setValue(5);
                    int damage = root.getDamageValue();
                    orb.playerTouch(player);
                    require(root.getDamageValue() < damage, "Mending did not repair root");
                    tick(player, root, 21, EquipmentSlot.LEGS);
                    close(amount(root.getAttributeModifiers()), expected(root), "Mending next-tick armor");
                    root.set(DataComponents.ENCHANTMENTS, protectedRoot.get(DataComponents.ENCHANTMENTS));
                    protectedFields(protectedRoot, root);
                    root.set(DataComponents.MAX_DAMAGE, root.getMaxDamage() + 100);
                    tick(player, root, 21, EquipmentSlot.LEGS);
                    close(amount(root.getAttributeModifiers()), expected(root), "Maximum change proportion");
                    var previousRules = CompositionRuleState.server();
                    try {
                        CompositionRuleState.installServer(rules(AttributePolicy.COMBINE));
                        var unchanged = root.copy();
                        tick(player, root, 21, EquipmentSlot.LEGS);
                        require(ItemStack.matches(unchanged, root), "Global rules changed saved root policy");
                        var data = MixedLeggingsStructure.validData(root).orElseThrow();
                        var rebuilt = MixedLeggingsRecomposer.recompose(root, data.left().source(), data.right().source());
                        require(rebuilt.get(SilentGearCompatibility.state()).armorPolicy() == AttributePolicy.COMBINE, "Recomposition did not capture supplied current policy");
                        close(amount(rebuilt.getAttributeModifiers()), expected(rebuilt), "Recomposition against final wear");
                    } finally { CompositionRuleState.installServer(previousRules); }
                    overrides(player, root);
                    root.remove(SilentGearCompatibility.state());
                    var noState = root.copy();
                    tick(player, root, 20, null);
                    require(ItemStack.matches(noState, root), "Missing state was reseeded on tick");
                    player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                }
                // Creation also uses FINAL composed wear rather than either historical source's damage ratio.
                source.setDamageValue(source.getMaxDamage() / 3);
                var wornPair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.DIAMOND_LEGGINGS), rules(policy));
                close(amount(wornPair.primary().getAttributeModifiers()), expected(wornPair.primary()), "Worn creation primary");
                close(amount(wornPair.inverse().getAttributeModifiers()), expected(wornPair.inverse()), "Worn creation inverse");
                source.setDamageValue(0);
            }
        }
        definitionChange(player);
        report.append("C ARMOR: native stale/explicit controls; bone/quartz both orientations, average/maximum/separate; final-wear creation/recomposition, real wear/Mending, max changes, equipped attributes, frozen policy, external overrides and read-only unchanged ticks/queries passed\n");
    }

    private static void overrides(ServerPlayer player, ItemStack original) {
        for (boolean remove : List.of(false, true)) {
            var root = original.copy();
            var entries = new ArrayList<>(root.getAttributeModifiers().modifiers().stream().filter(e -> !SilentGearState.managed(e)).toList());
            entries.add(new ItemAttributeModifiers.Entry(Attributes.ARMOR_TOUGHNESS,
                    new AttributeModifier(Identifier.parse("test:unrelated"), 17, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.LEGS));
            if (!remove) { entries.add(new ItemAttributeModifiers.Entry(Attributes.ARMOR,
                    new AttributeModifier(SilentGearState.MODIFIER, 99, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.LEGS)); }
            root.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(entries));
            root.set(DataComponents.CUSTOM_NAME, Component.literal("live external state"));
            var current = root.copy();
            root.setDamageValue(1);
            tick(player, root, 21, EquipmentSlot.LEGS);
            require(root.get(DataComponents.ATTRIBUTE_MODIFIERS).equals(current.get(DataComponents.ATTRIBUTE_MODIFIERS))
                    && root.get(SilentGearCompatibility.state()).armorOverridden(), "External managed edit/removal was reclaimed");
            var data = MixedLeggingsStructure.validData(root).orElseThrow();
            var rebuilt = MixedLeggingsRecomposer.recompose(root, data.left().source(), data.right().source());
            require(SilentGearState.Projection.capture(rebuilt.getAttributeModifiers()).equals(SilentGearState.Projection.capture(current.getAttributeModifiers())), "Recomposer resurrected overridden armor");
            root.set(DataComponents.ATTRIBUTE_MODIFIERS, original.get(DataComponents.ATTRIBUTE_MODIFIERS));
            tick(player, root, 21, EquipmentSlot.LEGS);
            require(root.get(SilentGearCompatibility.state()).armorOverridden(), "Later numeric coincidence reclaimed ownership");
            require(root.get(DataComponents.CUSTOM_NAME).equals(current.get(DataComponents.CUSTOM_NAME)), "External root component overwritten");
        }
    }

    @SuppressWarnings("unchecked")
    private static void definitionChange(ServerPlayer player) throws Exception {
        var source = FoundationChecks.armor("bone");
        var root = MixedLeggingsFactory.createPair(source, new ItemStack(Items.DIAMOND_LEGGINGS)).primary();
        root.setDamageValue(root.getMaxDamage() / 2);
        tick(player, root, 21, EquipmentSlot.LEGS);
        var effect = TraitHelper.getTraits(source).stream().filter(t -> t.getTraitId().getPath().equals("chipping")).findFirst().orElseThrow().getTrait().getEffects().getFirst();
        var field = effect.getClass().getDeclaredField("mods");
        field.setAccessible(true);
        var original = (Map<Object, Object>) field.get(effect);
        var replacement = original.get(GearProperties.ARMOR.get()).getClass().getConstructor(float.class, boolean.class, boolean.class).newInstance(-.1f, true, true);
        try {
            field.set(effect, Map.of(GearProperties.ARMOR.get(), replacement));
            double previous = amount(root.getAttributeModifiers());
            tick(player, root, 21, EquipmentSlot.LEGS);
            close(amount(root.getAttributeModifiers()), expected(root), "Admitted definition reload");
            require(Math.abs(previous - amount(root.getAttributeModifiers())) > 1E-5, "Definition input missing from refresh sample");
        } finally { field.set(effect, original); }
    }

    private static void oxidation(ServerPlayer player, StringBuilder report) throws Exception {
        var older = FoundationChecks.armor("copper");
        older.set(SgDataComponents.OXIDATION_COUNTER.get(), 1199); // Accelerated only in this fixture.
        var younger = older.copy();
        younger.set(SgDataComponents.OXIDATION_COUNTER.get(), 10);
        for (var inputs : List.of(List.of(older, younger), List.of(older, new ItemStack(Items.DIAMOND_LEGGINGS)))) {
            for (var root : FoundationChecks.paid(player, inputs.getFirst(), inputs.getLast())) {
                var protectedRoot = root.copy();
                for (var slot : List.of(EquipmentSlot.LEGS, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
                    var before = root.copy();
                    tick(player, root, 20, slot);
                    require(ItemStack.matches(before, root), "Selected oxidation advanced");
                }
                var data = MixedLeggingsStructure.validData(root).orElseThrow();
                var controls = List.of(data.left().source(), data.right().source());
                var before = root.copy();
                tick(player, root, 21, null);
                require(ItemStack.matches(before, root), "Non-cadence oxidation advanced");
                tick(player, root, 20, null);
                for (int i = 0; i < 2; i++) {
                    var nativeSource = controls.get(i);
                    if (!SilentGearCompatibility.leggings(nativeSource)) { continue; }
                    nativeTick(player, nativeSource);
                    require(lane(root, i).equals(capture(nativeSource)), "Native phase/independent clock mismatch");
                }
                require(!root.has(SgDataComponents.RECALCULATE_FLAG.get()), "Root owns native pending work");
                protectedFields(protectedRoot, root);
                // Native reset to Oxidized I must remain observable on repeated accelerated boundaries.
                for (int step = 0; step < 3; step++) {
                    var state = root.get(SilentGearCompatibility.state());
                    root.set(SilentGearCompatibility.state(), new SilentGearState(
                            state.left().map(v -> new SilentGearState.Oxidation(v.phase(), v.level(), v.stage(), v.phase().equals("polished") ? 1199 : 599)),
                            state.right().map(v -> new SilentGearState.Oxidation(v.phase(), v.level(), v.stage(), v.phase().equals("polished") ? 1199 : 599)),
                            state.armorPolicy(), state.armorProjection(), state.armorOverridden(), state.sample()));
                    tick(player, root, 20, null);
                    for (var value : List.of(root.get(SilentGearCompatibility.state()).left(), root.get(SilentGearCompatibility.state()).right())) {
                        value.ifPresent(v -> require(v.phase().equals("oxidized") && v.level() == 1 && v.stage() == 1 && v.counter() == 0, "Native repeated Oxidized-I reset changed"));
                    }
                }
                var live = root.get(SilentGearCompatibility.state());
                var trimmed = data.left().source();
                trimmed.set(DataComponents.TRIM, new ArmorTrim(player.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).get(Identifier.withDefaultNamespace("quartz")).orElseThrow(),
                        player.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).get(Identifier.withDefaultNamespace("coast")).orElseThrow()));
                var rebuilt = MixedLeggingsRecomposer.recompose(root, trimmed, data.right().source());
                require(rebuilt.get(SilentGearCompatibility.state()).left().equals(live.left()) && rebuilt.get(SilentGearCompatibility.state()).right().equals(live.right()), "Trim reset live clocks");
                close(amount(rebuilt.getAttributeModifiers()), amount(root.getAttributeModifiers()), "Trim changed live phase projection");
            }
        }
        for (var mode : List.of("coated", "oxidized", "terminal", "pending")) {
            var source = mode.equals("coated") ? FoundationChecks.armor("copper", "coating", "netherite") : older.copy();
            if (mode.equals("oxidized") || mode.equals("terminal")) {
                var values = new HashMap<>(GearData.getProperties(source).properties());
                values.put(GearProperties.TRAITS.get(), new TraitListPropertyValue(List.of(TraitInstance.of(DataResource.trait("silentgear:oxidized"), mode.equals("terminal") ? 3 : 2))));
                source.set(SgDataComponents.GEAR_PROPERTIES.get(), new GearPropertiesData(values));
                source.set(SgDataComponents.OXIDATION_STAGE.get(), (byte) (mode.equals("terminal") ? 3 : 1));
                source.set(SgDataComponents.OXIDATION_COUNTER.get(), 100);
            }
            if (mode.equals("pending")) {
                player.tickCount = 20;
                source.inventoryTick(player.level(), player, null);
                require(source.has(SgDataComponents.RECALCULATE_FLAG.get()), "Native pending-work control absent");
            }
            for (var root : FoundationChecks.paid(player, source, new ItemStack(Items.DIAMOND_LEGGINGS))) {
                var before = root.copy();
                var control = source.copy();
                if (control.has(SgDataComponents.RECALCULATE_FLAG.get())) { GearData.recalculateGearData(control, player); control.remove(SgDataComponents.RECALCULATE_FLAG.get()); }
                nativeTick(player, control);
                tick(player, root, 20, null);
                int side = SilentGearCompatibility.leggings(MixedLeggingsStructure.validData(root).orElseThrow().left().source()) ? 0 : 1;
                require(lane(root, side).equals(capture(control)), "Native " + mode + " control differs");
                if (mode.equals("coated") || mode.equals("terminal")) { require(ItemStack.matches(before, root), "Paused/terminal clock wrote root"); }
            }
        }
        // Explicit computed-trait fixture isolates cache invalidation when a clock and armor share one source.
        var combined = older.copy();
        var values = new HashMap<>(GearData.getProperties(combined).properties());
        var traits = new ArrayList<>(TraitHelper.getTraits(combined));
        traits.add(TraitInstance.of(DataResource.trait("silentgear:chipping"), 1));
        values.put(GearProperties.TRAITS.get(), new TraitListPropertyValue(traits));
        combined.set(SgDataComponents.GEAR_PROPERTIES.get(), new GearPropertiesData(values));
        for (var root : FoundationChecks.paid(player, combined, new ItemStack(Items.DIAMOND_LEGGINGS))) {
            tick(player, root, 20, null);
            unchanged(player, root);
        }
        report.append("C OXIDATION: real idle/selected cadence, unequal one/two-sided clocks, native Polished transition and repeated Oxidized-I resets, coating, explicit oxidized and separately supplied terminal stage, pending seed settlement and trim preservation passed (boundary counters accelerated only in fixtures)\n");
    }

    private static void sibling(ServerPlayer player, StringBuilder report) throws Exception {
        var boot = ((net.silentchaos512.gear.api.item.GearItem) net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:boots")))
                .construct(List.of(net.silentchaos512.gear.gear.part.PartInstance.from(FoundationChecks.part("boot_plates", "copper"))));
        boot.set(SgDataComponents.OXIDATION_COUNTER.get(), 1199);
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, boot, new ItemStack(Items.DIAMOND_BOOTS));
        var root = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
        var type = (net.minecraft.core.component.DataComponentType<?>) Class.forName("games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCompatibility").getMethod("state").invoke(null);
        var before = root.get(type);
        tick(player, root, 20, null);
        require(root.get(type) != null && !root.get(type).equals(before) && !root.has(SilentGearCompatibility.state()), "Boots independent live oxidation failed");
        report.append("C current Boots independent native clock/state dispatch passed\n");
    }

    @SuppressWarnings("unchecked")
    private static void ownership(ServerPlayer player, StringBuilder report) throws Exception {
        var eternal = MixedLeggingsFactory.createPair(FoundationChecks.armor("quartz"), new ItemStack(Items.DIAMOND_LEGGINGS)).primary();
        eternal.set(LeggingsContent.forbiddenModifiersType(), new games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifiers(true, true));
        eternal.remove(DataComponents.MAX_DAMAGE);
        eternal.remove(DataComponents.DAMAGE);
        var before = eternal.copy();
        tick(player, eternal, 21, EquipmentSlot.LEGS);
        require(!eternal.has(DataComponents.MAX_DAMAGE) && !eternal.has(DataComponents.DAMAGE)
                && eternal.get(LeggingsContent.forbiddenModifiersType()).equals(before.get(LeggingsContent.forbiddenModifiersType())), "C overwrote Eternal/Soulbound root authority");
        protectedFields(before, eternal);
        var sample = eternal.get(SilentGearCompatibility.state()).sample().orElseThrow();
        require(sample.maximum() == 0 && sample.damage() == 0, "Eternal refresh sample lost actual non-damageable inputs");
        var encoded = SilentGearState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, eternal.get(SilentGearCompatibility.state())).getOrThrow();
        require(SilentGearState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow().equals(eternal.get(SilentGearCompatibility.state())), "Non-damageable C state codec");
        report.append("C OWNERSHIP: existing Eternal non-damageable projection and independent Eternal/Soulbound flags preserved (no native FA lifecycle claim)\n");
        if (!Boolean.getBoolean("leggings.test.sgLiveInfusion")) { return; }
        require(ModList.get().isLoaded("undergarden"), "Missing native infusion graph");
        var type = (net.minecraft.core.component.DataComponentType<Object>) net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse("undergarden:rogdorium_infusion"));
        var json = new com.google.gson.JsonObject();
        json.addProperty("infusionAmount", 53);
        json.addProperty("infusionMax", 3600);
        Object reserve = type.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
        var root = MixedLeggingsFactory.createPair(FoundationChecks.armor("bone"), new ItemStack(Items.DIAMOND_LEGGINGS)).primary();
        root.set(type, reserve);
        before = root.copy();
        root.setDamageValue(root.getMaxDamage() / 2);
        tick(player, root, 21, EquipmentSlot.LEGS);
        require(root.get(type).equals(reserve), "C overwrote live native infusion");
        protectedFields(before, root);
        var data = MixedLeggingsStructure.validData(root).orElseThrow();
        var rebuilt = MixedLeggingsRecomposer.recompose(root, data.left().source(), data.right().source());
        require(rebuilt.get(type).equals(reserve), "Recomposition lost live root-only infusion");
        report.append("C OWNERSHIP: actual registered Undergarden live root-only infusion 53/3600 survives owned armor refresh and recomposition\n");
    }

    static void refusal(ServerPlayer player, StringBuilder report) throws Exception {
        // A changed definition on the SECOND lane must refuse before advancing the first.
        var first = FoundationChecks.armor("copper");
        first.set(SgDataComponents.OXIDATION_COUNTER.get(), 1199);
        var second = FoundationChecks.armor("bone");
        boolean boundary = System.getProperty("leggings.test.sgLiveVariant").equals("boundary");
        ItemStack root;
        if (boundary) {
            // An already initialized saved-root fixture: C's unsafe native class is transformed at startup,
            // so it cannot be used to initialize a fresh copper pair in this JVM.
            root = MixedLeggingsFactory.createPair(FoundationChecks.armor("iron"), new ItemStack(Items.DIAMOND_LEGGINGS)).primary();
            MixedLeggingsStructure.apply(root, new games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData(
                    new games.alejandrocoria.mismatchedleggings.data.LeggingHalf(first), new games.alejandrocoria.mismatchedleggings.data.LeggingHalf(second)));
            var old = root.get(SilentGearCompatibility.state());
            root.set(SilentGearCompatibility.state(), new SilentGearState(java.util.Optional.of(capture(first)), java.util.Optional.empty(),
                    old.armorPolicy(), old.armorProjection(), false, java.util.Optional.empty()));
        } else { root = MixedLeggingsFactory.createPair(first, second).primary(); }
        var repair = MixedLeggingsFactory.createPair(FoundationChecks.armor("amethyst"), new ItemStack(Items.DIAMOND_LEGGINGS)).primary();
        repair.setDamageValue(12);
        var effect = TraitHelper.getTraits(second).stream().filter(t -> t.getTraitId().getPath().equals("chipping")).findFirst().orElseThrow().getTrait().getEffects().getFirst();
        var field = effect.getClass().getDeclaredField("mods");
        field.setAccessible(true);
        Object original = field.get(effect);
        var before = root.copy();
        try {
            if (boundary) { corruptOxidation = true; }
            else { field.set(effect, Map.of()); }
            tick(player, root, 20, null);
            tick(player, root, 20, null);
            require(ItemStack.matches(before, root), "C refusal partly advanced a lane/projection");
            if (boundary) { require(boundaryCalls == 0, "Changed native callback ran before local admission"); }
        } finally { field.set(effect, original); corruptOxidation = false; }
        var randomField = Class.forName("net.silentchaos512.lib.util.MathUtils").getDeclaredField("RANDOM");
        randomField.setAccessible(true);
        var random = (java.util.Random) randomField.get(null);
        long seed = 0;
        while (new java.util.Random(seed).nextDouble() >= .01) { seed++; }
        random.setSeed(seed);
        tick(player, repair, 20, null);
        require(repair.getDamageValue() < 12, "C refusal disabled B repair");
        if (ModList.get().isLoaded("mismatchedboots")) { sibling(player, report); }
        report.append("C refusal ").append(boundary ? "competing native write surface, refused before invocation on preinitialized fixture" : "changed second-source definition").append(": last valid complete root retained, repeat refused, B repair and sibling remain active\n");
    }

    public static boolean corruptOxidation;
    public static int boundaryCalls;

    private static void unchanged(ServerPlayer player, ItemStack root) {
        var before = root.copy();
        recalculations = 0;
        observing = true;
        try {
            for (int i = 0; i < 3; i++) {
                root.getAttributeModifiers();
                root.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(player.level()), player, net.minecraft.world.item.TooltipFlag.NORMAL);
                tick(player, root, 21, EquipmentSlot.LEGS);
            }
        } finally { observing = false; }
        require(recalculations == 0 && ItemStack.matches(before, root), "Unchanged ticks/queries recalculated or wrote root");
    }

    private static double expected(ItemStack root) {
        var data = MixedLeggingsStructure.validData(root).orElseThrow();
        var copies = new ArrayList<ItemAttributeModifiers>();
        for (var source : List.of(data.left().source(), data.right().source())) {
            if (SilentGearCompatibility.leggings(source)) {
                source.setDamageValue((int) ((long) root.getDamageValue() * source.getMaxDamage() / root.getMaxDamage()));
                GearData.recalculateGearData(source, null);
            }
            copies.add(new NeoForgeCompositionAttributes().query(source));
        }
        return amount(AttributeMerger.merge(copies.getFirst(), copies.getLast(), id -> root.get(SilentGearCompatibility.state()).armorPolicy()).primary());
    }

    private static void nativeTick(ServerPlayer player, ItemStack source) {
        player.tickCount = 20;
        source.inventoryTick(player.level(), player, null);
        player.tickCount = 21;
        source.inventoryTick(player.level(), player, null); // Native ordinary path settles pending recomputation next tick.
    }

    private static SilentGearState.Oxidation capture(ItemStack source) {
        var value = TraitHelper.getTraits(source).stream().filter(t -> List.of("polished", "oxidized").contains(t.getTraitId().getPath())).findFirst().orElseThrow();
        return new SilentGearState.Oxidation(value.getTraitId().getPath(), value.getLevel(), source.getOrDefault(SgDataComponents.OXIDATION_STAGE.get(), (byte) 0), source.getOrDefault(SgDataComponents.OXIDATION_COUNTER.get(), 0));
    }

    private static SilentGearState.Oxidation lane(ItemStack root, int side) {
        var value = root.get(SilentGearCompatibility.state());
        return (side == 0 ? value.left() : value.right()).orElseThrow();
    }

    private static void protectedFields(ItemStack before, ItemStack after) {
        var a = before.copy();
        var b = after.copy();
        for (var type : List.of(DataComponents.DAMAGE, DataComponents.ATTRIBUTE_MODIFIERS, SilentGearCompatibility.state())) { a.remove(type); b.remove(type); }
        // Product-owned pending native flags are removed at construction, never used as root authority.
        require(ItemStack.matches(a, b), "C wrote equipment/name/history/enchantments or unrelated state");
    }

    private static void equipped(ServerPlayer player, ItemStack root) throws Exception {
        player.setItemSlot(EquipmentSlot.LEGS, root);
        var method = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");
        method.setAccessible(true);
        method.invoke(player);
        close(player.getAttributeValue(Attributes.ARMOR), amount(root.getAttributeModifiers()), "Actual equipped armor");
    }

    private static double amount(ItemAttributeModifiers values) {
        return values.modifiers().stream().filter(SilentGearState::managed).mapToDouble(e -> e.modifier().amount()).sum();
    }

    private static CompositionRules rules(AttributePolicy policy) {
        return new CompositionRules(new AttributeRuleSet(AttributePolicy.SEPARATE, Map.of(SilentGearState.ARMOR, policy)), CompositionRules.defaults().components());
    }

    private static void tick(ServerPlayer player, ItemStack root, int time, EquipmentSlot slot) {
        player.tickCount = time;
        boolean previous = observing;
        observing = true;
        try { root.inventoryTick(player.level(), player, slot); }
        finally { observing = previous; }
    }

    private static void close(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < 1E-5, message + ": " + actual + " != " + expected);
    }
}
