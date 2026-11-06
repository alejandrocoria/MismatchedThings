package games.alejandrocoria.mismatchedboots.client.compatibility.forbiddenarcanus;

import games.alejandrocoria.mismatchedboots.compatibility.forbiddenarcanus.ForbiddenBootsModifiers;
import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifierService;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Presentation reads established root authority; it never adopts legacy data. */
public final class ForbiddenModifierTooltip {
    private ForbiddenModifierTooltip() {}

    public static void title(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        var state = ForbiddenBootsModifiers.state(stack);
        var level = event.getContext().level();
        if (state.isEmpty() || stack.has(DataComponents.CUSTOM_NAME) || level == null
                || event.getToolTip().isEmpty() || ForbiddenBootsModifiers.validData(stack).isEmpty()) {
            return;
        }

        var original = event.getToolTip().getFirst();
        event.getToolTip().set(0, ForbiddenModifierService.INSTANCE.title(original, state, level.registryAccess()));
    }
}
