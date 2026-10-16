package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.blaze3d.platform.NativeImage;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** GPU proof of the registered production decorator, using undecorated model pixels as references. */
final class JustDireItemBarRenderingTest {
    private static final int BACKGROUND = 0xFF202020;
    private static final StringBuilder report = new StringBuilder();
    private static Scene scene;
    private static int scale = 1, stage, waited, targetFrame;
    private static boolean pending, captured, done;
    private static Throwable failure;

    private JustDireItemBarRenderingTest() {}

    static boolean tick(Minecraft client) {
        if (failure != null) { throw new AssertionError("Production item bar framebuffer proof failed", failure); }
        if (done) { return true; }
        if (scene == null) {
            client.options.guiScale().set(scale); client.resizeGui();
            scene = new Scene(); client.setScreen(scene);
            require(client.getWindow().getGuiScale() == scale, "Requested GUI scale unavailable");
            waited = 0; targetFrame = 3;
        }
        if (captured) {
            captured = false;
            if (++stage == 3) {
                if (++scale == 3) { done = true; client.setScreen(null); return true; }
                scene = null; stage = 0;
                return false;
            }
            // Mutate the same displayed root: healthy -> worn -> repaired. No cached lane survives.
            scene.transition.setDamageValue(stage == 1 ? scene.transition.getMaxDamage() / 2 : 0);
            scene.snapshot(); targetFrame = scene.frames + 3; waited = 0;
        }
        if (pending || ++waited < 4 || scene.frames < targetFrame) { return false; }
        pending = true;
        Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
            try (image) {
                scene.checkPixels(image, scale);
                Path path = Path.of(System.getProperty("mismatchedboots.runtimetest.markers"),
                        "live-scale-" + scale + "-" + List.of("healthy", "worn", "repaired").get(stage) + ".png");
                Files.createDirectories(path.getParent()); image.writeToFile(path);
                report.append("GPU scale ").append(scale).append(" transition ").append(stage)
                        .append(": full-icon pixel oracle, dynamic FE/durability, native/vanilla, pose/scissor and following control\n");
                captured = true;
            } catch (Throwable caught) { failure = caught; }
            finally { pending = false; }
        });
        return false;
    }

    static String report() { return report + "Six real framebuffer captures in a synthetic screen; count override shown for visual review\n"; }

    private static final class Scene extends Screen {
        private final List<ItemStack> samples = new ArrayList<>();
        private final String[] labels = {"single FE", "dual bars", "full, new", "full, worn", "zero FE", "Unbreakable", "native", "vanilla"};
        private final ItemStack transition = mixed();
        private List<ItemStack> before;
        private int frames;

        private Scene() {
            super(Component.literal("Production JDT item bars"));
            for (int i = 0; i < labels.length; i++) {
                var root = mixed();
                if (i == 1 || i == 3 || i == 4 || i == 5) { root.setDamageValue(root.getMaxDamage() / 2); }
                if (i == 2 || i == 3) { root.set(JustDireResourceTestSupport.energyComponent(), 10_000); }
                if (i == 4) { root.set(JustDireResourceTestSupport.energyComponent(), 0); }
                if (i == 5) { root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE); }
                if (i == 6) { root = NeoForgeJustDireResourcesTest.boots("celestigem", 3_000); }
                if (i == 7) { root = new ItemStack(Items.DIAMOND_BOOTS); root.setDamageValue(root.getMaxDamage() / 2); }
                samples.add(root);
            }
            snapshot();
        }

        private void snapshot() {
            before = new ArrayList<>(samples.stream().map(ItemStack::copy).toList());
            before.add(transition.copy());
        }

        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(0, 0, width, height, BACKGROUND);
        }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            frames++;
            graphics.text(font, "Production JDT bars, scale " + scale + ", transition " + stage, 12, 10, 0xFFFFFFFF, false);
            for (int i = 0; i < samples.size(); i++) {
                int x = 20 + i * 76;
                graphics.text(font, labels[i], x, 32, 0xFFFFFFFF, false);
                decorated(graphics, samples.get(i), x, 48);
                graphics.item(samples.get(i), x, 80);
            }
            graphics.text(font, "Undecorated references above; same-root transition below", 12, 112, 0xFFCCCCCC, false);
            graphics.text(font, "transition", 20, 140, 0xFFFFFFFF, false);
            decorated(graphics, transition, 20, 160); graphics.item(transition, 64, 160);
            graphics.text(font, "pose+clip", 220, 140, 0xFFFFFFFF, false);
            graphics.pose().pushMatrix(); graphics.pose().translate(20, 0);
            graphics.enableScissor(202, 160, 212, 176);
            decorated(graphics, samples.get(1), 200, 160);
            graphics.disableScissor(); graphics.pose().popMatrix();
            graphics.text(font, "reference", 280, 140, 0xFFFFFFFF, false);
            graphics.item(samples.get(1), 280, 160);
            graphics.text(font, "following", 360, 140, 0xFFFFFFFF, false);
            decorated(graphics, samples.get(1), 360, 160);
            graphics.text(font, "count override", 440, 140, 0xFFFFFFFF, false);
            graphics.item(samples.get(1), 440, 160);
            graphics.itemDecorations(font, samples.get(1), 440, 160, "2");
        }

        private void decorated(GuiGraphicsExtractor graphics, ItemStack root, int x, int y) {
            graphics.item(root, x, y); graphics.itemDecorations(font, root, x, y);
        }

        private void checkPixels(NativeImage image, int guiScale) {
            for (int i = 0; i < samples.size(); i++) {
                int x = 20 + i * 76;
                icon(image, guiScale, samples.get(i), x, 48, x, 80, false);
                require(ItemStack.matches(before.get(i), samples.get(i)), "Model/decorator extraction mutated a sample");
            }
            icon(image, guiScale, transition, 20, 160, 64, 160, false);
            icon(image, guiScale, samples.get(1), 220, 160, 280, 160, true);
            icon(image, guiScale, samples.get(1), 360, 160, 280, 160, false);
            require(ItemStack.matches(before.getLast(), transition), "Rendering changed the transitioning root");
        }
    }

    private static void icon(NativeImage image, int scale, ItemStack root, int x, int y, int refX, int refY, boolean clipped) {
        boolean standard = root.isBarVisible();
        int standardWidth = standard ? root.getBarWidth() : 0;
        int standardColor = standard ? 0xFF000000 | root.getBarColor() : 0;
        int charge = ModContent.isMixedBoots(root) ? root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0) : 10_000;
        for (int dy = -2; dy < 18; dy++) {
            for (int dx = -2; dx < 18; dx++) {
                // Inspect every physical pixel, not just the first pixel of each scaled GUI cell.
                for (int sy = 0; sy < scale; sy++) {
                    for (int sx = 0; sx < scale; sx++) {
                        int expected = dx < 0 || dx >= 16 || dy < 0 || dy >= 16 ? BACKGROUND
                                : image.getPixel((refX + dx) * scale + sx, (refY + dy) * scale + sy);
                        if (dx >= 2 && dx < 15) {
                            if (standard) { expected = bar(expected, dx, dy, 13, standardWidth, standardColor); }
                            if (charge < 10_000) { expected = bar(expected, dx, dy, standard ? 10 : 13, 13 * charge / 10_000, 0xFF40CFFF); }
                        }
                        if (clipped && (dx < 2 || dx >= 12 || dy < 0 || dy >= 16)) { expected = BACKGROUND; }
                        int actual = image.getPixel((x + dx) * scale + sx, (y + dy) * scale + sy);
                        require(actual == expected, "GPU icon " + x + "," + y + " pixel " + dx + "," + dy + " scale " + scale
                                + ": " + Integer.toHexString(actual) + " != " + Integer.toHexString(expected));
                    }
                }
            }
        }
    }

    private static int bar(int underlying, int dx, int dy, int row, int width, int color) {
        if (dy == row) { return dx < 2 + width ? color : 0xFF000000; }
        return dy == row + 1 ? 0xFF000000 : underlying;
    }
    private static ItemStack mixed() {
        var root = MixedBootsFactory.createPair(NeoForgeJustDireResourcesTest.boots("celestigem", 6_000), new ItemStack(Items.DIAMOND_BOOTS)).primary();
        require(JustDireResourceTestSupport.handler(root).getCapacityAsInt() == 10_000, "Unexpected visual fixture capacity");
        return root;
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
