package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = CommonHooks.class, priority = 1500)
abstract class JustDireArmorWearBaselineMixin {
}
