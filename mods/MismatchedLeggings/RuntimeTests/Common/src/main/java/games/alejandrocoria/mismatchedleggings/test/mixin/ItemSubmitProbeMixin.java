package games.alejandrocoria.mismatchedleggings.test.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.test.render.EmptyItemModelChecks;
import games.alejandrocoria.mismatchedleggings.test.render.ItemChecks;
import games.alejandrocoria.mismatchedleggings.test.render.ItemClient;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
abstract class ItemSubmitProbeMixin {
    @Shadow ItemDisplayContext displayContext;

    @Inject(method = "submit", at = @At("HEAD"))
    private void leggingsTest$record(PoseStack pose, SubmitNodeCollector destination, int light, int overlay,
                                     int outline, CallbackInfo callback) {
        if (!Boolean.getBoolean("leggings.test.items")) { return; }
        var faces = ItemChecks.faces((ItemStackRenderState) (Object) this);
        if (Boolean.getBoolean("leggings.test.emptyItems")) {
            EmptyItemModelChecks.observe(displayContext, faces);
        }
        if (faces.stream().anyMatch(f -> f.texture().contains("redstone_block"))
                && faces.stream().anyMatch(f -> f.texture().contains("lapis_block"))) {
            ItemClient.submitted.add(displayContext);
        }
    }
}
