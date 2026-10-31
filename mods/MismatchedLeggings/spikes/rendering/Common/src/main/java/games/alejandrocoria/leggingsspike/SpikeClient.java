package games.alejandrocoria.leggingsspike;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class SpikeClient {
    public static EquipmentLayerRenderer nativeRenderer;
    public static boolean withBoots;
    public static Supplier<String> extraChecks = () -> "";
    private static final Path REPORT = Path.of(System.getProperty("leggings.spike.report"));
    private static int stage;
    private static int ticks;
    private static SpikeScene scene;
    private static EquipmentLayerRenderer previousRenderer;
    private static CompletableFuture<Void> reload;
    private static volatile boolean screenshotDone;
    private static volatile Throwable screenshotFailure;
    private static String evidence;

    private SpikeClient() {
    }

    public static void tick(Minecraft client) {
        try {
            SpikeChecks.require(++ticks < 1800, "Spike client timed out");
            SpikeChecks.require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE)
                    == GLFW.GLFW_FALSE, "Automated window became visible");
            if (stage == 0 && client.screen instanceof TitleScreen && client.getOverlay() == null) {
                scene = new SpikeScene();
                client.setScreen(scene);
                stage = 1;
            } else if (stage == 1 && scene.frames >= 8 && nativeRenderer != null) {
                evidence = SpikeChecks.run(client) + extraChecks.get();
                previousRenderer = nativeRenderer;
                Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                    try (image) {
                        image.writeToFile(REPORT.resolve("waist-panel.png"));
                        screenshotDone = true;
                    } catch (Throwable failure) {
                        screenshotFailure = failure;
                    }
                });
                stage = 2;
            } else if (stage == 2) {
                if (screenshotFailure != null) { throw new AssertionError("Screenshot failed", screenshotFailure); }
                if (screenshotDone) {
                    nativeRenderer = null;
                    reload = client.reloadResourcePacks();
                    stage = 3;
                }
            } else if (stage == 3 && reload.isDone() && client.getOverlay() == null) {
                reload.join();
                scene = new SpikeScene();
                client.setScreen(scene);
                stage = 4;
            } else if (stage == 4 && scene.frames >= 8 && nativeRenderer != null) {
                SpikeChecks.require(previousRenderer != nativeRenderer, "Native renderer survived reload unexpectedly");
                SpikeChecks.run(client);
                extraChecks.get();
                if (withBoots) {
                    SpikeChecks.require(BootsCoexistence.ironSeen && BootsCoexistence.goldSeen,
                            "Unchanged Boots did not render both actual mixed feet");
                }
                Files.writeString(REPORT.resolve("client.ok"), evidence + "reload.rebuiltAndRetested=true\n"
                        + "window.hidden=true\nboots.present=" + withBoots + "\n"
                        + "rendered.pairs=" + PairRenderer.successfulPairs + "\n");
                stage = 5;
                client.stop();
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(REPORT.resolve("failure.txt"), failure.toString()); }
            catch (Exception ignored) { failure.printStackTrace(); }
            stage = 5;
            client.stop();
        }
    }
}
