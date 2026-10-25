package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearMagnet;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearMagnetApi;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.nativeClass;

/** Production mixed item ticks against independent native controls; synchronous impulses, not real-time pickup. */
public final class SilentGearMagnetTest {
    private static boolean observing;
    private static int calls;
    private static ItemStack observedCarrier, carrierBefore;
    private static boolean failAfterQueue;
    private static Reload reload;
    private final ServerPlayer player;
    private final Class<?> magnet = nativeClass("gear.trait.effect.ItemMagnetTraitEffect");
    private final Method tick = magnet.getMethod("inventoryTick", nativeClass("api.traits.TraitActionContext"),
                                                 Level.class, net.minecraft.world.entity.Entity.class, boolean.class);
    private final SilentGearMagnetApi guarded = new SilentGearMagnetApi();
    private final ModConfigSpec.BooleanValue crouch = (ModConfigSpec.BooleanValue)
            nativeClass("Config$Common").getField("magnetPullDisabledOnCrouch").get(null);
    private final Map<?, ?> pending;

    private SilentGearMagnetTest(ServerPlayer player) throws Exception {
        this.player = player;
        var field = nativeClass("gear.util.MagnetPullTracker").getDeclaredField("ITEMS_TO_PUSH");
        field.setAccessible(true);
        pending = (Map<?, ?>) field.get(null);
    }

    public static void beforeCall(Object context) throws Exception {
        if (!observing) {
            return;
        }
        calls++;
        observedCarrier = (ItemStack) context.getClass().getMethod("gear").invoke(context);
        carrierBefore = observedCarrier.copy();
    }

    public static void afterCall() {
        if (!observing) {
            return;
        }
        require(ItemStack.matches(carrierBefore, observedCarrier), "Native magnet changed its disposable context gear");
        if (failAfterQueue) {
            failAfterQueue = false;
            throw new IllegalStateException("Controlled failure after native magnet queue publication");
        }
    }

