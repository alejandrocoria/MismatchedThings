package games.alejandrocoria.mismatchedboots.mixin;

import games.alejandrocoria.mismatchedboots.client.MismatchedBootsClientFabric;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class ClientRecipeDisconnectMixin {
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("TAIL"))
    private void mismatchedboots$invalidateRecipes(CallbackInfo ci) {
        MismatchedBootsClientFabric.onClientDisconnected();
    }
}
