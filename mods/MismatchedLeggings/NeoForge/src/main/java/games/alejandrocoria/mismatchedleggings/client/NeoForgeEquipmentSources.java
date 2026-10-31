package games.alejandrocoria.mismatchedleggings.client;

import games.alejandrocoria.mismatchedleggings.client.render.EquipmentSources;
import net.minecraft.world.item.ItemStack;

/** Native extensions resolve their final model/materials inside the buffered equipment call. */
public final class NeoForgeEquipmentSources implements EquipmentSources {
    @Override
    public boolean usesStandardRenderer(ItemStack source) {
        return true;
    }
}
