package games.alejandrocoria.mismatchedleggings.test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Frozen published-writer NBT enters through a native container, not a replacement item decoder. */
public final class ForbiddenBootsStorage {
    public static final AtomicInteger adoptions = new AtomicInteger();
    private static final BlockPos POSITION = new BlockPos(0, 65, 0);

    private ForbiddenBootsStorage() {}

    static void seed(MinecraftServer server) throws Exception {
        var fixture = ForbiddenBootsChecks.fixture();
        var expected = new CompoundTag();
        var items = new ListTag();
        var ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
        for (int slot = 0; slot < fixture.getIntOr("count", 0); slot++) {
            var raw = fixture.getCompoundOrEmpty("root" + slot).copy();
            var decoded = ItemStack.CODEC.parse(ops, raw).getOrThrow();
            expected.put("root" + slot, RecoveryChecks.encode(decoded, server.registryAccess()));
            raw.putByte("Slot", (byte) slot);
            items.add(raw);
        }
        expected.putInt("count", items.size());
        NbtIo.writeCompressed(expected, baseline());
        server.overworld().getChunkAt(POSITION);
        server.overworld().setBlock(POSITION, Blocks.BARREL.defaultBlockState(), 3);
        var data = new CompoundTag();
        data.put("Items", items);
        var problems = new ProblemReporter.Collector();
        int before = adoptions.get();
        storage(server).loadWithComponents(TagValueInput.create(problems, server.registryAccess(), data));
        require(problems.getReport().isEmpty(), "Native legacy container load reported data loss: " + problems.getReport());
        verify(server);
        require(adoptions.get() == before + 7, "Native barrel did not adopt exactly the seven supported live scalars");
        storage(server).setChanged();
        Files.writeString(ForbiddenArcanusChecks.report("boots-storage-server"),
                "Native barrel loaded 15 pinned published roots, adopted seven live scalars and preserved exact full state\n");
    }

    static void verify(MinecraftServer server) {
        if (!ForbiddenBootsChecks.enabled()) { return; }
        CompoundTag expected;
        try {
            expected = RecoveryChecks.read(baseline());
        } catch (Exception failure) {
            throw new AssertionError("Could not read legacy container oracle", failure);
        }
        var barrel = storage(server);
        for (int slot = 0; slot < expected.getIntOr("count", 0); slot++) {
            require(expected.getCompoundOrEmpty("root" + slot)
                            .equals(RecoveryChecks.encode(barrel.getItem(slot), server.registryAccess())),
                    "Native saved legacy container lost full root/history in slot " + slot);
        }
    }

    private static BarrelBlockEntity storage(MinecraftServer server) {
        server.overworld().getChunkAt(POSITION);
        var barrel = server.overworld().getBlockEntity(POSITION);
        require(barrel instanceof BarrelBlockEntity, "Legacy native barrel was not loaded");
        return (BarrelBlockEntity) barrel;
    }

    private static Path baseline() {
        return Path.of(System.getProperty("leggings.test.report"), "forbidden-boots-storage.nbt");
    }
}
