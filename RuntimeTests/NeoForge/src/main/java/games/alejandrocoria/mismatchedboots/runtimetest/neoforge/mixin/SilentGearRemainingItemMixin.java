package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.NeoForgeMixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearRemainingSpike;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enabled only in the isolated research client, never in a packaged production jar. */
@Mixin(NeoForgeMixedBootsItem.class)
abstract class SilentGearRemainingItemMixin extends MixedBootsItem {
    private SilentGearRemainingItemMixin(Properties properties) { super(properties); }

    @Override
    public boolean canWalkOnPowderedSnow(ItemStack stack, LivingEntity wearer) {
        return super.canWalkOnPowderedSnow(stack, wearer) || SilentGearRemainingSpike.snow(stack);
    }

    @Inject(method = "inventoryTick", at = @At("TAIL"))
    private void cures(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot, CallbackInfo ci) {
        SilentGearRemainingSpike.cures(stack, level, entity, slot);
    }
}
