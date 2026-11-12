package games.alejandrocoria.jdtspike.mixin;

import games.alejandrocoria.jdtspike.Candidate;

import com.direwolf20.justdirethings.client.screens.ToolSettingScreen;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.common.items.interfaces.AbilityParams;
import com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumSet;

/** Candidate owns Leggings only; native and Boots calls must still reach their original handlers. */
@Mixin(value = ToolSettingScreen.class, remap = false)
abstract class ScreenMixin {
    @Shadow protected ItemStack tool;
    @Shadow private EnumSet<Ability> abilities;

    @WrapOperation(method = {"<init>", "init", "refreshButtons", "mouseClicked"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getItem()Lnet/minecraft/world/item/Item;"))
    private Item metadata(ItemStack stack, Operation<Item> original) {
        return Candidate.mixed(stack) ? Candidate.metadata(stack) : original.call(stack);
    }

    @WrapOperation(method = {"init", "mouseClicked"}, at = @At(value = "INVOKE",
            target = "Lcom/direwolf20/justdirethings/common/items/interfaces/ToggleableTool;getAbilities()Ljava/util/EnumSet;"))
    private EnumSet<Ability> abilities(ToggleableTool item, Operation<EnumSet<Ability>> original) {
        return Candidate.mixed(tool) ? Candidate.abilities(tool) : original.call(item);
    }

    @WrapOperation(method = "refreshButtons", at = @At(value = "INVOKE",
            target = "Lcom/direwolf20/justdirethings/common/items/interfaces/ToggleableTool;getAbilityParams(Lcom/direwolf20/justdirethings/common/items/interfaces/Ability;)Lcom/direwolf20/justdirethings/common/items/interfaces/AbilityParams;"))
    private AbilityParams parameters(ToggleableTool item, Ability ability, Operation<AbilityParams> original) {
        if (!Candidate.mixed(tool)) { return original.call(item, ability); }
        var view = Candidate.view(tool, ability);
        return ((ToggleableTool) view.getItem()).getAbilityParams(ability);
    }

    @WrapOperation(method = "refreshButtons", at = @At(value = "INVOKE",
            target = "Lcom/direwolf20/justdirethings/common/items/interfaces/ToggleableTool;getToolValue(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)I"))
    private int value(ItemStack stack, String name, Operation<Integer> original) {
        return Candidate.mixed(stack) ? ToggleableTool.getToolValue(Candidate.view(stack, Ability.byName(name)), name)
                : original.call(stack, name);
    }

    @Inject(method = "refreshButtons", at = @At("HEAD"))
    private void refresh(CallbackInfo ci) {
        if (Candidate.mixed(tool)) { abilities = Candidate.abilities(tool); }
    }

    // This spike intentionally tests local widget/root routing, not a second production network protocol.
    @Inject(method = "toggleSetting", at = @At("HEAD"), cancellable = true)
    private void toggle(String name, CallbackInfo ci) {
        if (Candidate.mixed(tool)) {
            ToggleableTool.setSetting(tool, name, !ToggleableTool.getSetting(tool, name));
            ci.cancel();
        }
    }

    @Inject(method = "setSetting", at = @At("HEAD"), cancellable = true)
    private void set(String name, int value, CallbackInfo ci) {
        if (Candidate.mixed(tool)) {
            var view = Candidate.view(tool, Ability.byName(name));
            ToggleableTool.setToolValue(view, name, value);
            tool.set(com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents.ABILITY_VALUES.get(Ability.byName(name)),
                    ToggleableTool.getToolValue(view, name));
            ci.cancel();
        }
    }
}
