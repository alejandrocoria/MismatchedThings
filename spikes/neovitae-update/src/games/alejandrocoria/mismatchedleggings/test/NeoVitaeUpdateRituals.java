package games.alejandrocoria.mismatchedleggings.test;

import com.breakinblocks.neovitae.api.soul.AnimaTicket;
import com.breakinblocks.neovitae.common.block.BlockRitualStone;
import com.breakinblocks.neovitae.common.block.NVBlocks;
import com.breakinblocks.neovitae.common.blockentity.MasterRitualStoneBlockEntity;
import com.breakinblocks.neovitae.common.datacomponent.NVDataComponents;
import com.breakinblocks.neovitae.common.item.NVItems;
import com.breakinblocks.neovitae.common.item.soul.HellforgedHelper;
import com.breakinblocks.neovitae.common.tag.NVTags;
import com.breakinblocks.neovitae.ritual.NVRituals;
import com.breakinblocks.neovitae.ritual.Ritual;
import com.breakinblocks.neovitae.ritual.RitualLayouts;
import com.breakinblocks.neovitae.ritual.RitualHelper;
import com.breakinblocks.neovitae.util.helper.AnimaHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native structure validation, activation, reagent selection and real Anima debits. */
final class NeoVitaeUpdateRituals {
    private static final BlockPos MASTER = new BlockPos(48, 80, 0);
    private final MinecraftServer server;
    private final ServerPlayer player;
    private final StringBuilder report;

    NeoVitaeUpdateRituals(MinecraftServer server, ServerPlayer player, StringBuilder report) {
        this.server = server;
        this.player = player;
        this.report = report;
    }

