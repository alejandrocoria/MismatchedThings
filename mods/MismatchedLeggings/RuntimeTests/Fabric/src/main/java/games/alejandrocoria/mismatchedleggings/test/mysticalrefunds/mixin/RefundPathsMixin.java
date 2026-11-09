package games.alejandrocoria.mismatchedleggings.test.mysticalrefunds.mixin;

import games.alejandrocoria.mismatchedleggings.test.mysticalrefunds.RefundControl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = ItemStack.class, priority = 800)
abstract class RefundPathsMixin {
    @Inject(method = "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"), cancellable = true)
    private void leggings$pre(int damage, ServerPlayer player, Consumer<Item> callback, CallbackInfo ci) {
        RefundControl.before((ItemStack) (Object) this);
        if (RefundControl.root == (Object) this && RefundControl.cancel) { ci.cancel(); }
    }
}
