package games.alejandrocoria.mismatchedleggings;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(LeggingsContent.MOD_ID)
public final class MismatchedLeggingsNeoForge {
    public MismatchedLeggingsNeoForge(IEventBus bus) {
        var items = DeferredRegister.createItems(LeggingsContent.MOD_ID);
        items.register("mixed_leggings", LeggingsContent::createItem);
        items.register(bus);
    }
}
