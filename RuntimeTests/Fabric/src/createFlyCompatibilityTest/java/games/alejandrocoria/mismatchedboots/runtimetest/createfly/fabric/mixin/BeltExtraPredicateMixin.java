package games.alejandrocoria.mismatchedboots.runtimetest.createfly.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;

/** The test plugin inserts the unrelated predicate into this target's body. */
@Mixin(targets = "com.zurrtum.create.content.kinetics.belt.BeltBlock", remap = false, priority = 2000)
abstract class BeltExtraPredicateMixin {
}
