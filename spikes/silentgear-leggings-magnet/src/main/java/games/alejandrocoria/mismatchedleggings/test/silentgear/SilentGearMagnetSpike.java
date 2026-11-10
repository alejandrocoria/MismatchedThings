package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.silentgear.FoundationChecks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.setup.SgDataComponents;
import net.silentchaos512.gear.util.TraitHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Test-only direct magnet candidate. It never installs production dispatch. */
public final class SilentGearMagnetSpike {
    private static boolean finished;

    private SilentGearMagnetSpike() {}

    public static void tick(MinecraftServer server) {
        if (finished || server.getTickCount() < 20) { return; }
        finished = true;
        try {
            run(server);
            SkeletonChecksPassed.passed();
        } catch (Throwable failure) {
            SkeletonChecksPassed.failed(failure);
        } finally {
            server.halt(false);
        }
    }

    private static void run(MinecraftServer server) throws Throwable {
        var mods = net.neoforged.fml.ModList.get();
        boolean withBoots = Boolean.getBoolean("leggings.test.boots");
        require(mods.isLoaded("mismatchedboots") == withBoots, "Wrong optional Boots graph");
        require(mods.isLoaded("silentgear") && mods.isLoaded("silentlib"), "Pinned native graph is missing");
        var player = FoundationChecks.player(server);

        ItemStack iron = FoundationChecks.armor("iron");
        ItemStack magnetic = installMagnetic(iron, player);
        ItemStack greedy = FoundationChecks.armor("blaze_gold");
        require(level(magnetic, "magnetic") == 5, "Native leggings part swap did not compute Magnetic V");
        require(level(greedy, "greedy") == 3, "Native Blaze Gold leggings did not compute Greedy III");

        var candidate = new Candidate();
        require(candidate.ready(), "Local magnet callback boundary is unavailable");
        require(candidate.bootsAvailable() == withBoots, "Candidate/packaged Boots availability mismatch");

        var magneticPair = FoundationChecks.paid(player, magnetic, magnetic);
        var mixedPair = FoundationChecks.paid(player, magnetic, greedy);
        var greedyPair = FoundationChecks.paid(player, greedy, new ItemStack(Items.DIAMOND_LEGGINGS));

        var report = new StringBuilder()
                .append("Native acquisition: actual GearPartSwapRecipe on native LEGS => Magnetic V; native GearArmorItem construction from Blaze Gold main plates => Greedy III\n")
                .append("Candidate boundary: test-only direct dispatch against exact current ItemMagnetTraitEffect instances; no production callback enabled; item ticks are direct server fixtures; client false branch is bytecode-inspected, not runtime-launched\n")
                .append("Boots graph: ").append(withBoots).append("; candidate available: ").append(candidate.ready())
                .append("; current packaged Boots magnet available: ").append(candidate.bootsAvailable()).append('\n');

        checkMixed(candidate, player, magneticPair, "duplicate Magnetic V", 1, report);
        checkMixed(candidate, player, mixedPair, "Magnetic V + Greedy III", 2, report);
        checkMixed(candidate, player, greedyPair, "Greedy III + non-SG", 1, report);
        candidate.crouchControl(player, mixedPair.getFirst(), report);
        candidate.refusalControl(player, mixedPair.getFirst(), report);
        candidate.queueArbitration(player, report);
        if (withBoots) { candidate.bootsOverlap(player, magnetic, mixedPair.getFirst(), report); }

        Files.writeString(Path.of(System.getProperty("leggings.test.report"), "magnet-spike.txt"), report);
    }

