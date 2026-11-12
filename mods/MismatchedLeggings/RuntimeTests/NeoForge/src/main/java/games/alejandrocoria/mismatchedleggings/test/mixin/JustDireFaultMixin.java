package games.alejandrocoria.mismatchedleggings.test.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = {"com.direwolf20.justdirethings.common.items.interfaces.AbilityMethods", "net.neoforged.neoforge.common.CommonHooks",
        "com.direwolf20.justdirethings.client.screens.ToolSettingScreen"}, remap = false, priority = 500)
@Pseudo
abstract class JustDireFaultMixin {
    @Unique private static int leggings$jdtFaultWitness;
}
