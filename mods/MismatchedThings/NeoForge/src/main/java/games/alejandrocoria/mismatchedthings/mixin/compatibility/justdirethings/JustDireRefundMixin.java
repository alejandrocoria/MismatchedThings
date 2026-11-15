package games.alejandrocoria.mismatchedthings.mixin.compatibility.justdirethings;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedthings.internal.compatibility.justdirethings.JustDireRefundRouter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
abstract class JustDireRefundMixin {
    @Unique
    private static boolean mismatchedthings$justDireRefundReady() {
        return false;
    }

    @WrapOperation(method = "applyDamage(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"), require = 0, expect = 0)
    private void mismatchedthings$justDireRefund(ItemStack root, int count, Operation<Void> ordinary,
                                                int damage, LivingEntity owner, Consumer<Item> onBreak) {
        // Direct method arguments avoid a mutable @Local bridge at the consumption boundary.
        JustDireRefundRouter.route(mismatchedthings$justDireRefundReady(), root, count, owner, ordinary);
    }
}
