package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
        Registry.register(
                BuiltInRegistries.RECIPE_SERIALIZER,
                ModContent.MIXED_BOOTS_RECIPE_ID,
                ModContent.createMixedBootsRecipeSerializer());
        Registry.register(
                BuiltInRegistries.RECIPE_SERIALIZER,
                ModContent.SMITHING_BASE_RECIPE_ID,
                ModContent.createSmithingBaseRecipeSerializer());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            AttributeRulesLifecycle.onServerStarting();
            ComponentRulesLifecycle.onServerStarting();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            AttributeRulesLifecycle.onServerStopped();
            ComponentRulesLifecycle.onServerStopped();
        });
        MismatchedBoots.init();
    }
}
