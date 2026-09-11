package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;

final class ModRegistries {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Constants.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Constants.MOD_ID);

    static {
        ITEMS.register(
                ModContent.MIXED_BOOTS_NAME,
                () -> new MixedBootsItem(ModContent.mixedBootsProperties().setId(ModContent.MIXED_BOOTS_KEY)));
        DATA_COMPONENT_TYPES.register(ModContent.MIXED_BOOTS_DATA_NAME, ModContent::createMixedBootsDataType);
    }

    private ModRegistries() {
    }

    static void register(BusGroup modBusGroup) {
        DATA_COMPONENT_TYPES.register(modBusGroup);
        ITEMS.register(modBusGroup);
    }
}
