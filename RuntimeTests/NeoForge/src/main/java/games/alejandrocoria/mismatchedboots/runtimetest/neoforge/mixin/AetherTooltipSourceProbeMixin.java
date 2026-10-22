package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.NeoForgeMixedBootsItem;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherTooltipSpike;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(NeoForgeMixedBootsItem.class)
abstract class AetherTooltipSourceProbeMixin {
    @Inject(method = "appendHoverText", at = @At("TAIL"))
    private void root(ItemStack root, Item.TooltipContext context, TooltipDisplay display,
                      Consumer<Component> output, TooltipFlag flag, CallbackInfo ci) {
        AetherTooltipSpike.root(root, flag, output);
    }

    @Inject(method = "sourceTooltip", at = @At("HEAD"))
    private void source(ItemStack source, TooltipFlag flag, Consumer<Component> output,
                        CallbackInfoReturnable<Consumer<Component>> ci) {
        AetherTooltipSpike.source(source, flag, output);
    }
}
