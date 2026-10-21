package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.NeoForgeMixedBootsItem;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTooltipCandidate;
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

/** Test-only append at the existing item-local boundary; production/JDT filtering is untouched. */
@Mixin(NeoForgeMixedBootsItem.class)
abstract class SilentGearTooltipSourceMixin {
    @Inject(method = "appendHoverText", at = @At("HEAD"))
    private void actualFlag(ItemStack root, Item.TooltipContext context, TooltipDisplay display,
                            Consumer<Component> output, TooltipFlag flag, CallbackInfo callback) {
        SilentGearTooltipCandidate.captureFlag(flag);
    }

    @Inject(method = "sourceTooltip", at = @At("HEAD"))
    private void sourceDetails(ItemStack source, Consumer<Component> output,
                               CallbackInfoReturnable<Consumer<Component>> callback) {
        SilentGearTooltipCandidate.append(source, output);
    }
}
