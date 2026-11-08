package games.alejandrocoria.mismatchedleggings.mixin.compatibility.aether;

import games.alejandrocoria.mismatchedleggings.tooltip.MixedLeggingsTooltipFormatter;
import org.spongepowered.asm.mixin.Mixin;

/** The optional client plugin scopes the two owned source-detail calls without changing Common code. */
@Mixin(MixedLeggingsTooltipFormatter.class)
abstract class AetherTooltipFormatterTargetMixin {
}
