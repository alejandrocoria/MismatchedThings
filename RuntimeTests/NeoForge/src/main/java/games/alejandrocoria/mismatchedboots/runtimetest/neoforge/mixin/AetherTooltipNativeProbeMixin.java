package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherTooltipSpike;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.client.event.hooks.RenderHooks", remap = false)
abstract class AetherTooltipNativeProbeMixin {
    @Inject(method = "addAbilityAttributeTooltip", at = @At("HEAD"), cancellable = true)
    private static void attributes(ItemStack stack, List<Component> lines, AttributeTooltipContext context, CallbackInfo ci) {
        AetherTooltipSpike.beforeAttributes(lines);
        if (AetherTooltipSpike.replacing()) { ci.cancel(); }
    }
    @Inject(method = "addAbilityAttributeTooltip", at = @At("RETURN"))
    private static void attributesDone(ItemStack stack, List<Component> lines, AttributeTooltipContext context, CallbackInfo ci) {
        AetherTooltipSpike.afterAttributes(lines);
    }
    @Inject(method = "addReinforcementTooltip", at = @At("HEAD"), cancellable = true)
    private static void tier(ItemStack stack, List<Component> lines, Item.TooltipContext context, TooltipFlag flag, CallbackInfo ci) {
        if (AetherTooltipSpike.replacing()) { ci.cancel(); }
    }
}
