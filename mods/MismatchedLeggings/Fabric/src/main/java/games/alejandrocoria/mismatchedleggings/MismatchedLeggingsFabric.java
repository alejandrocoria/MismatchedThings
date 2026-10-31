package games.alejandrocoria.mismatchedleggings;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class MismatchedLeggingsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, LeggingsContent.MIXED_LEGGINGS_DATA_ID, LeggingsContent.createDataType());
        Registry.register(BuiltInRegistries.ITEM, LeggingsContent.MIXED_LEGGINGS_ID, LeggingsContent.createItem());
    }
}
