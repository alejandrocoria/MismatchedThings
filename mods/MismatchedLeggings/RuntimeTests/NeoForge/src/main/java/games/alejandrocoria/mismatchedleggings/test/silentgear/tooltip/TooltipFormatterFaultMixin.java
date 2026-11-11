package games.alejandrocoria.mismatchedleggings.test.silentgear.tooltip;

import games.alejandrocoria.mismatchedleggings.test.silentgear.TooltipClient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;

/** Fail after actual native list population, scoped to Leggings production captures, never the oracle/peer. */
@Pseudo
@Mixin(targets = "net.silentchaos512.gear.client.tooltips.GearItemTooltips", remap = false)
abstract class TooltipFormatterFaultMixin {
    @Inject(method = "tooltipListParts", at = @At("RETURN"))
    private static void afterParts(ItemStack source, List<Component> lines, Collection<?> parts,
            @Coerce Object flag, CallbackInfo callback) {
        TooltipClient.formatterFault(lines);
    }
}
