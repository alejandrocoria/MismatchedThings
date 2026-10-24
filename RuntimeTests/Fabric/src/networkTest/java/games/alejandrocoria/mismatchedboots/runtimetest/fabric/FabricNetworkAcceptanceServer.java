package games.alejandrocoria.mismatchedboots.runtimetest.fabric;

import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceServerTest;
import games.alejandrocoria.mismatchedboots.runtimetest.missingmods.MissingModsProof;
import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorObservations;
import games.alejandrocoria.mismatchedboots.runtimetest.separator.SeparatorServerProbe;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

public final class FabricNetworkAcceptanceServer implements ModInitializer {
    @Override
    public void onInitialize() {
        if (MissingModsProof.enabled()) {
            if (!MissingModsProof.stage().equals("absent")) {
                Registry.register(BuiltInRegistries.ATTRIBUTE, Identifier.parse(MissingModsProof.MOD + ":temporary_attribute"),
                        new RangedAttribute("missing-mod-proof", 0, -1024, 1024));
                Registry.register(BuiltInRegistries.SOUND_EVENT, Identifier.parse(MissingModsProof.MOD + ":temporary_equip"),
                        SoundEvent.createVariableRangeEvent(Identifier.parse(MissingModsProof.MOD + ":temporary_equip")));
                Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.parse(MissingModsProof.MOD + ":temporary_energy"),
                        DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
                Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.parse(MissingModsProof.MOD + ":throwing_property"),
                        MissingModsProof.throwingProperty());
            }
            ServerTickEvents.END_SERVER_TICK.register(MissingModsProof::tick); return;
        }
        if (games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalLifecycle.enabled()) {
            ServerTickEvents.END_SERVER_TICK.register(games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalLifecycle::tick); return;
        }
        if (games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalNativeTestApi.enabled()) {
            MysticalAugmentsServer.register(); return;
        }
        if (SeparatorObservations.enabled()) {
            if (Boolean.getBoolean("mismatchedboots.test.separatorSeed")) {
                ServerTickEvents.END_SERVER_TICK.register(SeparatorServerProbe::run);
            }
            return;
        }
        ServerTickEvents.END_SERVER_TICK.register(NetworkAcceptanceServerTest::tick);
    }
}
