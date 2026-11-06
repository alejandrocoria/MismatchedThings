package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.ForbiddenModifiers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.fml.ModList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Extend the existing real-world recovery gate with an actual absent/reinstalled optional mod. */
public final class ForbiddenModifierRecoveryChecks {
    private ForbiddenModifierRecoveryChecks() {}

    public static void stored(MinecraftServer server) throws Exception {
        String stage = RecoveryFixture.stage();
        boolean present = !stage.startsWith("absent");
        require(ModList.get().isLoaded("forbidden_arcanus") == present, "Native mod was not actually removed/reinstalled");
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(new BlockPos(0, 65, 0));
        require(barrel != null, "Native recovery barrel disappeared");
        var baseline = Path.of(System.getProperty("leggings.test.recoveryBaseline"), "forbidden-roots.nbt");
        if (stage.equals("write")) {
            var player = ForbiddenModifierChecks.actor(server);
            var roots = new ArrayList<ItemStack>();
            try {
                var pair = MixedLeggingsFactory.createPair(ForbiddenArcanusChecks.nativeItem("draco_arcanus_leggings"),
                        new ItemStack(Items.DIAMOND_LEGGINGS), CompositionRules.defaults(), server.registryAccess());
                for (var plain : List.of(pair.primary(), pair.inverse())) {
                    var soul = ForbiddenModifierSmithingChecks.upgrade(player, plain, "soulbound", false);
                    roots.add(soul);
                    roots.add(ForbiddenModifierSmithingChecks.upgrade(player, soul, "eternal", true));
                }
            } finally {
                ForbiddenModifierChecks.close(player);
            }
            var encoded = new CompoundTag();
            for (int index = 0; index < roots.size(); index++) {
                var root = roots.get(index);
                root.set(CompositionFixture.CHARGE, 987L);
                root.set(DataComponents.REPAIR_COST, 7);
                root.set(DataComponents.CUSTOM_NAME, Component.literal("Paid native recovery " + index));
                var opaque = new CompoundTag();
                opaque.putLongArray("paid", new long[]{Long.MAX_VALUE, Long.MIN_VALUE});
                root.set(DataComponents.CUSTOM_DATA, CustomData.of(opaque));
                barrel.setItem(6 + index, root);
                encoded.put("root" + index, RecoveryChecks.encode(root, server.registryAccess()));
            }
            NbtIo.writeCompressed(encoded, baseline);
        } else {
            var expected = RecoveryChecks.read(baseline);
            for (int index = 0; index < 4; index++) {
                var root = barrel.getItem(6 + index);
                if (present) {
                    var decoded = RecoveryChecks.decode(expected.getCompoundOrEmpty("root" + index), server.registryAccess());
                    ForbiddenModifierChecks.verify(root, new ForbiddenModifiers(index % 2 == 1, true));
                    require(ItemStack.matches(root, decoded)
                                    && MixedLeggingsStructure.validData(root).orElseThrow()
                                    .equals(MixedLeggingsStructure.validData(decoded).orElseThrow()),
                            "Reinstallation partially recovered native live state/history in slot " + index);
                } else {
                    RecoveryChecks.inactive(root);
                    require(!root.has(LeggingsContent.forbiddenModifiersType()), "Suspended root exposed live native effect flags");
                    var raw = RecoveryChecks.encode(root, server.registryAccess());
                    var repeated = baseline.resolveSibling("forbidden-absent-" + index + ".nbt");
                    if (stage.equals("absent")) {
                        NbtIo.writeCompressed(raw, repeated);
                    } else {
                        require(raw.equals(RecoveryChecks.read(repeated)), "Repeated absent save changed the native archive");
                    }
                }
            }
        }
        barrel.setChanged();
        Files.writeString(RecoveryChecks.report("forbidden-recovery.txt"),
                stage + ": actual FA source, both orientations, installed Soulbound/dual flags and complete native root/history passed\n");
    }
}
