package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireSettingsSpikeClient;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireSettingsSpikeSupport;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumSet;

@Pseudo
@Mixin(targets = "com.direwolf20.justdirethings.client.screens.ToolSettingScreen", remap = false)
abstract class JustDireSettingsScreenSpikeMixin {
    @Shadow protected ItemStack tool;

    @WrapOperation(method = {"<init>", "init", "refreshButtons", "mouseClicked"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getItem()Lnet/minecraft/world/item/Item;"))
    private Item metadataItem(ItemStack stack, Operation<Item> original) {
        var view = JustDireSettingsSpikeSupport.view(stack);
        return view == null ? original.call(stack) : view.getItem();
    }

    @WrapOperation(method = {"init", "mouseClicked"}, at = @At(value = "INVOKE",
            target = "Lcom/direwolf20/justdirethings/common/items/interfaces/ToggleableTool;getAbilities()Ljava/util/EnumSet;"))
    private EnumSet<?> boundedAbilities(@Coerce Object item, Operation<EnumSet<?>> original) {
        return JustDireSettingsSpikeSupport.mixed(tool) ? JustDireSettingsSpikeSupport.abilities() : original.call(item);
    }

    @WrapOperation(method = "refreshButtons", at = @At(value = "INVOKE",
            target = "Lcom/direwolf20/justdirethings/common/items/interfaces/ToggleableTool;getToolValue(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)I"))
    private int rootValue(ItemStack stack, String name, Operation<Integer> original) {
        return JustDireSettingsSpikeSupport.mixed(stack) ? JustDireSettingsSpikeSupport.value(stack) : original.call(stack, name);
    }

    @Inject(method = "refreshToolAndSlots", at = @At("TAIL"))
    private void select(CallbackInfo ci) { JustDireSettingsSpikeClient.select(this); }

    @Inject(method = "toggleSetting", at = @At("HEAD"), cancellable = true)
    private void toggle(String name, CallbackInfo ci) { if (JustDireSettingsSpikeClient.edit(this, name, 1, 0, 0, false, true)) { ci.cancel(); } }

    @Inject(method = "setSetting", at = @At("HEAD"), cancellable = true)
    private void value(String name, int value, CallbackInfo ci) { if (JustDireSettingsSpikeClient.edit(this, name, 2, value, 0, false, true)) { ci.cancel(); } }

    @Inject(method = "toggleRender", at = @At("HEAD"), cancellable = true)
    private void particles(String name, int value, CallbackInfo ci) { if (JustDireSettingsSpikeClient.edit(this, name, 3, value, 0, false, true)) { ci.cancel(); } }

    @Inject(method = "sendBinding", at = @At("HEAD"), cancellable = true)
    private void binding(String name, int mode, int key, boolean mouse, CallbackInfo ci) {
        if (JustDireSettingsSpikeClient.edit(this, name, 4, mode, key, mouse,
                (boolean) JustDireSettingsSpikeClient.field(this, "requireEquipped"))) { ci.cancel(); }
    }
}
