package games.alejandrocoria.mismatchedboots.runtimetest.mystical.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalAugmentsProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Read-only witness: accepted entities need not yet be visible through a world-index query. */
@Mixin(ServerLevel.class)
abstract class AugmentDropWitnessMixin {
    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void added(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue()) && entity instanceof ItemEntity item) {
            MysticalAugmentsProbe.witnessedDrops.add(item);
        }
    }
}
