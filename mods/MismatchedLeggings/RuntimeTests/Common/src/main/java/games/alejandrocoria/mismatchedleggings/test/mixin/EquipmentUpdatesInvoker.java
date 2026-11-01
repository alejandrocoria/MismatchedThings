package games.alejandrocoria.mismatchedleggings.test.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface EquipmentUpdatesInvoker {
    @Invoker("detectEquipmentUpdates")
    void leggings$detectEquipmentUpdates();
}
