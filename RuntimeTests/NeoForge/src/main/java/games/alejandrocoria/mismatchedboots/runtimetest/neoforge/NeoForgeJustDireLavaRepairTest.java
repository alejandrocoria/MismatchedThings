package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireLavaRepairCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Map;

/** Bounded server-thread real ItemEntity ticks; not a full production/lifecycle acceptance gate. */
public final class NeoForgeJustDireLavaRepairTest {
    private static final String PREFIX = "just-dire-lava-repair";
    private static final BlockPos LAVA = new BlockPos(5, 80, 5);
    private static final StringBuilder report = new StringBuilder();
    private static boolean finished;
    private static int warmup, groups;

    private NeoForgeJustDireLavaRepairTest() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer()));
    }

    private static void run(MinecraftServer server) {
        if (finished) {
            return;
        }
        ServerLevel level = server.overworld();
        if (++warmup == 1) {
            level.setChunkForced(0, 0, true);
        }
        if (warmup < 200 && !level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(0, 0))) {
            return;
        }
        finished = true;
        try {
            require(warmup < 200, "Fixture chunk not ticking");
            if (!System.getProperty("mismatchedboots.runtimetest.justDireLavaRepairVariant", "").isEmpty()) {
                guards(server);
                return;
            }
            nativeControls(level);
            productionControls(level);
            preservation(level);
            interruptions(server);
            config(level);
            NetworkAcceptanceFiles.write(PREFIX + "-report.txt",
                                         groups + " production groups; actual ItemEntity.tick, native controls, production item hooks; particle/sound observers\n" + report);
            NetworkAcceptanceFiles.write(PREFIX + "-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.write(PREFIX + "-partial-report.txt", report.toString());
            NetworkAcceptanceFiles.failure(PREFIX, failure);
        } finally {
            reset();
            level.setChunkForced(0, 0, false);
            server.halt(false);
        }
    }

    private static void nativeControls(ServerLevel level) throws ReflectiveOperationException {
        arena(level);
        ItemStack nativeBoots = damaged(boots("blazegold"));
        ItemEntity original = drop(level, nativeBoots);
        ticks(original, 20);
        JustDireLavaRepairTestSupport.validateEmissions(LAVA);
        int ticks = 20 + untilRepaired(original, 260);
        require(original.isAlive() && !nativeBoots.isDamaged() && obsidian(level), "Native ritual did not repair/consume");
        group("Native damaged Blazegold completed after " + ticks + " entity ticks; survived and consumed source");

        arena(level);
        ItemEntity healthy = drop(level, boots("blazegold"));
        ticks(healthy, 260);
        require(obsidian(level), "Native healthy-item consumption changed");
        group("Native healthy Blazegold consumes source, matching mixed policy");

        arena(level);
        ItemEntity ordinary = drop(level, damaged(new ItemStack(Items.DIAMOND_BOOTS)));
        ticks(ordinary, 160);
        require(!ordinary.isAlive() && source(level, LAVA), "Unprotected lava control did not die");
        group("Diamond control dies in real lava, does not consume source");

    }

    private static void productionControls(ServerLevel level) throws ReflectiveOperationException {
        for (ItemStack root : List.of(pair().primary(),
                                      pair().inverse(),
                                      MixedBootsFactory.createPair(boots("blazegold"), boots("blazegold")).primary())) {
            arena(level);
            damaged(root);
            ItemStack before = root.copy();
            ItemEntity entity = drop(level, root);
            int elapsed = untilRepaired(entity, 260);
            require(entity.isAlive() && obsidian(level) && !root.isDamaged() && JustDireLavaRepairTestSupport.sounds.size() == 1,
                    "Production did not complete exactly once");
            before.setDamageValue(0);
            require(ItemStack.matches(before, root), "Repair changed more than root durability");
            JustDireLavaRepairTestSupport.validateEmissions(LAVA);
            int emitted = JustDireLavaRepairTestSupport.emissions.size();
            ticks(entity, 100);
            require(JustDireLavaRepairTestSupport.emissions.size() == emitted && JustDireLavaRepairTestSupport.sounds.equals(List.of(LAVA)),
                    "Effects repeated after completion");
            require(entity.isAlive() && entity.getAge() >= elapsed + 100 && !entity.hasPickUpDelay(), "Normal lifecycle/pickup countdown skipped");
            group("Production pair orientation/source count: " + elapsed + " ticks; one consumption, root-only repair, normal age/pickup countdown");
        }

        for (ItemStack healthy : List.of(pair().primary(),
                                         pair().inverse(),
                                         MixedBootsFactory.createPair(boots("blazegold"), boots("blazegold")).primary())) {
            arena(level);
            ItemStack before = healthy.copy();
            ItemEntity entity = drop(level, healthy);
            ticks(entity, 240);
            require(entity.isAlive() && obsidian(level) && JustDireLavaRepairTestSupport.sounds.equals(List.of(LAVA))
                            && ItemStack.matches(before, entity.getItem()), "Healthy ritual did not consume exactly once or changed resources");
            JustDireLavaRepairTestSupport.validateEmissions(LAVA);
        }
        group("Healthy mixed roots in both orientations/one-two providers run native ritual, consume once and preserve root state");

        for (String disabled : List.of("tool_enabled", "lavarepair_toggle")) {
            arena(level);
            ItemStack root = pair().primary();
            damaged(root);
            root.set(JustDireLavaRepairTestSupport.<Boolean>component(disabled), false);
            ItemEntity entity = drop(level, root);
            int damage = root.getDamageValue();
            ticks(entity, 240);
            require(entity.isAlive() && source(level, LAVA)
                            && root.getDamageValue() == damage && entity.fireImmune(), "Disabled root lost permanent immunity or consumed lava");
            group(disabled + ": permanent item immunity, no ritual/consumption");
        }

        arena(level);
        ItemStack root = damaged(pair().primary());
        ItemEntity immune = drop(level, root);
        require(root.get(DataComponents.DAMAGE_RESISTANT) == null, "One-source projection changed");
        for (var damage : List.of(level.damageSources().lava(), level.damageSources().inFire(), level.damageSources().onFire(),
                                  level.damageSources().hotFloor())) {
            require(!root.canBeHurtBy(damage) && !immune.hurtServer(level, damage, 4), "Fire-tagged damage not denied by item hook");
        }
        require(immune.fireImmune() && root.canBeHurtBy(level.damageSources().generic())
                        && immune.hurtServer(level, level.damageSources().generic(), 100) && !immune.isAlive(),
                "Immunity leaked into unrelated damage");
        ItemStack plain = MixedBootsFactory.createPair(new ItemStack(Items.NETHERITE_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)).primary();
        require(plain.get(DataComponents.DAMAGE_RESISTANT) == null && plain.canBeHurtBy(level.damageSources().inFire()),
                "Generic intersection was broadened");
        ItemStack otherProtection = pair().primary();
        otherProtection.set(DataComponents.DAMAGE_RESISTANT,
                            new DamageResistant(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypeTags.IS_FALL)));
        require(!otherProtection.canBeHurtBy(level.damageSources().fall()) && !otherProtection.canBeHurtBy(level.damageSources().inFire())
                        && otherProtection.canBeHurtBy(level.damageSources().generic()), "Item fire hook lost independent component protection");
        ItemStack invalid = new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID));
        require(invalid.canBeHurtBy(level.damageSources().inFire()), "Bare root gained immunity");
        root.setCount(2);
        require(root.canBeHurtBy(level.damageSources().inFire()), "Invalid count gained exception");
        group("Item hook denies fire only; generic damage still destroys; component intersection/bare/count controls unchanged");

        arena(level);
        level.setBlock(LAVA, Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 4), 3);
        root = damaged(pair().primary());
        ItemEntity flow = drop(level, root);
        ticks(flow, 220);
        require(flow.isAlive() && root.isDamaged() && !obsidian(level), "Flowing-only contact starts repair");
        group("Flowing lava: immunity without repair/consumption");

        arena(level);
        JustDireLavaRepairTestSupport.refusedBlock = LAVA;
        root = damaged(pair().primary());
        ItemEntity denied = drop(level, root);
        try {
            ticks(denied, 220);
        } finally {
            JustDireLavaRepairTestSupport.refusedBlock = null;
        }
        require(denied.isAlive() && root.isDamaged() && JustDireLavaRepairTestSupport.refusedWrites > 0
                        && source(level, LAVA), "Production awarded repair after refused write");
        group("Production completion checks actual write receipt: denied consumption never repairs");

        arena(level);
        ItemEntity first = drop(level, damaged(pair().primary()));
        ItemEntity second = drop(level, damaged(pair().inverse()));
        for (int i = 0; i < 240; i++) {
            first.tick();
            second.tick();
        }
        require(first.isAlive() && second.isAlive() && obsidian(level) && JustDireLavaRepairTestSupport.sounds.size() == 1
                        && first.getItem().isDamaged() != second.getItem().isDamaged(), "Shared source paid twice");
        group("Two entities sharing one source: exactly one successful repair");

        arena(level);
        root = damaged(MixedBootsFactory.createPair(boots("blazegold"), boots("eclipsealloy")).primary());
        root.set(JustDireResourceTestSupport.energyComponent(), 12345);
        ItemStack before = root.copy();
        ItemEntity powered = drop(level, root);
        untilRepaired(powered, 260);
        before.setDamageValue(0);
        require(!root.isDamaged() && ItemStack.matches(root, before), "Repair changed live FE/controls/provenance");
        group("Blazegold + powered source: FE/controls/history unchanged, only root durability restored");
    }

    private static void interruptions(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (String interruption : List.of("sourceRemoved", "leaveBounds", "toggleOff", "rootReplaced", "ceiling")) {
            arena(level);
            ItemStack root = damaged(pair().primary());
            ItemEntity entity = drop(level, root);
            ticks(entity, 10);
            require(entity.getY() > 80.2, "No rising episode to interrupt");
            switch (interruption) {
                case "sourceRemoved" -> level.setBlock(LAVA, Blocks.AIR.defaultBlockState(), 3);
                case "leaveBounds" -> entity.setPos(10.5, 84, 5.5);
                case "toggleOff" -> root.set(JustDireLavaRepairTestSupport.<Boolean>component("lavarepair_toggle"), false);
                case "rootReplaced" -> {
                    entity.setItem(damaged(pair().inverse()));
                    entity.setPos(10.5, 84, 5.5);
                }
                case "ceiling" -> {
                    entity.setPos(5.5, 80.2, 5.5);
                    level.setBlock(LAVA.above(), Blocks.STONE.defaultBlockState(), 3);
                }
                default -> throw new AssertionError(interruption);
            }
            ticks(entity, 250);
            require(root.isDamaged() && JustDireLavaRepairTestSupport.sounds.isEmpty(), "Interrupted episode granted repair");
            require(entity.getItem().isDamaged(), interruption + ": current stack was repaired");
            group(interruption + ": no stale/free completion; bounded production cancellation");
        }

        arena(level);
        ItemStack root = damaged(pair().primary());
        ItemEntity first = drop(level, root);
        ticks(first, 15);
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        ItemStack loaded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, root).getOrThrow()).getOrThrow();
        // Deliberately import native working fields, not the adapter's transient episode.
        loaded.set(JustDireLavaRepairTestSupport.<Integer>component("floatingticks"), 79);
        loaded.set(JustDireLavaRepairTestSupport.<BlockPos>component("lavapos"), LAVA);
        ItemEntity reloaded = drop(level, loaded);
        reloaded.setPos(10.5, 84, 5.5);
        ticks(reloaded, 100);
        require(loaded.isDamaged() && source(level, LAVA)
                        && !loaded.has(JustDireLavaRepairTestSupport.component("lavapos")), "Imported progress remotely repaired");
        ItemStack transformed = MixedBootsRecomposer.recompose(root, boots("eclipsealloy"), new ItemStack(Items.DIAMOND_BOOTS));
        require(!JustDireLavaRepairCompatibility.eligible(transformed) && transformed.canBeHurtBy(level.damageSources().inFire()),
                "Lost Lava source retained exception");
        group("Codec/new entity ignores stale native coordinates; recompose removing final Blazegold removes exception");
    }

    private static void config(ServerLevel level) throws ReflectiveOperationException {
        Map<?, ?> flags = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config").getField("AVAILABLE_ABILITY_MAP").get(null);
        ModConfigSpec.BooleanValue enabled = flags.entrySet().stream().filter(e -> ((Enum<?>) e.getKey()).name().equals("LAVAREPAIR"))
                                                  .map(e -> (ModConfigSpec.BooleanValue) e.getValue()).findFirst().orElseThrow();
        boolean previous = enabled.get();
        try {
            enabled.set(false);
            arena(level);
            ItemStack root = damaged(pair().primary());
            ItemEntity entity = drop(level, root);
            ticks(entity, 160);
            require(!JustDireLavaRepairCompatibility.eligible(root) && !entity.isAlive() && !obsidian(level),
                    "Config-disabled intrinsic ability retained exception");
        } finally {
            enabled.set(previous);
        }
        group("Native config disables intrinsic capability/exception; runtime toggle does not");
    }

    private static void arena(ServerLevel level) {
        reset();
        for (BlockPos pos : BlockPos.betweenClosed(1, 79, 1, 12, 88, 12)) {
            level.setBlock(pos, (pos.getY() == 79 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
        }
        level.setBlock(LAVA, Blocks.LAVA.defaultBlockState(), 3);
    }

    private static ItemEntity drop(ServerLevel level, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level, 5.5, 80.2, 5.5, stack);
        entity.setDeltaMovement(Vec3.ZERO);
        return entity;
    }

    private static void preservation(ServerLevel level) {
        ItemStack source = boots("blazegold");
        source.set(JustDireLavaRepairTestSupport.<Integer>component("floatingticks"), 79);
        source.set(JustDireLavaRepairTestSupport.<BlockPos>component("lavapos"), LAVA);
        source.set(JustDireLavaRepairTestSupport.<Boolean>component("lavarepair_toggle"), false);
        ItemStack root = MixedBootsFactory.createPair(source, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        require(!root.has(JustDireLavaRepairTestSupport.component("lavapos")) && !root.has(JustDireLavaRepairTestSupport.component("floatingticks")),
                "Creation copied working progress into root");
        require(ModContent.getMixedBootsData(root).orElseThrow().left().source().has(JustDireLavaRepairTestSupport.component("lavapos")),
                "Sanitization rewrote immutable history");
        root.set(JustDireLavaRepairTestSupport.<Integer>component("lavarepair_bindingmode"), 2);
        ItemStack next = MixedBootsRecomposer.recompose(root, boots("blazegold"), boots("eclipsealloy"));
        require(Boolean.FALSE.equals(next.get(JustDireLavaRepairTestSupport.<Boolean>component("lavarepair_toggle")))
                        && next.getOrDefault(JustDireLavaRepairTestSupport.<Integer>component("lavarepair_bindingmode"), 0) == 2,
                "Live Lava controls were reprojected");
        root.remove(JustDireLavaRepairTestSupport.component("lavarepair_toggle"));
        root.remove(JustDireLavaRepairTestSupport.component("lavarepair_bindingmode"));
        next = MixedBootsRecomposer.recompose(root, source, new ItemStack(Items.DIAMOND_BOOTS));
        require(!next.has(JustDireLavaRepairTestSupport.component("lavarepair_toggle"))
                        && !next.has(JustDireLavaRepairTestSupport.component("lavarepair_bindingmode")), "Explicit absence lost on recompose");
        group("Creation/recomposition sanitize working fields, preserve false/absent Lava preferences and source history");
    }

    private static void guards(MinecraftServer server) throws ReflectiveOperationException {
        String variant = System.getProperty("mismatchedboots.runtimetest.justDireLavaRepairVariant");
        ServerLevel level = server.overworld();
        arena(level);
        if (variant.equals("absent")) {
            ItemStack root = MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)).primary();
            require(!JustDireLavaRepairCompatibility.eligible(root), "Absent JDT granted capability");
            ItemEntity entity = drop(level, damaged(root));
            ticks(entity, 160);
            require(!entity.isAlive() && !obsidian(level), "Absent adapter changed ordinary item damage");
        } else {
            require(games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireUpgradesCompatibility.available(),
                    "Lava failure disabled upgrades");
            ItemStack root = damaged(pair().primary());
            if (variant.equals("queryFailure") || variant.equals("emissionFailure")) {
                var api = JustDireLavaRepairCompatibility.class.getDeclaredField(variant.equals("queryFailure") ? "api" : "visuals");
                api.setAccessible(true);
                Object binding = api.get(null);
                var components = binding.getClass().getRecordComponents();
                Object[] arguments = new Object[components.length];
                for (int i = 0; i < arguments.length; i++) {
                    var accessor = components[i].getAccessor();
                    accessor.setAccessible(true);
                    arguments[i] = accessor.invoke(binding);
                    if (arguments[i] instanceof java.lang.invoke.MethodHandle handle) {
                        var throwing = java.lang.invoke.MethodHandles.throwException(handle.type().returnType(), IllegalStateException.class)
                                                                     .bindTo(new IllegalStateException("injected optional Lava " + variant));
                        arguments[i] = java.lang.invoke.MethodHandles.dropArguments(throwing, 0, handle.type().parameterList());
                    }
                }
                var constructor = binding.getClass().getDeclaredConstructors()[0];
                constructor.setAccessible(true);
                api.set(null, constructor.newInstance(arguments));
            }
            if (variant.equals("apiMissing") || variant.equals("queryFailure")) {
                require(!JustDireLavaRepairCompatibility.eligible(root) && !JustDireLavaRepairCompatibility.eligible(root),
                        "Failed API admitted root");
                ItemEntity entity = drop(level, root);
                ticks(entity, 160);
                require(!entity.isAlive() && !obsidian(level), "Failed gameplay API granted repair/protection");
            } else {
                require(JustDireLavaRepairCompatibility.eligible(root), "Visual failure removed intrinsic fire resistance");
                ItemEntity entity = drop(level, root);
                untilRepaired(entity, 260);
                require(entity.isAlive() && !root.isDamaged() && obsidian(level) && JustDireLavaRepairTestSupport.emissions.isEmpty()
                                && JustDireLavaRepairTestSupport.sounds.size() == 1, "Visual failure disabled/repeated gameplay");
            }
            // Registry-only preservation must not depend on either binding remaining available.
            preservation(level);
        }
        NetworkAcceptanceFiles.write(PREFIX + "-report.txt",
                                     variant + ": isolated failure/absence, root protection/repair and unrelated upgrade readiness\n" + report);
        NetworkAcceptanceFiles.write(PREFIX + "-success.txt", "neoforge\n");
    }

    private static void reset() {
        JustDireLavaRepairCompatibility.clearEpisodes();
        JustDireLavaRepairTestSupport.reset();
    }

    private static boolean source(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(Blocks.LAVA) && state.getValue(LiquidBlock.LEVEL) == 0;
    }

    private static void ticks(ItemEntity entity, int ticks) {
        for (int i = 0; i < ticks && entity.isAlive(); i++) {
            entity.tick();
        }
    }

    private static int untilRepaired(ItemEntity entity, int maximum) {
        int i = 0;
        while (i < maximum && entity.isAlive() && entity.getItem().isDamaged()) {
            entity.tick();
            i++;
        }
        return i;
    }

    private static boolean obsidian(ServerLevel level) {
        return level.getBlockState(LAVA).is(Blocks.OBSIDIAN);
    }

    private static ItemStack damaged(ItemStack stack) {
        stack.setDamageValue(Math.min(100, stack.getMaxDamage() - 1));
        return stack;
    }

    private static ItemStack boots(String tier) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", tier + "_boots")));
    }

    private static MixedBootsPair pair() {
        return MixedBootsFactory.createPair(boots("blazegold"), new ItemStack(Items.DIAMOND_BOOTS));
    }

    private static void group(String text) {
        groups++;
        report.append(groups).append(". ").append(text).append('\n');
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
