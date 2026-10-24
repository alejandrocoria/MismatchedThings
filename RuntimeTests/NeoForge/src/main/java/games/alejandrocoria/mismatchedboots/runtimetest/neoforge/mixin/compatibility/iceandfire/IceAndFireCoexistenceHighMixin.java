package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.iceandfire;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.IceAndFireCoexistenceFixture;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.event.handler.ServerEvents", remap = false, priority = 1500)
abstract class IceAndFireCoexistenceHighMixin {
    @WrapOperation(method = "onEntityDamage(Lnet/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre;)V",
            at = @At(value = "games.alejandrocoria.mismatchedboots.runtimetest.neoforge.IceAndFireFixtureInjectionPoint",
                    args = "slot=HEAD"), require = 3, allow = 3)
    private static boolean fixtureHigh$head(Object item, Operation<Boolean> original, LivingDamageEvent.Pre event) {
        return IceAndFireCoexistenceFixture.head(true, item, original, event);
    }

    @WrapOperation(method = "onEntityDamage(Lnet/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre;)V",
            at = @At(value = "games.alejandrocoria.mismatchedboots.runtimetest.neoforge.IceAndFireFixtureInjectionPoint",
                    args = "slot=FEET"), require = 0, allow = 3)
    private static boolean fixtureHigh$feet(Object item, Operation<Boolean> original) {
        IceAndFireCoexistenceFixture.highFeet++;
        return original.call(item);
    }

    @WrapOperation(method = "onLivingHurt(Lnet/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre;)V",
            at = @At(value = "games.alejandrocoria.mismatchedboots.runtimetest.neoforge.IceAndFireFixtureInjectionPoint",
                    args = "slot=HEAD"), require = 1, allow = 1)
    private static boolean fixtureHigh$lightningHead(ItemStack head, Object item, Operation<Boolean> original) {
        return IceAndFireCoexistenceFixture.lightningHead(true, head, item, original);
    }

    @WrapOperation(method = "onLivingHurt(Lnet/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre;)V",
            at = @At(value = "games.alejandrocoria.mismatchedboots.runtimetest.neoforge.IceAndFireFixtureInjectionPoint",
                    args = "slot=FEET"), require = 1, allow = 1)
    private static boolean fixtureHigh$lightningFeet(ItemStack feet, Object item, Operation<Boolean> original) {
        IceAndFireCoexistenceFixture.highLightning++;
        return original.call(feet, item);
    }
}
