package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.blaze3d.platform.NativeImage;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/** Opt-in research only: one real decorator, production capability, native public bar queries. */
final class NeoForgeJustDireItemBarsSpike {
    private static final String OUTPUT = "just-dire-item-bars";
    private static final String VARIANT = System.getProperty("mismatchedboots.runtimetest.justDireItemBarsVariant", "live");
    private static final int CYAN = 0xFF40CFFF;
    private static final StringBuilder report = new StringBuilder();
    private static int lane = 11, ticks, phase, waiting;
    private static boolean cyan, finished, capturing;
    private static Future<?> job;
    private static ItemStack packetExpected;
    private static ComparisonScreen screen;

    static void register(IEventBus bus) {
        bus.addListener((RegisterItemDecorationsEvent event) -> event.register(
                BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID), NeoForgeJustDireItemBarsSpike::decorate));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
    }

    // Deliberately local to the spike: production can reuse its audited metadata instead
    // of maintaining another boot-identity table. The root capability is the validity gate.
    private static Item receiver(ItemStack root) {
        if (!ModContent.isMixedBoots(root)) { return null; }
        var handler = JustDireResourceTestSupport.handler(root);
        if (handler == null || handler.getCapacityAsInt() <= 0) { return null; }
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        for (var source : List.of(data.left().source(), data.right().source())) {
            var id = BuiltInRegistries.ITEM.getKey(source.getItem());
            if (id.equals(id("celestigem_boots")) || id.equals(id("eclipsealloy_boots"))) { return source.getItem(); }
        }
        return null;
    }

    private static boolean decorate(GuiGraphicsExtractor graphics, Font font, ItemStack root, int x, int y) {
        Item nativeItem = receiver(root);
        if (nativeItem == null || !nativeItem.isBarVisible(root)) { return false; }
        int width = nativeItem.getBarWidth(root), color = nativeItem.getBarColor(root);
        require(width >= 0 && width <= 13 && color >= 0, "Unsupported native bar query");
        graphics.fill(x + 2, y + lane, x + 15, y + lane + 2, 0xFF000000);
        graphics.fill(x + 2, y + lane, x + 2 + width, y + lane + 1, cyan ? CYAN : 0xFF000000 | color);
        return true;
    }

    private static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 2_400, "Spike timed out at phase " + phase);
            if (client.player == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) { return; }
            client.options.pauseOnLostFocus = false;
            if (job != null) { if (!job.isDone()) { return; } job.get(); job = null; }
            if (phase == 0) {
                matrix(client);
                if (phase == 3) { return; }
                var ownerId = client.player.getUUID();
                packetExpected = mixed(nativeBoot("celestigem", 6_000), new ItemStack(Items.DIAMOND_BOOTS));
                job = client.getSingleplayerServer().submit(() -> {
                    var owner = client.getSingleplayerServer().getPlayerList().getPlayer(ownerId);
                    owner.getInventory().clearContent();
                    owner.getInventory().setItem(0, packetExpected.copy()); owner.inventoryMenu.broadcastChanges();
                });
                phase = 1;
            } else if (phase == 1) {
                if (!ItemStack.matches(packetExpected, client.player.getInventory().getItem(0))) { return; }
                inspect(client, client.player.getInventory().getItem(0), !VARIANT.equals("live"));
                packetExpected.set(JustDireResourceTestSupport.energyComponent(), 1_234);
                var ownerId = client.player.getUUID();
                job = client.getSingleplayerServer().submit(() -> {
                    var owner = client.getSingleplayerServer().getPlayerList().getPlayer(ownerId);
                    owner.getInventory().getItem(0).set(JustDireResourceTestSupport.energyComponent(), 1_234);
                    owner.inventoryMenu.broadcastChanges();
                });
                phase = 2;
            } else if (phase == 2) {
                if (!ItemStack.matches(packetExpected, client.player.getInventory().getItem(0))) { return; }
                inspect(client, client.player.getInventory().getItem(0), !VARIANT.equals("live"));
                report.append(VARIANT.equals("live")
                        ? "Ordinary slot packets: initial/replaced root charge reflected without source mutation or bar cache\n"
                        : "Ordinary slot packets: root charge synchronizes; refused storage still adds no FE bar\n");
                phase = 3;
            } else if (phase == 3 || phase == 5) {
                int scale = phase == 3 ? 1 : 2;
                client.options.guiScale().set(scale); client.resizeGui();
                screen = new ComparisonScreen(); client.setScreen(screen);
                waiting = 0; phase++;
            } else if (phase == 4 || phase == 6) {
                if (++waiting < 8 || capturing) { return; }
                capturing = true;
                int scale = (int) client.getWindow().getGuiScale();
                Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                    try (image) {
                        screen.checkPixels(image, scale);
                        Path path = Path.of(System.getProperty("mismatchedboots.runtimetest.markers"), VARIANT + "-scale-" + scale + ".png");
                        Files.createDirectories(path.getParent()); image.writeToFile(path);
                        report.append("GPU framebuffer: scale ").append(scale).append(VARIANT.equals("live")
                                ? "; four candidates, both actual bar rows and clipping checked\n"
                                : "; fallback durability pixels checked; deferred matrix refuses FE\n");
                        phase++; waiting = 0;
                    } catch (Throwable failure) { fail(client, failure); }
                    finally { capturing = false; }
                });
            } else if (phase == 7 && ++waiting > 20) {
                NetworkAcceptanceFiles.write(OUTPUT + "-report.txt", report + "Synthetic comparison screen, not owner visual acceptance or interactive screen coverage\n");
                NetworkAcceptanceFiles.write(OUTPUT + "-success.txt", VARIANT + "\n");
                finished = true; client.stop();
            }
        } catch (Throwable failure) { fail(client, failure); }
    }

    private static void matrix(Minecraft client) {
        if (VARIANT.equals("absent")) {
            require(BuiltInRegistries.ITEM.getOptional(id("celestigem_boots")).isEmpty(), "JDT was not absent");
            inspect(client, mixed(new ItemStack(Items.GOLDEN_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)), true);
            report.append("Quiet optional absence: mixed vanilla durability only\n");
            // No native component lookup, packet or powered fixture is valid in this JVM.
            phase = 3;
            return;
        }
        int cases = 0;
        for (String tier : List.of("celestigem", "eclipsealloy")) {
            for (boolean secondPowered : List.of(false, true)) {
                var pair = MixedBootsFactory.createPair(nativeBoot(tier, 6_000),
                        secondPowered ? nativeBoot("eclipsealloy", 80_000) : new ItemStack(Items.DIAMOND_BOOTS));
                for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
                    int capacity = tier.equals("eclipsealloy") || secondPowered ? 500_000 : 10_000;
                    for (int charge : new int[]{0, 1, capacity / 3, capacity}) {
                        root.set(JustDireResourceTestSupport.energyComponent(), charge);
                        for (boolean damaged : List.of(false, true)) {
                            root.setDamageValue(damaged ? root.getMaxDamage() / 3 : 0);
                            for (int row : new int[]{11, 10}) {
                                lane = row; inspect(client, root, !VARIANT.equals("live")); cases++;
                            }
                        }
                    }
                    root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                    require(!root.isBarVisible(), "Unbreakable root showed vanilla durability");
                    inspect(client, root, !VARIANT.equals("live"));
                }
            }
        }
        ItemStack root = mixed(nativeBoot("celestigem", 4_000), new ItemStack(Items.DIAMOND_BOOTS));
        root.setDamageValue(37);
        root.remove(JustDireResourceTestSupport.energyComponent()); inspect(client, root, !VARIANT.equals("live"));
        root.set(JustDireResourceTestSupport.energyComponent(), -1); inspect(client, root, true);
        root.set(JustDireResourceTestSupport.energyComponent(), 10_001); inspect(client, root, true);
        root.set(JustDireResourceTestSupport.energyComponent(), 0); root.setCount(2); inspect(client, root, true);
        root.setCount(1); root.remove(ModContent.mixedBootsDataType().orElseThrow()); inspect(client, root, true);
        for (ItemStack control : List.of(new ItemStack(Items.DIAMOND_BOOTS), nativeBoot("ferricore", 0), nativeBoot("celestigem", 3_000))) {
            control.setDamageValue(25); inspect(client, control, true);
        }
        report.append(cases).append(" mixed cases: tiers/orientations/provider count/charge/damage/lanes; ")
                .append(VARIANT.equals("live") ? "native public queries match live root capacity\n" : "missing storage adds no FE bar; durability retained\n")
                .append("Unbreakable, absent charge, invalid charge/count/provenance; vanilla/passive/native powered controls; all reads preserve whole stacks\n");
    }

    private static void inspect(Minecraft client, ItemStack root, boolean noFe) {
        ItemStack before = root.copy();
        var state = new GuiRenderState();
        var graphics = new GuiGraphicsExtractor(client, state, 0, 0);
        graphics.itemDecorations(client.font, root, 20, 20);
        List<ColoredRectangleRenderState> rectangles = new ArrayList<>();
        state.forEachElement(element -> { if (element instanceof ColoredRectangleRenderState rect) { rectangles.add(rect); } }, GuiRenderState.TraverseRange.ALL);
        Item nativeItem = receiver(root);
        require(!noFe || nativeItem == null, "Refused root acquired FE view");
        boolean fe = nativeItem != null && nativeItem.isBarVisible(root);
        int expected = (root.isBarVisible() ? 2 : 0) + (fe ? 2 : 0);
        require(rectangles.size() == expected, "Unexpected real decoration rectangles: " + rectangles + "; expected " + expected);
        if (nativeItem != null) {
            var handler = JustDireResourceTestSupport.handler(root);
            require(nativeItem.isBarVisible(root) == (handler.getAmountAsInt() < handler.getCapacityAsInt())
                    && nativeItem.getBarWidth(root) == 13 * handler.getAmountAsInt() / handler.getCapacityAsInt(), "Native receiver used source/tier capacity instead of root");
        }
        for (var rect : rectangles) {
            // fill normalizes vertex order; bounds describe the actual GUI rectangle,
            // including a zero-width native FE fill which remains in deferred state.
            var bounds = rect.bounds();
            require(bounds.left() == 22 && (bounds.top() == 33 || bounds.top() == 20 + lane), "Bar escaped its lane");
            boolean durability = bounds.top() == 33, backing = bounds.height() == 2;
            int width = backing ? 13 : durability ? root.getBarWidth() : nativeItem.getBarWidth(root);
            int color = backing ? 0xFF000000 : durability ? 0xFF000000 | root.getBarColor()
                    : cyan ? CYAN : 0xFF000000 | nativeItem.getBarColor(root);
            require(bounds.width() == width && bounds.height() == (backing ? 2 : 1) && rect.col1() == color,
                    "Submitted bar geometry/color differs from native queries: " + rect);
        }
        require(ItemStack.matches(before, root), "Read-only decoration mutated root/source state");
    }

    private static final class ComparisonScreen extends Screen {
        private final List<ItemStack> samples = new ArrayList<>();
        private final String[] labels = {"damaged", "healthy", "zero FE", "full FE", "Unbreak.", "native", "count=2"};
        private int frames;

        private ComparisonScreen() {
            super(Component.literal("Item bar research"));
            for (int i = 0; i < labels.length; i++) {
                ItemStack root = VARIANT.equals("absent") ? mixed(new ItemStack(Items.GOLDEN_BOOTS), new ItemStack(Items.DIAMOND_BOOTS))
                        : mixed(nativeBoot("eclipsealloy", 100_000), new ItemStack(Items.DIAMOND_BOOTS));
                root.setDamageValue(i == 1 ? 0 : root.getMaxDamage() / 2);
                if (!VARIANT.equals("absent")) { root.set(JustDireResourceTestSupport.energyComponent(), i == 2 ? 0 : i == 3 ? 500_000 : 150_000); }
                if (i == 4) { root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE); }
                if (i == 5) { root = VARIANT.equals("absent") ? new ItemStack(Items.DIAMOND_BOOTS) : nativeBoot("eclipsealloy", 150_000); }
                samples.add(root);
            }
        }

        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(0, 0, width, height, 0xFF202020);
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            frames++;
            graphics.text(font, "JDT item bars: " + VARIANT + " (research only)", 12, 10, 0xFFFFFFFF, false);
            for (int row = 0; row < 4; row++) {
                lane = row % 2 == 0 ? 11 : 10; cyan = row >= 2;
                int y = 48 + row * 72;
                graphics.text(font, "FE y+" + lane + (cyan ? " cyan" : " native HSV"), 12, y - 16, 0xFFFFFFFF, false);
                for (int col = 0; col < samples.size(); col++) {
                    int x = 20 + col * 78;
                    graphics.text(font, labels[col], x, y + 20, 0xFFCCCCCC, false);
                    graphics.item(samples.get(col), x, y);
                    graphics.itemDecorations(font, samples.get(col), x, y, col == 6 ? "2" : null);
                }
            }
            // Real split-model extraction and caller scissor/pose, then an unaffected control.
            lane = 10; cyan = true;
            graphics.pose().pushMatrix(); graphics.pose().translate(20, 0);
            graphics.enableScissor(12, 330, 22, 346);
            graphics.item(samples.getFirst(), 10, 330);
            graphics.itemDecorations(font, samples.getFirst(), 10, 330);
            graphics.disableScissor(); graphics.pose().popMatrix();
            graphics.item(samples.getFirst(), 80, 330); graphics.itemDecorations(font, samples.getFirst(), 80, 330);
            graphics.text(font, "scissor/pose and clean following control", 112, 332, 0xFFCCCCCC, false);
        }

        private void checkPixels(NativeImage image, int scale) {
            require(frames >= 2, "Captured before comparison rendered");
            for (int row = 0; row < 4; row++) {
                int y = 48 + row * 72, offset = row % 2 == 0 ? 11 : 10;
                for (int col = 0; col < 6; col++) {
                    int x = 20 + col * 78;
                    ItemStack root = samples.get(col); Item nativeItem = receiver(root);
                    if (nativeItem != null && nativeItem.isBarVisible(root)) {
                        int w = nativeItem.getBarWidth(root);
                        pixel(image, scale, x + 2, y + offset, w == 0 ? 0xFF000000 : row >= 2 ? CYAN : 0xFF000000 | nativeItem.getBarColor(root));
                        pixel(image, scale, x + 14, y + offset + 1, 0xFF000000);
                    }
                    if (root.isBarVisible()) {
                        pixel(image, scale, x + 2, y + 13, 0xFF000000 | root.getBarColor());
                        pixel(image, scale, x + 14, y + 14, 0xFF000000);
                    }
                }
            }
            if (VARIANT.equals("live")) {
                pixel(image, scale, 32, 340, CYAN);
                pixel(image, scale, 42, 340, 0xFF202020);
                pixel(image, scale, 82, 340, CYAN);
            }
        }
    }

    private static void pixel(NativeImage image, int scale, int x, int y, int color) {
        require(image.getPixel(x * scale, y * scale) == color,
                "Framebuffer pixel " + x + "," + y + " scale " + scale + ": " + Integer.toHexString(image.getPixel(x * scale, y * scale)) + " != " + Integer.toHexString(color));
    }
    private static ItemStack nativeBoot(String tier, int charge) { return NeoForgeJustDireResourcesTest.boots(tier, charge); }
    private static ItemStack mixed(ItemStack a, ItemStack b) { return MixedBootsFactory.createPair(a, b).primary(); }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("justdirethings", path); }
    private static void fail(Minecraft client, Throwable failure) {
        NetworkAcceptanceFiles.failure(OUTPUT, failure); finished = true; client.stop();
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
