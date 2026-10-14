package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.NeoForgeMixedBootsItem;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireLavaRepairCandidate;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Historical candidate override, only in the research process; production runs use the real hooks. */
@Mixin(value = NeoForgeMixedBootsItem.class, remap = false)
public abstract class JustDireLavaRepairItemMixin {
    @Inject(method = "canBeHurtBy", at = @At("HEAD"), cancellable = true)
    private void researchDamage(ItemStack stack, DamageSource source, CallbackInfoReturnable<Boolean> result) {
        result.setReturnValue(!source.is(DamageTypeTags.IS_FIRE) || !JustDireLavaRepairCandidate.eligible(stack));
    }

    @Inject(method = "onEntityItemUpdate", at = @At("HEAD"), cancellable = true)
    private void researchTick(ItemStack stack, ItemEntity entity, CallbackInfoReturnable<Boolean> result) {
        JustDireLavaRepairCandidate.tick(stack, entity);
        result.setReturnValue(false);
    }
}
