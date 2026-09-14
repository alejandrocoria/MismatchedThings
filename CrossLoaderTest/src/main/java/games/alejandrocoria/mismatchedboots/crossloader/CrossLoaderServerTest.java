package games.alejandrocoria.mismatchedboots.crossloader;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

public final class CrossLoaderServerTest {
    private static final Identifier MOVEMENT_SPEED = Identifier.withDefaultNamespace("movement_speed");
    private static final Identifier DORMANT_ATTRIBUTE = Identifier.fromNamespaceAndPath("futuremod", "dormant");
    private static final int TIMEOUT_TICKS = 3_600;

    private static int ticks;
    private static boolean ready;
    private static boolean playerSeen;
    private static boolean resultDelivered;
    private static boolean stopping;

    private CrossLoaderServerTest() {
    }

    public static void tick(MinecraftServer server) {
        if (stopping) {
            return;
        }
        try {
            ticks++;
            if (!ready) {
                assertRules();
                CrossLoaderTestFiles.write(
                        "server-snapshot.txt", CrossLoaderTestFiles.snapshot(AttributeRulesLifecycle.serverRules()));
                CrossLoaderTestFiles.write("server-ready.txt", loaderName());
                ready = true;
                if (Boolean.getBoolean("mismatchedboots.crossloader.stopAfterReady")) {
                    succeedAndStop(server);
                    return;
                }
            }

            if (!resultDelivered && !server.getPlayerList().getPlayers().isEmpty()) {
                playerSeen = true;
                craftAndDeliver(server.getPlayerList().getPlayers().getFirst());
                resultDelivered = true;
            } else if (resultDelivered && playerSeen && server.getPlayerList().getPlayers().isEmpty()) {
                succeedAndStop(server);
            }

            if (ticks > TIMEOUT_TICKS) {
                throw new AssertionError("Network acceptance server timed out");
            }
        } catch (Throwable throwable) {
            CrossLoaderTestFiles.failure("server", throwable);
            stopping = true;
            server.halt(false);
        }
    }

    private static void assertRules() {
        var rules = AttributeRulesLifecycle.serverRules();
        if (rules.policyFor(MOVEMENT_SPEED) != AttributePolicy.COMBINE
                || rules.policyFor(DORMANT_ATTRIBUTE) != AttributePolicy.MINIMUM) {
            throw new AssertionError("Server did not load the isolated rules: " + rules);
        }
    }

    private static void craftAndDeliver(ServerPlayer player) {
        InventoryMenu menu = new InventoryMenu(player.getInventory(), true, player);
        menu.getInputGridSlots().get(0).set(CrossLoaderTestStacks.firstSource());
        menu.getInputGridSlots().get(3).set(CrossLoaderTestStacks.secondSource());

        ItemStack crafted = menu.getSlot(0).safeTake(1, 1, player);
        String description = CrossLoaderTestStacks.describePrimary(crafted);
        ItemStack inverse = menu.getInputGridSlots().get(0).getItem();
        CrossLoaderTestStacks.assertInverse(inverse);
        if (menu.getInputGridSlots().stream().filter(slot -> !slot.getItem().isEmpty()).count() != 1L) {
            throw new AssertionError("Real crafting did not conserve exactly two outputs");
        }
        if (!player.getInventory().add(crafted.copy())) {
            throw new AssertionError("Could not deliver the crafted stack to the client");
        }
        player.inventoryMenu.broadcastChanges();
        CrossLoaderTestFiles.write("server-result.txt", description);
    }

    private static void succeedAndStop(MinecraftServer server) {
        CrossLoaderTestFiles.write("server-success.txt", loaderName());
        stopping = true;
        server.halt(false);
    }

    private static String loaderName() {
        return System.getProperty("mismatchedboots.crossloader.loader", "unknown");
    }
}
