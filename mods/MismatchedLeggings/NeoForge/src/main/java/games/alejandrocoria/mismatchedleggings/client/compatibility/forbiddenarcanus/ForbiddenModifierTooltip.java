package games.alejandrocoria.mismatchedleggings.client.compatibility.forbiddenarcanus;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifierService;
import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifiers;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Presentation admission stays local; native prefix lookup belongs to the shared service. */
public final class ForbiddenModifierTooltip {
    private ForbiddenModifierTooltip() {}

    public static void title(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        var state = stack.getOrDefault(LeggingsContent.forbiddenModifiersType(), ForbiddenModifiers.EMPTY);
        var level = event.getContext().level();
        if (state.isEmpty() || stack.has(DataComponents.CUSTOM_NAME) || level == null
                || event.getToolTip().isEmpty() || MixedLeggingsStructure.validData(stack).isEmpty()) {
            return;
        }
        var original = event.getToolTip().getFirst();
        event.getToolTip().set(0, ForbiddenModifierService.INSTANCE.title(original, state, level.registryAccess()));
    }
}
