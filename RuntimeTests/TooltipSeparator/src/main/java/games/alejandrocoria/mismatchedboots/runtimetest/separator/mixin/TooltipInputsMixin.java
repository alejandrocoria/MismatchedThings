package games.alejandrocoria.mismatchedboots.runtimetest.separator.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorObservations;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Scenario inputs only: emulate another mod copying text or supplying a native tooltip image. */
@Mixin(ItemStack.class)
abstract class TooltipInputsMixin {
    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
    private void copyText(Item.TooltipContext context, Player player, TooltipFlag flag,
                          CallbackInfoReturnable<List<Component>> result) {
        if (SeparatorObservations.enabled() && SeparatorObservations.copyText && (Object) this == SeparatorObservations.input) {
            var copied = new ArrayList<Component>();
            result.getReturnValue().forEach(line -> copied.add(line.copy()));
            result.setReturnValue(copied);
        }
    }

    @Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
    private void image(CallbackInfoReturnable<Optional<TooltipComponent>> result) {
        if (SeparatorObservations.enabled() && (Object) this == SeparatorObservations.input && SeparatorObservations.image.isPresent()) {
            result.setReturnValue(SeparatorObservations.image);
        }
    }
}
