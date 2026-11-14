package games.alejandrocoria.mismatchedleggings.test.lavaspike;

import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.setup.Config;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Bounded research controls through actual ItemEntity ticks and the native optional artifact. */
public final class LavaChecks {
    static final BlockPos SOURCE = new BlockPos(5, 80, 5);
    private static final StringBuilder evidence = new StringBuilder();
    private static boolean finished;

    private LavaChecks() {
    }

    public static void register(IEventBus bus) {
        LavaCandidate.initialize(bus);
        if (FMLEnvironment.getDist().isClient()) {
            LavaClient.register(bus);
        } else {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
        }
    }

    private static void tick(MinecraftServer server) {
        if (finished || server.getTickCount() < 20) {
            return;
        }
        finished = true;
        try {
            require(ModList.get().isLoaded("mismatchedboots") == Boolean.getBoolean("leggings.test.lavaBoots"), "Wrong Boots graph");
            var level = server.overworld();
            for (boolean healthy : List.of(false, true)) {
                complete(level, "native healthy=" + healthy, nativeItem("blazegold_leggings"), healthy);
                for (boolean inverse : List.of(false, true)) {
                    complete(level, "hybrid inverse=" + inverse + " healthy=" + healthy, mixed(inverse, false), healthy);
                    complete(level, "diamond inverse=" + inverse + " healthy=" + healthy, diamond(inverse), healthy);
                }
            }
            complete(level, "two providers", mixed(false, true), false);

            for (String control : List.of("tool_enabled", "lavarepair_toggle")) {
                var root = mixed(false, false);
                set(root, control, false);
                require(LavaCandidate.eligible(root) && !root.canBeHurtBy(level.damageSources().lava()), "Disabled switch lost permanent protection");
                var entity = spawn(level, root, SOURCE);
                var snapshot = root.copy();
                steps(entity, 25);
                require(ItemStack.matches(snapshot, entity.getItem()) && level.getBlockState(SOURCE).is(Blocks.LAVA), "Disabled ritual ran");
                entity.discard();
                evidence.append(control).append(": protected, ritual off\n");
            }

            var configuration = Config.AVAILABLE_ABILITY_MAP.get(Ability.LAVAREPAIR);
            boolean previous = configuration.get();
            try {
                configuration.set(false);
                require(!LavaCandidate.eligible(mixed(false, false)), "Configured-off capability remained eligible");
            } finally {
                configuration.set(previous);
            }
            require(!LavaCandidate.eligible(new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID))), "Bare mixed eligible");
            var counted = mixed(false, false);
            counted.setCount(2);
            require(!LavaCandidate.eligible(counted), "Counted root eligible");
            var ordinary = MixedLeggingsFactory.createPair(nativeItem("ferricore_leggings"), new ItemStack(Items.DIAMOND_LEGGINGS)).primary();
            require(!LavaCandidate.eligible(ordinary) && ordinary.canBeHurtBy(level.damageSources().lava()), "Unrelated fire intersection changed");
            require(mixed(false, false).canBeHurtBy(level.damageSources().generic()), "Non-fire damage was blocked");
            evidence.append("config, bare/count, ordinary and non-fire controls passed\n");