    private static ItemStack installMagnetic(ItemStack gear, ServerPlayer player) throws Exception {
        var swapClass = Class.forName("net.silentchaos512.gear.crafting.recipe.GearPartSwapRecipe");
        Object recipe = swapClass.getField("INSTANCE").get(null);
        ItemStack upgrade = new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.parse("silentgear:magnetic_upgrade")).orElseThrow());
        ItemStack gearBefore = gear.copy();
        ItemStack upgradeBefore = upgrade.copy();
        var input = CraftingInput.of(2, 1, List.of(gear, upgrade));
        require((boolean) swapClass.getMethod("matches", CraftingInput.class, Level.class).invoke(recipe, input, player.level()),
                "Native part-swap recipe rejected Magnetic Upgrade on leggings");
        var result = (ItemStack) swapClass.getMethod("assemble", CraftingInput.class).invoke(recipe, input);
        require(ItemStack.matches(gearBefore, gear) && ItemStack.matches(upgradeBefore, upgrade), "Native part-swap mutated its inputs");
        return result;
    }

    private static int level(ItemStack stack, String name) {
        return TraitHelper.getTraitLevel(stack, DataResource.trait("silentgear:" + name));
    }

    private static void checkMixed(Candidate candidate, ServerPlayer player, List<ItemStack> roots, String label, int expectedCalls,
                                   StringBuilder report) throws Throwable {
        for (ItemStack root : roots) {
            ItemStack rootBefore = root.copy();
            double radius = candidate.radius(root);
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.LEGS, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
                Result nativeResult = measure(player, radius, () -> nativeTick(root, player, slot));
                Result mixedResult = measure(player, radius, () -> candidate.tick(root, player.level(), player, slot));
                same(nativeResult.pushes(), mixedResult.pushes(), label + "/" + slot);
                require(mixedResult.calls() == expectedCalls, "Mixed callback count/max-by-ID mismatch");
                activeFilters(mixedResult, label.contains("Greedy") && !label.contains("Magnetic"));
                require(nativeResult.pushes().getFirst().lengthSqr() > 0, "Independent native near-diamond positive is inert");
            }
            Result nativeOtherSlot = measure(player, radius, () -> nativeTick(root, player, EquipmentSlot.HEAD));
            Result mixedOtherSlot = measure(player, radius, () -> candidate.tick(root, player.level(), player, EquipmentSlot.HEAD));
            require(nativeOtherSlot.pushes().getFirst().lengthSqr() > 0 && mixedOtherSlot.calls() == 0
                    && mixedOtherSlot.pushes().stream().allMatch(Vec3.ZERO::equals), "Candidate wrong-slot policy differs from native selected-item rule");
            Result idleNative = measure(player, radius, () -> nativeTick(root, player, null));
            Result idleMixed = measure(player, radius, () -> candidate.tick(root, player.level(), player, null));
            same(idleNative.pushes(), idleMixed.pushes(), label + "/idle");
            require(idleMixed.calls() == 0, "Idle inventory candidate dispatched");
            require(ItemStack.matches(rootBefore, root), "Direct native controls changed the mixed root");
        }
        report.append(label).append(": both paid orientations; LEGS/MAINHAND/OFFHAND match independent direct native item.inventoryTick; native HEAD remains selected while local candidate admits only target slots; idle inert; native filter/range/delay/prevention controls and root snapshots pass\n");
    }

    private static int nativeTick(ItemStack root, ServerPlayer player, EquipmentSlot slot) {
        int ticks = 0;
        for (ItemStack stored : nativeSources(root)) {
            ItemStack storedBefore = stored.copy();
            ItemStack disposable = stored.copy();
            ItemStack before = disposable.copy();
            disposable.inventoryTick(player.level(), player, slot);
            require(ItemStack.matches(before, disposable), "Native direct magnet control mutated its disposable source");
            require(ItemStack.matches(storedBefore, stored), "Native direct control changed source history");
            ticks++;
        }
        return ticks;
    }

    private static void activeFilters(Result result, boolean greedyOnly) {
        var pushes = result.pushes();
        require(pushes.get(0).lengthSqr() > 0, "Near diamond was not attracted");
        require((pushes.get(1).lengthSqr() == 0) == greedyOnly, "Greedy dirt filter differs");
        require(pushes.get(2).lengthSqr() > 0, "Inside-radius diamond was not attracted");
        require(pushes.get(3).lengthSqr() == 0, "Outside-radius item was attracted");
        require(pushes.get(4).lengthSqr() == 0, "Pickup-delay item was attracted");
        require(pushes.get(5).lengthSqr() == 0, "PreventRemoteMovement item was attracted");
        require(pushes.get(6).lengthSqr() == 0, "Box-corner item outside native sphere was attracted");
        require(pushes.get(7).lengthSqr() == 0, "Exact outer-radius item was attracted");
    }

    private static List<ItemStack> nativeSources(ItemStack root) {
        var data = MixedLeggingsStructure.validData(root).orElseThrow();
        var result = new ArrayList<ItemStack>();
        for (var half : List.of(data.left(), data.right())) {
            ItemStack source = half.source();
            if (source.getItem() == BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:leggings"))) { result.add(source); }
        }
        return result;
    }

    private static Result measure(ServerPlayer player, double radius, Action action) throws Throwable {
        var items = new ArrayList<ItemEntity>();
        NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, player.level()));
        var stacks = List.of(Items.DIAMOND, Items.DIRT, Items.DIAMOND, Items.DIAMOND,
                Items.DIAMOND, Items.DIAMOND, Items.DIAMOND, Items.DIAMOND);
        double[][] offsets = {{2, 0, 0}, {2, 0, 1}, {radius - 0.25, 0, 0}, {radius + 0.25, 0, 0},
                {2, 0, -1}, {2, 0, -2}, {radius * 0.8, 0, radius * 0.8}, {radius, 0, 0}};
        int calls = 0;
        try {
            for (int i = 0; i < stacks.size(); i++) {
                var offset = offsets[i];
                double x = player.getX() + offset[0];
                double z = player.getZ() + offset[2];
                player.level().getChunk(BlockPos.containing(x, player.getY(), z));
                var item = new ItemEntity(player.level(), x, player.getY() + offset[1], z, new ItemStack(stacks.get(i)));
                item.setNoGravity(true);
                item.setNoPickUpDelay();
                item.setDeltaMovement(Vec3.ZERO);
                if (i == 4) { item.setPickUpDelay(20); }
                if (i == 5) { item.getPersistentData().putBoolean("PreventRemoteMovement", true); }
                require(player.level().addFreshEntity(item), "Could not spawn native magnet ItemEntity control");
                items.add(item);
            }
            calls = action.run();
            require(items.stream().allMatch(item -> item.getDeltaMovement().equals(Vec3.ZERO)), "Native callback moved item before level-post event");
            NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, player.level()));
            return new Result(items.stream().map(ItemEntity::getDeltaMovement).toList(), calls);
        } finally {
            items.forEach(ItemEntity::discard);
            NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, player.level()));
        }
    }

    private static void same(List<Vec3> expected, List<Vec3> actual, String label) {
        require(expected.size() == actual.size(), "Incomplete native impulse comparison: " + label);
        for (int i = 0; i < expected.size(); i++) {
            require(expected.get(i).distanceToSqr(actual.get(i)) < 1E-20, "Impulse mismatch " + label + " item=" + i);
        }
    }

    private record Result(List<Vec3> pushes, int calls) {}
    @FunctionalInterface private interface Action { int run() throws Throwable; }

    private static final class Candidate {
        private final Class<?> effect = nativeClass("gear.trait.effect.ItemMagnetTraitEffect");
        private final Class<?> context = nativeClass("api.traits.TraitActionContext");
        private final Class<?> sources = classFor("games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearEffectSources");
        private final Object reader;
        private final Method readTraits;
        private final Method tick;
        private final Field strength;
        private final Field range;
        private final Map<?, ?> queue;

        Candidate() throws Exception {
            var constructor = sources.getDeclaredConstructor();
            constructor.setAccessible(true);
            reader = constructor.newInstance();
            readTraits = sources.getDeclaredMethod("traits", ItemStack.class);
            readTraits.setAccessible(true);
            tick = effect.getMethod("inventoryTick", context, Level.class, Entity.class, boolean.class);
            strength = field(effect, "pullStrength");
            range = field(effect, "effectRange");
            var queueField = nativeClass("gear.util.MagnetPullTracker").getDeclaredField("ITEMS_TO_PUSH");
            queueField.setAccessible(true);
            queue = (Map<?, ?>) queueField.get(null);
        }

        boolean ready() {
            return effect.getName().equals("net.silentchaos512.gear.gear.trait.effect.ItemMagnetTraitEffect")
                    && queue != null && tick.getParameterCount() == 4;
        }

        boolean bootsAvailable() {
            try {
                var type = Class.forName("games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearMagnet");
                return (boolean) type.getMethod("available").invoke(null);
            } catch (ClassNotFoundException absent) {
                return false;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not read current Boots magnet availability", failure);
            }
        }

        void crouchControl(ServerPlayer player, ItemStack root, StringBuilder report) throws Throwable {
            Object option = nativeClass("Config$Common").getField("magnetPullDisabledOnCrouch").get(null);
            Method get = option.getClass().getMethod("get");
            Method set = option.getClass().getMethod("set", Object.class);
            boolean original = (boolean) get.invoke(option);
            boolean oldShift = player.isShiftKeyDown();
            Pose oldPose = player.getPose();
            try {
                player.setShiftKeyDown(true);
                player.setPose(Pose.CROUCHING);
                require(player.isCrouching(), "Crouch control is not crouching");
                for (boolean disabled : List.of(true, false)) {
                    set.invoke(option, disabled);
                    double radius = radius(root);
                    Result nativeResult = measure(player, radius, () -> nativeTick(root, player, EquipmentSlot.LEGS));
                    Result candidateResult = measure(player, radius, () -> tick(root, player.level(), player, EquipmentSlot.LEGS));
                    same(nativeResult.pushes(), candidateResult.pushes(), "crouch config=" + disabled);
                    require((candidateResult.pushes().getFirst().lengthSqr() == 0) == disabled,
                            "Native crouch configuration did not control attraction");
                }
            } finally {
                set.invoke(option, original);
                player.setShiftKeyDown(oldShift);
                player.setPose(oldPose);
            }
            report.append("Crouch configuration: enabled suppresses and disabled permits matching native/candidate pushes\n");
        }

        double radius(ItemStack root) throws Exception {
            double maximum = 2;
            for (var call : selected(root)) {
                int level = (int) call.instance().getClass().getMethod("getLevel").invoke(call.instance());
                maximum = Math.max(maximum, 1 + range.getFloat(call.effect()) * level);
            }
            return maximum;
        }

        int tick(ItemStack root, Level level, Entity entity, EquipmentSlot slot) throws Throwable {
            if (!ready() || level.isClientSide() || !(entity instanceof ServerPlayer player)
                    || slot != EquipmentSlot.LEGS && slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) { return 0; }
            ItemStack rootBefore = root.copy();
            var data = MixedLeggingsStructure.validData(root).orElseThrow();
            var sourceCopies = List.of(data.left().source().copy(), data.right().source().copy());
            List<Call> calls = selected(root);
            if (calls.isEmpty()) { return 0; }
            var sourcesData = MixedLeggingsStructure.validData(root).orElseThrow();
            ItemStack carrier = sourcesData.left().source().getItem() == BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:leggings"))
                    ? sourcesData.left().source().copy() : sourcesData.right().source().copy();
            ItemStack carrierBefore = carrier.copy();
            int invocationCount = 0;
            for (var call : calls) {
                var ctor = context.getConstructor(net.minecraft.world.entity.player.Player.class,
                        nativeClass("api.traits.TraitInstance"), ItemStack.class);
                Object ctx = ctor.newInstance(player, call.instance(), carrier);
                tick.invoke(call.effect(), ctx, level, player, true);
                invocationCount++;
                require(ItemStack.matches(carrierBefore, carrier), "Native magnet changed candidate carrier");
            }
            require(ItemStack.matches(rootBefore, root), "Candidate changed mixed root");
            var after = MixedLeggingsStructure.validData(root).orElseThrow();
            require(ItemStack.matches(sourceCopies.getFirst(), after.left().source())
                    && ItemStack.matches(sourceCopies.getLast(), after.right().source()), "Candidate changed immutable source history");
            return invocationCount;
        }

        private List<Call> selected(ItemStack root) throws Exception {
            var merged = new java.util.TreeMap<String, Call>();
            for (Object traitRecord : (List<?>) readTraits.invoke(reader, root)) {
                String id = recordMethod(traitRecord, "id").invoke(traitRecord).toString();
                if (!id.equals("silentgear:magnetic") && !id.equals("silentgear:greedy")) { continue; }
                Object instance = recordMethod(traitRecord, "nativeInstance").invoke(traitRecord);
                Object nativeTrait = instance.getClass().getMethod("getTrait").invoke(instance);
                var effects = (List<?>) nativeTrait.getClass().getMethod("getEffects").invoke(nativeTrait);
                var magnets = effects.stream().filter(value -> value.getClass() == effect).toList();
                if (magnets.size() != 1) { throw new IllegalStateException("Current trait must expose one exact native magnet effect"); }
                Object magnet = magnets.getFirst();
                int level = (int) instance.getClass().getMethod("getLevel").invoke(instance);
                float base = strength.getFloat(magnet);
                float effectRange = range.getFloat(magnet);
                if (!Float.isFinite(base) || !Float.isFinite(effectRange) || level < 1
                        || !Float.isFinite(1 + effectRange * level)) {
                    throw new IllegalStateException("Unsupported native magnet parameters");
                }
                merged.put(id, new Call(instance, magnet));
            }
            return List.copyOf(merged.values());
        }

        void refusalControl(ServerPlayer player, ItemStack root, StringBuilder report) throws Throwable {
            var data = MixedLeggingsStructure.validData(root).orElseThrow();
            ItemStack magneticSource = level(data.left().source(), "magnetic") > 0 ? data.left().source() : data.right().source();
            Object sourceTrait = selectedSourceTrait(magneticSource, "magnetic");
            Field effects = field(sourceTrait.getClass(), "effects");
            Object prior = effects.get(sourceTrait);
            // Greedy's mining-speed companion remains in its native list; the candidate calls only its magnet.
            ItemStack greedySource = level(data.left().source(), "greedy") > 0 ? data.left().source() : data.right().source();
            Object greedyTrait = selectedSourceTrait(greedySource, "greedy");
            var greedyEffects = (List<?>) field(greedyTrait.getClass(), "effects").get(greedyTrait);
            require(greedyEffects.stream().anyMatch(value -> value.getClass() != effect), "Greedy mining-speed companion/control disappeared");
            var diamond = new ItemEntity(player.level(), player.getX() + 2, player.getY(), player.getZ(), new ItemStack(Items.DIAMOND));
            diamond.setNoGravity(true);
            diamond.setNoPickUpDelay();
            diamond.setDeltaMovement(Vec3.ZERO);
            require(player.level().addFreshEntity(diamond), "Could not create local refusal publication control");
            try {
                effects.set(sourceTrait, List.of());
                NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, player.level()));
                require(queue.isEmpty(), "Native queue was not empty before local refusal control");
                boolean refused = false;
                try {
                    tick(root, player.level(), player, EquipmentSlot.LEGS);
                } catch (IllegalStateException expected) {
                    refused = true;
                }
                require(refused, "Missing magnet callback definition was not refused");
                require(queue.isEmpty(), "Local admission refusal published part of the batch before cleanup");
                require(diamond.getDeltaMovement().equals(Vec3.ZERO), "Local admission refusal moved a magnetic diamond before cleanup");
            } finally {
                effects.set(sourceTrait, prior);
                diamond.discard();
                NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, player.level()));
            }
            require(selected(root).size() == 2, "Restored definitions were not reread");
            report.append("Local compatible/refusal control: Greedy's mining-speed companion is ignored; removed second trait magnet refuses entire batch before native queue publication; no transformed hook claim\n");
        }

        void queueArbitration(ServerPlayer player, StringBuilder report) throws Exception {
            var item = new ItemEntity(player.level(), player.getX() + 2, player.getY(), player.getZ(), new ItemStack(Items.DIAMOND));
            item.setNoGravity(true);
            item.setNoPickUpDelay();
            item.setDeltaMovement(Vec3.ZERO);
            require(player.level().addFreshEntity(item), "Could not create queue arbitration item");
            try {
                var push = nativeClass("gear.util.MagnetPullTracker").getMethod("pushItem", ItemEntity.class, Vec3.class);
                push.invoke(null, item, new Vec3(0.02, 0, 0));
                push.invoke(null, item, new Vec3(0.08, 0, 0));
                push.invoke(null, item, new Vec3(0.04, 0, 0));
                require(item.getDeltaMovement().equals(Vec3.ZERO), "Tracker applied before native LevelTickEvent.Post");
                NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, player.level()));
                require(item.getDeltaMovement().distanceToSqr(new Vec3(0.08, 0, 0)) < 1E-20,
                        "Native tracker did not retain greatest squared-magnitude vector");
                require(queue.isEmpty(), "Native level-post tracker did not drain queue");
            } finally {
                item.discard();
                NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, player.level()));
            }
            report.append("Native queue control: multiple real pushItem requests retain the greatest vector; registered LevelTickEvent.Post applies once and drains the shared queue\n");
        }

        void bootsOverlap(ServerPlayer player, ItemStack magneticLeggings, ItemStack mixedLeggings, StringBuilder report) throws Throwable {
            var boot = (net.silentchaos512.gear.api.item.GearItem) BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:boots"));
            var nativeBoot = boot.construct(List.of(net.silentchaos512.gear.gear.part.PartInstance.from(FoundationChecks.part("boot_plates", "iron"))));
            var magneticBoot = installMagnetic(nativeBoot, player);
            Class<?> factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
            Object pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class)
                    .invoke(null, magneticBoot, new ItemStack(Items.DIAMOND_BOOTS));
            ItemStack mixedBoots = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
            ItemStack bootsBefore = mixedBoots.copy();
            double radius = Math.max(radius(mixedLeggings), 16);
            var nativeOnly = measure(player, radius, () -> { mixedBoots.inventoryTick(player.level(), player, EquipmentSlot.FEET); return 1; });
            var overlap = measure(player, radius, () -> {
                mixedBoots.inventoryTick(player.level(), player, EquipmentSlot.FEET);
                int calls = tick(mixedLeggings, player.level(), player, EquipmentSlot.LEGS);
                return calls + 1;
            });
            same(nativeOnly.pushes(), overlap.pushes(), "current Boots + candidate leggings strongest-vector overlap");
            require(ItemStack.matches(bootsBefore, mixedBoots), "Candidate overlap changed current Boots root");
            report.append("Current packaged Boots overlap: actual mixed-boot FEET tick plus candidate mixed-leggings LEGS tick share native queue and match single stronger/equal vector; dispatch-count multiplication is not inferred from queue result\n");
        }
    }

    private record Call(Object instance, Object effect) {}

    private static Object selectedSourceTrait(ItemStack source, String id) throws Exception {
        Class<?> helper = nativeClass("util.TraitHelper");
        for (Object instance : (List<?>) helper.getMethod("getTraits", net.minecraft.world.item.ItemInstance.class).invoke(null, source)) {
            if (instance.getClass().getMethod("getTraitId").invoke(instance).toString().equals("silentgear:" + id)) {
                return instance.getClass().getMethod("getTrait").invoke(instance);
            }
        }
        throw new IllegalStateException("Native source lacks " + id);
    }

    private static Class<?> nativeClass(String suffix) {
        try { return Class.forName("net.silentchaos512.gear." + suffix); }
        catch (ClassNotFoundException failure) { throw new IllegalStateException("Missing native Silent Gear class " + suffix, failure); }
    }

    private static Class<?> classFor(String name) {
        try { return Class.forName(name); }
        catch (ClassNotFoundException failure) { throw new IllegalStateException("Missing local source reader " + name, failure); }
    }

    private static Method recordMethod(Object record, String name) throws NoSuchMethodException {
        Method method = record.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static final class SkeletonChecksPassed {
        private static void passed() throws Exception {
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "server.ok"), "ok\n");
        }
        private static void failed(Throwable failure) {
            try { Files.writeString(Path.of(System.getProperty("leggings.test.report"), "server.failure.txt"), failure.toString()); }
            catch (Exception ignored) { failure.printStackTrace(); }
            failure.printStackTrace();
        }
    }
}
