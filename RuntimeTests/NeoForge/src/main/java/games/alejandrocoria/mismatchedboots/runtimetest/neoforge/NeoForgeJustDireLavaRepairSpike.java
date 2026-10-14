package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
public final class NeoForgeJustDireLavaRepairSpike {
    private static final String PREFIX = "just-dire-lava-repair";
    private static final BlockPos LAVA = new BlockPos(5, 80, 5);
    private static final StringBuilder report = new StringBuilder();
    private static boolean finished;
    private static int warmup, groups;

    private NeoForgeJustDireLavaRepairSpike() {}
    public static void register() { NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer())); }

    private static void run(MinecraftServer server) {
        if (finished) { return; }
        ServerLevel level = server.overworld();
        if (++warmup == 1) { level.setChunkForced(0, 0, true); }
        if (warmup < 200 && !level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(0, 0))) { return; }
        finished = true;
        try {
            require(warmup < 200, "Fixture chunk not ticking");
            JustDireLavaRepairCandidate.initialize();
            nativeControls(level);
            candidateControls(level);
            interruptions(server);
            config(level);
            NetworkAcceptanceFiles.write(PREFIX + "-report.txt", groups + " research groups; actual ItemEntity.tick, native controls, opt-in own-item hooks\n" + report);
            NetworkAcceptanceFiles.write(PREFIX + "-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.write(PREFIX + "-partial-report.txt", report.toString());
            NetworkAcceptanceFiles.failure(PREFIX, failure);
        }
        finally { JustDireLavaRepairCandidate.reset(); level.setChunkForced(0, 0, false); server.halt(false); }
    }

    private static void nativeControls(ServerLevel level) {
        arena(level);
        ItemStack nativeBoots = damaged(boots("blazegold"));
        ItemEntity original = drop(level, nativeBoots);
        int ticks = untilRepaired(original, 260);
        require(original.isAlive() && !nativeBoots.isDamaged() && obsidian(level), "Native ritual did not repair/consume");
        group("Native damaged Blazegold completed after " + ticks + " entity ticks; survived and consumed source");

        arena(level); ItemEntity healthy = drop(level, boots("blazegold")); ticks(healthy, 260);
        require(obsidian(level), "Native healthy-item consumption changed");
        group("Native healthy Blazegold also consumes source (intentional candidate difference)");

        arena(level); ItemEntity ordinary = drop(level, damaged(new ItemStack(Items.DIAMOND_BOOTS))); ticks(ordinary, 160);
        require(!ordinary.isAlive() && JustDireLavaRepairCandidate.source(level, LAVA), "Unprotected lava control did not die");
        group("Diamond control dies in real lava, does not consume source");

        arena(level); JustDireLavaRepairCandidate.nativeReuse = true;
        ItemStack root = damaged(pair().primary()); ItemEntity reused = drop(level, root);
        try { untilRepaired(reused, 260); }
        finally { JustDireLavaRepairCandidate.nativeReuse = false; }
        require(reused.isAlive() && !root.isDamaged() && obsidian(level), "Direct native helper cannot operate on root");
        group("Direct native helper accepts actual mixed root with own-item permanent immunity");

        arena(level); root = damaged(pair().primary());
        root.set(JustDireLavaRepairCandidate.<Integer>component("floatingticks"), 79);
        root.set(JustDireLavaRepairCandidate.<BlockPos>component("lavapos"), LAVA);
        ItemEntity denied = drop(level, root); denied.setPos(5.5, 83.2, 5.5);
        JustDireLavaRepairCandidate.refusedBlock = LAVA;
        boolean receipt;
        try { receipt = JustDireLavaRepairCandidate.invokeNative(root, denied); }
        finally { JustDireLavaRepairCandidate.refusedBlock = null; }
        require(!receipt && !root.isDamaged() && JustDireLavaRepairCandidate.source(level, LAVA)
                && JustDireLavaRepairCandidate.refusedWrites == 1, "Native unchecked-write/false-return finding changed");
        group("Native helper repairs despite a refused obsidian write and returns false: unsafe completion receipt");
    }

    private static void candidateControls(ServerLevel level) {
        for (ItemStack root : List.of(pair().primary(), pair().inverse(), MixedBootsFactory.createPair(boots("blazegold"), boots("blazegold")).primary())) {
            arena(level); damaged(root); ItemStack before = root.copy();
            ItemEntity entity = drop(level, root);
            int elapsed = untilRepaired(entity, 260);
            require(entity.isAlive() && obsidian(level) && !root.isDamaged() && JustDireLavaRepairCandidate.completions == 1,
                    "Candidate did not complete exactly once");
            before.setDamageValue(0);
            require(ItemStack.matches(before, root), "Repair changed more than root durability");
            ticks(entity, 100);
            require(entity.isAlive() && entity.getAge() >= elapsed + 100 && !entity.hasPickUpDelay(), "Normal lifecycle/pickup countdown skipped");
            group("Candidate pair orientation/source count: " + elapsed + " ticks; one consumption, root-only repair, normal age/pickup countdown");
        }

        for (String disabled : List.of("healthy", "tool_enabled", "lavarepair_toggle")) {
            arena(level); ItemStack root = pair().primary();
            if (!disabled.equals("healthy")) { damaged(root); root.set(JustDireLavaRepairCandidate.<Boolean>component(disabled), false); }
            ItemEntity entity = drop(level, root); int damage = root.getDamageValue(); ticks(entity, 240);
            require(entity.isAlive() && JustDireLavaRepairCandidate.source(level, LAVA)
                    && root.getDamageValue() == damage && entity.fireImmune(), "Healthy/disabled lost permanent immunity or consumed lava");
            group(disabled + ": permanent item immunity, no ritual/consumption");
        }

        arena(level); ItemStack root = damaged(pair().primary()); ItemEntity immune = drop(level, root);
        require(root.get(DataComponents.DAMAGE_RESISTANT) == null, "One-source projection changed");
        for (var damage : List.of(level.damageSources().lava(), level.damageSources().inFire(), level.damageSources().onFire(),
                level.damageSources().hotFloor())) {
            require(!root.canBeHurtBy(damage) && !immune.hurtServer(level, damage, 4), "Fire-tagged damage not denied by item hook");
        }
        require(immune.fireImmune() && root.canBeHurtBy(level.damageSources().generic())
                && immune.hurtServer(level, level.damageSources().generic(), 100) && !immune.isAlive(), "Immunity leaked into unrelated damage");
        ItemStack plain = MixedBootsFactory.createPair(new ItemStack(Items.NETHERITE_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)).primary();
        require(plain.get(DataComponents.DAMAGE_RESISTANT) == null && plain.canBeHurtBy(level.damageSources().inFire()), "Generic intersection was broadened");
        ItemStack invalid = new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID));
        require(invalid.canBeHurtBy(level.damageSources().inFire()), "Bare root gained immunity");
        root.setCount(2); require(root.canBeHurtBy(level.damageSources().inFire()), "Invalid count gained exception");
        group("Item hook denies fire only; generic damage still destroys; component intersection/bare/count controls unchanged");

        arena(level); level.setBlock(LAVA, Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 4), 3);
        root = damaged(pair().primary()); ItemEntity flow = drop(level, root); ticks(flow, 220);
        require(flow.isAlive() && root.isDamaged() && !obsidian(level), "Flowing-only contact starts repair");
        group("Flowing lava: immunity without repair/consumption");

        arena(level); JustDireLavaRepairCandidate.refusedBlock = LAVA;
        root = damaged(pair().primary()); ItemEntity denied = drop(level, root);
        try { ticks(denied, 220); } finally { JustDireLavaRepairCandidate.refusedBlock = null; }
        require(denied.isAlive() && root.isDamaged() && JustDireLavaRepairCandidate.refusedWrites > 0
                && JustDireLavaRepairCandidate.source(level, LAVA), "Candidate awarded repair after refused write");
        group("Candidate completion checks actual write receipt: denied consumption never repairs");

        arena(level); ItemEntity first = drop(level, damaged(pair().primary())); ItemEntity second = drop(level, damaged(pair().inverse()));
        for (int i = 0; i < 240; i++) { first.tick(); second.tick(); }
        require(first.isAlive() && second.isAlive() && obsidian(level) && JustDireLavaRepairCandidate.completions == 1
                && first.getItem().isDamaged() != second.getItem().isDamaged(), "Shared source paid twice");
        group("Two entities sharing one source: exactly one successful repair");

        arena(level); root = damaged(MixedBootsFactory.createPair(boots("blazegold"), boots("eclipsealloy")).primary());
        root.set(JustDireResourceTestSupport.energyComponent(), 12345); ItemStack before = root.copy();
        ItemEntity powered = drop(level, root); untilRepaired(powered, 260); before.setDamageValue(0);
        require(!root.isDamaged() && ItemStack.matches(root, before), "Repair changed live FE/controls/provenance");
        group("Blazegold + powered source: FE/controls/history unchanged, only root durability restored");
    }

    private static void interruptions(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (String interruption : List.of("sourceRemoved", "leaveBounds", "toggleOff", "rootReplaced", "ceiling")) {
            arena(level); ItemStack root = damaged(pair().primary()); ItemEntity entity = drop(level, root);
            ticks(entity, 10);
            require(JustDireLavaRepairCandidate.pending(entity), "No episode to interrupt");
            switch (interruption) {
                case "sourceRemoved" -> level.setBlock(LAVA, Blocks.AIR.defaultBlockState(), 3);
                case "leaveBounds" -> entity.setPos(10.5, 84, 5.5);
                case "toggleOff" -> root.set(JustDireLavaRepairCandidate.<Boolean>component("lavarepair_toggle"), false);
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
            require(root.isDamaged() && JustDireLavaRepairCandidate.completions == 0, "Interrupted episode granted repair");
            require(JustDireLavaRepairCandidate.cancellations > 0, interruption + ": cancellation/timeout not exercised");
            group(interruption + ": no stale/free completion; bounded candidate cancellation");
        }

        arena(level); ItemStack root = damaged(pair().primary()); ItemEntity first = drop(level, root); ticks(first, 15);
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        ItemStack loaded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, root).getOrThrow()).getOrThrow();
        // Deliberately import exactly the native stale-state attack, not the candidate's in-memory episode.
        loaded.set(JustDireLavaRepairCandidate.<Integer>component("floatingticks"), 79);
        loaded.set(JustDireLavaRepairCandidate.<BlockPos>component("lavapos"), LAVA);
        ItemEntity reloaded = drop(level, loaded); reloaded.setPos(10.5, 84, 5.5); ticks(reloaded, 100);
        require(loaded.isDamaged() && JustDireLavaRepairCandidate.source(level, LAVA)
                && !loaded.has(JustDireLavaRepairCandidate.component("lavapos")), "Imported progress remotely repaired");
        ItemStack transformed = MixedBootsRecomposer.recompose(root, boots("eclipsealloy"), new ItemStack(Items.DIAMOND_BOOTS));
        require(!JustDireLavaRepairCandidate.eligible(transformed) && transformed.canBeHurtBy(level.damageSources().inFire()), "Lost Lava source retained exception");
        group("Codec/new entity ignores stale native coordinates; recompose removing final Blazegold removes exception");
    }

    private static void config(ServerLevel level) throws ReflectiveOperationException {
        Map<?, ?> flags = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config").getField("AVAILABLE_ABILITY_MAP").get(null);
        ModConfigSpec.BooleanValue enabled = flags.entrySet().stream().filter(e -> ((Enum<?>) e.getKey()).name().equals("LAVAREPAIR"))
                .map(e -> (ModConfigSpec.BooleanValue) e.getValue()).findFirst().orElseThrow();
        boolean previous = enabled.get();
        try {
            enabled.set(false); arena(level); ItemStack root = damaged(pair().primary()); ItemEntity entity = drop(level, root);
            ticks(entity, 160);
            require(!JustDireLavaRepairCandidate.eligible(root) && !entity.isAlive() && !obsidian(level), "Config-disabled intrinsic ability retained exception");
        } finally { enabled.set(previous); }
        group("Native config disables intrinsic capability/exception; runtime toggle does not");
    }

    private static void arena(ServerLevel level) {
        JustDireLavaRepairCandidate.reset();
        for (BlockPos pos : BlockPos.betweenClosed(1, 79, 1, 12, 88, 12)) {
            level.setBlock(pos, (pos.getY() == 79 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
        }
        level.setBlock(LAVA, Blocks.LAVA.defaultBlockState(), 3);
    }
    private static ItemEntity drop(ServerLevel level, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level, 5.5, 80.2, 5.5, stack);
        entity.setDeltaMovement(Vec3.ZERO); return entity;
    }
    private static void ticks(ItemEntity entity, int ticks) { for (int i = 0; i < ticks && entity.isAlive(); i++) { entity.tick(); } }
    private static int untilRepaired(ItemEntity entity, int maximum) {
        int i = 0; while (i < maximum && entity.isAlive() && entity.getItem().isDamaged()) { entity.tick(); i++; }
        return i;
    }
    private static boolean obsidian(ServerLevel level) { return level.getBlockState(LAVA).is(Blocks.OBSIDIAN); }
    private static ItemStack damaged(ItemStack stack) { stack.setDamageValue(Math.min(100, stack.getMaxDamage() - 1)); return stack; }
    private static ItemStack boots(String tier) { return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", tier + "_boots"))); }
    private static MixedBootsPair pair() { return MixedBootsFactory.createPair(boots("blazegold"), new ItemStack(Items.DIAMOND_BOOTS)); }
    private static void group(String text) { groups++; report.append(groups).append(". ").append(text).append('\n'); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
