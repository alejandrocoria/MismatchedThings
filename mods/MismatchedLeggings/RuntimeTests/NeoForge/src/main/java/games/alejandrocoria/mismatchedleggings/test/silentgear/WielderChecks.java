package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearWearer;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearWearerApi;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData;
import games.alejandrocoria.mismatchedleggings.test.silentgear.mixin.WielderAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.property.TraitListPropertyValue;
import net.silentchaos512.gear.api.traits.TraitActionContext;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.core.component.GearPropertiesData;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.gear.trait.condition.GearTypeTraitCondition;
import net.silentchaos512.gear.gear.trait.effect.WielderEffectTraitEffect;
import net.silentchaos512.gear.setup.SgDataComponents;
import net.silentchaos512.gear.setup.SgRegistries;
import net.silentchaos512.gear.setup.gear.GearProperties;
import net.silentchaos512.gear.setup.gear.GearTypes;
import net.silentchaos512.gear.util.GearData;
import net.silentchaos512.gear.util.TraitHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Maintained C gate: real item ticks, independent native oracles and scoped wearer-only write witnesses. */
public final class WielderChecks {
    private static final List<String[]> FAMILIES = List.of(new String[]{"adamant", "nickel"}, new String[]{"aquatic", "lead"},
            new String[]{"flame_ward", "crimson_steel"}, new String[]{"stellar", "refined_iron"});
    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD, EquipmentSlot.CHEST);
    private static boolean observing;
    private static boolean failAfterActor;
    private static int calls;
    private static ItemStack carrierBefore;
    private static List<ItemStack> equipmentBefore;

    private WielderChecks() {}

    public static void recalculation() {
        if (observing) { throw new AssertionError("Wielder entered native gear recalculation"); }
    }

    public static void before(Object context, Player player, boolean selected) {
        if (!observing) { return; }
        calls++;
        require(selected, "Production dispatched an idle Wielder");
        carrierBefore = ((TraitActionContext) context).gear().copy();
        equipmentBefore = Arrays.stream(EquipmentSlot.values()).map(s -> player.getItemBySlot(s).copy()).toList();
    }

    public static void after(Object context, Player player) {
        if (!observing) { return; }
        require(ItemStack.matches(carrierBefore, ((TraitActionContext) context).gear()), "Wielder changed carrier");
        int index = 0;
        for (var slot : EquipmentSlot.values()) {
            require(ItemStack.matches(equipmentBefore.get(index++), player.getItemBySlot(slot)), "Wielder changed equipment/root/history/state");
        }
        if (failAfterActor) {
            require(!player.getActiveEffects().isEmpty(), "Failure control did not first affect the actor");
            throw new IllegalStateException("Controlled failure after native actor change");
        }
    }

    public static boolean recognizesAlias(Object context, EquipmentSlot slot, ItemStack stack) {
        return SilentGearWearer.recognizes(context, slot, stack);
    }
    public static void callbackAlias() {}

    static void run(ServerPlayer player, StringBuilder report) throws Throwable {
        String variant = System.getProperty("leggings.test.sgWielderVariant", "");
        if (variant.isEmpty()) { return; }
        require(WielderTestPlugin.actualOrder != null, "Missing actual production set order");
        report.append("C actual set mixin order ").append(WielderTestPlugin.actualOrder).append('\n');
        boolean ready = !List.of("type", "trait", "callback", "potion").contains(variant);
        boolean calibrated = false;
        observing = true;
        try { GearData.recalculateGearData(FoundationChecks.armor("iron"), player); }
        catch (AssertionError expected) { calibrated = true; }
        finally { observing = false; }
        require(calibrated, "Wielder recalculation tripwire lacks positive calibration");
        var api = new SilentGearWearerApi();
        for (var family : FAMILIES) {
            String name = family[0];
            var pieces = List.of(armor("leggings", "legging_plates", family[1]), armor("boots", "boot_plates", family[1]),
                    armor("helmet", "helmet_plates", family[1]), armor("chestplate", "chestplate_plates", family[1]));
            var trait = trait(pieces.getFirst(), name);
            var effect = (WielderEffectTraitEffect) api.admittedEffects(trait).getFirst();
            for (int count = 0; count <= 4; count++) {
                equip(player, pieces, count);
                require(count(effect, player, trait, pieces.getFirst()) == count, "Native armor count differs");
                nativeTick(effect, player, trait, pieces.getFirst(), true);
                check(player, name, count, true);
            }
            // Retain the native non-armor branch, mismatched gear branch, null player and idle/cadence semantics.
            require(((WielderAccess) effect).sgtest$count(new TraitActionContext(player, trait, pieces.getFirst()), GearTypes.SWORD.get(), player) == 1,
                    "Changed non-armor count branch");
            nativeTick(effect, player, trait, armor("sword", "sword_blade", family[1]), true);
            require(player.getActiveEffects().isEmpty(), "Armor branch reached native sword");
            effect.inventoryTick(new TraitActionContext(null, trait, pieces.getFirst()), player.level(), player, true);
            require(player.getActiveEffects().isEmpty(), "Null native player activated effects");
            nativeTick(effect, player, trait, pieces.getFirst(), false);
            check(player, name, 4, false);
            for (var other : List.of(new ItemStack(Items.DIAMOND_LEGGINGS), pieces.getFirst(), FoundationChecks.armor("iron"))) {
                for (var root : FoundationChecks.paid(player, pieces.getFirst(), other)) {
                    var fireResistance = root.get(DataComponents.DAMAGE_RESISTANT);
                    root.remove(SilentGearCompatibility.state());
                    for (int n = 0; n <= 4; n++) {
                        var mixed = new ArrayList<>(pieces);
                        mixed.set(0, root);
                        equip(player, mixed, n);
                        if (n == 0) { player.setItemSlot(EquipmentSlot.MAINHAND, root); }
                        int recognized = n - (!ready && n > 0 ? 1 : 0);
                        require(count(effect, player, trait, pieces.getFirst()) == recognized, "Native caller lost coherent mixed LEGS count");
                        int previous = calls;
                        tick(root, player, n == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.LEGS, true);
                        require(calls - previous == (ready ? 1 : 0), "Duplicate/missing production Wielder call");
                        check(player, name, n, ready);
                        // Actual native armor item tick is a separate caller, including native companion ownership.
                        player.removeAllEffects();
                        pieces.get(2).inventoryTick(player.level(), player, EquipmentSlot.HEAD);
                        check(player, name, recognized, true);
                    }
                    selection(player, root, pieces, name, ready);
                    require(java.util.Objects.equals(fireResistance, root.get(DataComponents.DAMAGE_RESISTANT)), "Flame Ward changed dropped-item fire composition");
                }
            }
            sibling(player, pieces, trait, effect, name, ready, report);
            report.append("C ").append(name).append(": native 0..4, both paid orientations, duplicate/plain sources, exact potion parameters,")
                    .append(" native caller, held/idle/cadence/refresh/expiry and unchanged root/source/carrier; ready=").append(ready).append('\n');
        }
        invalid(player, api, ready);
        sources(player, api, ready);
        combinedRepair(player, ready, report);
        definitions(player, api, ready, report);
        if (ready && variant.equals("actor")) { actorFailure(player, report); }
        ManualFixtures.wearer(player);
        clear(player);
        report.append("C production variant=").append(variant).append(" passed; research dispatcher/recognition disabled\n");
    }

    private static void selection(ServerPlayer player, ItemStack root, List<ItemStack> pieces, String name, boolean ready) throws Exception {
        for (var slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.LEGS)) {
            equip(player, List.of(), 0);
            player.setItemSlot(slot, root);
            tick(root, player, slot, true);
            check(player, name, slot == EquipmentSlot.LEGS ? 1 : 0, ready);
        }
        equip(player, pieces, 4);
        player.getInventory().setItem(9, root);
        int previous = calls;
        tick(root, player, null, true);
        require(calls == previous && player.getActiveEffects().isEmpty(), "Idle copy activated or added armor member");
        player.tickCount = 21;
        tick(root, player, EquipmentSlot.MAINHAND, true);
        require(calls == previous && player.getActiveEffects().isEmpty(), "Wrong player cadence");
        player.tickCount = 30;
        player.setItemSlot(EquipmentSlot.MAINHAND, root);
        tick(root, player, EquipmentSlot.MAINHAND, true);
        check(player, name, 4, ready);
        if (ready) {
            var effects = net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("tickEffects");
            effects.setAccessible(true);
            for (int i = 0; i < 8; i++) { effects.invoke(player); }
            player.tickCount = 40;
            tick(root, player, EquipmentSlot.MAINHAND, true);
            check(player, name, 4, true); // Native refresh back to 38.
            var existing = player.getActiveEffects().stream().map(MobEffectInstance::new).toList();
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            tick(root, player, null, true);
            require(existing.equals(player.getActiveEffects().stream().map(MobEffectInstance::new).toList()), "Moving gear erased/changed existing effects");
            for (int i = 0; i < 40; i++) { effects.invoke(player); }
            require(player.getActiveEffects().isEmpty(), "Stopped selection did not expire naturally");
        }
    }

    private static void sibling(ServerPlayer player, List<ItemStack> pieces, TraitInstance trait, WielderEffectTraitEffect effect,
                                String name, boolean ready, StringBuilder report) throws Throwable {
        if (!Boolean.getBoolean("leggings.test.boots")) { return; }
        var legs = FoundationChecks.paid(player, pieces.getFirst(), new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        legs.remove(SilentGearCompatibility.state());
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, pieces.get(1), new ItemStack(Items.DIAMOND_BOOTS));
        var feet = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
        var mixed = List.of(legs, feet, pieces.get(2), pieces.get(3));
        for (int n : List.of(2, 4)) {
            equip(player, mixed, n);
            player.setItemSlot(EquipmentSlot.MAINHAND, legs.copy());
            player.setItemSlot(EquipmentSlot.OFFHAND, feet.copy());
            player.getInventory().setItem(9, legs.copy());
            require(count(effect, player, trait, pieces.get(2)) == n - (ready ? 0 : 1), "Independent LEGS/FEET count differs");
            tick(legs, player, EquipmentSlot.LEGS, true);
            check(player, name, n, ready);
            player.removeAllEffects();
            feet.inventoryTick(player.level(), player, EquipmentSlot.FEET);
            check(player, name, n - (ready ? 0 : 1), true);
        }
        report.append("C ").append(name).append(ready ? ": mixed-only LEGS/FEET=2, native head/chest=4" : ": LEGS refused, FEET=1, native head/chest=3")
                .append(", carried copies=0; independent Boots retained\n");
    }

    private static void sources(ServerPlayer player, SilentGearWearerApi api, boolean ready) throws Throwable {
        var source = FoundationChecks.armor("refined_iron");
        for (boolean matching : List.of(false, true)) {
            var low = only(source, TraitInstance.of(DataResource.trait("silentgear:stellar"), 1));
            var high = only(source, TraitInstance.of(DataResource.trait("silentgear:stellar"), 4,
                    new GearTypeTraitCondition(matching ? GearTypes.LEGGINGS : GearTypes.BOOTS)));
            for (var root : FoundationChecks.paid(player, low, high)) {
                root.remove(SilentGearCompatibility.state());
                require(api.traits(root).size() == 1 && api.traits(root).getFirst().level() == (matching ? 4 : 1), "Summed/duplicated levels or unresolved condition");
                equip(player, List.of(root), 1);
                int previous = calls;
                tick(root, player, EquipmentSlot.LEGS, true);
                require(calls - previous == (ready ? 1 : 0), "Unequal duplicate dispatched twice");
                // Native PIECE_COUNT uses set count, not the tier sum.
                check(player, "stellar", 1, ready);
            }
        }
        for (var root : FoundationChecks.paid(player, FoundationChecks.armor("nickel"), source)) {
            root.remove(SilentGearCompatibility.state());
            equip(player, List.of(root), 1);
            int previous = calls;
            tick(root, player, EquipmentSlot.LEGS, true);
            require(calls - previous == (ready ? 2 : 0), "Different source traits lost/double dispatched");
            check(player, "adamant", 1, ready);
            check(player, "stellar", 1, ready);
        }
    }

    private static void invalid(ServerPlayer player, SilentGearWearerApi api, boolean ready) throws Throwable {
        var source = FoundationChecks.armor("refined_iron");
        var root = FoundationChecks.paid(player, source, new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        var malformed = root.copy(); malformed.remove(DataComponents.EQUIPPABLE);
        var wrong = root.copy(); wrong.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build());
        var foreign = new ItemStack(Items.DIAMOND_LEGGINGS);
        foreign.set(LeggingsContent.mixedLeggingsDataType(), root.get(LeggingsContent.mixedLeggingsDataType()));
        var suspended = root.copy(); suspended.set(LeggingsContent.createSuspendedDataType(), SuspendedLeggingsData.of(new CompoundTag(), new CompoundTag(), new CompoundTag()));
        var quarantined = root.copy(); quarantined.set(LeggingsContent.createSuspendedDataType(), SuspendedLeggingsData.quarantined(new CompoundTag(), new CompoundTag()));
        var trait = trait(source, "stellar");
        var effect = (WielderEffectTraitEffect) api.admittedEffects(trait).getFirst();
        for (var rejected : List.of(new ItemStack(root.getItem()), root.copyWithCount(2), malformed, wrong, foreign, suspended, quarantined)) {
            equip(player, List.of(rejected), 1);
            int previous = calls;
            require(count(effect, player, trait, source) == 0, "Invalid root counted as LEGS");
            tick(rejected, player, EquipmentSlot.LEGS, false);
            require(calls == previous && player.getActiveEffects().isEmpty(), "Invalid root dispatched Wielder");
        }
        equip(player, List.of(), 0);
        player.setItemSlot(EquipmentSlot.FEET, root);
        require(count(effect, player, trait, source) == 0, "Mixed LEGS counted in wrong physical slot");
        boolean denied = false;
        try { api.context(player, trait, root); } catch (IllegalArgumentException expected) { denied = true; }
        require(denied, "Mixed root accepted as native GearItem receiver");
        denied = false;
        try { api.context(player, trait, armor("boots", "boot_plates", "refined_iron")); } catch (IllegalArgumentException expected) { denied = true; }
        require(denied, "Wrong-slot native receiver admitted");
    }

    private static void combinedRepair(ServerPlayer player, boolean ready, StringBuilder report) throws Throwable {
        var source = FoundationChecks.armor("refined_iron");
        var trait = trait(source, "stellar");
        var repair = trait.getTrait().getEffects().stream().filter(e -> e.getClass().getSimpleName().equals("SelfRepairTraitEffect")).findFirst().orElseThrow();
        var chance = repair.getClass().getDeclaredField("activationChance"); chance.setAccessible(true);
        float probability = chance.getFloat(repair) * trait.getLevel();
        var field = Class.forName("net.silentchaos512.lib.util.MathUtils").getDeclaredField("RANDOM"); field.setAccessible(true);
        var random = (Random) field.get(null);
        for (boolean activation : List.of(true, false)) {
            long seed = 0;
            while ((new Random(seed).nextDouble() < probability) != activation && seed < 100_000) { seed++; }
            require(seed < 100_000, "Missing bounded repair seed");
            for (var root : FoundationChecks.paid(player, source, source)) {
                root.setDamageValue(12);
                equip(player, List.of(root), 1);
                var before = root.copy();
                var oracle = new Random(seed); oracle.nextDouble();
                random.setSeed(seed);
                int previous = calls;
                tick(root, player, EquipmentSlot.LEGS, false);
                require(root.getDamageValue() == 12 - (activation ? trait.getLevel() : 0), "Combined tick healed more than existing repair opportunity");
                require(random.nextDouble() == oracle.nextDouble(), "Wielder consumed an extra repair roll");
                require(calls - previous == (ready ? 1 : 0), "Combined tick Wielder opportunity differs");
                before.setDamageValue(root.getDamageValue());
                require(ItemStack.matches(before, root), "Combined tick changed non-repair root metadata/state/lanes");
            }
        }
        report.append("C combined Stellar ordinary tick: seeded repair success/failure, one repair roll, Wielder calls=").append(ready ? 1 : 0)
                .append("; scoped wearer has zero gear writes\n");
    }

    @SuppressWarnings("unchecked")
    private static void definitions(ServerPlayer player, SilentGearWearerApi api, boolean ready, StringBuilder report) throws Throwable {
        var source = FoundationChecks.armor("nickel");
        var trait = trait(source, "adamant");
        var effect = (WielderEffectTraitEffect) api.admittedEffects(trait).getFirst();
        var potions = effect.getClass().getDeclaredField("potions"); potions.setAccessible(true);
        var original = (Map<Object, List<Object>>) potions.get(effect);
        // Reload-equivalent replacement of supported native potion data is read on the next call.
        var entry = original.values().iterator().next().getFirst();
        var duration = entry.getClass().getDeclaredField("duration"); duration.setAccessible(true);
        int originalDuration = duration.getInt(entry);
        try {
            duration.setInt(entry, 57);
            equip(player, List.of(source), 1);
            var root = FoundationChecks.paid(player, source, new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
            root.remove(SilentGearCompatibility.state());
            tick(root, player, EquipmentSlot.MAINHAND, true);
            require(!ready || player.getEffect(MobEffects.RESISTANCE).getDuration() == 57, "Supported changed native data was cached");
        } finally { duration.setInt(entry, originalDuration); }
        require(api.admittedEffects(trait).size() == 1, "Restored native data not revalidated");
        var effectsField = trait.getTrait().getClass().getDeclaredField("effects"); effectsField.setAccessible(true);
        var originalEffects = trait.getTrait().getEffects();
        var unknown = trait(FoundationChecks.armor("amethyst"), "renew").getTrait().getEffects().getFirst();
        for (var bad : List.of(List.of(effect), List.of(effect, unknown), List.of(effect, effect, originalEffects.getLast()),
                List.of(new WielderEffectTraitEffect(Map.of()) {}, originalEffects.getLast()))) {
            try {
                effectsField.set(trait.getTrait(), bad);
                boolean refused = false;
                try { api.admittedEffects(trait); } catch (IllegalStateException expected) { refused = true; }
                require(refused, "Unknown companion, missing/duplicate Wielder or subclass admitted");
            } finally { effectsField.set(trait.getTrait(), originalEffects); }
        }
        for (var bad : List.of(Map.of(), Map.of(GearTypes.SWORD.get(), original.values().iterator().next()))) {
            try {
                potions.set(effect, bad);
                boolean refused = false;
                try { api.admittedEffects(trait); } catch (IllegalStateException expected) { refused = true; }
                require(refused, "Unknown/missing Wielder branch admitted");
            } finally { potions.set(effect, original); }
        }
        // Exact PotionData identity prevents a subclass from overriding potion calculations after admission.
        var subclass = new WielderEffectTraitEffect.PotionData(WielderEffectTraitEffect.LevelType.PIECE_COUNT,
                MobEffects.RESISTANCE, 38, List.of(1, 1, 1, 2)) {};
        try {
            potions.set(effect, Map.of(GearTypes.ARMOR.get(), List.of(subclass)));
            boolean refused = false;
            try { api.admittedEffects(trait); } catch (IllegalStateException expected) { refused = true; }
            require(refused, "Potion receiver subclass admitted");
        } finally { potions.set(effect, original); }
        // Run the actual native trait resource listener, with paid roots/instances retained across replacement.
        var retained = FoundationChecks.paid(player, source, new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        retained.remove(SilentGearCompatibility.state());
        var oldDefinition = trait.getTrait();
        try {
            potions.set(effect, Map.of());
            SgRegistries.TRAIT.onResourceManagerReload(player.level().getServer().getResourceManager());
        } finally { potions.set(effect, original); }
        require(trait.getTrait() != oldDefinition && api.admittedEffects(trait).getFirst() != effect, "Native trait reload retained old definitions/calls");
        equip(player, List.of(retained), 1);
        tick(retained, player, EquipmentSlot.LEGS, true);
        check(player, "adamant", 1, ready);
        effect = (WielderEffectTraitEffect) api.admittedEffects(trait).getFirst();
        original = (Map<Object, List<Object>>) potions.get(effect);
        String variant = System.getProperty("leggings.test.sgWielderVariant", "");
        if (ready && variant.equals("definition")) {
            var stellar = FoundationChecks.armor("refined_iron");
            var lastEffect = api.admittedEffects(trait(stellar, "stellar")).getFirst();
            var lastPotions = potions.get(lastEffect);
            var root = FoundationChecks.paid(player, source, stellar).getFirst();
            root.remove(SilentGearCompatibility.state());
            try {
                // Stellar sorts after Adamant: no earlier valid trait may publish before this refusal.
                potions.set(lastEffect, Map.of());
                equip(player, List.of(root), 1);
                int previous = calls;
                tick(root, player, EquipmentSlot.LEGS, true);
                require(calls == previous && player.getActiveEffects().isEmpty(), "Unsafe definition dispatched part of an unvalidated batch");
            } finally { potions.set(lastEffect, lastPotions); }
            require(count(effect, player, trait, source) == 0, "Runtime refusal retained LEGS recognition for native callers");
            tick(root, player, EquipmentSlot.LEGS, true);
            require(player.getActiveEffects().isEmpty(), "Runtime refusal retried after restore");
            refusedSibling(player, report);
            // Repeat the independent adapter gates with a fresh actor (Mending's XP pickup cooldown is actor-owned).
            var independent = FoundationChecks.player(player.level().getServer());
            BrilliantChecks.run(independent, report);
            EffectsChecks.run(independent, report);
            LiveStateChecks.run(independent, report);
        }
        report.append("C supported data replacement/restoration, actual native trait-resource reload with retained paid roots,")
                .append(" unsupported companions/subclasses/receivers/armor branches validated on current instances\n");
    }

    private static void actorFailure(ServerPlayer player, StringBuilder report) throws Throwable {
        var root = FoundationChecks.paid(player, FoundationChecks.armor("nickel"), FoundationChecks.armor("refined_iron")).getFirst();
        root.remove(SilentGearCompatibility.state());
        equip(player, List.of(root), 1);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 200, 0));
        failAfterActor = true;
        int previous = calls;
        try { tick(root, player, EquipmentSlot.LEGS, true); }
        finally { failAfterActor = false; }
        require(calls == previous + 1 && player.hasEffect(MobEffects.RESISTANCE) && !player.hasEffect(MobEffects.JUMP_BOOST)
                && player.hasEffect(MobEffects.NIGHT_VISION), "Actor failure replayed batch or erased native/external effects");
        tick(root, player, EquipmentSlot.LEGS, true);
        require(calls == previous + 1 && player.hasEffect(MobEffects.RESISTANCE), "Actor failure replayed on next tick");
        var source = FoundationChecks.armor("nickel");
        var trait = trait(source, "adamant");
        var effect = (WielderEffectTraitEffect) new SilentGearWearerApi().admittedEffects(trait).getFirst();
        require(count(effect, player, trait, source) == 0, "Actor failure retained native caller LEGS recognition");
        refusedSibling(player, report);
        BrilliantChecks.run(player, report);
        report.append("C controlled post-actor failure: one call, no replay/rollback/potion erase, coherent runtime set revocation; Brilliant retained\n");
    }

    private static void refusedSibling(ServerPlayer player, StringBuilder report) throws Throwable {
        var pieces = List.of(armor("leggings", "legging_plates", "nickel"), armor("boots", "boot_plates", "nickel"),
                armor("helmet", "helmet_plates", "nickel"), armor("chestplate", "chestplate_plates", "nickel"));
        var trait = trait(pieces.getFirst(), "adamant");
        var effect = (WielderEffectTraitEffect) new SilentGearWearerApi().admittedEffects(trait).getFirst();
        sibling(player, pieces, trait, effect, "adamant", false, report);
    }

    private static void tick(ItemStack root, ServerPlayer player, EquipmentSlot slot, boolean unchanged) {
        var before = root.copy();
        observing = true;
        try { root.inventoryTick(player.level(), player, slot); }
        finally { observing = false; }
        if (unchanged) { require(ItemStack.matches(before, root), "Read-only wearer admission changed root/source/history/state"); }
    }

    private static int count(WielderEffectTraitEffect effect, ServerPlayer player, TraitInstance trait, ItemStack source) {
        observing = true;
        try { return ((WielderAccess) effect).sgtest$count(new TraitActionContext(player, trait, source), GearTypes.ARMOR.get(), player); }
        finally { observing = false; }
    }

    private static void nativeTick(WielderEffectTraitEffect effect, ServerPlayer player, TraitInstance trait, ItemStack source, boolean selected) {
        player.removeAllEffects();
        var before = source.copy();
        observing = true;
        try { effect.inventoryTick(new TraitActionContext(player, trait, source), player.level(), player, selected); }
        finally { observing = false; }
        require(ItemStack.matches(before, source), "Native isolated Wielder changed gear");
    }

    private static TraitInstance trait(ItemStack source, String name) {
        return TraitHelper.getTraits(source).stream().filter(t -> t.getTraitId().getPath().equals(name)).findFirst().orElseThrow();
    }

    private static ItemStack only(ItemStack source, TraitInstance trait) {
        var copy = source.copy();
        var map = new HashMap<>(copy.get(SgDataComponents.GEAR_PROPERTIES.get()).properties());
        map.put(GearProperties.TRAITS.get(), new TraitListPropertyValue(List.of(trait)));
        copy.set(SgDataComponents.GEAR_PROPERTIES.get(), new GearPropertiesData(map));
        return copy;
    }

    static ItemStack armor(String item, String part, String material) throws Exception {
        return ((GearItem) BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:" + item)))
                .construct(List.of(PartInstance.from(FoundationChecks.part(part, material))));
    }

    private static void clear(ServerPlayer player) { equip(player, List.of(), 0); }
    private static void equip(ServerPlayer player, List<ItemStack> pieces, int count) {
        player.removeAllEffects();
        player.tickCount = 20;
        player.getInventory().clearContent();
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
        for (int i = 0; i < count; i++) { player.setItemSlot(SLOTS.get(i), pieces.get(i)); }
    }

    private static void check(ServerPlayer player, String family, int count, boolean selected) {
        switch (family) {
            case "adamant" -> potion(player, MobEffects.RESISTANCE, selected ? count == 4 ? 2 : 1 : 0);
            case "aquatic" -> potion(player, MobEffects.WATER_BREATHING, selected && count == 4 ? 1 : 0);
            case "flame_ward" -> potion(player, MobEffects.FIRE_RESISTANCE, selected && count == 4 ? 1 : 0);
            case "stellar" -> {
                potion(player, MobEffects.SPEED, selected ? Math.max(0, count - 1) : 0);
                potion(player, MobEffects.JUMP_BOOST, selected ? Math.max(1, count) : 0);
            }
            default -> throw new AssertionError(family);
        }
    }

    private static void potion(ServerPlayer player, Holder<MobEffect> type, int level) {
        var actual = player.getEffect(type);
        require((actual == null ? 0 : actual.getAmplifier() + 1) == level, "Potion level differs: " + type + " " + actual + " expected=" + level);
        if (actual != null) { require(actual.getDuration() == 38 && actual.isAmbient() && !actual.isVisible(), "Potion duration/visibility differs"); }
    }
}
