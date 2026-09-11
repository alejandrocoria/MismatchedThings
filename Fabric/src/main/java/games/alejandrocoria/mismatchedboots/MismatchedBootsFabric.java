package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public class MismatchedBootsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                ModContent.MIXED_BOOTS_DATA_ID,
                ModContent.createMixedBootsDataType());
        Registry.register(
                BuiltInRegistries.ITEM,
                ModContent.MIXED_BOOTS_ID,
                new MixedBootsItem(ModContent.mixedBootsProperties().setId(ModContent.MIXED_BOOTS_KEY)));
        MismatchedBoots.init();
    }
}
