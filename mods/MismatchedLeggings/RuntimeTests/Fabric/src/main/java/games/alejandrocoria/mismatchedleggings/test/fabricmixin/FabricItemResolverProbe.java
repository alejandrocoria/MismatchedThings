package games.alejandrocoria.mismatchedleggings.test.fabricmixin;

import games.alejandrocoria.mismatchedleggings.test.FabricItemChecks;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
abstract class FabricItemResolverProbe {
    @Inject(method = "appendItemLayers", at = @At("RETURN"))
    private void leggingsTest$emitMesh(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context,
                                       Level level, ItemOwner owner, int seed, CallbackInfo callback) {
        FabricItemChecks.convert(state, stack.get(DataComponents.ITEM_MODEL));
    }
}
