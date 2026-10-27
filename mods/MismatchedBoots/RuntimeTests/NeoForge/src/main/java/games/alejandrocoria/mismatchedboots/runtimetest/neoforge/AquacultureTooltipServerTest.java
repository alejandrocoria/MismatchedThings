package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.aquaculture.AquacultureTooltipCompatibility;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;

public final class AquacultureTooltipServerTest {
    private static boolean finished;

    private AquacultureTooltipServerTest() {
    }

    public static void tick(MinecraftServer server) {
        if (finished) {
            return;
        }
        finished = true;
        try {
            var boots = new ItemStack(BuiltInRegistries.ITEM.get(Identifier.parse("aquaculture:neptunium_boots")).orElseThrow());
            var lines = new ArrayList<Component>();
            AquacultureTooltipCompatibility.appendSource(boots, TooltipFlag.NORMAL, lines::add);
            if (!lines.isEmpty()) {
                throw new AssertionError("Dedicated server invoked native client presentation");
            }
            NetworkAcceptanceFiles.write("seed-report.txt", "Aquaculture: packaged dedicated-server client isolation passed; world seeded\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("seed", failure);
        }
        server.halt(false);
    }
}