    static void run(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Throwable {
        SilentGearTraitSpike.initialize(api);
        var spike = new SilentGearMagnetTest(player);
        boolean oldCrouch = spike.crouch.get();
        try {
            clear(player);
            player.tickCount = 21;
            player.level().getChunk(0, 0);
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
            menu.getSlot(SmithingMenu.BASE_SLOT).set(iron.copy());
            menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(upgrade.copy());
            ItemStack smithing = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
            require(!smithing.isEmpty() && ItemStack.matches(smithing, iron), "Pinned native smithing no-op changed; re-examine acquisition");
            report.append("MAGNET B: actual production mixed inventory ticks; independent native controls\n")
                  .append("Acquisition: real native part-swap grid installs Magnetic V; Blaze Gold computes Greedy ")
                  .append(api.traitLevel(greedy, "greedy"))
                  .append("; native smithing preview returns unchanged iron boots (upstream copied-result loss)\n");

            // A real attempted recalculation calibrates the already-maintained independent tripwire.
            SilentGearWearerFixture.active = true;
            try {
                boolean witnessed = false;
                try {
                    nativeClass("util.GearData").getMethod("recalculateGearData", ItemStack.class, net.minecraft.world.entity.player.Player.class)
                                                .invoke(null, iron.copy(), player);
                } catch (InvocationTargetException expected) {
                    witnessed = expected.getCause() instanceof AssertionError;
                }
                require(witnessed, "Magnet read-only witness was not calibrated");
            } finally {
                SilentGearWearerFixture.active = false;
            }

            String variant = System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant");
            boolean refused = List.of("magnet-effect", "magnet-queue").contains(variant);
            require(SilentGearMagnet.available() != refused, "Magnet startup availability differs from final proof");
            if (refused) {
                spike.refusal(player, api, magnetic, report);
                return;
            }
            spike.guarded.requireProof();
            if (variant.equals("magnet-definition")) {
                spike.batchRefusal(api, magnetic, greedy, report);
                return;
            }
            if (variant.equals("magnet-throw")) {
                spike.partialFailure(api, magnetic, greedy, report);
                return;
            }

            spike.crouch.set(true);
            for (var sample : List.of(new Sample("magnetic", magnetic), new Sample("greedy", greedy))) {
                Object instance = SilentGearTraitSpike.view(sample.stack).get("silentgear:" + sample.id);
                Object effect = spike.effect(instance);
                float radius = spike.radius(effect, instance);
                ItemStack nativeBefore = sample.stack.copy();
                List<ItemStack> roots = NeoForgeSilentGearTest.workbench(player, sample.stack, new ItemStack(Items.DIAMOND_BOOTS));
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, null}) {
                    place(player, sample.stack, slot);
                    var control = spike.measure(radius, () -> sample.stack.inventoryTick(player.level(), player, slot));
                    require(control.calls == 0, "Native control incorrectly passed through our binding");
                    spike.filters(control, sample.id, slot != null);
                    for (ItemStack root : roots) {
                        place(player, root, slot);
                        var mixed = spike.measure(radius, () -> spike.mixedTick(root, slot));
                        same(control, mixed);
                        require(mixed.calls == (slot == null ? 0 : 1), "Duplicate/idle production native dispatch");
                    }
                }
                for (boolean disable : List.of(true, false)) {
                    place(player, sample.stack, EquipmentSlot.FEET);
                    spike.crouch.set(disable);
                    player.setShiftKeyDown(true);
                    player.setPose(Pose.CROUCHING);
                    require(player.isCrouching(), "Crouch fixture is not actually crouching");
                    var control = spike.measure(radius, () -> sample.stack.inventoryTick(player.level(), player, EquipmentSlot.FEET));
                    spike.filters(control, sample.id, !disable);
                    for (ItemStack root : roots) {
                        place(player, root, EquipmentSlot.FEET);
                        player.setShiftKeyDown(true);
                        player.setPose(Pose.CROUCHING);
                        same(control, spike.measure(radius, () -> spike.mixedTick(root, EquipmentSlot.FEET)));
                    }
                    player.setShiftKeyDown(false);
                    player.setPose(Pose.STANDING);
                }
                spike.crouch.set(true);
                var nativeResult = spike.measure(radius, () -> sample.stack.inventoryTick(player.level(), player, EquipmentSlot.FEET));
                for (ItemStack root : NeoForgeSilentGearTest.workbench(player, sample.stack, sample.stack)) {
                    var duplicate = spike.measure(radius, () -> spike.mixedTick(root, EquipmentSlot.FEET));
                    same(nativeResult, duplicate);
                    require(duplicate.calls == 1, "Duplicate trait ID dispatched twice");
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
                var mixed = spike.measure(greedyRadius, () -> spike.mixedTick(root, EquipmentSlot.FEET));
                same(strongControl, mixed);
                require(mixed.calls == 1, "Unequal same-ID traits did not dispatch highest level once");
            }
            report.append("Unequal native Greedy levels ").append(low).append('/').append(high)
                  .append(": explicit level-I native-instance fixture (not stock acquisition); both paid inverses use highest source level once, not owner/sum\n");
            float radius = spike.radius(spike.effect(SilentGearTraitSpike.view(magnetic).get("silentgear:magnetic")),
                                        SilentGearTraitSpike.view(magnetic).get("silentgear:magnetic"));
            place(player, greedy, EquipmentSlot.FEET);
            player.setItemSlot(EquipmentSlot.MAINHAND, magnetic);
            var together = spike.measure(radius, () -> {
                magnetic.inventoryTick(player.level(), player, EquipmentSlot.MAINHAND);
                greedy.inventoryTick(player.level(), player, EquipmentSlot.FEET);
            });
            var magneticOnly = spike.measure(radius, () -> magnetic.inventoryTick(player.level(), player, EquipmentSlot.FEET));
            same(together, magneticOnly);
            require(together.calls == 0, "Native peers passed through the optional mixed binding");
            for (ItemStack root : NeoForgeSilentGearTest.workbench(player, magnetic, greedy)) {
                place(player, root, EquipmentSlot.FEET);
                var both = spike.measure(radius, () -> spike.mixedTick(root, EquipmentSlot.FEET));
                same(together, both);
                require(both.calls == 2, "Distinct magnet trait dispatch differs");
                ItemStack held = root.copy();
                player.setItemSlot(EquipmentSlot.OFFHAND, held);
                var copies = spike.measure(radius, () -> {
                    spike.mixedTick(root, EquipmentSlot.FEET);
                    spike.mixedTick(held, EquipmentSlot.OFFHAND);
                });
                same(together, copies);
                require(copies.calls == 4, "Held/worn copies were not both exercised");
                var peers = spike.measure(radius, () -> {
                    spike.mixedTick(root, EquipmentSlot.FEET);
                    magnetic.inventoryTick(player.level(), player, EquipmentSlot.MAINHAND);
                });
                same(together, peers);
                require(peers.calls == 2, "Mixed/native peers changed dispatch count");
                var idle = spike.measure(radius, () -> spike.mixedTick(root, null));
                require(idle.calls == 0 && idle.pushes.stream().allMatch(Vec3.ZERO::equals), "Idle combination attracted items");
            }
            Object instance = SilentGearTraitSpike.view(magnetic).get("silentgear:magnetic");
            var noPlayer = spike.measure(radius, () -> spike.tick.invoke(spike.effect(instance),
                                                                         SilentGearTraitSpike.context(null, instance, magnetic.copy()),
                                                                         player.level(),
                                                                         player,
                                                                         true));
            require(noPlayer.calls == 0 && noPlayer.pushes.stream().allMatch(Vec3.ZERO::equals), "Native null-player control did not exit");
            spike.definitions(api, greedy, report);
            spike.negatives(api, magnetic, report);
            if (variant.equals("magnet-server")) {
                spike.joint(api, magnetic, report);
            }
            spike.independent(api, report);
            report.append(
                          "Overlap: two distinct native traits / both mixed inverses / held+worn copies retain native strongest-impulse selection, not summed acceleration\n")
                  .append("Native LevelTickEvent.Post publishes queued impulses; range sphere, item filter, pickup delay and PreventRemoteMovement controls; immutable root/source/carrier/equipment; zero native recalculations\n")
                  .append("Synchronous server callbacks/registered level-post publication only; real-time motion/pickup, connected client and lifecycle remain C\n");
        } finally {
            observing = false;
            SilentGearWearerFixture.active = false;
            spike.crouch.set(oldCrouch);
            clear(player);
            spike.flush();
        }
    }

