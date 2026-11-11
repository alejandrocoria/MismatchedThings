package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearMagnet;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearMagnetApi;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.silentchaos512.gear.api.property.TraitListPropertyValue;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.core.component.GearPropertiesData;
import net.silentchaos512.gear.gear.trait.effect.ItemMagnetTraitEffect;
import net.silentchaos512.gear.setup.SgDataComponents;
import net.silentchaos512.gear.setup.SgRegistries;
import net.silentchaos512.gear.setup.gear.GearProperties;
import net.silentchaos512.gear.util.TraitHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real production item callbacks, independent native item oracle and mod-owned invocation counts. */
final class MagnetDispatchChecks {
    private MagnetDispatchChecks() {}

    static void run(ServerPlayer player, ItemStack magnetic, ItemStack greedy, StringBuilder report) throws Throwable {
        player.tickCount = 21; // Magnet activation must not inherit Wielder's ten-tick cadence.
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }

        checkPair(player, magnetic, new ItemStack(Items.DIAMOND_LEGGINGS), 1, false);
        checkPair(player, greedy, new ItemStack(Items.DIAMOND_LEGGINGS), 1, true);
        checkPair(player, magnetic, greedy, 2, false);
        checkPair(player, magnetic, magnetic, 1, false);
        // Fixture-only Magnetic II: there is no claimed stock acquisition recipe for this lower level.
        var lower = withTraits(magnetic, List.of(TraitInstance.of(DataResource.trait("silentgear:magnetic"), 2)));
        var unequal = FoundationChecks.paid(player, lower, magnetic);
        for (var root : unequal) {
            var expected = measure(player, radius(magnetic), () -> nativeTick(magnetic, player, EquipmentSlot.LEGS));
            var actual = measure(player, radius(magnetic), () -> mixedTick(root, player, player, EquipmentSlot.LEGS, 1));
            same(expected, actual, "unequal duplicate levels");
            require(((TraitInstance) MagnetDispatchProbe.instances.getFirst()).getLevel() == 5,
                    "Duplicate magnetic did not select the maximum native level once");
        }
        var root = FoundationChecks.paid(player, magnetic, greedy).getFirst();
        var noLiveState = root.copy();
        noLiveState.remove(SilentGearCompatibility.state());
        var before = noLiveState.copy();
        require(measure(player, 16, () -> SilentGearMagnet.tick(noLiveState, player.level(), player, EquipmentSlot.LEGS))
                .getFirst().lengthSqr() > 0 && ItemStack.matches(before, noLiveState), "Read-only magnet seeded missing live state");
        negatives(player, root);
        crouch(player, root);
        reload(player, root, greedy, report);
        trackerControl(player, report);
        overlap(player, magnetic, root, report);
        wearerControl(player, true);
        report.append("Magnet B: paid inverse single/combined/duplicate/unequal-level sources, fixed invocation counts; LEGS/both hands vs idle/wrong-slot/non-player/invalid/no-source; native filter/radius/sphere/above-below/delay/prevention/crouch and immutable root/source/carrier/equipment controls pass\n");

