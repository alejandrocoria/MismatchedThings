package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.missingmods;

import games.alejandrocoria.mismatchedboots.runtimetest.missingmods.MissingModsProof;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Loader registration only; all recovery behavior lives in the shared proof/model. */
public final class MissingModsSpike {
    private MissingModsSpike() {}
    public static boolean enabled() { return MissingModsProof.enabled(); }
    public static void tick(MinecraftServer server) { MissingModsProof.tick(server); }
    public static void initialize(IEventBus bus) {
        if (MissingModsProof.stage().equals("absent")) { return; }
        var attributes = DeferredRegister.create(Registries.ATTRIBUTE, MissingModsProof.MOD);
        attributes.register("temporary_attribute", () -> new RangedAttribute("missing-mod-proof", 0, -1024, 1024));
        attributes.register(bus);
        var sounds = DeferredRegister.create(Registries.SOUND_EVENT, MissingModsProof.MOD);
        sounds.register("temporary_equip", () -> SoundEvent.createVariableRangeEvent(Identifier.parse(MissingModsProof.MOD + ":temporary_equip")));
        sounds.register(bus);
        var components = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MissingModsProof.MOD);
        components.register("throwing_property", MissingModsProof::throwingProperty);
        components.register(bus);
    }
}
