package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.fml.ModList;

import java.nio.file.Files;
import java.nio.file.Path;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real JDT disappearance extends the existing world/codec/TCP gate; no native types load during absence. */
public final class JustDireRecoveryChecks {
    private JustDireRecoveryChecks() {}

    public static void stored(MinecraftServer server) throws Exception {
        boolean present = !RecoveryFixture.stage().startsWith("absent");
        require(ModList.get().isLoaded("justdirethings") == present, "JDT was not actually removed/reinstalled");
        var barrel = storage(server);
        var baseline = baseline();
        if (RecoveryFixture.stage().equals("write")) {
            var roots = JustDireLifecycleChecks.roots(server);
            var encoded = new CompoundTag();
            for (int index = 0; index < roots.size(); index++) {
                var root = roots.get(index);
                barrel.setItem(6 + index, root);
                encoded.put("root" + index, RecoveryChecks.encode(root, server.registryAccess()));
            }
            NbtIo.writeCompressed(encoded, baseline);
        } else {
            var expected = RecoveryChecks.read(baseline);
            for (int index = 0; index < 2; index++) {
                var root = barrel.getItem(6 + index);
                if (present) {
                    require(ItemStack.matches(root, RecoveryChecks.decode(expected.getCompoundOrEmpty("root" + index), server.registryAccess())),
                            "JDT reinstall/reopen partially recovered paid root/history");
                    JustDireLifecycleChecks.verify(root);
                    JustDireLifecycleChecks.verifyWearer(server, root);
                } else {
                    RecoveryChecks.inactive(root);
                    require(root.getComponents().keySet().stream().noneMatch(JustDireRecoveryChecks::nativeComponent)
                                    && !root.has(LeggingsContent.mixedLeggingsDataType()),
                            "Suspended JDT root still exposes live native components/history");
                    var raw = RecoveryChecks.encode(root, server.registryAccess());
                    var repeated = baseline.resolveSibling("jdt-absent-" + index + ".nbt");
                    if (RecoveryFixture.stage().equals("absent")) { NbtIo.writeCompressed(raw, repeated); }
                    else { require(raw.equals(RecoveryChecks.read(repeated)), "Repeated absent save changed JDT archive"); }
                }
            }
        }
        barrel.setChanged();
        Files.writeString(RecoveryChecks.report("jdt-recovery.txt"), RecoveryFixture.stage()
                + ": paid inverse roots, installed upgrades, edited controls, live charge, tier/trim histories and complete native archive passed\n");
    }

    public static void connected(MinecraftServer server, ServerPlayer player) {
        // The existing death/respawn witness owns slots LEGS/0. These additional barrel copies
        // travel in ordinary inventory packets and are cleared before that unrelated death test.
        for (int index = 0; index < 2; index++) {
            if (RecoveryFixture.stage().equals("absent")) {
                require(ItemStack.matches(player.getInventory().getItem(1 + index), storage(server).getItem(6 + index)),
                        "Saved JDT player copy did not suspend like its barrel copy");
            } else {
                player.getInventory().setItem(1 + index, storage(server).getItem(6 + index).copy());
            }
        }
    }

    private static boolean nativeComponent(DataComponentType<?> type) {
        Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        return id != null && id.getNamespace().equals("justdirethings");
    }

    private static Path baseline() {
        return Path.of(System.getProperty("leggings.test.recoveryBaseline"), "jdt-roots.nbt");
    }

    private static BarrelBlockEntity storage(MinecraftServer server) {
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(new BlockPos(0, 65, 0));
        require(barrel != null, "JDT recovery barrel missing");
        return barrel;
    }
}