        String variant = System.getProperty("leggings.test.sgMagnetVariant");
        if (variant.equals("definition")) { batchRefusal(player, root, report); }
        if (variant.equals("late")) { lateFailure(player, root, report); }
    }

    private static void checkPair(ServerPlayer player, ItemStack left, ItemStack right, int calls, boolean greedyOnly) throws Throwable {
        for (var root : FoundationChecks.paid(player, left, right)) {
            var marker = new ItemStack(Items.IRON_HELMET);
            marker.setDamageValue(7);
            player.setItemSlot(EquipmentSlot.HEAD, marker);
            double radius = Math.max(radius(left), radius(right));
            for (var slot : List.of(EquipmentSlot.LEGS, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
                var expected = measure(player, radius, () -> {
                    nativeTick(left, player, slot);
                    nativeTick(right, player, slot);
                });
                var actual = measure(player, radius, () -> mixedTick(root, player, player, slot, calls));
                same(expected, actual, "paid orientation/" + slot);
                require(actual.get(0).lengthSqr() > 0 && actual.get(2).lengthSqr() > 0, "Native/mixed in-radius positives are inert");
                require((actual.get(1).lengthSqr() == 0) == greedyOnly, "Greedy live filter differs from Magnetic");
                for (int i : new int[]{3, 4, 5, 6, 7}) { require(actual.get(i).equals(Vec3.ZERO), "Native exclusion failed at item " + i); }
                require(actual.get(8).lengthSqr() > 0 && actual.get(9).lengthSqr() > 0, "Above/below positives are inert");
                require(MagnetDispatchProbe.instances.stream().map(value -> ((TraitInstance) value).getTraitId().toString()).toList()
                        .equals(calls == 2 ? List.of("silentgear:greedy", "silentgear:magnetic")
                                : List.of(greedyOnly ? "silentgear:greedy" : "silentgear:magnetic")), "Unstable/duplicate magnet batch");
            }
        }
    }

    private static void negatives(ServerPlayer player, ItemStack root) throws Throwable {
        for (var slot : new EquipmentSlot[]{null, EquipmentSlot.HEAD, EquipmentSlot.FEET, EquipmentSlot.CHEST}) {
            require(measure(player, 16, () -> mixedTick(root, player, player, slot, 0)).stream().allMatch(Vec3.ZERO::equals),
                    "Idle/wrong-slot mixed magnet activated");
        }
        var malformed = root.copy();
        malformed.remove(DataComponents.EQUIPPABLE);
        var stacked = root.copyWithCount(2);
        var foreign = new ItemStack(Items.IRON_LEGGINGS);
        foreign.set(LeggingsContent.mixedLeggingsDataType(), root.get(LeggingsContent.mixedLeggingsDataType()));
        var bare = new ItemStack(root.getItem());
        var suspended = root.copy();
        var suspendedType = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(LeggingsContent.SUSPENDED_LEGGINGS_ID);
        @SuppressWarnings("unchecked") var type = (net.minecraft.core.component.DataComponentType<SuspendedLeggingsData>) suspendedType;
        require(type != null, "Missing suspended leggings component fixture");
        suspended.set(type, SuspendedLeggingsData.quarantined(new CompoundTag(), new CompoundTag()));
        var noSource = FoundationChecks.paid(player, new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        for (var rejected : List.of(malformed, stacked, foreign, bare, suspended, noSource)) {
            require(measure(player, 16, () -> {
                // Foreign items cannot route through our Item override: exercise its public admission directly.
                if (rejected == foreign) { SilentGearMagnet.tick(rejected, player.level(), player, EquipmentSlot.LEGS); }
                else { mixedTick(rejected, player, player, EquipmentSlot.LEGS, 0); }
            }).stream().allMatch(Vec3.ZERO::equals), "Invalid/no-source mixed magnet activated");
        }
        var nonPlayer = new ItemEntity(player.level(), 0, 0, 0, new ItemStack(Items.DIRT));
        require(measure(player, 16, () -> mixedTick(root, player, nonPlayer, EquipmentSlot.LEGS, 0)).stream().allMatch(Vec3.ZERO::equals),
                "Non-player actor activated magnet");
        var otherLevel = player.level().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        require(otherLevel != null, "Missing level mismatch fixture");
        require(measure(player, 16, () -> SilentGearMagnet.tick(root, otherLevel, player, EquipmentSlot.LEGS)).stream().allMatch(Vec3.ZERO::equals),
                "Mismatched actor level activated magnet");
    }

    private static void crouch(ServerPlayer player, ItemStack root) throws Throwable {
        var option = net.silentchaos512.gear.Config.Common.magnetPullDisabledOnCrouch;
        boolean original = option.get();
        boolean oldShift = player.isShiftKeyDown();
        Pose oldPose = player.getPose();
        try {
            player.setShiftKeyDown(true);
            player.setPose(Pose.CROUCHING);
            require(player.isCrouching(), "Native crouch control is not crouching");
            for (boolean disabled : List.of(true, false)) {
                option.set(disabled);
                var sources = MixedLeggingsStructure.validData(root).orElseThrow();
                var expected = measure(player, 16, () -> {
                    nativeTick(sources.left().source(), player, EquipmentSlot.LEGS);
                    nativeTick(sources.right().source(), player, EquipmentSlot.LEGS);
                });
                var actual = measure(player, 16, () -> mixedTick(root, player, player, EquipmentSlot.LEGS, 2));
                same(expected, actual, "native crouch config=" + disabled);
                require((actual.getFirst().lengthSqr() == 0) == disabled, "Crouch configuration did not control magnet");
            }
        } finally {
            option.set(original);
            player.setShiftKeyDown(oldShift);
            player.setPose(oldPose);
        }
    }

    private static void reload(ServerPlayer player, ItemStack retained, ItemStack greedy, StringBuilder report) throws Throwable {
        var instance = MagnetChecks.trait(greedy, "greedy");
        var api = new SilentGearMagnetApi();
        Object oldEffect = api.admittedEffect(instance);
        Object oldDefinition = instance.getTrait();
        var filter = field(oldEffect.getClass(), "affectedItems");
        Object original = filter.get(oldEffect);
        var greedyRoot = FoundationChecks.paid(player, greedy, new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        try {
            filter.set(oldEffect, Optional.of(Ingredient.of(Items.DIRT)));
            var changed = measure(player, 10, () -> mixedTick(greedyRoot, player, player, EquipmentSlot.LEGS, 1));
            require(changed.get(0).equals(Vec3.ZERO) && changed.get(1).lengthSqr() > 0, "Supported live native filter replacement was cached");
            // The real native trait resource listener replaces definitions from the active resource manager.
            SgRegistries.TRAIT.onResourceManagerReload(player.level().getServer().getResourceManager());
        } finally { filter.set(oldEffect, original); }
        require(instance.getTrait() != oldDefinition && api.admittedEffect(instance) != oldEffect, "Native reload retained stale magnet definitions");
        var unchanged = retained.copy();
        var current = measure(player, 16, () -> mixedTick(retained, player, player, EquipmentSlot.LEGS, 2));
        require(current.get(0).lengthSqr() > 0 && ItemStack.matches(unchanged, retained), "Retained paid root did not survive native trait reload");
        var restored = measure(player, 10, () -> mixedTick(greedyRoot, player, player, EquipmentSlot.LEGS, 1));
        require(restored.get(0).lengthSqr() > 0 && restored.get(1).equals(Vec3.ZERO), "Native resource reload did not restore current live filter");
        var carrier = api.carrier(retained);
        boolean staleRejected = false;
        try { api.tick(oldEffect, player, instance, carrier, true); }
        catch (IllegalArgumentException expected) { staleRejected = true; }
        require(staleRejected && queue().isEmpty(), "Stale pre-reload effect queued work");
        report.append("Magnet B: supported live filter update plus real native trait-resource reload; retained paid roots resolve new definitions/filter; stale pre-reload callback refused before publication\n");
    }

    private static void trackerControl(ServerPlayer player, StringBuilder report) throws Throwable {
        var item = new ItemEntity(player.level(), player.getX() + 2, player.getY(), player.getZ(), new ItemStack(Items.DIAMOND));
        item.setDeltaMovement(Vec3.ZERO);
        require(player.level().addFreshEntity(item), "Could not spawn native queue arbitration control");
        try {
            flush(player);
            var strongest = new Vec3(.08, 0, 0);
            net.silentchaos512.gear.gear.util.MagnetPullTracker.pushItem(item, new Vec3(.02, 0, 0));
            net.silentchaos512.gear.gear.util.MagnetPullTracker.pushItem(item, strongest);
            net.silentchaos512.gear.gear.util.MagnetPullTracker.pushItem(item, new Vec3(.04, 0, 0));
            require(item.getDeltaMovement().equals(Vec3.ZERO), "Native tracker published before its level-post event");
            flush(player);
            require(item.getDeltaMovement().equals(strongest) && queue().isEmpty(), "Native queue did not retain strongest request and drain once");
        } finally {
            item.discard();
            flush(player);
        }
        report.append("Magnet C consolidation: independent native unequal-vector arbitration/drain control retained from retired spike\n");
    }

    private static void overlap(ServerPlayer player, ItemStack magnetic, ItemStack root, StringBuilder report) throws Throwable {
        ItemStack boots = ItemStack.EMPTY;
        if (Boolean.getBoolean("leggings.test.boots")) {
            var source = MagnetChecks.magnetic(WielderChecks.armor("boots", "boot_plates", "iron"), player);
            var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
            Object pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, source, new ItemStack(Items.DIAMOND_BOOTS));
            boots = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
        }
        ItemStack sibling = boots;
        ItemStack before = sibling.copy();
        var single = measure(player, 16, () -> nativeTick(magnetic, player, EquipmentSlot.LEGS));
        var overlap = measure(player, 16, () -> {
            nativeTick(magnetic, player, EquipmentSlot.MAINHAND);
            if (!sibling.isEmpty()) { sibling.inventoryTick(player.level(), player, EquipmentSlot.FEET); }
            mixedTick(root, player, player, EquipmentSlot.LEGS, 2);
            mixedTick(root.copy(), player, player, EquipmentSlot.OFFHAND, 2);
        });
        same(single, overlap, "actual native/mixed copies/current Boots overlapping ticks");
        require(single.get(0).lengthSqr() > 0 && queue().isEmpty() && ItemStack.matches(before, sibling), "Shared queue/sibling state differs");
        require(SilentGearMagnet.available(), "Normal overlapping ticks revoked Leggings magnets");
        report.append("Magnet B: overlapping actual native/mixed item ticks/current Boots retain native strongest-vector arbitration, not per-source sums; each physical mixed copy dispatches independently\n");
    }

    private static void batchRefusal(ServerPlayer player, ItemStack root, StringBuilder report) throws Throwable {
        var data = MixedLeggingsStructure.validData(root).orElseThrow();
        var source = TraitHelper.getTraitLevel(data.left().source(), DataResource.trait("silentgear:magnetic")) > 0
                ? data.left().source() : data.right().source();
        var definition = MagnetChecks.trait(source, "magnetic").getTrait();
        var effects = field(definition.getClass(), "effects");
        Object original = effects.get(definition);
        try {
            effects.set(definition, List.of()); // Magnetic sorts after valid Greedy, which can attract this diamond.
            var pushes = measure(player, 16, () -> {
                mixedTick(root, player, player, EquipmentSlot.LEGS, 0);
                require(queue().isEmpty(), "Unsafe second definition published the earlier valid batch before cleanup");
            });
            require(pushes.stream().allMatch(Vec3.ZERO::equals) && !SilentGearMagnet.available(), "Whole-batch refusal did not revoke only mixed magnets");
        } finally { effects.set(definition, original); }
        SgRegistries.TRAIT.onResourceManagerReload(player.level().getServer().getResourceManager());
        independence(player, root, report);
        report.append("Magnet B: unsupported second current definition refuses entire batch before queue writes; real native reload does not rearm revocation\n");
    }

    private static void lateFailure(ServerPlayer player, ItemStack root, StringBuilder report) throws Throwable {
        var pushes = measure(player, 16, () -> {
            var sentinel = new ItemEntity(player.level(), player.getX() + 2, player.getY(), player.getZ() - 3, new ItemStack(Items.DIRT));
            sentinel.setNoGravity(true);
            sentinel.setNoPickUpDelay();
            sentinel.setDeltaMovement(Vec3.ZERO);
            require(player.level().addFreshEntity(sentinel), "Could not spawn foreign queue control");
            try {
                var external = new Vec3(0.123, 0, 0);
                net.silentchaos512.gear.gear.util.MagnetPullTracker.pushItem(sentinel, external);
                MagnetDispatchProbe.failAfterCall = true;
                try { mixedTick(root, player, player, EquipmentSlot.LEGS, 1); }
                finally { MagnetDispatchProbe.failAfterCall = false; }
                require(queue().get(sentinel).equals(external) && queue().size() > 1, "Late failure cleared own/foreign queued requests");
                mixedTick(root, player, player, EquipmentSlot.LEGS, 0);
                require(queue().get(sentinel).equals(external), "Revoked tick erased foreign queue");
                flush(player);
                require(sentinel.getDeltaMovement().equals(external), "Foreign queued request did not publish after failure");
            } finally { sentinel.discard(); }
        }, false);
        require(pushes.get(0).lengthSqr() > 0 && !SilentGearMagnet.available(), "Already queued work was rolled back/revocation failed");
        independence(player, root, report);
        report.append("Magnet B: controlled post-native failure publishes earlier work, preserves foreign queue, stops the remaining batch and never replays; other SG paths remain active\n");
    }

    private static void independence(ServerPlayer player, ItemStack root, StringBuilder report) throws Throwable {
        require(measure(player, 16, () -> mixedTick(root, player, player, EquipmentSlot.LEGS, 0)).stream().allMatch(Vec3.ZERO::equals), "Revoked magnet retried on next tick");
        require(!SilentGearMagnet.available(), "Revoked magnet rearmed");
        wearerControl(player, false);
        var source = MagnetChecks.magnetic(FoundationChecks.armor("iron"), player);
        require(measure(player, 16, () -> nativeTick(source, player, EquipmentSlot.LEGS)).getFirst().lengthSqr() > 0,
                "Local refusal disabled native magnets");
        // Reuse maintained independent feature controls, not a second trait engine.
        BrilliantChecks.run(FoundationChecks.player(player.level().getServer()), report);
        if (Boolean.getBoolean("leggings.test.boots")) {
            var type = Class.forName("games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearMagnet");
            require((boolean) type.getMethod("available").invoke(null), "Local refusal revoked Boots readiness");
            var bootSource = MagnetChecks.magnetic(WielderChecks.armor("boots", "boot_plates", "iron"), player);
            var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
            Object pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, bootSource, new ItemStack(Items.DIAMOND_BOOTS));
            ItemStack boots = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
            require(measure(player, 16, () -> boots.inventoryTick(player.level(), player, EquipmentSlot.FEET)).getFirst().lengthSqr() > 0,
                    "Local refusal disabled actual Boots magnet dispatch");
        }
    }

    private static void wearerControl(ServerPlayer fixture, boolean magnetReady) throws Throwable {
        var player = FoundationChecks.player(fixture.level().getServer());
        var source = MagnetChecks.magnetic(FoundationChecks.armor("nickel"), player);
        var root = FoundationChecks.paid(player, source, new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        player.setItemSlot(EquipmentSlot.LEGS, root);
        player.tickCount = 20;
        var pushes = measure(player, 16, () -> mixedTick(root, player, player, EquipmentSlot.LEGS, magnetReady ? 1 : 0));
        require(player.hasEffect(MobEffects.RESISTANCE) && (pushes.getFirst().lengthSqr() > 0) == magnetReady,
                "Independent Wielder activation and magnet lifetime interfered");
    }

    private static void mixedTick(ItemStack root, ServerPlayer player, Entity actor, EquipmentSlot slot, int expectedCalls) {
        var before = root.copy();
        var equipment = Arrays.stream(EquipmentSlot.values()).map(s -> player.getItemBySlot(s).copy()).toList();
        MagnetDispatchProbe.instances.clear();
        MagnetDispatchProbe.observing = true;
        try { root.inventoryTick(player.level(), actor, slot); }
        finally { MagnetDispatchProbe.observing = false; }
        require(MagnetDispatchProbe.instances.size() == expectedCalls, "Production magnet calls=" + MagnetDispatchProbe.instances.size() + " expected=" + expectedCalls);
        require(ItemStack.matches(before, root), "Production item dispatch changed immutable root/source/history/state");
        int index = 0;
        for (var equipped : EquipmentSlot.values()) {
            require(ItemStack.matches(equipment.get(index++), player.getItemBySlot(equipped)), "Magnet tick changed unrelated equipment");
        }
    }

    private static void nativeTick(ItemStack source, ServerPlayer player, EquipmentSlot slot) {
        var copy = source.copy();
        var before = copy.copy();
        copy.inventoryTick(player.level(), player, slot);
        require(ItemStack.matches(before, copy), "Native magnet oracle changed source history/carrier");
    }

    private static ItemStack withTraits(ItemStack source, List<TraitInstance> traits) {
        var copy = source.copy();
        var values = new HashMap<>(copy.get(SgDataComponents.GEAR_PROPERTIES.get()).properties());
        values.put(GearProperties.TRAITS.get(), new TraitListPropertyValue(traits));
        copy.set(SgDataComponents.GEAR_PROPERTIES.get(), new GearPropertiesData(values));
        return copy;
    }

    private static double radius(ItemStack source) throws Exception {
        double radius = 2;
        for (var trait : TraitHelper.getTraits(source)) {
            if (!SilentGearMagnetApi.IDS.contains(trait.getTraitId().toString())) { continue; }
            for (var effect : trait.getTrait().getEffects()) {
                if (effect instanceof ItemMagnetTraitEffect) { radius = Math.max(radius, 1 + field(effect.getClass(), "effectRange").getFloat(effect) * trait.getLevel()); }
            }
        }
        return radius;
    }

    private static List<Vec3> measure(ServerPlayer player, double radius, Action action) throws Throwable {
        return measure(player, radius, action, true);
    }

    private static List<Vec3> measure(ServerPlayer player, double radius, Action action, boolean checkDeferred) throws Throwable {
        flush(player);
        var items = new ArrayList<ItemEntity>();
        double[][] offsets = {{2, 0, 0}, {2, 0, 1}, {radius - 0.25, 0, 0}, {radius + 0.25, 0, 0},
                {2, 0, -1}, {2, 0, -2}, {radius * 0.8, 0, radius * 0.8}, {radius, 0, 0}, {2, 1, 0}, {2, -1, 0}};
        try {
            for (int i = 0; i < offsets.length; i++) {
                var at = offsets[i];
                var item = new ItemEntity(player.level(), player.getX() + at[0], player.getY() + at[1], player.getZ() + at[2],
                        new ItemStack(i == 1 ? Items.DIRT : Items.DIAMOND));
                item.setNoGravity(true);
                item.setNoPickUpDelay();
                item.setDeltaMovement(Vec3.ZERO);
                if (i == 4) { item.setPickUpDelay(20); }
                if (i == 5) { item.getPersistentData().putBoolean("PreventRemoteMovement", true); }
                require(player.level().addFreshEntity(item), "Could not spawn magnet acceptance item");
                items.add(item);
            }
            require(player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(radius + 2)).containsAll(items),
                    "Magnet controls are not all query-visible");
            action.run();
            if (checkDeferred) { require(items.stream().allMatch(item -> item.getDeltaMovement().equals(Vec3.ZERO)), "Magnet bypassed native level-post publication"); }
            flush(player);
            return items.stream().map(ItemEntity::getDeltaMovement).toList();
        } finally {
            items.forEach(ItemEntity::discard);
            flush(player);
        }
    }

    private static void same(List<Vec3> expected, List<Vec3> actual, String label) {
        for (int i = 0; i < expected.size(); i++) {
            require(expected.get(i).distanceToSqr(actual.get(i)) < 1E-20, "Native/mixed impulse mismatch " + label + " item=" + i);
        }
    }

    private static void flush(ServerPlayer player) { NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, player.level())); }

    private static Map<?, ?> queue() throws ReflectiveOperationException {
        return (Map<?, ?>) field(net.silentchaos512.gear.gear.util.MagnetPullTracker.class, "ITEMS_TO_PUSH").get(null);
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @FunctionalInterface private interface Action { void run() throws Throwable; }
}
