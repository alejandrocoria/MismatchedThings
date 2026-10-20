package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearWearerApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.nativeClass;

/** Opt-in feasibility only: actual native magnet callbacks/tracker, never a production item hook. */
public final class SilentGearMagnetSpike {
    private static boolean observing;
    private static int calls;
    private final ServerPlayer player;
    private final Class<?> magnet = nativeClass("gear.trait.effect.ItemMagnetTraitEffect");
    private final Method tick = magnet.getMethod("inventoryTick", nativeClass("api.traits.TraitActionContext"),
            Level.class, net.minecraft.world.entity.Entity.class, boolean.class);
    private final SilentGearWearerApi.Carriers carriers = new SilentGearWearerApi.Carriers();
    private final ModConfigSpec.BooleanValue crouch = (ModConfigSpec.BooleanValue)
            nativeClass("Config$Common").getField("magnetPullDisabledOnCrouch").get(null);
    private final Map<?, ?> pending;

    private SilentGearMagnetSpike(ServerPlayer player) throws Exception {
        this.player = player;
        var field = nativeClass("gear.util.MagnetPullTracker").getDeclaredField("ITEMS_TO_PUSH");
        field.setAccessible(true); pending = (Map<?, ?>) field.get(null);
    }

    public static void nativeCall() { if (observing) { calls++; } }

    static void run(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Throwable {
        SilentGearTraitSpike.initialize(api);
        var spike = new SilentGearMagnetSpike(player); boolean oldCrouch = spike.crouch.get();
        try {
            clear(player); player.level().getChunk(0, 0);
            ItemStack iron = api.boot("iron"), greedy = api.boot("blaze_gold");
            ItemStack upgrade = new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.parse("silentgear:magnetic_upgrade")).orElseThrow());
            ItemStack before = iron.copy(), upgradeBefore = upgrade.copy();
            Object recipe = api.swapClass.getField("INSTANCE").get(null);
            var input = CraftingInput.of(2, 1, List.of(iron, upgrade));
            require((boolean) api.matches.invoke(recipe, input, player.level()), "Native grid refuses Magnetic Upgrade on boots");
            ItemStack magnetic = (ItemStack) api.assemble.invoke(recipe, input);
            require(api.traitLevel(magnetic, "magnetic") == 5 && api.traitLevel(greedy, "greedy") > 0,
                    "Native acquisition did not yield Magnetic V / Blaze Gold Greedy");
            require(ItemStack.matches(before, iron) && ItemStack.matches(upgradeBefore, upgrade), "Native recipe preview mutated inputs");
            var menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.create(player.level(), BlockPos.ZERO));
            menu.getSlot(SmithingMenu.BASE_SLOT).set(iron.copy()); menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(upgrade.copy());
            ItemStack smithing = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
            require(!smithing.isEmpty() && ItemStack.matches(smithing, iron), "Pinned native smithing no-op changed; re-examine acquisition");
            report.append("MAGNET SPIKE: test-only native effect dispatch, not production support\n")
                    .append("Acquisition: real native part-swap grid installs Magnetic V; Blaze Gold computes Greedy ")
                    .append(api.traitLevel(greedy, "greedy")).append("; native smithing preview returns unchanged iron boots (upstream copied-result loss)\n");

            // A real attempted recalculation calibrates the already-maintained independent tripwire.
            SilentGearWearerFixture.active = true;
            try {
                boolean witnessed = false;
                try { nativeClass("util.GearData").getMethod("recalculateGearData", ItemStack.class, net.minecraft.world.entity.player.Player.class).invoke(null, iron.copy(), player); }
                catch (InvocationTargetException expected) { witnessed = expected.getCause() instanceof AssertionError; }
                require(witnessed, "Magnet read-only witness was not calibrated");
            } finally { SilentGearWearerFixture.active = false; }

