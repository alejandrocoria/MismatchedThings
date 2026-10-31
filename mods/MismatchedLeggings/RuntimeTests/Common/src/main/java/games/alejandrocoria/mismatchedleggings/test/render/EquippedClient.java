package games.alejandrocoria.mismatchedleggings.test.render;

import games.alejandrocoria.mismatchedleggings.client.render.LeggingsGeometryCache;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class EquippedClient {
    public static EquipmentLayerRenderer renderer;
    public static Supplier<String> platformChecks = () -> "";
    public static Runnable prepareGallery = () -> {};
    private static int ticks;
    private static int stage;
    private static EquippedScene scene;
    private static EquipmentLayerRenderer previousRenderer;
    private static long generation;
    private static CompletableFuture<Void> reload;
    private static volatile boolean captured;
    private static volatile Throwable captureFailure;

    private EquippedClient() {}

    public static void tick(Minecraft client) {
        if (stage == 5) {
            return;
        }
        try {
            EquippedChecks.require(++ticks < 2400, "Equipped client timed out at stage " + stage);
            EquippedChecks.require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated window became visible");
            if (stage == 0 && client.level != null && client.player != null && client.screen == null && client.getOverlay() == null) {
                prepareGallery.run();
                scene = new EquippedScene();
                client.setScreen(scene);
                stage = 1;
            } else if (stage == 1 && scene.frames >= 8 && renderer != null) {
                EquippedChecks.run(client);
                platformChecks.get();
                previousRenderer = renderer;
                generation = LeggingsGeometryCache.statistics().generation();
                Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                    try (image) {
                        image.writeToFile(Path.of(System.getProperty("leggings.test.report"), "equipped-panel.png"));
                        captured = true;
                    } catch (Throwable failure) {
                        captureFailure = failure;
                    }
                });
                stage = 2;
            } else if (stage == 2 && captured) {
                renderer = null;
                reload = client.reloadResourcePacks();
                stage = 3;
            } else if (stage == 3 && reload.isDone() && client.getOverlay() == null) {
                reload.join();
                EquippedChecks.require(LeggingsGeometryCache.statistics().generation() > generation,
                        "Resource reload did not invalidate geometry");
                scene = new EquippedScene();
                client.setScreen(scene);
                stage = 4;
            } else if (stage == 4 && scene.frames >= 8 && renderer != null) {
                EquippedChecks.require(previousRenderer != renderer, "Native renderer was not recreated on reload");
                EquippedChecks.run(client);
                platformChecks.get();
                if (Boolean.getBoolean("leggings.test.boots")) {
                    EquippedChecks.require(BootsCoexistence.ironSeen && BootsCoexistence.goldSeen,
                            "Boots did not keep rendering both native feet");
                }
                SkeletonChecks.passed("client");
                stage = 5;
                client.stop();
            }
            if (captureFailure != null) {
                throw new AssertionError("Screenshot failed", captureFailure);
            }
        } catch (Throwable failure) {
            stage = 5;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }
}
