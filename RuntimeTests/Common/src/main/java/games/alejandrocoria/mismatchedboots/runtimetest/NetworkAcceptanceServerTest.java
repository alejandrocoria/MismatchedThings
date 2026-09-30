package games.alejandrocoria.mismatchedboots.runtimetest;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.component.DurabilityPolicy;
import games.alejandrocoria.mismatchedboots.component.RepairCostPolicy;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class NetworkAcceptanceServerTest {
    private static final Identifier MOVEMENT_SPEED = Identifier.withDefaultNamespace("movement_speed");
    private static final Identifier DORMANT_ATTRIBUTE = Identifier.fromNamespaceAndPath("futuremod", "dormant");
    private static final Identifier ENCHANTABLE = Identifier.withDefaultNamespace("enchantable");
    private static final Identifier DORMANT_COMPONENT =
            Identifier.fromNamespaceAndPath("futuremod", "dormant_energy");
    private static final int TIMEOUT_TICKS = 3_600;

    private static int ticks;
    private static boolean ready;
    private static boolean playerSeen;
    private static boolean resultDelivered;
    private static boolean stopping;

    private NetworkAcceptanceServerTest() {
    }

    public static void tick(MinecraftServer server) {
        if (stopping) {
            return;
        }
        try {
            ticks++;
            if (!ready) {
                assertRules();
                assertBehaviorTagProjection();
                assertOptionalModAbsent();
                NetworkAcceptanceFiles.write(
                        "server-snapshot.txt", NetworkAcceptanceFiles.snapshot(
                                AttributeRulesLifecycle.serverRules(), ComponentRulesLifecycle.serverRules()));
                NetworkAcceptanceFiles.write("server-ready.txt", loaderName());
                ready = true;
                if (Boolean.getBoolean("mismatchedboots.runtimetest.stopAfterReady")) {
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
            NetworkAcceptanceFiles.failure("server", throwable);
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
        var components = ComponentRulesLifecycle.serverRules();
        if (components.durability() != DurabilityPolicy.SEPARATE
                || components.repairCost() != RepairCostPolicy.OWNER
                || !ComponentRule.of(ComponentPolicy.OWNER).equals(components.declarations().get(ENCHANTABLE))
                || !ComponentRule.of(ComponentPolicy.SHARE).equals(
                        components.declarations().get(DORMANT_COMPONENT))
                || components.components().rules().containsKey(DORMANT_COMPONENT)) {
            throw new AssertionError("Server did not load the isolated component rules: " + components);
        }
    }

    private static void assertBehaviorTagProjection() {
        var qualifying = MixedBootsFactory.createPair(
                new ItemStack(Items.GOLDEN_BOOTS), new ItemStack(Items.IRON_BOOTS));
        if (!qualifying.primary().is(ItemTags.PIGLIN_SAFE_ARMOR)
                || !qualifying.inverse().is(ItemTags.PIGLIN_SAFE_ARMOR)) {
            throw new AssertionError("Loader server did not project piglin safety in both orientations");
        }
        ItemStack ordinary = MixedBootsFactory.createPair(
                new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)).primary();
        if (ordinary.is(ItemTags.PIGLIN_SAFE_ARMOR) || qualifying.primary().is(ItemTags.FOOT_ARMOR)) {
            throw new AssertionError("Loader server projected an unsupported behavior tag");
        }
    }

    private static void assertOptionalModAbsent() {
        String hook = "com/autovw/advancednetherite/api/impl/IAdvancedHooks.class";
        if (NetworkAcceptanceServerTest.class.getClassLoader().getResource(hook) != null) {
            throw new AssertionError("Advanced Netherite unexpectedly entered the canonical runtime classpath");
        }
    }

    private static void craftAndDeliver(ServerPlayer player) {
        InventoryMenu menu = new InventoryMenu(player.getInventory(), true, player);
        menu.getInputGridSlots().get(0).set(NetworkAcceptanceStacks.firstSource());
        menu.getInputGridSlots().get(3).set(NetworkAcceptanceStacks.secondSource());

        ItemStack crafted = menu.getSlot(0).safeTake(1, 1, player);
        String description = NetworkAcceptanceStacks.describePrimary(crafted);
        ItemStack inverse = menu.getInputGridSlots().get(0).getItem();
        NetworkAcceptanceStacks.assertInverse(inverse);
        if (menu.getInputGridSlots().stream().filter(slot -> !slot.getItem().isEmpty()).count() != 1L) {
            throw new AssertionError("Real crafting did not conserve exactly two outputs");
        }
        if (!player.getInventory().add(crafted.copy())) {
            throw new AssertionError("Could not deliver the crafted stack to the client");
        }
        player.inventoryMenu.broadcastChanges();
        NetworkAcceptanceFiles.write("server-result.txt", description);
    }

    private static void succeedAndStop(MinecraftServer server) {
        NetworkAcceptanceFiles.write("server-success.txt", loaderName());
        stopping = true;
        server.halt(false);
    }

    private static String loaderName() {
        return System.getProperty("mismatchedboots.runtimetest.loader", "unknown");
    }
}
