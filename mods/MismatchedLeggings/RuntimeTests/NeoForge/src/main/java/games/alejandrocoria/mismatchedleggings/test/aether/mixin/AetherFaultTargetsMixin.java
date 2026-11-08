package games.alejandrocoria.mismatchedleggings.test.aether.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = {"com.aetherteam.aetherii.item.equipment.EquipmentUtil",
        "com.aetherteam.aetherii.block.fluid.AlkahestFluid",
        "com.aetherteam.aetherii.AetherIIEventListeners"}, remap = false)
abstract class AetherFaultTargetsMixin {}