            spike.crouch.set(true);
            for (var sample : List.of(new Sample("magnetic", magnetic), new Sample("greedy", greedy))) {
                Object instance = SilentGearTraitSpike.view(sample.stack).get("silentgear:" + sample.id);
                Object effect = spike.effect(instance); float radius = spike.radius(effect, instance);
                ItemStack nativeBefore = sample.stack.copy();
                List<ItemStack> roots = NeoForgeSilentGearTest.workbench(player, sample.stack, new ItemStack(Items.DIAMOND_BOOTS));
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, null}) {
                    place(player, sample.stack, slot);
                    var control = spike.measure(radius, () -> sample.stack.inventoryTick(player.level(), player, slot));
                    require(control.calls == 1, "Native callback observer did not see exactly one magnet invocation");
                    spike.filters(control, sample.id, slot != null);
                    for (ItemStack root : roots) {
                        place(player, root, slot);
                        var absent = spike.measure(radius, () -> root.inventoryTick(player.level(), player, slot));
                        require(absent.calls == 0 && absent.pushes.stream().allMatch(Vec3.ZERO::equals), "Production unexpectedly implements magnets");
                        var candidate = spike.measure(radius, () -> spike.candidate(root, slot));
                        same(control, candidate);
                        require(candidate.calls == (slot == null ? 0 : 1), "Duplicate/idle candidate native dispatch");
                    }
                }
                for (boolean disable : List.of(true, false)) {
                    place(player, sample.stack, EquipmentSlot.FEET);
                    spike.crouch.set(disable); player.setShiftKeyDown(true); player.setPose(Pose.CROUCHING);
                    require(player.isCrouching(), "Crouch fixture is not actually crouching");
                    var control = spike.measure(radius, () -> sample.stack.inventoryTick(player.level(), player, EquipmentSlot.FEET));
                    spike.filters(control, sample.id, !disable);
                    for (ItemStack root : roots) {
                        place(player, root, EquipmentSlot.FEET); player.setShiftKeyDown(true); player.setPose(Pose.CROUCHING);
                        same(control, spike.measure(radius, () -> spike.candidate(root, EquipmentSlot.FEET)));
                    }
                    player.setShiftKeyDown(false); player.setPose(Pose.STANDING);
                }
                spike.crouch.set(true);
                var nativeResult = spike.measure(radius, () -> sample.stack.inventoryTick(player.level(), player, EquipmentSlot.FEET));
                for (ItemStack root : NeoForgeSilentGearTest.workbench(player, sample.stack, sample.stack)) {
                    var duplicate = spike.measure(radius, () -> spike.candidate(root, EquipmentSlot.FEET));
                    same(nativeResult, duplicate); require(duplicate.calls == 1, "Duplicate trait ID dispatched twice");
                }
                require(ItemStack.matches(nativeBefore, sample.stack), "Native magnet tick mutated source control");
                report.append(sample.id).append(": native radius=").append(radius)
                        .append("; FEET/main/offhand/idle, crouch config on/off, both paid inverses, duplicate sources; native vectors/filter parity\n");
            }
            // Explicit native-instance fixture for datapack levels, not a claim of a stock level-I recipe.
            ItemStack weaker = weakerGreedy(api, greedy);
            int low = api.traitLevel(weaker, "greedy"), high = api.traitLevel(greedy, "greedy");
            require(low == 1 && low < high, "Supplied native Greedy control is not weaker");
            Object strongest = SilentGearTraitSpike.view(greedy).get("silentgear:greedy");
            float greedyRadius = spike.radius(spike.effect(strongest), strongest);
            var strongControl = spike.measure(greedyRadius, () -> greedy.inventoryTick(player.level(), player, EquipmentSlot.FEET));
            for (ItemStack root : NeoForgeSilentGearTest.workbench(player, weaker, greedy)) {
                var mixed = spike.measure(greedyRadius, () -> spike.candidate(root, EquipmentSlot.FEET));
                same(strongControl, mixed); require(mixed.calls == 1, "Unequal same-ID traits did not dispatch highest level once");
            }
            report.append("Unequal native Greedy levels ").append(low).append('/').append(high)
                    .append(": explicit level-I native-instance fixture (not stock acquisition); both paid inverses use highest source level once, not owner/sum\n");
            float radius = spike.radius(spike.effect(SilentGearTraitSpike.view(magnetic).get("silentgear:magnetic")),
                    SilentGearTraitSpike.view(magnetic).get("silentgear:magnetic"));
            place(player, greedy, EquipmentSlot.FEET); player.setItemSlot(EquipmentSlot.MAINHAND, magnetic);
            var together = spike.measure(radius, () -> {
                magnetic.inventoryTick(player.level(), player, EquipmentSlot.MAINHAND);
                greedy.inventoryTick(player.level(), player, EquipmentSlot.FEET);
            });
            var magneticOnly = spike.measure(radius, () -> magnetic.inventoryTick(player.level(), player, EquipmentSlot.FEET));
            same(together, magneticOnly); require(together.calls == 2, "Native overlapping magnets were not both called");
            for (ItemStack root : NeoForgeSilentGearTest.workbench(player, magnetic, greedy)) {
                place(player, root, EquipmentSlot.FEET);
                var both = spike.measure(radius, () -> spike.candidate(root, EquipmentSlot.FEET));
                same(together, both); require(both.calls == 2, "Distinct magnet trait dispatch differs");
                ItemStack held = root.copy(); player.setItemSlot(EquipmentSlot.OFFHAND, held);
                var copies = spike.measure(radius, () -> { spike.candidate(root, EquipmentSlot.FEET); spike.candidate(held, EquipmentSlot.OFFHAND); });
                same(together, copies); require(copies.calls == 4, "Held/worn copies were not both exercised");
                var idle = spike.measure(radius, () -> spike.candidate(root, null));
                require(idle.calls == 0 && idle.pushes.stream().allMatch(Vec3.ZERO::equals), "Idle combination attracted items");
            }
            Object instance = SilentGearTraitSpike.view(magnetic).get("silentgear:magnetic");
            var noPlayer = spike.measure(radius, () -> spike.tick.invoke(spike.effect(instance),
                    SilentGearTraitSpike.context(null, instance, magnetic.copy()), player.level(), player, true));
            require(noPlayer.calls == 1 && noPlayer.pushes.stream().allMatch(Vec3.ZERO::equals), "Native null-player control did not exit");
            report.append("Overlap: two distinct native traits / both mixed inverses / held+worn copies retain native strongest-impulse selection, not summed acceleration\n")
                    .append("Native LevelTickEvent.Post publishes queued impulses; range sphere, item filter, pickup delay and PreventRemoteMovement controls; immutable root/source/carrier/equipment; zero native recalculations\n")
                    .append("No production routing/guard, client packets, real-time pickup, reload/lifecycle or native mixed-upgrade editing acceptance claimed\n");
        } finally {
            observing = false; SilentGearWearerFixture.active = false; spike.crouch.set(oldCrouch);
            clear(player); spike.flush();
        }
    }

    private static ItemStack weakerGreedy(NeoForgeSilentGearTest.NativeApi api, ItemStack nativeBoot) throws Exception {
        ItemStack copy = nativeBoot.copy(); Object props = copy.get(api.properties);
        var values = new java.util.HashMap<Object, Object>((Map<?, ?>) props.getClass().getMethod("properties").invoke(props));
        Object key = ((java.util.function.Supplier<?>) nativeClass("setup.gear.GearProperties").getField("TRAITS").get(null)).get();
        var instances = new ArrayList<Object>((List<?>) values.get(key).getClass().getMethod("value").invoke(values.get(key)));
        Object old = SilentGearTraitSpike.view(copy).get("silentgear:greedy");
        Object low = nativeClass("api.traits.TraitInstance").getMethod("of", nativeClass("gear.trait.Trait"), int.class, java.util.Collection.class)
                .invoke(null, SilentGearTraitSpike.traitOf(old), 1, List.of());
        require(instances.remove(old), "Supplied Greedy control could not locate native property instance"); instances.add(low);
        values.put(key, nativeClass("api.property.TraitListPropertyValue").getConstructor(List.class).newInstance(instances));
        copy.set(api.properties, props.getClass().getConstructor(Map.class).newInstance(values)); return copy;
    }

    private void candidate(ItemStack root, EquipmentSlot slot) throws Throwable {
        if (slot == null) { return; }
        ItemStack before = root.copy(), carrier = carriers.carrier(root), carrierBefore = carrier.copy();
        var equipment = new ArrayList<ItemStack>(); for (var value : EquipmentSlot.values()) { equipment.add(player.getItemBySlot(value).copy()); }
        SilentGearWearerFixture.active = true;
        try {
            for (var entry : SilentGearCompatibility.effectiveTraits(root)) {
                if (!List.of("silentgear:magnetic", "silentgear:greedy").contains(entry.id().toString())) { continue; }
                Object instance = entry.nativeInstance();
                tick.invoke(effect(instance), SilentGearTraitSpike.context(player, instance, carrier), player.level(), player, true);
            }
        } finally { SilentGearWearerFixture.active = false; }
        require(ItemStack.matches(before, root) && ItemStack.matches(carrierBefore, carrier), "Candidate mutated root/source/carrier");
        int index = 0; for (var value : EquipmentSlot.values()) { require(ItemStack.matches(equipment.get(index++), player.getItemBySlot(value)), "Candidate replaced/changed real equipment"); }
    }

    private Object effect(Object instance) throws Exception {
        var effects = (List<?>) SilentGearTraitSpike.traitOf(instance).getClass().getMethod("getEffects").invoke(SilentGearTraitSpike.traitOf(instance));
        var matches = effects.stream().filter(value -> value.getClass() == magnet).toList();
        require(matches.size() == 1, "Expected one exact native magnet effect per trait"); return matches.getFirst();
    }

    private float radius(Object effect, Object instance) throws Exception {
        var field = magnet.getDeclaredField("effectRange"); field.setAccessible(true);
        return field.getFloat(effect) * SilentGearTraitSpike.level(instance) + 1;
    }

    private Result measure(float radius, Action action) throws Throwable {
        flush(); var items = new ArrayList<ItemEntity>();
        var stacks = List.of(Items.DIAMOND, Items.DIRT, Items.IRON_INGOT, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND);
        double[][] offsets = {{2, 0, 0}, {2, 0, 1}, {3, 0, 0}, {2, 0, -1}, {2, 0, -2}, {radius - 0.25, 0, 0}, {radius + 0.25, 0, 0}, {radius * .8, 0, radius * .8}, {2, 3, 0}};
        try {
            for (int index = 0; index < stacks.size(); index++) {
                var offset = offsets[index]; double x = player.getX() + offset[0], z = player.getZ() + offset[2];
                player.level().getChunk(BlockPos.containing(x, player.getY(), z));
                var item = new ItemEntity(player.level(), x, player.getY() + offset[1], z, new ItemStack(stacks.get(index)));
                item.setNoGravity(true); item.setNoPickUpDelay(); item.setDeltaMovement(Vec3.ZERO);
                if (index == 3) { item.setPickUpDelay(20); }
                if (index == 4) { item.getPersistentData().putBoolean("PreventRemoteMovement", true); }
                require(player.level().addFreshEntity(item), "Could not add actual magnet item control"); items.add(item);
            }
            calls = 0; observing = true; action.run(); observing = false;
            require(items.stream().allMatch(item -> item.getDeltaMovement().equals(Vec3.ZERO)), "Native magnet bypassed deferred tracker");
            int observedCalls = calls; flush();
            return new Result(items.stream().map(ItemEntity::getDeltaMovement).toList(), observedCalls);
        } finally { observing = false; items.forEach(ItemEntity::discard); flush(); }
    }

    private void flush() {
        NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, player.level()));
        require(pending.isEmpty(), "Native level-post tracker did not drain its queue");
    }

    private void filters(Result value, String id, boolean active) {
        for (int index = 0; index < value.pushes.size(); index++) {
            boolean eligible = active && index != 3 && index != 4 && index != 6 && index != 7 && !(id.equals("greedy") && index == 1);
            require((value.pushes.get(index).lengthSqr() > 0) == eligible, "Native filter/range control differs: " + id + " item=" + index);
        }
    }

    private static void same(Result expected, Result actual) {
        require(expected.pushes.size() == actual.pushes.size(), "Incomplete impulse control");
        for (int i = 0; i < expected.pushes.size(); i++) { require(expected.pushes.get(i).distanceToSqr(actual.pushes.get(i)) < 1E-20, "Candidate impulse differs from native item callback: " + i); }
    }

    private static void place(ServerPlayer player, ItemStack root, EquipmentSlot slot) {
        clear(player); if (slot == null) { player.getInventory().setItem(10, root); } else { player.setItemSlot(slot, root); }
    }
    private static void clear(ServerPlayer player) {
        player.getInventory().clearContent(); for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
        player.setShiftKeyDown(false); player.setPose(Pose.STANDING); player.removeAllEffects();
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
    private record Sample(String id, ItemStack stack) {}
    private record Result(List<Vec3> pushes, int calls) {}
    private interface Action { void run() throws Throwable; }
}
