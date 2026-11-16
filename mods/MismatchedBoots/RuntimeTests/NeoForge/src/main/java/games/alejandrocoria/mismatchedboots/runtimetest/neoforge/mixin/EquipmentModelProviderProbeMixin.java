package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.equipment.ArmorLayerEntryChecks;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Passive counter: the native provider is always invoked exactly once. */
@Mixin(EquipmentLayerRenderer.class)
abstract class EquipmentModelProviderProbeMixin {
    @WrapOperation(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
            + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
            + "Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/extensions/common/IClientItemExtensions;"
                    + "getGenericArmorModel(Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
                    + "Lnet/minecraft/client/model/Model;)Lnet/minecraft/client/model/Model;"))
    private <S> Model<? super S> mismatchedboots$countProvider(IClientItemExtensions extension, ItemStack stack,
                                                              EquipmentClientInfo.LayerType layer, Model<? super S> model,
                                                              Operation<Model<? super S>> original) {
        ArmorLayerEntryChecks.recordProvider();
        return original.call(extension, stack, layer, model);
    }
}
