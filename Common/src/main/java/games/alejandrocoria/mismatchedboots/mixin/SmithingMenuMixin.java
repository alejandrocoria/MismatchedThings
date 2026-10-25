package games.alejandrocoria.mismatchedboots.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedboots.interaction.MixedBootsSmithingPolicy;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.interaction.SmithingMenuRecipeState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
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

import java.util.Collections;
import java.util.List;

@Mixin(SmithingMenu.class)
abstract class SmithingMenuMixin implements SmithingMenuRecipeState {
    @Shadow @Final private Level level;

    @Unique private RecipeHolder<SmithingRecipe> mismatchedboots$additionalRecipe;
    @Unique private RecipeHolder<SmithingRecipe> mismatchedboots$primaryRecipe;
    @Unique private ItemStack mismatchedboots$preview;
    @Unique private List<ItemStack> mismatchedboots$matchedInputs;

    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void mismatchedboots$clearAdditionalRecipe(CallbackInfo callback) {
        mismatchedboots$clearAdditionalRecipe();
        SmithingMenu menu = (SmithingMenu) (Object) this;
        for (int slot : new int[]{SmithingMenu.TEMPLATE_SLOT, SmithingMenu.BASE_SLOT, SmithingMenu.ADDITIONAL_SLOT}) {
            if (ModContent.suspendedBootsData(menu.getSlot(slot).getItem()).isPresent()) {
                ResultContainer result = (ResultContainer) menu.getSlot(SmithingMenu.RESULT_SLOT).container;
                result.setRecipeUsed(null);
                result.setItem(0, ItemStack.EMPTY);
                callback.cancel();
                return;
            }
        }
    }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void mismatchedboots$trySourceSmithing(CallbackInfo callback) {
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
            if (mismatchedboots$additionalRecipe != null) {
                mismatchedboots$primaryRecipe = result.primaryRecipe();
                mismatchedboots$preview = result.output().copy();
                mismatchedboots$matchedInputs = List.of(
                        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().copy(),
                        menu.getSlot(SmithingMenu.BASE_SLOT).getItem().copy(),
                        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().copy());
            }
        });
    }

    @WrapOperation(
            method = "onTake",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/ResultContainer;awardUsedRecipes(Lnet/minecraft/world/entity/player/Player;Ljava/util/List;)V"))
    private void mismatchedboots$awardAdditionalRecipe(
            ResultContainer resultSlots,
            Player player,
            List<ItemStack> inputs,
            Operation<Void> original,
            @Local(argsOnly = true) ItemStack taken) {
        RecipeHolder<SmithingRecipe> additional = mismatchedboots$additionalRecipe;
        boolean matchesPreview = additional != null
                && resultSlots.getRecipeUsed() != null
                && resultSlots.getRecipeUsed().id().equals(mismatchedboots$primaryRecipe.id())
                && (taken.isEmpty() || ItemStack.isSameItemSameComponents(taken, mismatchedboots$preview))
                && mismatchedboots$sameInputs(inputs, mismatchedboots$matchedInputs);
        try {
            original.call(resultSlots, player, inputs);
        } finally {
            mismatchedboots$clearAdditionalRecipe();
        }
        if (matchesPreview) {
            player.triggerRecipeCrafted(additional, inputs);
            if (!additional.value().isSpecial()) {
                player.awardRecipes(Collections.singleton(additional));
            }
        }
    }

    @Unique
    private static boolean mismatchedboots$sameInputs(List<ItemStack> inputs, List<ItemStack> snapshot) {
        if (inputs.size() != snapshot.size()) {
            return false;
        }
        for (int index = 0; index < inputs.size(); index++) {
            if (!ItemStack.matches(inputs.get(index), snapshot.get(index))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void mismatchedboots$clearAdditionalRecipe() {
        mismatchedboots$additionalRecipe = null;
        mismatchedboots$primaryRecipe = null;
        mismatchedboots$preview = null;
        mismatchedboots$matchedInputs = null;
    }
}
