package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.justdirethings;

import org.spongepowered.asm.mixin.Mixin;

/** The fixture plugin edits the actual late consumer/getter after the production wrapper. */
@Mixin(targets = "com.direwolf20.justdirethings.common.events.LivingEntityEvents", priority = 500, remap = false)
abstract class JustDireNegateConsumerMixin {
}
