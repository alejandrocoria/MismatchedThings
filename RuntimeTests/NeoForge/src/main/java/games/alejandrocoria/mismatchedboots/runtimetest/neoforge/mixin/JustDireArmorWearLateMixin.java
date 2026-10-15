package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = CommonHooks.class, priority = 500)
abstract class JustDireArmorWearLateMixin {}

