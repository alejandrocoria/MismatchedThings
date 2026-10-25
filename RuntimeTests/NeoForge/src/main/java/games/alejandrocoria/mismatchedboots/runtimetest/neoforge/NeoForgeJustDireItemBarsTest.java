package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireResourcesCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/** Production event registration and deferred decoration, never a second decorator/provider. */
public final class NeoForgeJustDireItemBarsTest {
    private static final String OUTPUT = "just-dire-item-bars";
    private static final String VARIANT = System.getProperty("mismatchedboots.runtimetest.justDireItemBarsVariant", "live");
    private static final StringBuilder report = new StringBuilder();
    private static final List<ItemStack> packetStates = new ArrayList<>();
    private static int ticks, phase, packetIndex, waiting, checks, queryFaults;
    private static boolean finished, armed;
    private static Future<?> job;

    private NeoForgeJustDireItemBarsTest() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
    }

    public static boolean faultArmed(ItemStack root) {
        return armed && ModContent.isMixedBoots(root);
    }

    public static void faultObserved() {
        queryFaults++;
    }

    private static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 1_800, "Item bar client timed out at phase " + phase + ", packet " + packetIndex);
            if (client.player == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) {
                return;
            }
            client.options.pauseOnLostFocus = false;
            if (job != null) {
                if (!job.isDone()) {
                    return;
                }
                job.get();
                job = null;
            }
            if (phase == 0) {
                if (VARIANT.equals("absent")) {
                    require(BuiltInRegistries.ITEM.getOptional(id("celestigem_boots")).isEmpty(), "JDT was not absent");
                    var root = mixed(new ItemStack(Items.GOLDEN_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
                    root.setDamageValue(37);
                    inspect(client, root, -1);
                    require(JustDireResourcesCompatibility.energyBarReceiver(root) == null, "Absent receiver available");
                    report.append("Quiet optional absence: ordinary mixed durability, no native linkage\n");
                    phase = 2;
                } else if (faultVariant()) {
                    queryFailure(client);
                    phase = 2;
                } else {
                    matrix(client);
                    preparePackets();
                    sendPacketState(client);
                    phase = 1;
                }
            } else if (phase == 1) {
                ItemStack expected = packetStates.get(packetIndex), received = client.player.getInventory().getItem(0);
                if (!ItemStack.matches(expected, received)) {
                    return;
                }
                inspect(client, received, expectedWidth(received, 10_000));
                require(ModContent.getMixedBootsData(received).equals(ModContent.getMixedBootsData(packetStates.getFirst())),
                        "Ordinary slot update changed historical sources");
                if (++packetIndex == packetStates.size()) {
                    report.append("10 actual slot packets: charge, damage/repair, Unbreakable on/off, full/zero; no extra transport or stale lane\n");
                    phase = 2;
                } else {
                    sendPacketState(client);
                }
            } else if (VARIANT.equals("live") && !JustDireItemBarRenderingTest.tick(client)) {
                // Reuse Live's actual production registration; other JVMs retain focused refusals.
            } else if (++waiting > 20) {
                NetworkAcceptanceFiles.write(OUTPUT + "-report.txt", VARIANT + ": " + checks + " actual decoration checks\n" + report
                        + (VARIANT.equals("live") ? JustDireItemBarRenderingTest.report()
                                                  : "Production deferred GUI extraction; no GPU proof in this refusal JVM\n")
                        + "Owner ordinary-screen visual acceptance is separate\n");
                NetworkAcceptanceFiles.write(OUTPUT + "-success.txt", VARIANT + "\n");
                finished = true;
                client.stop();
            }
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure(OUTPUT, failure);
            finished = true;
            client.stop();
        }
    }

    private static void matrix(Minecraft client) {
        for (String tier : List.of("celestigem", "eclipsealloy")) {
            for (boolean secondPowered : List.of(false, true)) {
                var pair = MixedBootsFactory.createPair(nativeBoot(tier, 6_000),
                                                        secondPowered ? nativeBoot("eclipsealloy", 80_000) : new ItemStack(Items.DIAMOND_BOOTS));
                int capacity = tier.equals("eclipsealloy") || secondPowered ? 500_000 : 10_000;
                for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
                    root.set(JustDireResourceTestSupport.energyComponent(), capacity / 3);
                    var before = root.copy();
                    var receiver = JustDireResourcesCompatibility.energyBarReceiver(root);
                    if (VARIANT.equals("live")) {
                        require(receiver != null && JustDireResourceTestSupport.handler(root).getCapacityAsInt() == capacity,
                                "Expected production root capacity unavailable");
                        var left = ModContent.getMixedBootsData(root).orElseThrow().left().source();
                        var expectedReceiver = left.is(Items.DIAMOND_BOOTS)
                                               ? ModContent.getMixedBootsData(root).orElseThrow().right().source().getItem() : left.getItem();
                        require(receiver == expectedReceiver, "Receiver did not reuse the powered source identity");
                    } else {
                        require(receiver == null, "Unavailable storage exposed a formatting receiver");
                    }
                    require(ItemStack.matches(before, root), "Metadata query mutated root/source state");
                    inspect(client, root, expectedWidth(root, capacity));
                    root.setDamageValue(37);
                    inspect(client, root, expectedWidth(root, capacity));
                    root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                    inspect(client, root, expectedWidth(root, capacity));
                    root.remove(DataComponents.UNBREAKABLE);
                    inspect(client, root, expectedWidth(root, capacity));
                    root.setDamageValue(0);
                    inspect(client, root, expectedWidth(root, capacity));
                    for (int charge : new int[]{0, 1, capacity}) {
                        root.set(JustDireResourceTestSupport.energyComponent(), charge);
                        inspect(client, root, expectedWidth(root, capacity));
                    }
                }
            }
        }
        ItemStack root = mixed(nativeBoot("celestigem", 4_000), new ItemStack(Items.DIAMOND_BOOTS));
        root.setDamageValue(37);
        root.remove(JustDireResourceTestSupport.energyComponent());
        inspect(client, root, expectedWidth(root, 10_000));
        root.set(JustDireResourceTestSupport.energyComponent(), -1);
        inspect(client, root, -1);
        root.set(JustDireResourceTestSupport.energyComponent(), 10_001);
        inspect(client, root, -1);
        root.set(JustDireResourceTestSupport.energyComponent(), 0);
        root.setCount(2);
        inspect(client, root, -1);
        root.setCount(1);
        root.remove(ModContent.mixedBootsDataType().orElseThrow());
        inspect(client, root, -1);
        var passive = mixed(nativeBoot("ferricore", 0), new ItemStack(Items.DIAMOND_BOOTS));
        passive.set(JustDireResourceTestSupport.energyComponent(), 123);
        passive.setDamageValue(37);
        inspect(client, passive, -1);
        for (ItemStack control : List.of(new ItemStack(Items.DIAMOND_BOOTS), nativeBoot("ferricore", 0), nativeBoot("celestigem", 3_000))) {
            control.setDamageValue(25);
            inspect(client, control, -1);
        }
        // Ordinary invalid data must not latch presentation off for the next valid stack.
        root = mixed(nativeBoot("celestigem", 4_000), new ItemStack(Items.DIAMOND_BOOTS));
        inspect(client, root, expectedWidth(root, 10_000));
        report.append(VARIANT.equals("live")
                      ? "Both tiers/orientations, one/two providers and root maximum capacity; same-root damage/repair/Unbreakable moves FE 13/10 without duplicates\n"
                      : "Missing storage: both tiers/orientations, one/two providers, charge/damage/Unbreakable states retain standard bars without FE\n")
              .append("Charge-only zero/tiny/full changes; missing/invalid charge, count/data and forged passive FE; vanilla/native controls; whole-stack read-only\n");
    }

    private static void preparePackets() {
        var root = mixed(nativeBoot("celestigem", 6_000), new ItemStack(Items.DIAMOND_BOOTS));
        packetStates.add(root.copy());
        root.set(JustDireResourceTestSupport.energyComponent(), 1_234);
        packetStates.add(root.copy());
        root.setDamageValue(37);
        packetStates.add(root.copy());
        root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        packetStates.add(root.copy());
        root.remove(DataComponents.UNBREAKABLE);
        packetStates.add(root.copy());
        root.setDamageValue(0);
        packetStates.add(root.copy());
        root.set(JustDireResourceTestSupport.energyComponent(), 10_000);
        packetStates.add(root.copy());
        root.setDamageValue(37);
        packetStates.add(root.copy());
        root.set(JustDireResourceTestSupport.energyComponent(), 0);
        packetStates.add(root.copy());
        root.setDamageValue(0);
        packetStates.add(root.copy());
    }

    private static void sendPacketState(Minecraft client) {
        var expected = packetStates.get(packetIndex).copy();
        int index = packetIndex;
        var ownerId = client.player.getUUID();
        var server = client.getSingleplayerServer();
        require(server != null, "Integrated server missing");
        job = server.submit(() -> {
            var owner = server.getPlayerList().getPlayer(ownerId);
            if (index == 0) {
                owner.getInventory().clearContent();
                owner.getInventory().setItem(0, expected);
            } else {
                var root = owner.getInventory().getItem(0);
                root.set(JustDireResourceTestSupport.energyComponent(), expected.get(JustDireResourceTestSupport.energyComponent()));
                root.setDamageValue(expected.getDamageValue());
                if (expected.has(DataComponents.UNBREAKABLE)) {
                    root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                } else {
                    root.remove(DataComponents.UNBREAKABLE);
                }
            }
            owner.inventoryMenu.broadcastChanges();
        });
    }

    private static void queryFailure(Minecraft client) {
        var root = mixed(nativeBoot("celestigem", 6_000), new ItemStack(Items.DIAMOND_BOOTS));
        root.setDamageValue(37);
        inspect(client, root, 3);
        armed = true;
        for (int repeat = 0; repeat < 3; repeat++) {
            inspect(client, root, -1);
        }
        require(queryFaults == 1, "Formatting fault was not reached exactly once: " + queryFaults);
        var handler = JustDireResourceTestSupport.handler(root);
        require(handler != null && handler.getCapacityAsInt() == 10_000 && handler.getAmountAsInt() == 3_000
                        && JustDireResourcesCompatibility.energyBarReceiver(root) != null, "Bar failure disabled storage/metadata");
        try (var transaction = Transaction.openRoot()) {
            require(handler.extract(100, transaction) == 100, "Bar failure blocked an actual FE payment");
            transaction.commit();
        }
        require(handler.getAmountAsInt() == 2_900 && root.get(JustDireResourceTestSupport.energyComponent()) == 2_900,
                "Committed FE debit did not reach the live root after bar refusal");
        inspect(client, root, -1);
        inspect(client, nativeBoot("celestigem", 2_000), -1);
        inspect(client, mixed(nativeBoot("eclipsealloy", 8_000), new ItemStack(Items.DIAMOND_BOOTS)), -1);
        require(queryFaults == 1, "Latched renderer retried failed native queries");
        report.append("One native ")
              .append(VARIANT)
              .append(" fault, bounded presentation-only refusal; actual FE payment/metadata/native original intact; no partial FE submission\n");
    }

    private static void inspect(Minecraft client, ItemStack root, int feWidth) {
        ItemStack before = root.copy();
        var expected = new ArrayList<Rectangle>();
        if (root.isBarVisible()) {
            expected.add(new Rectangle(33, 13, 2, 0xFF000000));
            expected.add(new Rectangle(33, root.getBarWidth(), 1, 0xFF000000 | root.getBarColor()));
        }
        if (feWidth >= 0) {
            int row = root.isBarVisible() ? 30 : 33;
            expected.add(new Rectangle(row, 13, 2, 0xFF000000));
            expected.add(new Rectangle(row, feWidth, 1, 0xFF40CFFF));
        }
        var state = new GuiRenderState();
        new GuiGraphicsExtractor(client, state, 0, 0).itemDecorations(client.font, root, 20, 20);
        var actual = new ArrayList<Rectangle>();
        state.forEachElement(element -> {
            if (element instanceof ColoredRectangleRenderState rect) {
                var bounds = rect.bounds();
                require(bounds.left() == 22 && rect.scissorArea() == null, "Decorator changed position/scissor");
                actual.add(new Rectangle(bounds.top(), bounds.width(), bounds.height(), rect.col1()));
            }
        }, GuiRenderState.TraverseRange.ALL);
        require(actual.size() == expected.size() && actual.containsAll(expected), "Actual production rectangles " + actual + " != " + expected);
        require(ItemStack.matches(before, root), "Decoration mutated root/source state");
        checks++;
    }

    private record Rectangle(int row, int width, int height, int color) {
    }

    private static boolean faultVariant() {
        return List.of("queryFailure", "invalidWidth", "linkageFailure").contains(VARIANT);
    }

    private static int expectedWidth(ItemStack root, int capacity) {
        int charge = root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0);
        return VARIANT.equals("live") && charge < capacity ? 13 * charge / capacity : -1;
    }

    private static ItemStack nativeBoot(String tier, int charge) {
        return NeoForgeJustDireResourcesTest.boots(tier, charge);
    }

    private static ItemStack mixed(ItemStack a, ItemStack b) {
        return MixedBootsFactory.createPair(a, b).primary();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("justdirethings", path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
