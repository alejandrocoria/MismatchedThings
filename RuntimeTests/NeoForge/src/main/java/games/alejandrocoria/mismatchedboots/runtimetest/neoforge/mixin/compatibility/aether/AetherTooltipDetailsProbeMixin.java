package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.aether;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherTooltipTestControl;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
abstract class AetherTooltipDetailsProbeMixin {
    @Inject(method = "addDetailsToTooltip", at = @At("HEAD"))
    private void source(Item.TooltipContext context, TooltipDisplay display, Player player, TooltipFlag flag,
                        Consumer<Component> output, CallbackInfo ci) {
        AetherTooltipTestControl.sourceProbe((ItemStack) (Object) this, display, flag);
    }
}
