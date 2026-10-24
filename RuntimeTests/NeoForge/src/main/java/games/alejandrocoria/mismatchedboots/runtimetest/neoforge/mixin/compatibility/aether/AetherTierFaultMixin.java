package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.aether;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherTooltipTestControl;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.item.components.ReinforcementTier", remap = false)
abstract class AetherTierFaultMixin {
    @Inject(method = "addToTooltip", at = @At("RETURN"))
    private void populated(Item.TooltipContext context, Consumer<Component> lines, TooltipFlag flag, DataComponentGetter stack, CallbackInfo callback) {
        // Only our audited source provider uses EMPTY; preserve the ordinary original native event/context.
        if (context == Item.TooltipContext.EMPTY) {
            AetherTooltipTestControl.tierScratchCalls++; lines.accept(AetherTooltipTestControl.TIER_SCRATCH);
            throw new IllegalStateException("Controlled populated source tier failure");
        }
    }
}
