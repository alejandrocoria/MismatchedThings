package spike.aether.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import spike.aether.Probe;
import spike.aether.TooltipBoundary;

import java.util.List;

@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.AetherIIEventListeners", remap = false)
abstract class AttributeProducerMixin {
    @WrapOperation(method = "onAddAttributeTooltips", at = @At(value = "INVOKE", target =
            "Lcom/aetherteam/aetherii/item/AetherIIItems;modifyTooltipAttributes(Lnet/minecraft/world/item/ItemStack;Ljava/util/List;Lnet/neoforged/neoforge/common/util/AttributeTooltipContext;)V"))
    private static void aether42$attributes(ItemStack stack, List<Component> lines, AttributeTooltipContext context,
                                            Operation<Void> original) {
        TooltipBoundary.attributeCalls++;
        if (TooltipBoundary.attributes && (Probe.managed(stack) || TooltipBoundary.source == stack)) {
            TooltipBoundary.suppressedAttributes++;
        } else {
            original.call(stack, lines, context);
        }
    }
}
