package games.alejandrocoria.rulesspike;

import games.alejandrocoria.rulesspike.mixin.MismatchAccessor;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.client.Minecraft;

@EventBusSubscriber(modid = "boots_rules_spike", value = Dist.CLIENT)
public final class NeoClientSpike {
    @SubscribeEvent
    public static void joined(ClientPlayerNetworkEvent.LoggingIn event) {
        if (Proof.CANDIDATE) {
            ClientProof.joining();
            ClientPacketDistributor.sendToServer(Ready.INSTANCE);
        }
    }

    @SubscribeEvent
    public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        if (Proof.CANDIDATE) {
            ClientProof.disconnected();
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if ((Proof.CASE.equals("new-old") || Proof.CASE.equals("old-new"))
                && client.screen instanceof net.neoforged.neoforge.client.gui.ModMismatchDisconnectedScreen screen) {
            RejectionProof.acceptNative(client, ((MismatchAccessor) screen).rulesSpike$channels().toString());
            return;
        }
        if (Proof.CANDIDATE) {
            ClientProof.tick(client);
        } else {
            RejectionProof.tick(client);
        }
    }
}
