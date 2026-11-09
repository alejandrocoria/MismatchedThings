package games.alejandrocoria.mismatchedleggings.test.iceandfire.mixin;
import games.alejandrocoria.mismatchedleggings.test.iceandfire.TideChecks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets = "games.alejandrocoria.mismatchedleggings.NeoForgeMixedLeggingsItem", remap = false)
abstract class TideEntryWitness {
    @Inject(method = "inventoryTick", at = @At("HEAD"))
    private void tideTest$root(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot, CallbackInfo ci) {
        TideChecks.rootTick(entity, stack, slot);
    }
}
