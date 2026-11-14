package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireLavaRepair;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireSettings;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CommonHooks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
            var roots = new ArrayList<>(JustDireLifecycleChecks.roots(server));
            roots.addAll(lavaRoots());
            require(roots.size() == RecoveryChecks.JDT_ROOTS, "Recovery snapshot omitted a JDT witness");
            var encoded = new CompoundTag();
            for (int index = 0; index < roots.size(); index++) {
                var root = roots.get(index);
                barrel.setItem(6 + index, root);
                encoded.put("root" + index, RecoveryChecks.encode(root, server.registryAccess()));
            }
            NbtIo.writeCompressed(encoded, baseline);
        } else {
            var expected = RecoveryChecks.read(baseline);
            for (int index = 0; index < RecoveryChecks.JDT_ROOTS; index++) {
                var root = barrel.getItem(6 + index);
                if (present) {
                    require(ItemStack.matches(root, RecoveryChecks.decode(expected.getCompoundOrEmpty("root" + index), server.registryAccess())),
                            "JDT reinstall/reopen partially recovered paid root/history");
                    if (index < 2) {
                        JustDireLifecycleChecks.verify(root);
                        JustDireLifecycleChecks.verifyWearer(server, root);
                        var equipped = root.copy();
                        var player = RepairFixtures.player(server);
                        player.setItemSlot(EquipmentSlot.LEGS, equipped);
                        CommonHooks.onArmorHurt(player.damageSources().generic(), new EquipmentSlot[]{EquipmentSlot.LEGS}, 2, player);
                        var paid = root.copy();
                        paid.set(JustDireDataComponents.FORGE_ENERGY, 88_480);
                        require(ItemStack.matches(equipped, paid), "Recovered root did not resume incoming FE payment with unchanged history/settings");
                    } else {
                        verifyLava(server, root);
                    }
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
                + ": paid inverse roots, installed upgrades, edited controls, live charge/Decoy clocks, tier/trim histories, Lava providers/preferences and complete native archive passed"
                + (present && !RecoveryFixture.stage().equals("write") ? "; reopened incoming FE payment passed\n" : "\n"));
    }

    public static void connected(MinecraftServer server, ServerPlayer player) {
        // The existing death/respawn witness owns slots LEGS/0. These additional barrel copies
        // travel in ordinary inventory packets and are cleared before that unrelated death test.
        for (int index = 0; index < RecoveryChecks.JDT_ROOTS; index++) {
            if (RecoveryFixture.stage().equals("absent")) {
                require(ItemStack.matches(player.getInventory().getItem(1 + index), storage(server).getItem(6 + index)),
                        "Saved JDT player copy did not suspend like its barrel copy");
            } else {
                player.getInventory().setItem(1 + index, storage(server).getItem(6 + index).copy());
            }
        }
    }

    private static List<ItemStack> lavaRoots() {
        var source = JustDireLavaChecks.nativeItem("blazegold_leggings");
        JustDireLavaChecks.set(source, "floatingticks", 79);
        JustDireLavaChecks.set(source, "lavapos", new BlockPos(20, 80, 5));
        var pair = MixedLeggingsFactory.createPair(source, JustDireLavaChecks.nativeItem("eclipsealloy_leggings"));
        var roots = List.of(pair.primary(), pair.inverse());
        for (int index = 0; index < roots.size(); index++) {
            var root = roots.get(index);
            root.setDamageValue(30);
            root.set(JustDireDataComponents.FORGE_ENERGY, 421);
            root.set(JustDireDataComponents.TOOL_ENABLED, index == 0);
            root.set(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.LAVAREPAIR), index != 0);
            root.set(JustDireDataComponents.ABILITY_BINDING_MODES.get(Ability.LAVAREPAIR), 2);
            root.set(JustDireDataComponents.ABILITY_BINDINGS, List.of(new ToolRecords.AbilityBinding("lavarepair", index == 0 ? 71 : 4, index != 0, index == 0)));
        }
        return roots;
    }

    private static void verifyLava(MinecraftServer server, ItemStack stored) {
        var level = server.overworld();
        require(JustDireLavaRepair.eligible(stored) && !stored.canBeHurtBy(level.damageSources().lava())
                        && JustDireSettings.metadata(stored, "lavarepair") != null, "Recovered Lava capability/settings/protection unavailable");
        var root = stored.copy();
        root.set(JustDireDataComponents.TOOL_ENABLED, true);
        root.set(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.LAVAREPAIR), true);
        var expected = root.copy();
        expected.setDamageValue(0);
        var source = new BlockPos(20, 80, 5);
        var entity = JustDireLavaChecks.spawn(level, root, source);
        for (int tick = 0; tick < 170; tick++) {
            entity.tick();
        }
        require(level.getBlockState(source).is(Blocks.OBSIDIAN)
                        && ItemStack.matches(expected, entity.getItem()), "Restored Lava root could not acquire a fresh checked ritual without changing FE/history/preferences");
        entity.discard();
        JustDireLavaObservations.capture = false;
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