    private static ItemStack weakerGreedy(NeoForgeSilentGearTest.NativeApi api, ItemStack nativeBoot) throws Exception {
        ItemStack copy = nativeBoot.copy();
        Object props = copy.get(api.properties);
        var values = new java.util.HashMap<Object, Object>((Map<?, ?>) props.getClass().getMethod("properties").invoke(props));
        Object key = ((java.util.function.Supplier<?>) nativeClass("setup.gear.GearProperties").getField("TRAITS").get(null)).get();
        var instances = new ArrayList<Object>((List<?>) values.get(key).getClass().getMethod("value").invoke(values.get(key)));
        Object old = SilentGearTraitSpike.view(copy).get("silentgear:greedy");
        Object low = nativeClass("api.traits.TraitInstance").getMethod("of", nativeClass("gear.trait.Trait"), int.class, java.util.Collection.class)
                                                            .invoke(null, SilentGearTraitSpike.traitOf(old), 1, List.of());
        require(instances.remove(old), "Supplied Greedy control could not locate native property instance");
        instances.add(low);
        values.put(key, nativeClass("api.property.TraitListPropertyValue").getConstructor(List.class).newInstance(instances));
        copy.set(api.properties, props.getClass().getConstructor(Map.class).newInstance(values));
        return copy;
    }

    private void mixedTick(ItemStack root, EquipmentSlot slot) {
        ItemStack before = root.copy();
        var equipment = new ArrayList<ItemStack>();
        for (var value : EquipmentSlot.values()) {
            equipment.add(player.getItemBySlot(value).copy());
        }
        SilentGearWearerFixture.active = true;
        try {
            root.inventoryTick(player.level(), player, slot);
        } finally {
            SilentGearWearerFixture.active = false;
        }
        require(ItemStack.matches(before, root), "Production magnet tick mutated root/sources");
        int index = 0;
        for (var value : EquipmentSlot.values()) {
            require(ItemStack.matches(equipment.get(index++), player.getItemBySlot(value)), "Production tick replaced/changed real equipment");
        }
    }

    private void refusal(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, ItemStack magnetic, StringBuilder report) throws Throwable {
        var nativeControl = measure(16, () -> magnetic.inventoryTick(player.level(), player, EquipmentSlot.FEET));
        filters(nativeControl, "magnetic", true);
        ItemStack greedy = api.boot("blaze_gold");
        filters(measure(7, () -> greedy.inventoryTick(player.level(), player, EquipmentSlot.FEET)), "greedy", true);
        for (ItemStack root : NeoForgeSilentGearTest.workbench(player, magnetic, new ItemStack(Items.DIAMOND_BOOTS))) {
            ItemStack before = root.copy();
            for (int attempt = 0; attempt < 3; attempt++) {
                var refused = measure(16, () -> mixedTick(root, EquipmentSlot.FEET));
                require(refused.calls == 0 && refused.pushes.stream().allMatch(Vec3.ZERO::equals), "Refused binding published native impulses");
            }
            require(ItemStack.matches(before, root), "Refused magnet binding changed complete root/sources");
        }
        independent(api, report);
        report.append(
                "Magnet refusal: both native traits/tracker still work; both paid inverses refuse production ticks three times before invocation/queue writes; other SG features survive\n");
    }

