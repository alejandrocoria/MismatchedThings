package games.alejandrocoria.mismatchedboots.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedboots.compatibility.forbiddenarcanus.ForbiddenBootsModifiers;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.interaction.MixedBootsSmithingPolicy;
import games.alejandrocoria.mismatchedboots.interaction.SmithingMenuRecipeState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
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
    @Shadow
    @Final
    private Level level;

    @Unique
    private RecipeHolder<SmithingRecipe> mismatchedboots$additionalRecipe;
    @Unique
    private RecipeHolder<SmithingRecipe> mismatchedboots$primaryRecipe;
    @Unique
    private ItemStack mismatchedboots$preview;
    @Unique
    private List<ItemStack> mismatchedboots$matchedInputs;
    @Unique
    private boolean mismatchedboots$sourceWise;
    @Unique
    private RecipeHolder<SmithingRecipe> mismatchedboots$nativeRecipe;
    @Unique
    private boolean mismatchedboots$modifierHandled;

    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void mismatchedboots$resetPreview(CallbackInfo callback) {
        mismatchedboots$clearSmithingState();
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
        var base = menu.getSlot(SmithingMenu.BASE_SLOT).getItem();
        if (ModContent.MIXED_BOOTS_ID.equals(BuiltInRegistries.ITEM.getKey(base.getItem()))
                && ForbiddenBootsModifiers.validData(base).isEmpty()) {
            var result = (ResultContainer) menu.getSlot(SmithingMenu.RESULT_SLOT).container;
            result.setRecipeUsed(null);
            result.setItem(0, ItemStack.EMPTY);
            callback.cancel();
            return;
        }
        if (level instanceof ServerLevel server) {
            var attempt = ForbiddenBootsModifiers.smithing(server, menu.getSlot(0).getItem(), base, menu.getSlot(2).getItem());
            if (attempt.handled()) {
                mismatchedboots$modifierHandled = true;
                var result = (ResultContainer) menu.getSlot(SmithingMenu.RESULT_SLOT).container;
                result.setRecipeUsed(null);
                result.setItem(0, ItemStack.EMPTY);
                if (attempt.result() != null) {
                    var match = attempt.result();
                    result.setRecipeUsed(match.primaryRecipe());
                    result.setItem(0, match.output());
                    mismatchedboots$primaryRecipe = match.primaryRecipe();
                    mismatchedboots$preview = match.output().copy();
                    mismatchedboots$matchedInputs = List.of(menu.getSlot(0).getItem().copy(), base.copy(), menu.getSlot(2).getItem().copy());
                }
                callback.cancel();
            }
        }
    }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void mismatchedboots$trySourceSmithing(CallbackInfo callback) {
        SmithingMenu menu = (SmithingMenu) (Object) this;
        if (mismatchedboots$modifierHandled || !(level instanceof ServerLevel serverLevel)
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
            mismatchedboots$sourceWise = result.sourceWise();
            if (result.sourceWise() || mismatchedboots$additionalRecipe != null) {
                mismatchedboots$primaryRecipe = result.primaryRecipe();
                mismatchedboots$preview = result.output().copy();
                mismatchedboots$matchedInputs = List.of(
                        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().copy(),
                        menu.getSlot(SmithingMenu.BASE_SLOT).getItem().copy(),
                        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().copy());
            }
            if (result.sourceWise()) {
                mismatchedboots$nativeRecipe = serverLevel.recipeAccess().getRecipeFor(RecipeType.SMITHING,
                        new SmithingRecipeInput(mismatchedboots$matchedInputs.get(0), mismatchedboots$matchedInputs.get(1),
                                mismatchedboots$matchedInputs.get(2)), serverLevel).orElse(null);
            }
        });
    }

    @Override
    public boolean mismatchedboots$validateSmithingPreview() {
        if (mismatchedboots$modifierHandled && level instanceof ServerLevel server) {
            return mismatchedboots$validateModifierPreview(server);
        }
        if (!mismatchedboots$sourceWise || !(level instanceof ServerLevel server)) {
            return true;
        }
        SmithingMenu menu = (SmithingMenu) (Object) this;
        List<ItemStack> inputs = List.of(menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem(),
                menu.getSlot(SmithingMenu.BASE_SLOT).getItem(), menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem());
        RecipeHolder<SmithingRecipe> nativeRecipe = server.recipeAccess().getRecipeFor(RecipeType.SMITHING,
                new SmithingRecipeInput(inputs.get(0), inputs.get(1), inputs.get(2)), server).orElse(null);
        ResultContainer result = (ResultContainer) menu.getSlot(SmithingMenu.RESULT_SLOT).container;
        // The ownership route was fixed when this preview was made. Do not rerun
        // tryRootSmithing here: compatibility-owned installation stays outside this guard.
        MixedBootsSmithingPolicy.Result fresh = mismatchedboots$sameInputs(inputs, mismatchedboots$matchedInputs)
                && nativeRecipe == mismatchedboots$nativeRecipe
                ? MixedBootsSmithingPolicy.evaluateSourceWise(server, inputs.get(0), inputs.get(1), inputs.get(2)).orElse(null)
                : null;
        if (fresh != null && fresh.primaryRecipe() == mismatchedboots$primaryRecipe
                && fresh.additionalRecipe() == mismatchedboots$additionalRecipe
                && result.getRecipeUsed() == mismatchedboots$primaryRecipe
                && ItemStack.matches(fresh.output(), mismatchedboots$preview)
                && ItemStack.matches(result.getItem(0), mismatchedboots$preview)) {
            return true;
        }
        result.setRecipeUsed(null);
        result.setItem(0, ItemStack.EMPTY);
        mismatchedboots$clearSmithingState();
        return false;
    }

    @Unique
    private boolean mismatchedboots$validateModifierPreview(ServerLevel server) {
        var menu = (SmithingMenu) (Object) this;
        var inputs = List.of(menu.getSlot(0).getItem(), menu.getSlot(1).getItem(), menu.getSlot(2).getItem());
        var attempt = ForbiddenBootsModifiers.smithing(server, inputs.get(0), inputs.get(1), inputs.get(2));
        var fresh = attempt.handled() ? attempt.result() : null;
        var result = (ResultContainer) menu.getSlot(SmithingMenu.RESULT_SLOT).container;
        if (mismatchedboots$sameInputs(inputs, mismatchedboots$matchedInputs) && fresh != null
                && fresh.primaryRecipe() == mismatchedboots$primaryRecipe
                && result.getRecipeUsed() == mismatchedboots$primaryRecipe
                && ItemStack.matches(fresh.output(), mismatchedboots$preview)
                && ItemStack.matches(result.getItem(0), mismatchedboots$preview)) {
            return true;
        }
        result.setRecipeUsed(null);
        result.setItem(0, ItemStack.EMPTY);
        mismatchedboots$clearSmithingState();
        return false;
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
            mismatchedboots$clearSmithingState();
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
        if (snapshot == null || inputs.size() != snapshot.size()) {
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
    public void mismatchedboots$clearSmithingState() {
        mismatchedboots$additionalRecipe = null;
        mismatchedboots$primaryRecipe = null;
        mismatchedboots$preview = null;
        mismatchedboots$matchedInputs = null;
        mismatchedboots$sourceWise = false;
        mismatchedboots$nativeRecipe = null;
        mismatchedboots$modifierHandled = false;
    }
}
