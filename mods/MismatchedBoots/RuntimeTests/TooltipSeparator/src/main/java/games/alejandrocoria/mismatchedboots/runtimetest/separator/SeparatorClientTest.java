package games.alejandrocoria.mismatchedboots.runtimetest.separator;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import games.alejandrocoria.mismatchedboots.tooltip.TooltipSeparator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Production presentation checks with a read-only rendering witness, not paid gameplay coverage. */
public final class SeparatorClientTest {
    private static final StringBuilder REPORT = new StringBuilder();
    private static Scene scene;
    private static List<ItemStack> roots;
    private static ItemStack bundle;
    private static ItemStack bundleBefore;
    private static int ticks;
    private static int phase;
    private static int compactWidth;
    private static int wideWidth;
    private static boolean finished;
    private static boolean screenshotPending;
    private static boolean screenshotDone;
    private static Throwable screenshotFailure;

    private SeparatorClientTest() {
    }

    public static void tick(Minecraft client) {
        if (finished) {
            return;
        }

        try {
            require(++ticks < 2400, "Separator client timed out at phase " + phase);
            if (client.screen instanceof BackupConfirmScreen backup) {
                backup.children().stream().filter(c -> c instanceof Button b && b.getMessage().equals(
                              Component.translatable("selectWorld.backupJoinSkipButton")))
                      .map(Button.class::cast).findFirst().orElseThrow().onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                return;
            }
            if (client.player == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) {
                return;
            }
            if (roots == null) {
                require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                                && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                        "Separator acceptance must be hidden and muted");
                client.options.pauseOnLostFocus = false;
                client.options.guiScale().set(2);
                client.resizeGui();
                var pair = new MixedBootsWorkbenchRecipe().assemblePair(new MismatchedWorkbenchInput(
                        new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.GOLDEN_BOOTS)));
                require(pair.isComplete(), "Recipe fixture rejected");
                ItemStack wide = pair.inverse();
                wide.set(DataComponents.CUSTOM_NAME, Component.literal("Separator width and wrapping probe"));
                wide.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(
                        "Wrapped root details preserve their native layout. ".repeat(7)))));
                var left = new ItemStack(Items.LEATHER_BOOTS);
                left.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Left source detail 1"),
                                                                   Component.literal("Left source detail 2"),
                                                                   Component.literal("Left source detail 3"))));
                var right = new ItemStack(Items.IRON_BOOTS);
                right.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Right source detail 1"),
                                                                    Component.literal("Right source detail 2"))));
                var multilinePair = new MixedBootsWorkbenchRecipe().assemblePair(new MismatchedWorkbenchInput(left, right));
                require(multilinePair.isComplete(), "Multiline source fixture rejected");
                var multiline = multilinePair.primary();
                multiline.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Root details survive after sources"))));
                bundle = new ItemStack(Items.BUNDLE);
                bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStackTemplate(Items.APPLE, 12),
                                                                                      new ItemStackTemplate(Items.DIAMOND, 3))));
                bundleBefore = bundle.copy();
                roots = List.of(pair.primary(), wide, new ItemStack(Items.DIAMOND_BOOTS), wide, multiline, multiline);
                configure(client);
                return;
            }
            if (screenshotFailure != null) {
                throw new AssertionError("Screenshot failed", screenshotFailure);
            }
            if (scene.frames < 8) {
                return;
            }

            boolean plainFabric = System.getProperty("mismatchedboots.test.separatorLoader", "").equals("Fabric")
                    && !Boolean.getBoolean("mismatchedboots.test.separatorDecorated");
            if (phase == 5 && Boolean.getBoolean("mismatchedboots.test.separatorCapture") && !screenshotDone) {
                if (!screenshotPending) {
                    screenshotPending = true;
                    Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                        try (image) {
                            image.writeToFile(Path.of(System.getProperty("mismatchedboots.runtimetest.markers"), "separator.png"));
                            screenshotDone = true;
                        } catch (Throwable failure) {
                            screenshotFailure = failure;
                        }
                    });
                }
                return;
            }
            require(ItemStack.matches(scene.before, scene.root), "Tooltip changed root/history");
            require(ItemStack.matches(bundleBefore, bundle), "Native graphic changed its bundle input");
            require(scene.text.equals(scene.root.getTooltipLines(Item.TooltipContext.of(client.level), client.player,
                                                                 client.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL)
                                                .stream().map(Component::getString).toList()), "Rendering changed tooltip text/order");
            if (phase == 2 || phase == 4) {
                require(SeparatorObservations.WIDTHS.isEmpty(), "Ordinary item/copied-text fallback acquired a separator");
            } else {
                require(SeparatorObservations.WIDTHS.size() >= 2, "Graphical separator lost during conversion/wrapping");
                require(SeparatorObservations.WIDTHS.size() % 2 == 0, "One of the two separator rows was lost");
                require(SeparatorObservations.WIDTHS.stream().distinct().count() == 1, "Rows received different tooltip widths");
                int width = SeparatorObservations.WIDTHS.getFirst();
                require(width > 0, "No production tooltip width");
                if (phase == 0) {
                    compactWidth = width;
                }
                if (phase == 1) {
                    wideWidth = width;
                    require(width > compactWidth, "Separator width did not adapt to content");
                }
                if (phase == 3 && !plainFabric) {
                    require(width < wideWidth, "Separator width did not adapt after wrapping/resizing");
                }
            }
            if (phase == 5) {
                var draws = SeparatorObservations.DRAWS;
                require(draws.size() >= 3, "Native bundle graphic was lost");
                var last = draws.subList(draws.size() - 3, draws.size());
                require(!last.get(0).separator() && last.get(1).separator() && last.get(2).separator(),
                        "Native graphic/separator order changed");
                require(last.get(0).y() + last.get(0).height() <= last.get(1).y()
                                && last.get(1).y() + last.get(1).height() <= last.get(2).y(),
                        "Native graphic and source separator rows overlap");
            } else {
                require(SeparatorObservations.DRAWS.stream().allMatch(SeparatorObservations.Draw::separator), "Unexpected native graphic");
            }
            REPORT.append("phase=").append(phase).append(" width=")
                  .append(SeparatorObservations.WIDTHS.stream().findFirst().orElse(0))
                  .append(" productionRowsObserved=").append(SeparatorObservations.WIDTHS.size())
                  .append(" gui=").append(client.getWindow().getGuiScaledWidth()).append('x')
                  .append(client.getWindow().getGuiScaledHeight()).append('\n');
            if (++phase < roots.size()) {
                configure(client);
                return;
            }
            REPORT.append(
                    "PRODUCTION normal/inverse/advanced/native-control; advanced-only dark-gray source IDs below names, no duplicates; multiline source boundaries; copied-text fallback; native bundle order/non-overlap; immutable root/history/bundle\n");
            if (plainFabric) {
                REPORT.append(
                        "Stock Fabric retains its over-wide unwrapped stress lore; final multiline screenshot fits without changing native layout\n");
            }
            NetworkAcceptanceFiles.write("separator-report.txt", REPORT.toString());
            NetworkAcceptanceFiles.write("separator-success.txt", "passed\n");
            finished = true;
            client.stop();
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.write("separator-report.txt", REPORT.toString());
            NetworkAcceptanceFiles.failure("separator", failure);
            finished = true;
            client.stop();
        }
    }

    private static void configure(Minecraft client) {
        client.options.advancedItemTooltips = phase == 1 || phase == 3 || phase == 5;
        var root = roots.get(phase);
        SeparatorObservations.input = root;
        SeparatorObservations.copyText = false;
        SeparatorObservations.image = Optional.empty();
        var marked = root.getTooltipLines(Item.TooltipContext.of(client.level), client.player,
                                          client.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL);
        require(marked.stream().filter(TooltipSeparator.class::isInstance).count() == (phase == 2 ? 0 : 2), "Wrong production source boundaries");
        if (phase != 2) {
            var data = ModContent.getMixedBootsData(root).orElseThrow();
            var sources = List.of(data.left().source(), data.right().source());
            int[] headers = {1, marked.indexOf(TooltipSeparator.INSTANCE) + 1};
            for (int side = 0; side < sources.size(); side++) {
                Component id = Component.literal(BuiltInRegistries.ITEM.getKey(sources.get(side).getItem()).toString())
                                        .withStyle(ChatFormatting.DARK_GRAY);
                require(marked.stream().filter(id::equals).count() == (client.options.advancedItemTooltips ? 1 : 0),
                        "Missing, duplicated or non-advanced source ID: " + id.getString());
                if (client.options.advancedItemTooltips) {
                    require(marked.get(headers[side] + 1).equals(id), "Source ID not directly below its name");
                }
            }
        }
        if (phase >= 4) {
            int first = marked.indexOf(TooltipSeparator.INSTANCE);
            int second = marked.lastIndexOf(TooltipSeparator.INSTANCE);
            require(index(marked, "Left source detail 3") < first && first < index(marked, "Right source detail 1")
                            && index(marked, "Right source detail 2") < second && second < index(marked, "Root details survive after sources"),
                    "Multiline source/root boundaries changed");
        }
        if (phase == 4) {
            SeparatorObservations.copyText = true;
            var copied = root.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL);
            require(copied.stream().noneMatch(TooltipSeparator.class::isInstance), "External text copy retained marker identity");
            require(marked.stream().map(Component::getString).toList().equals(copied.stream().map(Component::getString).toList()),
                    "Copied-text fallback lost/reordered source or root information");
            marked = copied;
        }
        if (phase == 5) {
            SeparatorObservations.image = Optional.of(bundle.getTooltipImage().orElseThrow());
        }
        SeparatorObservations.WIDTHS.clear();
        SeparatorObservations.DRAWS.clear();
        if (phase == 3) {
            GLFW.glfwSetWindowSize(client.getWindow().handle(), 1280, 720);
        }
        if (phase == 5) {
            GLFW.glfwSetWindowSize(client.getWindow().handle(), 1920, 1080);
        }
        scene = new Scene(root, marked.stream().map(Component::getString).toList());
        client.setScreen(scene);
    }

    private static int index(List<Component> lines, String text) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getString().equals(text)) {
                return i;
            }
        }
        throw new AssertionError("Missing source/root detail: " + text);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Scene extends Screen {
        private final ItemStack root;
        private final ItemStack before;
        private final List<String> text;
        private int frames;

        private Scene(ItemStack root, List<String> text) {
            super(Component.literal("Production separators"));
            this.root = root;
            before = root.copy();
            this.text = text;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) {
            g.fill(0, 0, width, height, 0xFF202020);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
            frames++;
            g.text(font, "Production solid separators / native tooltip pipeline", 14, 10, 0xFFFFFFFF, false);
            g.setTooltipForNextFrame(font, root, 18, 45);
        }
    }
}
