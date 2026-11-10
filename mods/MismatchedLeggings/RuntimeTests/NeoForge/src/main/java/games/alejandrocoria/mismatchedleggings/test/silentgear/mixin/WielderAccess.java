package games.alejandrocoria.mismatchedleggings.test.silentgear.mixin;

import net.minecraft.world.entity.player.Player;
import net.silentchaos512.gear.api.item.GearType;
import net.silentchaos512.gear.api.traits.TraitActionContext;
import net.silentchaos512.gear.gear.trait.effect.WielderEffectTraitEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = WielderEffectTraitEffect.class, remap = false)
public interface WielderAccess {
    @Invoker("getSetPieceCount")
    int sgtest$count(TraitActionContext context, GearType type, Player player);
}
