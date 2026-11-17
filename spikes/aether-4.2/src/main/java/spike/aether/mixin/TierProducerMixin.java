package spike.aether.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import spike.aether.Probe;
import spike.aether.TooltipBoundary;

import java.util.List;

@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.client.AetherIIClientEventListeners", remap = false)
abstract class TierProducerMixin {
    @WrapOperation(method = "onAddTooltipsLowest", at = @At(value = "INVOKE", target =
            "Lcom/aetherteam/aetherii/client/event/hooks/RenderHooks;addReinforcementTooltip(Lnet/minecraft/world/item/ItemStack;Ljava/util/List;Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/TooltipFlag;)V"))
    private static void aether42$tiers(ItemStack stack, List<Component> lines, Item.TooltipContext context,
                                      TooltipFlag flag, Operation<Void> original) {
        TooltipBoundary.tierCalls++;
        if (TooltipBoundary.tiers && Probe.managed(stack)) {
            TooltipBoundary.suppressedTiers++;
        } else {
            original.call(stack, lines, context, flag);
        }
    }
}
