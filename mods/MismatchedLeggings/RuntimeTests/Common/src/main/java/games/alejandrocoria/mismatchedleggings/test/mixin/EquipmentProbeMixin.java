package games.alejandrocoria.mismatchedleggings.test.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EquipmentLayerRenderer.class)
abstract class EquipmentProbeMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void leggingsTest$observeNativeRenderer(CallbackInfo callback) {
        if (Boolean.getBoolean("leggings.test.betterEnd") || Boolean.getBoolean("leggings.test.createFly")
                || Boolean.getBoolean("leggings.test.allTheModium") || Boolean.getBoolean("leggings.test.aquaculture")
                || Boolean.getBoolean("leggings.test.forbiddenArcanus")
                || Boolean.getBoolean("leggings.test.oritechCapture") || Boolean.getBoolean("leggings.test.rootsRendering")
                || Boolean.getBoolean("leggings.test.aetherConsolidation") || Boolean.getBoolean("leggings.test.rogdoriumPresentation")) {
            EquippedClient.renderer = (EquipmentLayerRenderer) (Object) this;
        }
    }

    @Inject(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
            + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
            + "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;II)V", at = @At("HEAD"))
    private void leggingsTest$observeBoots(EquipmentClientInfo.LayerType type, ResourceKey<EquipmentAsset> asset,
                                           Model<?> model, Object state, ItemStack stack, PoseStack pose,
                                           SubmitNodeCollector collector, int light, int outline, CallbackInfo callback) {
        if (Boolean.getBoolean("leggings.test.equipped")) {
            EquippedClient.renderer = (EquipmentLayerRenderer) (Object) this;
        }
        if (model.getClass().getName().contains("MixedBootsEquipmentRenderer$SideModel")) {
            BootsCoexistence.ironSeen |= stack.is(Items.IRON_BOOTS);
            BootsCoexistence.goldSeen |= stack.is(Items.GOLDEN_BOOTS);
        }
    }
}
