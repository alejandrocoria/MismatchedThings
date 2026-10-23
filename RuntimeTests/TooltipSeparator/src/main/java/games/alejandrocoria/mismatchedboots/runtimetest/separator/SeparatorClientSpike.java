package games.alejandrocoria.mismatchedboots.runtimetest.separator;

import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;

/** Isolated presentation experiment: recipe outputs, not evidence of paid gameplay. */
public final class SeparatorClientSpike {
    private static final StringBuilder REPORT = new StringBuilder();
    private static Scene scene;
    private static List<ItemStack> roots;
    private static int ticks, phase;
    private static int compactWidth, wideWidth;
    private static boolean finished, screenshotPending, screenshotDone;
    private static Throwable screenshotFailure;

    private SeparatorClientSpike() {}

    public static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 2400, "Separator client timed out at phase " + phase);
            if (client.screen instanceof BackupConfirmScreen backup) {
                backup.children().stream().filter(c -> c instanceof Button b && b.getMessage().equals(
                        Component.translatable("selectWorld.backupJoinSkipButton")))
                        .map(Button.class::cast).findFirst().orElseThrow().onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                return;
            }
            if (client.player == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) { return; }
            if (roots == null) {
                require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                        && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                        "Spike must be hidden and muted");
                client.options.pauseOnLostFocus = false;
                client.options.guiScale().set(2); client.resizeGui();
                var pair = new MixedBootsWorkbenchRecipe().assemblePair(new MismatchedWorkbenchInput(
                        new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.GOLDEN_BOOTS)));
                require(pair.isComplete(), "Recipe fixture rejected");
                ItemStack wide = pair.inverse();
                wide.set(DataComponents.CUSTOM_NAME, Component.literal("Separator width and wrapping probe"));
                wide.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(
                        "Wrapped root details preserve their native layout. ".repeat(7)))));
                roots = List.of(pair.primary(), wide, new ItemStack(Items.DIAMOND_BOOTS), wide);
                configure(client); return;
            }
            if (screenshotFailure != null) { throw new AssertionError("Screenshot failed", screenshotFailure); }
            if (scene.frames < 8) { return; }
            boolean plainFabric = System.getProperty("mismatchedboots.test.separatorLoader", "").equals("Fabric")
                    && !Boolean.getBoolean("mismatchedboots.test.separatorDecorated");
            if (phase == (plainFabric ? 0 : 3) && !screenshotDone) {
                if (!screenshotPending) {
                    screenshotPending = true;
                    Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                        try (image) {
                            image.writeToFile(Path.of(System.getProperty("mismatchedboots.runtimetest.markers"), "separator.png"));
                            screenshotDone = true;
                        } catch (Throwable failure) { screenshotFailure = failure; }
                    });
                }
                return;
            }
            require(ItemStack.matches(scene.before, scene.root), "Tooltip changed root/history");
            if (phase == 2) { require(SeparatorLine.WIDTHS.isEmpty(), "Native control acquired a separator"); }
            else {
                require(SeparatorLine.WIDTHS.size() >= 2, "Graphical separator lost during conversion/wrapping");
                require(SeparatorLine.WIDTHS.size() % 2 == 0, "One of the two separator rows was lost");
                require(SeparatorLine.WIDTHS.stream().distinct().count() == 1, "Rows received different tooltip widths");
                int width = SeparatorLine.WIDTHS.getFirst();
                if (phase == 0) { compactWidth = width; }
                if (phase == 1) { wideWidth = width; require(width > compactWidth, "Separator width did not adapt to content"); }
                if (phase == 3 && !plainFabric) { require(width < wideWidth, "Separator width did not adapt after wrapping/resizing"); }
            }
            REPORT.append("phase=").append(phase).append(" width=")
                    .append(SeparatorLine.WIDTHS.stream().findFirst().orElse(0))
                    .append(" rowsDrawn=").append(SeparatorLine.WIDTHS.size())
                    .append(" preservedWraps=").append(SeparatorLine.preservedSplits)
                    .append(" gui=").append(client.getWindow().getGuiScaledWidth()).append('x')
                    .append(client.getWindow().getGuiScaledHeight()).append('\n');
            if (++phase < roots.size()) { configure(client); return; }
            REPORT.append("normal/inverse/advanced/native-control; renderer-owned width; text unchanged; root/history immutable; color=FF707070; thickness=1\n");
            if (plainFabric) { REPORT.append("Stock Fabric retains its over-wide unwrapped lore; compact screenshot only, no native overflow fix\n"); }
            NetworkAcceptanceFiles.write("separator-report.txt", REPORT.toString());
            NetworkAcceptanceFiles.write("separator-success.txt", "passed\n");
            finished = true; client.stop();
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.write("separator-report.txt", REPORT.toString());
            NetworkAcceptanceFiles.failure("separator", failure); finished = true; client.stop();
        }
    }

    private static void configure(Minecraft client) {
        client.options.advancedItemTooltips = phase == 1 || phase == 3;
        var root = roots.get(phase);
        // Compare the real formatter's marker path with its untouched text output.
        SeparatorMarker.inserting = false;
        List<Component> baseline;
        try { baseline = root.getTooltipLines(Item.TooltipContext.of(client.level), client.player,
                client.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL); }
        finally { SeparatorMarker.inserting = true; }
        var marked = root.getTooltipLines(Item.TooltipContext.of(client.level), client.player,
                client.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL);
        require(baseline.stream().map(Component::getString).toList().equals(marked.stream().map(Component::getString).toList()),
                "Marker changed tooltip text/order");
        require(marked.stream().filter(SeparatorMarker.class::isInstance).count() == (phase == 2 ? 0 : 2), "Wrong source boundaries");
        SeparatorLine.WIDTHS.clear(); SeparatorLine.preservedSplits = 0;
        if (phase == 3) { GLFW.glfwSetWindowSize(client.getWindow().handle(), 1280, 720); }
        scene = new Scene(root); client.setScreen(scene);
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    private static final class Scene extends Screen {
        private final ItemStack root, before;
        private int frames;
        private Scene(ItemStack root) { super(Component.literal("Separator spike")); this.root = root; before = root.copy(); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) { g.fill(0, 0, width, height, 0xFF202020); }
        @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
            frames++;
            g.text(font, "Test-only solid separator / native tooltip pipeline", 14, 10, 0xFFFFFFFF, false);
            g.setTooltipForNextFrame(font, root, 18, 45);
        }
    }
}
