package games.alejandrocoria.mismatchedleggings.test.mystical.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedleggings.test.mystical.MysticalCandidate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
abstract class RefundRouter {
    @WrapOperation(method = "applyDamage(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
    private void spike$route(ItemStack root, int count, Operation<Void> original, int damage, LivingEntity owner, Consumer<Item> callback) {
        var capture = MysticalCandidate.capture(root, damage, owner);
        original.call(root, count);
        MysticalCandidate.complete(capture);
    }
}
