package games.alejandrocoria.mismatchedleggings.mixin;

import games.alejandrocoria.mismatchedleggings.client.MismatchedLeggingsClientFabric;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class ClientRecipeReloadMixin {
    @Inject(method = "handleUpdateRecipes", at = @At("TAIL"))
    private void mismatchedleggings$invalidateRecipes(CallbackInfo ci) {
        MismatchedLeggingsClientFabric.onVanillaRecipesUpdated();
    }
}
