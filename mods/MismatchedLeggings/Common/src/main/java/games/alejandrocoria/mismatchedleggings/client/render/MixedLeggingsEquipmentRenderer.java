package games.alejandrocoria.mismatchedleggings.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** Pair-wide native preflight, then split/complete-owner/neutral, never a partially drawn pair. */
public final class MixedLeggingsEquipmentRenderer {
    private MixedLeggingsEquipmentRenderer() {}

    public static <S> void render(EquipmentLayerRenderer renderer, Model<? super S> nativeModel, S state,
                                 EquipmentClientInfo.LayerType layer, ItemStack root, PoseStack pose,
                                 SubmitNodeCollector destination, int light, int outline) {
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (data == null) {
            return;
        }
        var left = capture(renderer, nativeModel, state, layer, data.left().source(), root.hasFoil(), pose, light, outline);
        var right = capture(renderer, nativeModel, state, layer, data.right().source(), root.hasFoil(), pose, light, outline);
        if (left != null && right != null && left.prepareSplit(true) && right.prepareSplit(false)) {
            left.flush(destination);
            right.flush(destination);
            return;
        }
        // Reuse the already resolved owner submissions: native extensions/material hooks run once.
        if (left != null && left.prepareFull()) {
            left.flush(destination);
            return;
        }
        // A bare product stack has no foreign item renderer, dye or trim. The product-owned
        // neutral asset still uses native equipment animation, lighting and root foil.
        var neutral = new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID));
        neutral.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, root.hasFoil());
        renderer.renderLayers(layer, ResourceKey.create(EquipmentAssets.ROOT_ID,
                Identifier.fromNamespaceAndPath(LeggingsContent.MOD_ID, "neutral")), nativeModel, state,
                neutral, pose, destination, light, outline);
    }

    private static <S> BufferedEquipment capture(EquipmentLayerRenderer renderer, Model<? super S> model, S state,
                                                EquipmentClientInfo.LayerType layer, ItemStack source, boolean foil,
                                                PoseStack pose, int light, int outline) {
        var equippable = source.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != EquipmentSlot.LEGS) {
            return null;
        }
        if (!EquipmentSources.INSTANCE.usesStandardRenderer(source)) {
            return EquipmentSources.INSTANCE.captureCustom(source, model, state, foil, pose, light, outline);
        }
        if (equippable.assetId().isEmpty()) {
            return null;
        }
        source.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
        var captured = new BufferedEquipment();
        renderer.renderLayers(layer, equippable.assetId().orElseThrow(), model, state, source,
                pose, captured, light, outline);
        return captured;
    }
}
