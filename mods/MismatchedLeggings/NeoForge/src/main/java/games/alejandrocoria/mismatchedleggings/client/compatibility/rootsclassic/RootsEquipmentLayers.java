package games.alejandrocoria.mismatchedleggings.client.compatibility.rootsclassic;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.mixin.client.EquipmentAssetsAccessor;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Roots 1.6.7's LEGS models have only HUMANOID assets. Never change original item rendering. */
public final class RootsEquipmentLayers {
    private static boolean warned;

    private RootsEquipmentLayers() {
    }

    public static EquipmentClientInfo.LayerType select(ItemStack source, EquipmentClientInfo.LayerType requested,
                                                       EquipmentLayerRenderer renderer) {
        var id = BuiltInRegistries.ITEM.getKey(source.getItem());
        if (!id.getNamespace().equals("rootsclassic")) {
            return requested;
        }
        String extension = switch (id.getPath()) {
            case "sylvan_tunic" -> "SylvanItemExtension";
            case "wildwood_leggings" -> "WildwoodItemExtension";
            default -> null;
        };
        if (extension == null || requested != EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
                && requested != EquipmentClientInfo.LayerType.HUMANOID_BABY) {
            return requested;
        }
        var equippable = source.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != EquipmentSlot.LEGS || equippable.assetId().isEmpty()) {
            return null;
        }
        var asset = ((EquipmentAssetsAccessor) renderer).mismatchedleggings$equipmentAssets().get(equippable.assetId().orElseThrow());
        // Resource packs/upstream fixes own the requested layer. Do not cache across reloads.
        if (!asset.getLayers(requested).isEmpty()) {
            return requested;
        }
        if (asset.getLayers(EquipmentClientInfo.LayerType.HUMANOID).isEmpty()) {
            return null;
        }
        if (!IClientItemExtensions.of(source).getClass().getName().equals("elucent.rootsclassic.client.extension." + extension)) {
            if (!warned) {
                warned = true;
                LeggingsContent.LOG.warn("Cannot select Roots Classic leggings alternate layer: unsupported native extension");
            }
            return null;
        }
        // Resolve the native model once, inside renderLayers. The ordinary buffered preflight
        // still validates its humanoid/leg geometry before either side reaches the destination.
        return EquipmentClientInfo.LayerType.HUMANOID;
    }
}
