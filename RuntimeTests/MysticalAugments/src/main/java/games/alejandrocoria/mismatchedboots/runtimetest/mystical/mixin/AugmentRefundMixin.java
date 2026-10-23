package games.alejandrocoria.mismatchedboots.runtimetest.mystical.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalAugmentsBridge;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Local event view only: native MA still owns the actual inventory/drop delivery. */
@Pseudo
@Mixin(targets = "com.blakebr0.mysticalagriculture.handler.TinkerableHandler", remap = false)
abstract class AugmentRefundMixin {
    @WrapOperation(method = "onItemBreak", at = @At(value = "INVOKE",
            target = "Lcom/blakebr0/cucumber/event/ItemBreakEvent;getItem()Lnet/minecraft/world/item/Item;"))
    private static Item ownerItem(@Coerce Object event, Operation<Item> original) {
        ItemStack root = (ItemStack) MysticalAugmentsBridge.invoke(event, "getItemStack", new Class<?>[]{});
        ItemStack owner = MysticalAugmentsBridge.refundSource(root);
        return owner == null ? original.call(event) : owner.getItem();
    }

    @WrapOperation(method = "onItemBreak", at = @At(value = "INVOKE",
            target = "Lcom/blakebr0/cucumber/event/ItemBreakEvent;getItemStack()Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack ownerStack(@Coerce Object event, Operation<ItemStack> original) {
        ItemStack root = original.call(event);
        ItemStack owner = MysticalAugmentsBridge.refundSource(root);
        return owner == null ? root : owner;
    }
}
