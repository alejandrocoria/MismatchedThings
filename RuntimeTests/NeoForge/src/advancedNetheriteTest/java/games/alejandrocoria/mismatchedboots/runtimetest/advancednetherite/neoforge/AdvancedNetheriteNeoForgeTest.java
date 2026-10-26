package games.alejandrocoria.mismatchedboots.runtimetest.advancednetherite.neoforge;

import games.alejandrocoria.mismatchedboots.runtimetest.advancednetherite.AcceptanceMarkers;
import games.alejandrocoria.mismatchedboots.runtimetest.advancednetherite.AdvancedNetheriteCompatibilityAssertions;
import games.alejandrocoria.mismatchedboots.runtimetest.advancednetherite.HookOnlyBootItem;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(AdvancedNetheriteCompatibilityAssertions.TEST_NAMESPACE)
public final class AdvancedNetheriteNeoForgeTest {
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(AdvancedNetheriteCompatibilityAssertions.TEST_NAMESPACE);
    private static final int TIMEOUT_TICKS = 3_600;

    private int ticks;
    private boolean finished;

    public AdvancedNetheriteNeoForgeTest(IEventBus eventBus) {
        ITEMS.registerItem(
                "hook_only_boots",
                HookOnlyBootItem::new,
                AdvancedNetheriteCompatibilityAssertions::hookBootProperties);
        ITEMS.register(eventBus);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ServerTickEvent.Post event) {
        if (finished) {
            return;
        }

        MinecraftServer server = event.getServer();
        try {
            ticks++;
            if (ticks > TIMEOUT_TICKS) {
                throw new AssertionError("Advanced Netherite NeoForge acceptance timed out");
            }
            var player = FakePlayerFactory.getMinecraft(server.overworld());
            AdvancedNetheriteCompatibilityAssertions.assertCompleteMatrix(player);
            AcceptanceMarkers.write("advanced-netherite-success.txt", "neoforge\n");
            finished = true;
            server.halt(false);
        } catch (Throwable throwable) {
            AcceptanceMarkers.failure(throwable);
            finished = true;
            server.halt(false);
        }
    }
}
