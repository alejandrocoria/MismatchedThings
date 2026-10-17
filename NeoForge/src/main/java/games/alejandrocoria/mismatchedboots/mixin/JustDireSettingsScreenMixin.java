package games.alejandrocoria.mismatchedboots.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings.JustDireSettingsClient;
import games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings.JustDireSettingsScreenAccess;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumSet;
import java.util.Map;

@Pseudo
@Mixin(targets = "com.direwolf20.justdirethings.client.screens.ToolSettingScreen", remap = false)
abstract class JustDireSettingsScreenMixin extends AbstractContainerScreen<AbstractContainerMenu> implements JustDireSettingsScreenAccess {
    protected JustDireSettingsScreenMixin(AbstractContainerMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    @Shadow protected ItemStack tool;
    @Shadow private int toolSlot;
    @Shadow private EnumSet<?> abilities;
    @Shadow protected boolean bindingEnabled;
    @Shadow protected boolean requireEquipped;
    @Shadow protected Button shownAbilityButton;
    @Shadow @Final protected Map<Button, Button> leftRightClickButtons;
    @Shadow @Final protected Map<Button, Object> buttonToAbilityMap;
    @Shadow protected Map<Button, Object> bindingMap;
    @Shadow public abstract void clearMaps();

    // Published only after all local metadata/selection/edit/restriction hooks survive transformation.
    @Unique private static boolean mismatchedboots$jdtSettingsReady() { return false; }
    @Override public boolean mismatchedboots$jdtReady() { return mismatchedboots$jdtSettingsReady() && JustDireSettingsClient.available(); }
    @Override public ItemStack mismatchedboots$jdtTool() { return tool; }
    @Override public int mismatchedboots$jdtSlot() { return toolSlot; }
    @Override public void mismatchedboots$jdtTool(ItemStack root) { tool = root; }
    @Override public void mismatchedboots$jdtClear() {
        bindingEnabled = false; tool = ItemStack.EMPTY; clearWidgets(); clearMaps();
    }

    @WrapOperation(method = {"<init>", "init", "refreshButtons", "mouseClicked"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getItem()Lnet/minecraft/world/item/Item;"))
    private Item mismatchedboots$jdtMetadata(ItemStack stack, Operation<Item> original) {
        Item view = mismatchedboots$jdtReady() ? JustDireSettingsCompatibility.metadataItem(stack) : null;
        return view == null ? original.call(stack) : view;
    }

    @WrapOperation(method = {"init", "mouseClicked"}, at = @At(value = "INVOKE",
            target = "Lcom/direwolf20/justdirethings/common/items/interfaces/ToggleableTool;getAbilities()Ljava/util/EnumSet;"))
    private EnumSet<?> mismatchedboots$jdtAbilities(@Coerce Object item, Operation<EnumSet<?>> original) {
        return JustDireSettingsCompatibility.mixed(tool) ? JustDireSettingsCompatibility.screenAbilities(tool) : original.call(item);
    }

    @Coerce
    @WrapOperation(method = "refreshButtons", at = @At(value = "INVOKE",
            target = "Lcom/direwolf20/justdirethings/common/items/interfaces/ToggleableTool;getAbilityParams(Lcom/direwolf20/justdirethings/common/items/interfaces/Ability;)Lcom/direwolf20/justdirethings/common/items/interfaces/AbilityParams;"))
    private Object mismatchedboots$jdtParameters(@Coerce Object item, @Coerce Object ability, Operation<Object> original) {
        return JustDireSettingsCompatibility.mixed(tool) ? JustDireSettingsCompatibility.screenParameters(tool, ability) : original.call(item, ability);
    }

    @WrapOperation(method = "refreshButtons", at = @At(value = "INVOKE",
            target = "Lcom/direwolf20/justdirethings/common/items/interfaces/ToggleableTool;getToolValue(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)I"))
    private int mismatchedboots$jdtValue(ItemStack stack, String name, Operation<Integer> original) {
        return JustDireSettingsCompatibility.mixed(stack) ? JustDireSettingsCompatibility.screenValue(stack, name) : original.call(stack, name);
    }

    @Inject(method = "refreshButtons", at = @At("HEAD"), cancellable = true)
    private void mismatchedboots$jdtRefreshGuard(CallbackInfo ci) {
        if (!JustDireSettingsCompatibility.mixed(tool)) { return; }
        var supported = mismatchedboots$jdtReady() ? JustDireSettingsCompatibility.screenAbilities(tool) : null;
        if (supported == null) { clearWidgets(); clearMaps(); ci.cancel(); }
        else { abilities = supported; }
    }

    @Inject(method = "refreshButtons", at = @At("TAIL"))
    private void mismatchedboots$jdtRestrictions(CallbackInfo ci) {
        if (!JustDireSettingsCompatibility.mixed(tool)) { return; }
        for (Button icon : buttonToAbilityMap.keySet()) {
            JustDireSettingsClient.restrict(leftRightClickButtons.get(icon));
        }
        if (!mismatchedboots$jdtReady()) { clearWidgets(); clearMaps(); }
    }

    @Inject(method = "refreshToolAndSlots", at = @At("TAIL"))
    private void mismatchedboots$jdtSelect(CallbackInfo ci) { JustDireSettingsClient.select(this); }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mismatchedboots$jdtMouseCaptureGuard(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> ci) {
        // Native capture already excludes left/right. Let ordinary clicks select another tool
        // and cancel capture through native widget rebuilding; block only invalid extra buttons.
        if (JustDireSettingsCompatibility.mixed(tool) && bindingEnabled
                && event.button() != 0 && event.button() != 1
                && !JustDireSettingsCompatibility.validBindingInput(event.button(), true)) { ci.setReturnValue(true); }
    }

    @Inject(method = "keyPressed", at = @At("RETURN"))
    private void mismatchedboots$jdtLocation(CallbackInfoReturnable<Boolean> ci) {
        // Native Escape's null-record branch resets this field after sendBinding returns.
        // Keep the new unbound row's preference, rather than leaking that default into its next click.
        if (mismatchedboots$jdtReady() && JustDireSettingsCompatibility.mixed(tool) && shownAbilityButton != null) {
            requireEquipped = JustDireSettingsCompatibility.screenEquipped(bindingMap.get(leftRightClickButtons.get(shownAbilityButton)));
        }
    }

    @Inject(method = "toggleSetting", at = @At("HEAD"), cancellable = true)
    private void mismatchedboots$jdtToggle(String name, CallbackInfo ci) {
        if (JustDireSettingsClient.edit(this, name, JustDireActiveNetwork.TOGGLE, 0, -1, false, true)) { ci.cancel(); }
    }
    @Inject(method = "setSetting", at = @At("HEAD"), cancellable = true)
    private void mismatchedboots$jdtSet(String name, int value, CallbackInfo ci) {
        if (JustDireSettingsClient.edit(this, name, JustDireActiveNetwork.VALUE, value, -1, false, true)) { ci.cancel(); }
    }
    @Inject(method = "toggleRender", at = @At("HEAD"), cancellable = true)
    private void mismatchedboots$jdtParticles(String name, int value, CallbackInfo ci) {
        if (JustDireSettingsClient.edit(this, name, JustDireActiveNetwork.PARTICLES, value, -1, false, true)) { ci.cancel(); }
    }
    @Inject(method = "sendBinding", at = @At("HEAD"), cancellable = true)
    private void mismatchedboots$jdtBinding(String name, int mode, int key, boolean mouse, CallbackInfo ci) {
        if (JustDireSettingsClient.edit(this, name, JustDireActiveNetwork.BINDING, mode, key, mouse, requireEquipped)) {
            // The native location callback does not replace bindingMap. Without this local update,
            // switching rows or capturing a new key could restore its previous location preference.
            Object record = JustDireSettingsCompatibility.screenBinding(name, key, mouse, requireEquipped);
            if (record != null) {
                for (var entry : buttonToAbilityMap.entrySet()) {
                    if (entry.getValue().toString().equalsIgnoreCase(name)) { bindingMap.put(leftRightClickButtons.get(entry.getKey()), record); }
                }
            }
            ci.cancel();
        }
    }
    @Inject(method = {"cycleSetting", "setCustomSetting2"}, at = @At("HEAD"), cancellable = true)
    private void mismatchedboots$jdtUnsupported(CallbackInfo ci) {
        if (JustDireSettingsCompatibility.mixed(tool)) { ci.cancel(); }
    }
}
