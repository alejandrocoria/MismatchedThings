package games.alejandrocoria.mismatchedboots.runtimetest.fabric;

import games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalAugmentsProbe;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

final class MysticalAugmentsServer implements MysticalAugmentsProbe.Platform {
    @Override
    public String loader() {
        return "Fabric";
    }

    static void register() {
        var platform = new MysticalAugmentsServer();
        ServerTickEvents.END_SERVER_TICK.register(server -> MysticalAugmentsProbe.run(server, platform));
    }

    @Override
    public void update(ServerPlayer player) {
        // Exercise MA's registered Fabric callback with this disconnected server-side subject.
        var players = player.level().players();
        players.add(player);
        try {
            ServerTickEvents.END_LEVEL_TICK.invoker().onEndTick(player.level());
        } finally {
            players.remove(player);
        }
    }

    @Override
    public void logout(ServerPlayer player) {
        ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(player.connection, player.level().getServer());
    }

    @Override
    public void death(ServerPlayer player) {
        ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(player, player.damageSources().generic());
    }
}
