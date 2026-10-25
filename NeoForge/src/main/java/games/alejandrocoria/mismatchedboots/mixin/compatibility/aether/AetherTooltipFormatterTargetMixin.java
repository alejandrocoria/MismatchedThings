package games.alejandrocoria.mismatchedboots.mixin.compatibility.aether;

import games.alejandrocoria.mismatchedboots.tooltip.MixedBootsTooltipFormatter;
import org.spongepowered.asm.mixin.Mixin;

/** The optional client plugin scopes the two owned source-detail calls without changing Common code. */
@Mixin(MixedBootsTooltipFormatter.class)
abstract class AetherTooltipFormatterTargetMixin {
}
