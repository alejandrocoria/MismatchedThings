package games.alejandrocoria.mismatchedleggings.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedleggings.compatibility.forbiddenarcanus.ForbiddenModifierAdapter;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.interaction.LeggingsSmithing;
import games.alejandrocoria.mismatchedleggings.interaction.LeggingsSmithingState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SmithingMenu.class)
abstract class SmithingMenuMixin implements LeggingsSmithingState {
    @Shadow
    @Final
    private Level level;
    @Unique
    private LeggingsSmithing.Result mismatchedleggings$match;
    @Unique
    private List<ItemStack> mismatchedleggings$inputs;
    @Unique
    private boolean mismatchedleggings$modifierHandled;

    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void mismatchedleggings$begin(CallbackInfo callback) {
        mismatchedleggings$match = null;
        mismatchedleggings$inputs = null;
        mismatchedleggings$modifierHandled = false;
        var menu = (SmithingMenu) (Object) this;
        for (int slot = SmithingMenu.TEMPLATE_SLOT; slot <= SmithingMenu.ADDITIONAL_SLOT; slot++) {
            if (LeggingsContent.suspendedLeggingsData(menu.getSlot(slot).getItem()).isPresent()) {
                mismatchedleggings$clearResult();
                callback.cancel();
                return;
            }
        }
        var base = menu.getSlot(SmithingMenu.BASE_SLOT).getItem();
        if (MixedLeggingsStructure.isMixedItem(base) && MixedLeggingsStructure.validData(base).isEmpty()) {
            mismatchedleggings$clearResult();
            callback.cancel();
            return;
        }
        if (level instanceof ServerLevel server && MixedLeggingsStructure.validData(base).isPresent()) {
            var decision = ForbiddenModifierAdapter.INSTANCE.smithing(server, menu.getSlot(0).getItem(), base, menu.getSlot(2).getItem());
            if (decision.handled()) {
                mismatchedleggings$modifierHandled = true;
                mismatchedleggings$clearResult();
                decision.result().ifPresent(this::mismatchedleggings$installMatch);
                callback.cancel();
            }
        }
    }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void mismatchedleggings$trySources(CallbackInfo callback) {
        var menu = (SmithingMenu) (Object) this;
        var base = menu.getSlot(SmithingMenu.BASE_SLOT).getItem();
        if (mismatchedleggings$modifierHandled || !(level instanceof ServerLevel server) || MixedLeggingsStructure.validData(base).isEmpty()) {
            return;
        }
        var result = (ResultContainer) menu.getSlot(SmithingMenu.RESULT_SLOT).container;
        // Datapacks can add broad armor tags and admit invisible native root-only trims.
        // Only the generic trimmable_armor recipe yields to source-wise application; dedicated results win.
        boolean genericTrim = LeggingsSmithing.isGenericRootTrim(result.getRecipeUsed());
        if (!result.getItem(0).isEmpty() && !genericTrim) {
            return;
        }
        if (genericTrim) {
            mismatchedleggings$clearResult();
        }
        LeggingsSmithing.evaluate(server, menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem(), base,
                menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem()).ifPresent(this::mismatchedleggings$installMatch);
    }

    @Override
    public boolean mismatchedleggings$validateSmithingPreview() {
        if (mismatchedleggings$match == null || !(level instanceof ServerLevel server)) {
            return true;
        }
        var menu = (SmithingMenu) (Object) this;
        var inputs = List.of(menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem(), menu.getSlot(SmithingMenu.BASE_SLOT).getItem(),
                menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem());
        var modifiers = ForbiddenModifierAdapter.INSTANCE.smithing(server, inputs.get(0), inputs.get(1), inputs.get(2));
        var fresh = modifiers.handled() ? modifiers.result().orElse(null)
                : LeggingsSmithing.hasDirectResult(server, inputs.get(0), inputs.get(1), inputs.get(2)) ? null
                : LeggingsSmithing.evaluate(server, inputs.get(0), inputs.get(1), inputs.get(2)).orElse(null);
        if (mismatchedleggings$sameInputs(inputs) && fresh != null
                && fresh.primary() == mismatchedleggings$match.primary()
                && fresh.additional() == mismatchedleggings$match.additional()
                && ItemStack.matches(fresh.output(), mismatchedleggings$match.output())
                && ItemStack.matches(menu.getSlot(SmithingMenu.RESULT_SLOT).getItem(), mismatchedleggings$match.output())) {
            return true;
        }
        mismatchedleggings$clearResult();
        mismatchedleggings$match = null;
        mismatchedleggings$inputs = null;
        return false;
    }

    @WrapOperation(method = "onTake", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/ResultContainer;awardUsedRecipes(Lnet/minecraft/world/entity/player/Player;Ljava/util/List;)V"))
    private void mismatchedleggings$awardBoth(ResultContainer results, Player player, List<ItemStack> inputs,
                                              Operation<Void> original, @Local(argsOnly = true) ItemStack taken) {
        var match = mismatchedleggings$match;
        boolean awardAdditional = match != null && match.additional() != null && results.getRecipeUsed() == match.primary()
                && mismatchedleggings$sameInputs(inputs)
                && (taken.isEmpty() || ItemStack.isSameItemSameComponents(taken, match.output()));
        try {
            original.call(results, player, inputs);
        } finally {
            mismatchedleggings$match = null;
            mismatchedleggings$inputs = null;
        }
        if (awardAdditional) {
            player.triggerRecipeCrafted(match.additional(), inputs);
            if (!match.additional().value().isSpecial()) {
                player.awardRecipes(List.of(match.additional()));
            }
        }
    }

    @Unique
    private void mismatchedleggings$installMatch(LeggingsSmithing.Result match) {
        var menu = (SmithingMenu) (Object) this;
        var result = (ResultContainer) menu.getSlot(SmithingMenu.RESULT_SLOT).container;
        result.setRecipeUsed(match.primary());
        result.setItem(0, match.output());
        mismatchedleggings$match = new LeggingsSmithing.Result(match.output().copy(), match.primary(), match.additional());
        mismatchedleggings$inputs = List.of(menu.getSlot(0).getItem().copy(), menu.getSlot(1).getItem().copy(), menu.getSlot(2).getItem().copy());
    }

    @Unique
    private boolean mismatchedleggings$sameInputs(List<ItemStack> inputs) {
        if (mismatchedleggings$inputs == null || inputs.size() != mismatchedleggings$inputs.size()) {
            return false;
        }
        for (int index = 0; index < inputs.size(); index++) {
            if (!ItemStack.matches(inputs.get(index), mismatchedleggings$inputs.get(index))) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private void mismatchedleggings$clearResult() {
        var menu = (SmithingMenu) (Object) this;
        var result = (ResultContainer) menu.getSlot(SmithingMenu.RESULT_SLOT).container;
        result.setRecipeUsed(null);
        result.setItem(0, ItemStack.EMPTY);
    }
}