            var flowing = spawn(level, mixed(false, false), SOURCE);
            level.setBlock(SOURCE, Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 1), 3);
            LavaCandidate.tick(flowing.getItem(), flowing);
            require(flowing.getY() == SOURCE.getY() + .2 && flowing.getDeltaMovement().y == 0, "Flowing-only lava started ritual");
            flowing.discard();

            var stale = spawn(level, mixed(false, false), SOURCE);
            stale.getItem().setDamageValue(30);
            for (int index = 0; index < 40; index++) { stale.tick(); }
            level.setBlock(SOURCE, Blocks.STONE.defaultBlockState(), 3);
            for (int index = 0; index < 110; index++) { stale.tick(); }
            require(stale.getItem().getDamageValue() == 30, "Removed lava repaired stale episode");
            stale.discard();
            evidence.append("flowing-only and removed source cannot authorize repair\n");

            if (Boolean.getBoolean("leggings.test.lavaBoots")) {
                coexistence(level);
            }
            Files.writeString(report("server.txt"), evidence);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    private static void complete(ServerLevel level, String label, ItemStack root, boolean healthy) {
        root.setDamageValue(healthy ? 0 : 30);
        var expected = root.copy();
        expected.setDamageValue(0);
        var entity = spawn(level, root, SOURCE);
        steps(entity, 170);
        require(level.getBlockState(SOURCE).is(Blocks.OBSIDIAN), label + ": no block receipt");
        require(entity.isAlive() && !entity.getItem().isDamaged(), label + ": root lost/not repaired");
        if (root.has(LeggingsContent.mixedLeggingsDataType())) {
            require(ItemStack.matches(expected, entity.getItem()), label + ": changed root controls/FE/history");
            require(!root.canBeHurtBy(level.damageSources().lava()), label + ": no permanent item protection");
        }
        evidence.append(label).append(": obsidian, root repaired, retained unrelated state\n");
        entity.discard();
    }

    private static void coexistence(ServerLevel level) throws ReflectiveOperationException {
        var root = boots();
        root.setDamageValue(30);
        var legs = mixed(false, false);
        legs.setDamageValue(30);
        var first = spawn(level, root, SOURCE);
        var second = new ItemEntity(level, SOURCE.getX() + .5, SOURCE.getY() + .2, SOURCE.getZ() + .5, legs);
        for (int index = 0; index < 170 && level.getBlockState(SOURCE).is(Blocks.LAVA); index++) {
            first.tick();
            second.tick();
        }
        require(level.getBlockState(SOURCE).is(Blocks.OBSIDIAN)
                        && (!first.getItem().isDamaged() ^ !second.getItem().isDamaged()), "One lava source paid two repairs");
        require(!root.canBeHurtBy(level.damageSources().lava()) && !legs.canBeHurtBy(level.damageSources().lava()), "Coexistence lost protection");
        first.discard();
        second.discard();
        complete(level, "Leggings after sibling", mixed(true, false), false);
        complete(level, "Boots independent", root, false);
        evidence.append("Boots independent callbacks; shared source pays one repair\n");
    }

    static ItemStack boots() throws ReflectiveOperationException {
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        Object pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class)
                             .invoke(null, nativeItem("blazegold_boots"), new ItemStack(Items.DIAMOND_BOOTS));
        return (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
    }

    static ItemStack mixed(boolean inverse, boolean two) {
        var source = nativeItem("blazegold_leggings");
        set(source, "lavarepair_toggle", false); // Root edits/repair must not rewrite this historical switch.
        var other = two ? nativeItem("blazegold_leggings") : nativeItem("eclipsealloy_leggings");
        var pair = MixedLeggingsFactory.createPair(source, other);
        var root = inverse ? pair.inverse() : pair.primary();
        set(root, "tool_enabled", true);
        set(root, "lavarepair_toggle", true);
        if (!two) { set(root, "forge_energy", 421); }
        return root;
    }

    static ItemStack diamond(boolean inverse) {
        var pair = MixedLeggingsFactory.createPair(nativeItem("blazegold_leggings"), new ItemStack(Items.DIAMOND_LEGGINGS));
        return inverse ? pair.inverse() : pair.primary();
    }

    static ItemStack nativeItem(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value());
    }

    @SuppressWarnings("unchecked")
    static <T> void set(ItemStack root, String path, T value) {
        var type = (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value();
        root.set(type, value);
    }

    static ItemEntity spawn(ServerLevel level, ItemStack root, BlockPos source) {
        LavaCandidate.clearEpisodes();
        level.setBlock(source.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(source, Blocks.LAVA.defaultBlockState(), 3);
        for (int index = 1; index <= 7; index++) { level.setBlock(source.above(index), Blocks.AIR.defaultBlockState(), 3); }
        var entity = new ItemEntity(level, source.getX() + .5, source.getY() + .2, source.getZ() + .5, root);
        entity.setDeltaMovement(0, 0, 0);
        return entity;
    }

    private static void steps(ItemEntity entity, int count) {
        for (int index = 0; index < count; index++) { entity.tick(); }
    }

    static Path report(String name) {
        return Path.of(System.getProperty("leggings.test.report"), name);
    }
}
