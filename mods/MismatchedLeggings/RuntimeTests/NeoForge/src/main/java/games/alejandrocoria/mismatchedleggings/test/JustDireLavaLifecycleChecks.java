package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireLavaRepair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.util.UUID;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** One saved live entity, then ordinary integrated-server ticks and native pickup/drop paths. */
final class JustDireLavaLifecycleChecks {
    private static final BlockPos SOURCE = new BlockPos(8, 80, 5);
    private static int stage;
    private static long started;
    private static ItemEntity entity;
    private static ItemStack expected;

    private JustDireLavaLifecycleChecks() {
    }

    static void saveInFlight(ServerLevel level) throws Exception {
        var root = JustDireLavaChecks.mixed(true, false);
        root.set(DataComponents.CUSTOM_NAME, Component.literal("Saved in-flight Lava root"));
        root.setDamageValue(30);
        var item = JustDireLavaChecks.spawn(level, root, SOURCE);
        require(level.addFreshEntity(item), "Cannot save live Lava actor");
        for (int index = 0; index < 45; index++) {
            item.tick();
        }
        require(item.getY() > SOURCE.getY() + 1 && level.getBlockState(SOURCE).is(Blocks.LAVA), "No in-flight episode to save");
        // Hold the saved actor airborne: only persisted authority could complete it after reopening.
        item.setNoGravity(true);
        item.setDeltaMovement(Vec3.ZERO);
        var witness = new CompoundTag();
        witness.putString("entity", item.getUUID().toString());
        witness.putInt("age", item.getAge());
        witness.put("root", RecoveryChecks.encode(root, level.registryAccess()));
        // Legacy native working fields must not let a reopened airborne root bypass source acquisition.
        JustDireLavaChecks.set(root, "floatingticks", 79);
        JustDireLavaChecks.set(root, "lavapos", SOURCE);
        NbtIo.writeCompressed(witness, JustDireLavaChecks.report("lifecycle.nbt"));
    }

    static boolean finished() {
        return stage == 8;
    }

    static void tick(ServerPlayer player) throws Exception {
        var level = player.level();
        if (stage == 0) {
            var witness = RecoveryChecks.read(JustDireLavaChecks.report("lifecycle.nbt"));
            entity = (ItemEntity) level.getEntity(UUID.fromString(witness.getStringOr("entity", "")));
            expected = RecoveryChecks.decode(witness.getCompoundOrEmpty("root"), level.registryAccess());
            require(entity != null && entity.isAlive(), "Saved live actor missing after world reopen");
            if (entity.getAge() <= witness.getIntOr("age", 0)) {
                return;
            }
            unchanged(level, "World reopen resumed in-flight authority or retained native working fields");
            if (entity.getAge() < witness.getIntOr("age", 0) + 100) {
                return;
            }
            player.setGameMode(GameType.SURVIVAL);
            player.setInvulnerable(true);
            player.setNoGravity(true);
            player.getInventory().clearContent();
            entity.setNoPickUpDelay();
            player.teleportTo(entity.getX(), entity.getY(), entity.getZ());
            advance(level);
        } else if (stage == 1) {
            if (entity.isAlive()) {
                require(elapsed(level) < 60, "Natural pickup did not consume the old entity");
                return;
            }
            var picked = player.getInventory().removeItemNoUpdate(0);
            require(ItemStack.matches(expected, picked), "Pickup lost live root state/history");
            player.teleportTo(6.5, 83, 10.5);
            entity = player.drop(picked, false, true);
            require(entity != null, "Native redrop did not create an entity");
            entity.setPos(SOURCE.getX() + .5, SOURCE.getY() + .2, SOURCE.getZ() + .5);
            entity.setDeltaMovement(Vec3.ZERO);
            JustDireLavaObservations.reset();
            advance(level);
        } else if (stage == 2) {
            if (entity.getItem().isDamaged()) {
                require(elapsed(level) < 200, "Fresh redrop did not acquire a new repair episode");
                return;
            }
            var repaired = expected.copy();
            repaired.setDamageValue(0);
            require(level.getBlockState(SOURCE).is(Blocks.OBSIDIAN) && ItemStack.matches(repaired, entity.getItem())
                            && JustDireLavaObservations.sounds == 1, "Fresh native drop did not pay/repair once with unchanged state");
            entity.discard();
            fresh(level);
            advance(level);
        } else if (stage == 3) {
            if (elapsed(level) < 35) {
                return;
            }
            require(entity.getY() > SOURCE.getY() + 1, "Replacement control never acquired a live episode");
            expected = JustDireLavaChecks.diamond(false);
            expected.setDamageValue(17);
            expected.set(DataComponents.CUSTOM_NAME, Component.literal("Replacement root, not the old episode"));
            entity.setItem(expected.copy());
            hold();
            advance(level);
        } else if (stage == 4) {
            if (elapsed(level) < 100) {
                return;
            }
            unchanged(level, "Replacement root inherited the old entity's repair authority");
            entity.discard();
            fresh(level);
            advance(level);
        } else if (stage == 5) {
            if (elapsed(level) < 35) {
                return;
            }
            require(entity.getY() > SOURCE.getY() + 1, "Source-change control never acquired a live episode");
            hold();
            level.setBlock(SOURCE, Blocks.STONE.defaultBlockState(), 3);
            advance(level);
        } else if (stage == 6) {
            if (elapsed(level) < 3) {
                return;
            }
            level.setBlock(SOURCE, Blocks.LAVA.defaultBlockState(), 3);
            advance(level);
        } else if (stage == 7) {
            if (elapsed(level) < 100) {
                return;
            }
            unchanged(level, "Returned lava resumed an ended airborne episode");
            entity.discard();
            player.setNoGravity(false);
            player.setGameMode(GameType.SPECTATOR);
            Files.writeString(JustDireLavaChecks.report("lifecycle.txt"),
                    "Independent saved-world reopen rejects in-flight/native progress; natural pickup/redrop, replacement root and removed/returned lava passed\n");
            advance(level);
        }
    }

    private static void fresh(ServerLevel level) {
        var root = JustDireLavaChecks.mixed(false, false);
        root.setDamageValue(30);
        expected = root.copy();
        entity = JustDireLavaChecks.spawn(level, root, SOURCE);
        require(level.addFreshEntity(entity), "Cannot spawn natural lifecycle actor");
    }

    private static void hold() {
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
    }

    private static void unchanged(ServerLevel level, String message) {
        require(entity.isAlive() && ItemStack.matches(expected, entity.getItem()) && level.getBlockState(SOURCE).is(Blocks.LAVA)
                        && !entity.getItem().canBeHurtBy(level.damageSources().lava()) && JustDireLavaRepair.eligible(entity.getItem()), message);
    }

    private static long elapsed(ServerLevel level) {
        return level.getGameTime() - started;
    }

    private static void advance(ServerLevel level) {
        stage++;
        started = level.getGameTime();
    }
}
