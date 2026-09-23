package games.alejandrocoria.mismatchedboots.mixin;

import games.alejandrocoria.mismatchedboots.interaction.MixedBootsSmithingPolicy;
import games.alejandrocoria.mismatchedboots.interaction.SmithingMenuRecipeState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmithingMenu.class)
abstract class SmithingMenuMixin implements SmithingMenuRecipeState {
    @Shadow @Final private Level level;

    @Unique private RecipeHolder<SmithingRecipe> mismatchedboots$additionalRecipe;

    @Inject(method = "createResult", at = @At("HEAD"))
    private void mismatchedboots$clearAdditionalRecipe(CallbackInfo callback) {
        mismatchedboots$additionalRecipe = null;
    }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void mismatchedboots$trySourceTrims(CallbackInfo callback) {
        SmithingMenu menu = (SmithingMenu) (Object) this;
        if (!(level instanceof ServerLevel serverLevel)
                || !menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty()) {
            return;
        }

        MixedBootsSmithingPolicy.evaluate(
                serverLevel,
                menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem(),
                menu.getSlot(SmithingMenu.BASE_SLOT).getItem(),
                menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem()).ifPresent(result -> {
            ResultContainer resultSlots = (ResultContainer) menu.getSlot(SmithingMenu.RESULT_SLOT).container;
            resultSlots.setRecipeUsed(result.primaryRecipe());
            resultSlots.setItem(0, result.output());
            mismatchedboots$additionalRecipe = result.additionalRecipe();
        });
    }

    @Override
    public void mismatchedboots$clearAdditionalRecipe() {
        mismatchedboots$additionalRecipe = null;
    }
}
