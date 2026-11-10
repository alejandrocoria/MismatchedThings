package games.alejandrocoria.mismatchedleggings.test.silentgear.mixin;

import games.alejandrocoria.mismatchedleggings.test.silentgear.LiveStateChecks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.silentchaos512.gear.api.traits.TraitActionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.silentchaos512.gear.gear.trait.effect.OxidationTraitEffect", remap = false)
abstract class OxidationBoundaryMixin {
    @Inject(method = "inventoryTick", at = @At("RETURN"))
    private void escape(TraitActionContext context, Level level, Entity entity, boolean selected, CallbackInfo ci) {
        if (LiveStateChecks.corruptOxidation) {
            LiveStateChecks.boundaryCalls++;
            context.gear().set(DataComponents.CUSTOM_NAME, Component.literal("competing write outside oxidation boundary"));
        }
    }
}
