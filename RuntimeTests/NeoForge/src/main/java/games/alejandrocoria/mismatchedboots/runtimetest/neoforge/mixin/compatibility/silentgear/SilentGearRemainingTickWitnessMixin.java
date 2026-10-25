package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.silentgear;

import games.alejandrocoria.mismatchedboots.NeoForgeMixedBootsItem;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearRemainingFixture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts selected mixed ticks, without changing collision or dispatching effects. */
@Mixin(NeoForgeMixedBootsItem.class)
abstract class SilentGearRemainingTickWitnessMixin {
    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void selected(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot, CallbackInfo ci) {
        if (slot != null && entity instanceof Player && SilentGearCompatibility.effectiveTraits(stack).stream()
                                                                               .anyMatch(trait -> java.util.List.of("silentgear:cure_poison",
                                                                                                                    "silentgear:cure_wither")
                                                                                                                .contains(trait.id().toString()))) {
            SilentGearRemainingFixture.cureTicks++;
        }
    }
}
