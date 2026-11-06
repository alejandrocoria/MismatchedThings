package games.alejandrocoria.aquaspike.mixin;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feasibility only: mirror the small native condition without ever owning NoGravity. */
@Mixin(Entity.class)
abstract class GravityQueryMixin {
    private static final ResourceKey<Item> LEGGINGS = ResourceKey.create(Registries.ITEM,
            Identifier.parse("aquaculture:neptunium_leggings"));

    @Inject(method = "getGravity", at = @At("RETURN"), cancellable = true)
    @SuppressWarnings("deprecation")
    private void buoyancy(CallbackInfoReturnable<Double> result) {
        if (result.getReturnValueD() == 0 || !((Object) this instanceof Player player)
                || !player.isEyeInFluid(FluidTags.WATER) || player.isCrouching()
                || player.isJumping() || player.isSwimming() || player.isSpectator()) {
            return;
        }
        var data = MixedLeggingsStructure.validData(player.getItemBySlot(EquipmentSlot.LEGS)).orElse(null);
        if (data != null && (data.left().source().is(LEGGINGS) || data.right().source().is(LEGGINGS))) {
            result.setReturnValue(0.0);
        }
    }
}
