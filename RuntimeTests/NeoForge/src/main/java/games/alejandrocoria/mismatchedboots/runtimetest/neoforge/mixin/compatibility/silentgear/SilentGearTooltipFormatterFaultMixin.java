package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.silentgear;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTooltipClientTest;
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

/** Fail only after a real native helper has populated the private source list. */
@Pseudo
@Mixin(targets = "net.silentchaos512.gear.client.tooltips.GearItemTooltips", remap = false)
abstract class SilentGearTooltipFormatterFaultMixin {
    @Inject(method = "tooltipListParts", at = @At("RETURN"))
    private static void afterParts(ItemStack source, List<Component> lines, Collection<?> parts,
                                   @Coerce Object flag, CallbackInfo callback) {
        SilentGearTooltipClientTest.formatterFault(lines);
    }
}
