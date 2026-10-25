package games.alejandrocoria.mismatchedboots.runtimetest.tide.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedboots.runtimetest.tide.TideCarrierProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Applied in MixinExtras' extension phase, after ordinary plugin postApply. */
@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem", remap = false, priority = 500)
abstract class TideLateEntryMixin {
    @WrapMethod(method = "inventoryTick")
    private void tideTest$lateEntry(ItemStack stack, ServerLevel level, Entity entity,
                                    EquipmentSlot slot, Operation<Void> original) {
        TideCarrierProbe.readCarrier(stack);
        original.call(stack, level, entity, slot);
    }
}
