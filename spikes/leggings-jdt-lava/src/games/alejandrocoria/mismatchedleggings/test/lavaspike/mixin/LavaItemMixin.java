package games.alejandrocoria.mismatchedleggings.test.lavaspike.mixin;

import games.alejandrocoria.mismatchedleggings.test.lavaspike.LavaCandidate;

import games.alejandrocoria.mismatchedleggings.NeoForgeMixedLeggingsItem;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsItem;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Candidate callbacks only: the packaged production item remains unchanged. */
@Mixin(NeoForgeMixedLeggingsItem.class)
public abstract class LavaItemMixin extends MixedLeggingsItem {
    protected LavaItemMixin(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canBeHurtBy(ItemStack root, DamageSource source) {
        return !(source.is(DamageTypeTags.IS_FIRE) && LavaCandidate.eligible(root)) && super.canBeHurtBy(root, source);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack root, ItemEntity entity) {
        LavaCandidate.tick(root, entity);
        return false;
    }
}
