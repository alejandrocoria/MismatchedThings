package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.interaction.SmithingMenuRecipeState;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireRootUpgradePrototype;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Intercept only audited JDT additions, including rejected duplicates. Ordinary smithing stays native. */
@Mixin(value = SmithingMenu.class, priority = 900)
abstract class JustDireRootSmithingMixin {
    @Shadow @Final private Level level;

    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void install(CallbackInfo callback) {
        var menu = (SmithingMenu) (Object) this;
        var root = menu.getSlot(SmithingMenu.BASE_SLOT).getItem();
        var addition = menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem();
        if (!(level instanceof ServerLevel server) || !JustDireRootUpgradePrototype.handles(root, addition)) { return; }
        ((SmithingMenuRecipeState) menu).mismatchedboots$clearAdditionalRecipe();
        var result = JustDireRootUpgradePrototype.install(server,
                menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem(), root, addition);
        var container = (ResultContainer) menu.getSlot(SmithingMenu.RESULT_SLOT).container;
        container.setRecipeUsed(result == null ? null : result.recipe());
        container.setItem(0, result == null ? ItemStack.EMPTY : result.output());
        callback.cancel(); // A handled rejection must not run the old source-wise installation route.
    }
}
