package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.aether;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherTooltipTestControl;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.item.AetherIIItems", remap = false)
abstract class AetherTooltipNativeProbeMixin {
    @Inject(method = "modifyTooltipAttributes", at = @At("HEAD"))
    private static void attributes(ItemStack stack, List<Component> lines, AttributeTooltipContext context, CallbackInfo ci) {
        AetherTooltipTestControl.beforeAttributes(lines);
    }

    @Inject(method = "modifyTooltipAttributes", at = @At("RETURN"))
    private static void attributesDone(ItemStack stack, List<Component> lines, AttributeTooltipContext context, CallbackInfo ci) {
        AetherTooltipTestControl.afterAttributes(lines);
    }
}
