package games.alejandrocoria.mismatchedboots.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

final class ForgeTooltipHookCompilationFixture {
    private static Observation observation;

    private ForgeTooltipHookCompilationFixture() {
    }

    static void register() {
        ItemTooltipEvent.BUS.addListener(ForgeTooltipHookCompilationFixture::observe);
    }

    private static void observe(ItemTooltipEvent event) {
        observation = new Observation(
                event.getItemStack(),
                event.getEntity(),
                event.getFlags(),
                event.getToolTip());
    }

    record Observation(
            ItemStack stack,
            Player player,
            TooltipFlag flag,
            List<Component> mutableLines) {
    }
}
