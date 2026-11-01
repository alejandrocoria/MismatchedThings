package games.alejandrocoria.mismatchedleggings.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class PresentationClient {
    private static int ticks;
    private static int stage;
    private static TooltipScene scene;
    private static volatile boolean captured;
    private static volatile Throwable captureFailure;

    private PresentationClient() {
    }

    static void tick(Minecraft client) {
        if (stage == 3) { return; }
        try {
            require(++ticks < 1800, "Presentation client timed out");
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated tooltip window became visible");
            if (stage == 0 && client.level != null && client.player != null && client.screen == null && client.getOverlay() == null) {
                scene = new TooltipScene(PresentationChecks.verify(client));
                client.setScreen(scene);
                stage = 1;
            } else if (stage == 1 && scene.frames >= 12) {
                Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                    try (image) {
                        image.writeToFile(Path.of(System.getProperty("leggings.test.report"), "tooltip.png"));
                        captured = true;
                    } catch (Throwable failure) {
                        captureFailure = failure;
                    }
                });
                stage = 2;
            } else if (stage == 2 && captured) {
                SkeletonChecks.passed("client");
                stage = 3;
                client.stop();
            }
            if (captureFailure != null) { throw new AssertionError("Tooltip screenshot failed", captureFailure); }
        } catch (Throwable failure) {
            stage = 3;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static final class TooltipScene extends Screen {
        private final ItemStack root;
        private int frames;

        private TooltipScene(ItemStack root) {
            super(Component.literal("Packaged leggings tooltip"));
            this.root = root;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            var client = Minecraft.getInstance();
            client.options.advancedItemTooltips = frames >= 6;
            graphics.fill(0, 0, width, height, 0xFF20252C);
            graphics.text(font, "Native source details / live root / advanced source IDs", 20, 12, 0xFFFFFFFF, false);
            graphics.item(root, 20, 34);
            graphics.setTooltipForNextFrame(font, root, 40, 38);
            frames++;
        }
    }
}
