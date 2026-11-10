package games.alejandrocoria.mismatchedleggings.spike.silentgear.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Forces final export inspection of the reachable native query/potion classes in this test only. */
@Mixin(targets = {"net.silentchaos512.gear.item.gear.GearArmorItem",
        "net.silentchaos512.gear.gear.trait.effect.WielderEffectTraitEffect$PotionData",
        "net.silentchaos512.gear.util.TraitHelper", "net.silentchaos512.gear.util.GearHelper"}, remap = false)
abstract class NativeContractMixin {
    @Unique private static boolean leggings$nativeContractWitness() { return true; }
}
