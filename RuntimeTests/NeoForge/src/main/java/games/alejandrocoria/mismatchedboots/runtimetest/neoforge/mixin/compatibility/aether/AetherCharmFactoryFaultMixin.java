package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.aether;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fault only the real source-native preparation, never original/root native tooltip payloads. */
@Mixin(ClientTooltipComponent.class)
interface AetherCharmFactoryFaultMixin {
    @Inject(method = "create(Lnet/minecraft/world/inventory/tooltip/TooltipComponent;)Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;",
            at = @At("HEAD"), cancellable = true)
    private static void fault(TooltipComponent payload, CallbackInfoReturnable<ClientTooltipComponent> callback) throws ReflectiveOperationException {
        if (!payload.getClass().getName().equals("com.aetherteam.aetherii.client.renderer.item.tooltip.ClientCharmTooltip$CharmTooltip")) { return; }
        var base = (ItemStack) payload.getClass().getMethod("base").invoke(payload);
        // Production's source-only renderer base deliberately has no holders; originals/root overrides still do.
        var type = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(net.minecraft.resources.Identifier.parse("aether_ii:charms")).orElseThrow();
        if (base.has(type)) { return; }
        if (System.getProperty("mismatchedboots.runtimetest.aetherTooltipVariant").equals("charmfactorymissing")) { callback.setReturnValue(null); }
        else { throw new IllegalStateException("Controlled native source charm factory failure"); }
    }
}
