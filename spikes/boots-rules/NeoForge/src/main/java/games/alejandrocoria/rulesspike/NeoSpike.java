package games.alejandrocoria.rulesspike;

import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.minecraft.server.level.ServerPlayer;

@Mod("boots_rules_spike")
public final class NeoSpike {
    public NeoSpike(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> Proof.started(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> Proof.serverTick(event.getServer()));
        if (!Proof.CANDIDATE) {
            return;
        }
        bus.addListener((RegisterPayloadHandlersEvent event) -> {
            var registrar = event.registrar("2");
            registrar.playToServer(Ready.TYPE, Ready.CODEC, (payload, context) ->
                    PacketDistributor.sendToPlayer((ServerPlayer) context.player(), Snapshot.server()));
            registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC, (payload, context) -> PreviewState.install(payload));
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            event.getEntity().getInventory().setItem(0, new MixedBootsWorkbenchRecipe().assemblePair(ClientInput.input()).primary());
        });
    }
}
