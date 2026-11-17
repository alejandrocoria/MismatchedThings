package games.alejandrocoria.mismatchedleggings.test;

import com.breakinblocks.neovitae.api.soul.AnimaTicket;
import com.breakinblocks.neovitae.common.block.BlockRitualStone;
import com.breakinblocks.neovitae.common.block.NVBlocks;
import com.breakinblocks.neovitae.common.blockentity.MasterRitualStoneBlockEntity;
import com.breakinblocks.neovitae.common.item.NVItems;
import com.breakinblocks.neovitae.common.item.soul.HellforgedHelper;
import com.breakinblocks.neovitae.common.tag.NVTags;
import com.breakinblocks.neovitae.ritual.NVRituals;
import com.breakinblocks.neovitae.ritual.Ritual;
import com.breakinblocks.neovitae.ritual.RitualHelper;
import com.breakinblocks.neovitae.ritual.RitualLayouts;
import com.breakinblocks.neovitae.util.helper.AnimaHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native structure validation, activation, reagent selection and real Anima debits. */
final class NeoVitaeHellforgingRituals {
    private static final BlockPos MASTER = new BlockPos(48, 80, 0);
    private final MinecraftServer server;
    private final ServerPlayer player;
    private final StringBuilder report;

    NeoVitaeHellforgingRituals(MinecraftServer server, ServerPlayer player, StringBuilder report) {
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

}
