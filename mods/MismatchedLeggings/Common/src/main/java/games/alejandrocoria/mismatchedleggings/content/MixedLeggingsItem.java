package games.alejandrocoria.mismatchedleggings.content;

import games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture.MysticalAugmentsCompatibility;
import games.alejandrocoria.mismatchedleggings.interaction.LeggingsRepair;
import games.alejandrocoria.mismatchedleggings.tooltip.MixedLeggingsTooltipFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class MixedLeggingsItem extends Item {
    public MixedLeggingsItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        MysticalAugmentsCompatibility.tick(stack, level, entity, slot);
    }

    /** Loader-local eligibility, including the native combine-repair veto on NeoForge. */
    public boolean canGrindstoneRepairWith(ItemStack stack, ItemStack donor) {
        return LeggingsRepair.acceptsDonor(stack, donor);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return MixedLeggingsStructure.canEquip(player.getItemInHand(hand))
                ? super.use(level, player, hand) : InteractionResult.PASS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                               Consumer<Component> tooltip, TooltipFlag flag) {
        var state = MixedLeggingsStructure.classify(stack);
        var recovery = LeggingsContent.suspendedLeggingsData(stack).orElse(null);
        if (recovery != null) {
            String diagnostic = recovery.quarantined() ? "quarantined" : recovery.conflicted() ? "conflicted" : "suspended";
            tooltip.accept(Component.translatable("tooltip.mismatchedleggings.mixed_leggings." + diagnostic)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (state != MixedLeggingsStructure.State.VALID) {
            tooltip.accept(Component.translatable("tooltip.mismatchedleggings.mixed_leggings.invalid")
                    .withStyle(ChatFormatting.RED));
            if (state == MixedLeggingsStructure.State.BARE) {
                tooltip.accept(Component.translatable("tooltip.mismatchedleggings.mixed_leggings.bare_hint",
                                Component.translatable("block.mismatchedthings.mismatched_workbench"))
                        .withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        MixedLeggingsTooltipFormatter.appendSourceSections(
                MixedLeggingsStructure.validData(stack).orElseThrow(), context, flag, tooltip);
    }
}
