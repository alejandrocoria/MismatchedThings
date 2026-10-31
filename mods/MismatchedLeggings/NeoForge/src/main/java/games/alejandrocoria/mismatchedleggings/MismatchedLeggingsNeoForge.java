package games.alejandrocoria.mismatchedleggings;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(LeggingsContent.MOD_ID)
public final class MismatchedLeggingsNeoForge {
    public MismatchedLeggingsNeoForge(IEventBus bus) {
        var components = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, LeggingsContent.MOD_ID);
        components.register("mixed_leggings_data", LeggingsContent::createDataType);
        components.register(bus);
        var items = DeferredRegister.createItems(LeggingsContent.MOD_ID);
        items.register("mixed_leggings", LeggingsContent::createItem);
        items.register(bus);
    }
}