    private MasterRitualStoneBlockEntity activate(Ritual ritual) {
        var level = server.overworld();
        level.setBlockAndUpdate(MASTER, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(MASTER, NVBlocks.MASTER_RITUAL_STONE.block().get().defaultBlockState());
        for (var component : RitualLayouts.get(level, ritual)) {
            var block = BuiltInRegistries.BLOCK.stream().filter(candidate -> candidate instanceof BlockRitualStone stone
                    && stone.getRuneType() == component.runeType()).findFirst().orElseThrow();
            level.setBlockAndUpdate(component.getBlockPos(MASTER), block.defaultBlockState());
        }
        var master = (MasterRitualStoneBlockEntity) level.getBlockEntity(MASTER);
        require(master != null && master.checkStructure(ritual), "Actual ritual layout did not validate");
        var network = AnimaHelper.getAnima(player);
        require(network != null, "Native Anima network missing");
        network.set(AnimaTicket.create(2_000_000), 2_000_000);
        int before = network.getCurrentEV();
        require(master.activateRitual(ritual, player, 2), "Native ritual activation failed");
        require(before - network.getCurrentEV() == ritual.getActivationCost(), "Native activation payment differs");
        return master;
    }

    ItemStack hellforge(ItemStack input, boolean eligible) {
        var master = activate(NVRituals.HELLFORGING.get());
        var level = server.overworld();
        var target = new ItemEntity(level, MASTER.getX() + .5, 81.25, .5, input.copy());
        var gem = new ItemEntity(level, MASTER.getX() + 1, 81.25, .5, new ItemStack(NVItems.PRISMATIC_SPIRITUS_GEM.get(), 2));
        var metal = new ItemEntity(level, MASTER.getX() + 1.5, 81.25, .5, new ItemStack(NVItems.HELLFORGED_INGOT.get(), 2));
        require(level.addFreshEntity(target) && level.addFreshEntity(gem) && level.addFreshEntity(metal), "Real ritual drops failed");
        try {
            boolean initiallyForged = HellforgedHelper.isHellforged(input);
            var network = AnimaHelper.getAnima(player);
            int before = network.getCurrentEV();
            int price = master.getCurrentRitual().getRefreshCost();
            var context = RitualHelper.createContext(master, price);
            require(context != null, "Native Hellforging context missing");
            var nearby = RitualHelper.getEntitiesInRange(context, master.getCurrentRitual(), "hellforgingRange", ItemEntity.class);
            require(nearby.contains(target) && nearby.contains(gem) && nearby.contains(metal),
                    "Native range cannot see spawned ritual items: " + nearby.size());
            require(gem.getItem().is(NVTags.Items.HELLFORGING_GEM) && metal.getItem().is(NVTags.Items.HELLFORGING_METAL),
                    "Native ritual reagent tags missing");
            master.getCurrentRitual().performRitual(master);
            var result = target.getItem().copy();
            require(HellforgedHelper.isHellforged(result) == (initiallyForged || eligible), "Native ritual eligibility/result differs: " + HellforgedHelper.readiness(input));
            require(before - network.getCurrentEV() == (eligible ? price : 0)
                    && gem.getItem().getCount() == (eligible ? 1 : 2) && metal.getItem().getCount() == (eligible ? 1 : 2),
                    "Native operation debit/reagent consumption differs");
            int after = network.getCurrentEV();
            master.getCurrentRitual().performRitual(master);
            require(ItemStack.matches(result, target.getItem()) && after == network.getCurrentEV()
                    && gem.getItem().getCount() == (eligible ? 1 : 2) && metal.getItem().getCount() == (eligible ? 1 : 2),
                    "Already-forged/ineligible item consumed another operation");
            report.append("Hellforging ").append(input.getItem()).append(": structure/activation paid; operation eligible=")
                    .append(eligible).append("; exact EV/reagents; repeated operation unchanged\n");
            return result;
        } finally {
            target.discard();
            gem.discard();
            metal.discard();
            master.stopRitual(Ritual.BreakType.DEACTIVATE);
        }
    }

    void deus() throws Exception {
        for (int orientation = 0; orientation < 2; orientation++) {
            NeoVitaeUpdateSpike.clear(player);
            player.setPos(MASTER.getX() + .5, 81, .5);
            var boots = NeoVitaeUpdateSpike.pair(NeoVitaeUpdateSpike.nativePiece(EquipmentSlot.FEET), new ItemStack(Items.DIAMOND_BOOTS), EquipmentSlot.FEET).get(orientation);
            var legs = NeoVitaeUpdateSpike.pair(NeoVitaeUpdateSpike.nativePiece(EquipmentSlot.LEGS), new ItemStack(Items.DIAMOND_LEGGINGS), EquipmentSlot.LEGS).get(orientation);
            var originalBoots = boots.copy();
            var originalLegs = legs.copy();
            var chest = NeoVitaeUpdateSpike.item("sentient_plate");
            chest.set(NVDataComponents.CURRENT_MAX_UPGRADE_POINTS.get(), 500);
            player.setItemSlot(EquipmentSlot.HEAD, NeoVitaeUpdateSpike.item("sentient_helmet"));
            player.setItemSlot(EquipmentSlot.CHEST, chest);
            player.setItemSlot(EquipmentSlot.FEET, boots);
            player.setItemSlot(EquipmentSlot.LEGS, legs);
            var master = activate(NVRituals.DEUS_ARMOUR_EVOLVE.get());
            var network = AnimaHelper.getAnima(player);
            int before = network.getCurrentEV();
            int price = master.getCurrentRitual().getRefreshCost();
            master.getCurrentRitual().performRitual(master);
            require(chest.get(NVDataComponents.CURRENT_MAX_UPGRADE_POINTS.get()) == 600
                    && Boolean.TRUE.equals(boots.get(NVDataComponents.BLOOD_MENDING.get()))
                    && Boolean.TRUE.equals(legs.get(NVDataComponents.BLOOD_MENDING.get()))
                    && before - network.getCurrentEV() == price, "Native Deus evolution/mixed Blood Mending/payment failed");
            for (var pair : List.of(List.of(originalBoots, boots), List.of(originalLegs, legs))) {
                var oldSources = NeoVitaeUpdateSpike.sources(pair.getFirst());
                var currentSources = NeoVitaeUpdateSpike.sources(pair.getLast());
                for (int side = 0; side < 2; side++) {
                    require(ItemStack.matches(oldSources.get(side), currentSources.get(side)), "Deus ritual changed source history");
                }
            }
        }
        for (var slot : List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS)) { hellforge(NeoVitaeUpdateSpike.nativePiece(slot), true); }
        report.append("Deus evolution: real layouts/activation; 500 to 600 chest points; both mixed orientations in feet/legs together; native Blood Mending and operation debit; immutable sources\n");
    }
}
