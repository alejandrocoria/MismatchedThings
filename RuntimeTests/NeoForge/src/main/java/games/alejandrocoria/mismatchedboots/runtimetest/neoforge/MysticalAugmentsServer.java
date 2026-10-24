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
    @Override public void coexist(net.minecraft.server.MinecraftServer server) throws Exception {
        for (String name : java.util.List.of("mismatchedboots$maRefundReady", "mismatchedboots$justDireRefundReady")) {
            var marker = net.minecraft.world.item.ItemStack.class.getDeclaredMethod(name);
            marker.setAccessible(true);
            if (!Boolean.TRUE.equals(marker.invoke(null))) { throw new AssertionError("Coexisting refund observer refused: " + name); }
        }
        JustDireGroundStompTestSupport.initializeApi();
        JustDireRefundBreakTest.register();
        var root = JustDireRefundBreakTest.mixed("ferricore", "stepheight");
        var credits = root.get(games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireRefundCompatibility.component());
        if (credits == null || !credits.names().equals(java.util.List.of("stepheight"))) { throw new AssertionError("JDT coexist fixture lacks native purchase credit"); }
        try { JustDireRefundBreakTest.direct(server, root, "stepheight"); }
        finally { JustDireRefundBreakTest.clearObservation(); }
    }
}
