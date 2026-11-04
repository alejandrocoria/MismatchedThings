package games.alejandrocoria.rulesspike;

import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;

public final class FabricSpike implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(Proof::started);
        ServerTickEvents.END_SERVER_TICK.register(Proof::serverTick);
        if (!Proof.CANDIDATE) {
            return;
        }
        PayloadTypeRegistry.clientboundConfiguration().register(Ready.TYPE, net.minecraft.network.codec.StreamCodec.unit(Ready.INSTANCE));
        PayloadTypeRegistry.serverboundConfiguration().register(Ready.TYPE, net.minecraft.network.codec.StreamCodec.unit(Ready.INSTANCE));
        PayloadTypeRegistry.serverboundPlay().register(Ready.TYPE, Ready.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Snapshot.TYPE, Snapshot.CODEC);
        ServerConfigurationNetworking.registerGlobalReceiver(Ready.TYPE, (payload, context) -> { });
        ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
            if (!ServerConfigurationNetworking.canSend(listener, Ready.TYPE)) {
                listener.disconnect(Component.literal(Proof.REASON));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(Ready.TYPE,
                (payload, context) -> ServerPlayNetworking.send(context.player(), Snapshot.server()));
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
            listener.player.getInventory().setItem(0, new MixedBootsWorkbenchRecipe().assemblePair(ClientInput.input()).primary());
        });
    }
}