    private void independent(NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Exception {
        ItemStack stellar = NeoForgeSilentGearTest.workbench(player, api.boot("refined_iron"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        place(player, stellar, EquipmentSlot.FEET);
        player.tickCount = 20;
        stellar.inventoryTick(player.level(), player, EquipmentSlot.FEET);
        require(player.hasEffect(net.minecraft.world.effect.MobEffects.JUMP_BOOST), "Magnet refusal disabled Wielder");
        ItemStack brilliant = NeoForgeSilentGearTest.workbench(player, api.boot("blaze_gold"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        place(player, brilliant, EquipmentSlot.FEET);
        require(net.minecraft.world.entity.monster.piglin.PiglinAi.isWearingSafeArmor(player), "Magnet refusal disabled Brilliant");
        ItemStack moon = NeoForgeSilentGearTest.workbench(player, api.boot("azure_silver"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        place(player, moon, EquipmentSlot.FEET);
        var fall = new net.neoforged.neoforge.event.entity.living.LivingFallEvent(player, 10, 1);
        NeoForge.EVENT_BUS.post(fall);
        require(fall.getDistance() > 0 && fall.getDistance() < 10, "Magnet refusal disabled Moonwalker");
        SilentGearEffectsTest.smoke(player, api, report);
        SilentGearLiveStateTest.smoke(player, api, report);
        player.tickCount = 21;
        report.append("Independent production Wielder/Brilliant/Moonwalker/repair/live-state smoke passed\n");
    }

    private void negatives(NeoForgeSilentGearTest.NativeApi api, ItemStack magnetic, StringBuilder report) throws Throwable {
        ItemStack root = NeoForgeSilentGearTest.workbench(player, magnetic, new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        var invalid = new ArrayList<ItemStack>();
        ItemStack copy = root.copy();
        copy.setCount(2);
        invalid.add(copy);
        copy = root.copy();
        copy.remove(DataComponents.EQUIPPABLE);
        invalid.add(copy);
        copy = root.copy();
        copy.set(DataComponents.EQUIPPABLE, new ItemStack(Items.DIAMOND_HELMET).get(DataComponents.EQUIPPABLE));
        invalid.add(copy);
        copy = root.copy();
        copy.remove(ModContent.mixedBootsDataType().orElseThrow());
        invalid.add(copy);
        copy = new ItemStack(Items.IRON_BOOTS);
        copy.set(ModContent.mixedBootsDataType().orElseThrow(), ModContent.getMixedBootsData(root).orElseThrow());
        invalid.add(copy);
        invalid.add(new ItemStack(Items.DIAMOND_BOOTS));
        invalid.addAll(NeoForgeSilentGearTest.workbench(player, api.boot("iron"), new ItemStack(Items.DIAMOND_BOOTS)));
        for (ItemStack value : invalid) {
            var result = measure(16, () -> mixedTick(value, EquipmentSlot.FEET));
            require(result.calls == 0 && result.pushes.stream().allMatch(Vec3.ZERO::equals), "Invalid/ordinary root attracted items");
        }
        var stand = new net.minecraft.world.entity.decoration.ArmorStand(player.level(), 0, 80, 0);
        ItemStack before = root.copy();
        var nonPlayer = measure(16, () -> root.inventoryTick(player.level(), stand, EquipmentSlot.FEET));
        require(nonPlayer.calls == 0 && nonPlayer.pushes.stream().allMatch(Vec3.ZERO::equals) && ItemStack.matches(before, root),
                "Non-player attracted items or changed root");
        var wrongSlot = measure(16, () -> mixedTick(root, EquipmentSlot.HEAD));
        require(wrongSlot.calls == 0 && wrongSlot.pushes.stream().allMatch(Vec3.ZERO::equals), "Unsupported selected slot attracted items");
        report.append(
                "Negatives: vanilla/no-magnet, malformed count/data/equippable, foreign component spoof, non-player and unsupported slot; idle covered with both inverses\n");
    }

    private void joint(NeoForgeSilentGearTest.NativeApi api, ItemStack magnetic, StringBuilder report) throws Throwable {
        var jdt = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:eclipsealloy_boots")));
        JustDireAttributeTestSupport.set(jdt, "stepheight_upgrade_installed", true);
        JustDireAttributeTestSupport.set(jdt, "stepheight_toggle", true);
        JustDireAttributeTestSupport.set(jdt, "groundstomp_upgrade_installed", true);
        Object binding = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                              .getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance("groundstomp", 71, false, true);
        Object cooldown = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown")
                               .getConstructor(String.class, int.class, boolean.class).newInstance("groundstomp", 80, false);
        jdt.set(JustDireResourceTestSupport.energyComponent(), 30_000);
        var nativeResult = measure(16, () -> magnetic.inventoryTick(player.level(), player, EquipmentSlot.FEET));
        for (ItemStack root : NeoForgeSilentGearTest.workbench(player, magnetic, jdt)) {
            require(root.has(games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireAttributesCompatibility.projections()),
                    "Joint magnet root lacks JDT projections");
            // Live settings are root-owned, not imported from the non-owner source.
            JustDireAttributeTestSupport.set(root, "ability_bindings", List.of(binding));
            JustDireAttributeTestSupport.set(root, "ability_cooldowns", List.of(cooldown));
            for (boolean enabled : List.of(false, true)) {
                for (int energy : List.of(0, 30_000)) {
                    JustDireAttributeTestSupport.set(root, "tool_enabled", enabled);
                    root.set(JustDireResourceTestSupport.energyComponent(), energy);
                    place(player, root, EquipmentSlot.FEET);
                    var actual = measure(16, () -> mixedTick(root, EquipmentSlot.FEET));
                    same(nativeResult, actual);
                    require(actual.calls == 1, "JDT master/FE changed independent SG magnet dispatch");
                }
            }
        }
        report.append(
                "SG+JDT: both paid inverses, FE empty/charged and JDT master off/on; actual mixed ticks preserve complete root/source/FE/settings/tables without magnet charges\n");
    }

    private void batchRefusal(NeoForgeSilentGearTest.NativeApi api, ItemStack magnetic, ItemStack greedy, StringBuilder report) throws Throwable {
        var roots = NeoForgeSilentGearTest.workbench(player, magnetic, greedy);
        Object trait = SilentGearTraitSpike.traitOf(SilentGearTraitSpike.view(magnetic).get("silentgear:magnetic"));
        var field = trait.getClass().getDeclaredField("effects");
        field.setAccessible(true);
        Object original = field.get(trait);
        try {
            field.set(trait, List.of()); // Greedy sorts first and is valid; Magnetic must refuse the complete batch.
            for (ItemStack root : roots) {
                var result = measure(16, () -> mixedTick(root, EquipmentSlot.FEET));
                require(result.calls == 0 && result.pushes.stream().allMatch(Vec3.ZERO::equals), "Invalid second definition allowed a partial batch");
            }
        } finally {
            field.set(trait, original);
        }
        require(!SilentGearMagnet.available(), "Rejected definition did not revoke mixed magnets");
        for (ItemStack root : roots) {
            for (int attempt = 0; attempt < 3; attempt++) {
                var result = measure(16, () -> mixedTick(root, EquipmentSlot.FEET));
                require(result.calls == 0 && result.pushes.stream().allMatch(Vec3.ZERO::equals), "Definition restoration silently rearmed magnets");
            }
        }
        filters(measure(16, () -> magnetic.inventoryTick(player.level(), player, EquipmentSlot.FEET)), "magnetic", true);
        filters(measure(7, () -> greedy.inventoryTick(player.level(), player, EquipmentSlot.FEET)), "greedy", true);
        independent(api, report);
        report.append(
                "Batch refusal: invalid second trait prevents valid first trait publication; restored definitions do not rearm; repeated inverses inert, natives/unrelated features work\n");
    }

    private void partialFailure(NeoForgeSilentGearTest.NativeApi api, ItemStack magnetic, ItemStack greedy, StringBuilder report) throws Throwable {
        var roots = NeoForgeSilentGearTest.workbench(player, magnetic, greedy);
        Object trait = SilentGearTraitSpike.traitOf(SilentGearTraitSpike.view(magnetic).get("silentgear:magnetic"));
        var field = trait.getClass().getDeclaredField("effects");
        field.setAccessible(true);
        Object original = field.get(trait);
        try {
            // A native dirt-only peer and mixed Greedy enqueue disjoint items: neither producer can hide cleanup of the other.
            field.set(trait, List.of(magnet.getConstructor(float.class, float.class, java.util.Optional.class, String.class)
                                           .newInstance(.06F,
                                                        3F,
                                                        java.util.Optional.of(net.minecraft.world.item.crafting.Ingredient.of(Items.DIRT)),
                                                        "failure peer")));
            var nativeResult = measure(16, () -> {
                magnetic.inventoryTick(player.level(), player, EquipmentSlot.MAINHAND);
                greedy.inventoryTick(player.level(), player, EquipmentSlot.FEET);
            });
            var partial = measure(16, () -> {
                magnetic.inventoryTick(player.level(), player, EquipmentSlot.MAINHAND);
                int peerSize = pending.size();
                require(peerSize == 1, "Dirt-only native peer did not isolate its request");
                failAfterQueue = true;
                try {
                    mixedTick(roots.getFirst(), EquipmentSlot.FEET);
                } finally {
                    failAfterQueue = false;
                }
                require(pending.size() > peerSize, "Owned work was not queued or failure erased shared requests");
                mixedTick(roots.getLast(), EquipmentSlot.OFFHAND);
            });
            same(nativeResult, partial);
            require(partial.calls == 1 && !SilentGearMagnet.available(), "Post-queue failure retried/continued the batch");
        } finally {
            field.set(trait, original);
        }
        for (ItemStack root : roots) {
            for (int attempt = 0; attempt < 3; attempt++) {
                var result = measure(16, () -> mixedTick(root, EquipmentSlot.FEET));
                require(result.calls == 0 && result.pushes.stream().allMatch(Vec3.ZERO::equals),
                        "Post-queue failure did not revoke subsequent ticks");
            }
        }
        filters(measure(7, () -> greedy.inventoryTick(player.level(), player, EquipmentSlot.FEET)), "greedy", true);
        independent(api, report);
        report.append(
                "Post-queue exception: one owned call, no retry/second trait/next mixed copy; native peer queue preserved and published; root/carrier/equipment unchanged; native/unrelated features remain functional\n");
    }

    static boolean reloadStep(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Throwable {
        SilentGearTraitSpike.initialize(api);
        if (reload == null) {
            reload = new Reload(player, api);
            return false;
        }
        return reload.advance(api, report);
    }

    /** Local asynchronous datapack sequence; never block the server waiting for its own reload executor. */
    private static final class Reload {
        private final SilentGearMagnetTest fixture;
        private final ItemStack source;
        private final List<ItemStack> roots;
        private Object previous;
        private CompletableFuture<Void> future;
        private int phase;

        private Reload(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api) throws Throwable {
            fixture = new SilentGearMagnetTest(player);
            clear(player);
            player.tickCount = 21;
            source = api.boot("blaze_gold");
            roots = NeoForgeSilentGearTest.workbench(player, source, new ItemStack(Items.DIAMOND_BOOTS));
            previous = fixture.effect(SilentGearTraitSpike.view(source).get("silentgear:greedy"));
            fixture.filters(fixture.measure(7, () -> source.inventoryTick(player.level(), player, EquipmentSlot.FEET)), "greedy", true);
            future = load("minecraft:dirt", true, .12F);
        }

        private boolean advance(NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Throwable {
            if (!future.isDone()) {
                return false;
            }
            future.join();
            clear(fixture.player);
            fixture.player.tickCount = 21;
            if (phase < 2 || phase == 3) {
                Object instance = SilentGearTraitSpike.view(source).get("silentgear:greedy");
                Object current = fixture.effect(instance);
                require(current != previous, "Resource reload retained the old native magnet receiver");
                var stale = fixture.measure(7,
                                            () -> rejected(() -> fixture.guarded.tick(previous,
                                                                                      fixture.player,
                                                                                      instance,
                                                                                      fixture.guarded.carrier(roots.getFirst()),
                                                                                      true)));
                require(stale.calls == 0 && stale.pushes.stream().allMatch(Vec3.ZERO::equals), "Reload allowed cached effect invocation");
                previous = current;
                var control = fixture.measure(7, () -> source.inventoryTick(fixture.player.level(), fixture.player, EquipmentSlot.FEET));
                boolean dirt = phase == 0;
                require((control.pushes.get(0).lengthSqr() > 0) == !dirt
                                && (control.pushes.get(1).lengthSqr() > 0) == dirt && control.pushes.get(2).equals(Vec3.ZERO),
                        "Reloaded native tag membership did not change");
                float strength = phase == 0 ? .12F : .18F;
                var field = fixture.magnet.getDeclaredField("pullStrength");
                field.setAccessible(true);
                require(field.getFloat(current) == strength, "Reload did not replace native numeric data");
                for (ItemStack root : roots) {
                    var mixed = fixture.measure(7, () -> fixture.mixedTick(root, EquipmentSlot.FEET));
                    if (phase == 3) {
                        require(mixed.calls == 0 && mixed.pushes.stream().allMatch(Vec3.ZERO::equals),
                                "Valid reload silently rearmed revoked magnets");
                    } else {
                        same(control, mixed);
                        require(mixed.calls == 1, "Reload changed per-ID production dispatch count");
                    }
                }
                if (phase == 3) {
                    require(!SilentGearMagnet.available(), "Reload rearmed process-local failure");
                    fixture.independent(api, report);
                    report.append(
                            "MAGNET B reload: four completed asynchronous datapack reloads; pre-existing paid inverses use new native strength and live dirt/diamond tag membership without recrafting; stale receivers rejected; missing native magnet revokes before work; later valid reload leaves mixed feature revoked, natives/other features usable\n");
                    return true;
                }
            } else {
                for (ItemStack root : roots) {
                    var mixed = fixture.measure(7, () -> fixture.mixedTick(root, EquipmentSlot.FEET));
                    require(mixed.calls == 0 && mixed.pushes.stream().allMatch(Vec3.ZERO::equals), "Missing reloaded definition used cached effect");
                }
                require(!SilentGearMagnet.available(), "Invalid reload did not revoke magnets");
            }
            phase++;
            future = load("minecraft:diamond", phase != 2, .18F);
            return false;
        }

        private CompletableFuture<Void> load(String item, boolean admitted, float strength) throws Exception {
            var server = fixture.player.level().getServer();
            var pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("mismatchedboots-magnet-reload");
            Files.createDirectories(pack);
            var version = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
            Files.writeString(pack.resolve("pack.mcmeta"), """
                    {"pack":{"description":"Fixture-only SG magnet reload",
                    "min_format":[%d,%d],"max_format":[%d,%d]}}
                    """.formatted(version.major(), version.minor(), version.major(), version.minor()));
            var trait = pack.resolve("data/silentgear/silentgear_traits/greedy.json");
            Files.createDirectories(trait.getParent());
            Files.writeString(trait, """
                    {"conditions":[],"max_level":5,"name":{"text":"Greedy fixture"},"description":{"text":"Reload fixture"},"effects":%s}
                    """.formatted(admitted ? """
                    [{"type":"silentgear:item_magnet","affected_items":"#silentgear:greedy_magnet_attracted",
                    "effect_range":2.0,"pull_strength":%s,"affected_items_text_for_wiki":"fixture tag"}]
                    """.formatted(strength) : "[]"));
            var tag = pack.resolve("data/silentgear/tags/item/greedy_magnet_attracted.json");
            Files.createDirectories(tag.getParent());
            Files.writeString(tag, "{\"replace\":true,\"values\":[\"" + item + "\"]}");
            server.getPackRepository().reload();
            var selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
            String id = "file/mismatchedboots-magnet-reload";
            require(server.getPackRepository().isAvailable(id), "Magnet datapack was not discovered");
            if (!selected.contains(id)) {
                selected.add(id);
            }
            return server.reloadResources(selected);
        }
    }

    private void definitions(NeoForgeSilentGearTest.NativeApi api, ItemStack greedy, StringBuilder report) throws Throwable {
        Object instance = SilentGearTraitSpike.view(greedy).get("silentgear:greedy"), trait = SilentGearTraitSpike.traitOf(instance);
        var field = trait.getClass().getDeclaredField("effects");
        field.setAccessible(true);
        var original = (List<?>) field.get(trait);
        Object old = guarded.admittedEffect(instance);
        try {
            field.set(trait, List.of());
            rejected(() -> guarded.admittedEffect(instance));
            field.set(trait, List.of(old, old));
            rejected(() -> guarded.admittedEffect(instance));
            field.set(trait, original.stream().filter(value -> value.getClass() != magnet).toList());
            rejected(() -> guarded.admittedEffect(instance));
            Object invalid = magnet.getConstructor(float.class, float.class, java.util.Optional.class, String.class)
                                   .newInstance(Float.NaN, 2F, java.util.Optional.empty(), "invalid fixture");
            field.set(trait, List.of(invalid));
            rejected(() -> guarded.admittedEffect(instance));
            Object replacement = magnet.getConstructor(float.class, float.class, java.util.Optional.class, String.class)
                                       .newInstance(0.12F,
                                                    2F,
                                                    java.util.Optional.of(net.minecraft.world.item.crafting.Ingredient.of(Items.DIRT)),
                                                    "fixture dirt");
            field.set(trait, List.of(replacement));
            require(guarded.admittedEffect(instance) == replacement, "Definition binding cached its old effect");
            for (ItemStack root : NeoForgeSilentGearTest.workbench(player, greedy, new ItemStack(Items.DIAMOND_BOOTS))) {
                var control = measure(7, () -> greedy.inventoryTick(player.level(), player, EquipmentSlot.FEET));
                require(control.pushes.get(0).equals(Vec3.ZERO) && control.pushes.get(1).lengthSqr() > 0,
                        "Replacement native filter was not exercised");
                same(control, measure(7, () -> mixedTick(root, EquipmentSlot.FEET)));
                var stale = measure(7, () -> rejected(() -> guarded.tick(old, player, instance, guarded.carrier(root), true)));
                require(stale.calls == 0 && stale.pushes.stream().allMatch(Vec3.ZERO::equals), "Stale native effect dispatched");
            }
        } finally {
            field.set(trait, original);
        }
        require(guarded.admittedEffect(instance) == old, "Restored native definition was not reread");
        report.append(
                "Definitions: missing/duplicate/foreign/non-finite effects refused by admission; replacement strength/filter matches native and production ticks; stale effect rejected before invocation; restored definition reread\n");
    }

    private static void rejected(Action call) throws Throwable {
        try {
            call.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Unsupported magnet binding/data was admitted");
    }

    private Object effect(Object instance) throws Exception {
        var effects = (List<?>) SilentGearTraitSpike.traitOf(instance)
                                                    .getClass()
                                                    .getMethod("getEffects")
                                                    .invoke(SilentGearTraitSpike.traitOf(instance));
        var matches = effects.stream().filter(value -> value.getClass() == magnet).toList();
        require(matches.size() == 1, "Expected one exact native magnet effect per trait");
        return matches.getFirst();
    }

    private float radius(Object effect, Object instance) throws Exception {
        var field = magnet.getDeclaredField("effectRange");
        field.setAccessible(true);
        return field.getFloat(effect) * SilentGearTraitSpike.level(instance) + 1;
    }

    private Result measure(float radius, Action action) throws Throwable {
        flush();
        var items = new ArrayList<ItemEntity>();
        var stacks = List.of(Items.DIAMOND,
                             Items.DIRT,
                             Items.IRON_INGOT,
                             Items.DIAMOND,
                             Items.DIAMOND,
                             Items.DIAMOND,
                             Items.DIAMOND,
                             Items.DIAMOND,
                             Items.DIAMOND,
                             Items.DIAMOND,
                             Items.DIAMOND);
        double[][] offsets = {{2, 0, 0},
                              {2, 0, 1},
                              {3, 0, 0},
                              {2, 0, -1},
                              {2, 0, -2},
                              {radius - 0.25, 0, 0},
                              {radius + 0.25, 0, 0},
                              {radius * .8, 0, radius * .8},
                              {2, 3, 0},
                              {2, -3, 0},
                              {radius, 0, 0}};
        try {
            for (int index = 0; index < stacks.size(); index++) {
                var offset = offsets[index];
                double x = player.getX() + offset[0], z = player.getZ() + offset[2];
                player.level().getChunk(BlockPos.containing(x, player.getY(), z));
                var item = new ItemEntity(player.level(), x, player.getY() + offset[1], z, new ItemStack(stacks.get(index)));
                item.setNoGravity(true);
                item.setNoPickUpDelay();
                item.setDeltaMovement(Vec3.ZERO);
                if (index == 3) {
                    item.setPickUpDelay(20);
                }
                if (index == 4) {
                    item.getPersistentData().putBoolean("PreventRemoteMovement", true);
                }
                require(player.level().addFreshEntity(item), "Could not add actual magnet item control");
                items.add(item);
            }
            calls = 0;
            observing = true;
            action.run();
            observing = false;
            require(items.stream().allMatch(item -> item.getDeltaMovement().equals(Vec3.ZERO)), "Native magnet bypassed deferred tracker");
            int observedCalls = calls;
            flush();
            return new Result(items.stream().map(ItemEntity::getDeltaMovement).toList(), observedCalls);
        } finally {
            observing = false;
            items.forEach(ItemEntity::discard);
            flush();
        }
    }

    private void flush() {
        NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, player.level()));
        require(pending.isEmpty(), "Native level-post tracker did not drain its queue");
    }

    private void filters(Result value, String id, boolean active) {
        for (int index = 0; index < value.pushes.size(); index++) {
            boolean eligible = active && index != 3 && index != 4 && index != 6 && index != 7 && index != 10 && !(id.equals("greedy") && index == 1);
            require((value.pushes.get(index).lengthSqr() > 0) == eligible, "Native filter/range control differs: " + id + " item=" + index);
        }
    }

    private static void same(Result expected, Result actual) {
        require(expected.pushes.size() == actual.pushes.size(), "Incomplete impulse control");
        for (int i = 0; i < expected.pushes.size(); i++) {
            require(expected.pushes.get(i).distanceToSqr(actual.pushes.get(i)) < 1E-20, "Production impulse differs from native item callback: " + i);
        }
    }

    private static void place(ServerPlayer player, ItemStack root, EquipmentSlot slot) {
        clear(player);
        if (slot == null) {
            player.getInventory().setItem(10, root);
        } else {
            player.setItemSlot(slot, root);
        }
    }

    private static void clear(ServerPlayer player) {
        player.getInventory().clearContent();
        for (var slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        player.setShiftKeyDown(false);
        player.setPose(Pose.STANDING);
        player.removeAllEffects();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Sample(String id, ItemStack stack) {
    }

    private record Result(List<Vec3> pushes, int calls) {
    }

    private interface Action {
        void run() throws Throwable;
    }
}
