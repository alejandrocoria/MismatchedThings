package games.alejandrocoria.mismatchedboots.mixin;

import games.alejandrocoria.mismatchedboots.client.MismatchedBootsClientFabric;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class ClientRecipeReloadMixin {
    @Inject(method = "handleUpdateRecipes", at = @At("TAIL"))
    private void mismatchedboots$invalidateRecipes(CallbackInfo ci) {
        MismatchedBootsClientFabric.onVanillaRecipesUpdated();
    }
}
