package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.runtimetest.mystical.MysticalAugmentsProbe;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

final class MysticalAugmentsServer implements MysticalAugmentsProbe.Platform {
    @Override public String loader() { return "NeoForge"; }
    static void register() {
        var platform = new MysticalAugmentsServer();
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> MysticalAugmentsProbe.run(event.getServer(), platform));
    }
    @Override public void update(ServerPlayer player) { NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player)); }
    @Override public void logout(ServerPlayer player) { NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player)); }
    @Override public void death(ServerPlayer player) { NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic())); }
}
