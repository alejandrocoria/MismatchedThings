package games.alejandrocoria.mismatchedboots.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireRefundCompatibility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
abstract class JustDireRefundMixin {
    @Unique private static boolean mismatchedboots$justDireRefundReady() { return false; }

    @WrapOperation(method = "applyDamage(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"), require = 0, expect = 0)
    private void mismatchedboots$justDireRefund(ItemStack root, int count, Operation<Void> ordinary,
                                              int damage, LivingEntity owner, Consumer<Item> onBreak) {
        // Capture method arguments directly; @Local would introduce a mutable LocalRef bridge here.
        JustDireRefundCompatibility.route(mismatchedboots$justDireRefundReady(), root, count, owner, ordinary);
    }
}
