package games.alejandrocoria.mismatchedboots.mixin.compatibility.betterx;

import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.betterx.betterend.interfaces.MobEffectApplier;
import org.betterx.betterend.item.CrystaliteBoots;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MixedBootsItem.class)
public abstract class BetterEndMobEffectApplierMixin implements MobEffectApplier {
    @Override
    public void applyEffect(LivingEntity entity) {
        ItemStack equipped = entity.getItemBySlot(EquipmentSlot.FEET);
        if (equipped.getItem() != (Object) this) {
            return;
        }

        MixedBootsSources.anySource(equipped, source -> {
            if (source.getItem() instanceof CrystaliteBoots boots) {
                boots.applyEffect(entity);
                return true;
            }
            return false;
        });
    }
}
