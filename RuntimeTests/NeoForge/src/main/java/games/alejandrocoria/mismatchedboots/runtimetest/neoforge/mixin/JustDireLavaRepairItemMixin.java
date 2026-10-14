package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.NeoForgeMixedBootsItem;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireLavaRepairCandidate;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Adds the two inherited NeoForge item hooks to the packaged root, only in the research process. */
@Mixin(value = NeoForgeMixedBootsItem.class, remap = false)
public abstract class JustDireLavaRepairItemMixin {
    public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        return !source.is(DamageTypeTags.IS_FIRE) || !JustDireLavaRepairCandidate.eligible(stack);
    }

    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        JustDireLavaRepairCandidate.tick(stack, entity);
        return false;
    }
}
