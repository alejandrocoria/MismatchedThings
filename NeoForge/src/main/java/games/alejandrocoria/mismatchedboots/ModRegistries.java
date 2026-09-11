package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

final class ModRegistries {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Constants.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Constants.MOD_ID);

    static {
        ITEMS.registerItem(ModContent.MIXED_BOOTS_NAME, MixedBootsItem::new, ModContent::mixedBootsProperties);
        DATA_COMPONENT_TYPES.register(ModContent.MIXED_BOOTS_DATA_NAME, ModContent::createMixedBootsDataType);
    }

    private ModRegistries() {
    }

    static void register(IEventBus eventBus) {
        DATA_COMPONENT_TYPES.register(eventBus);
        ITEMS.register(eventBus);
    }
}
