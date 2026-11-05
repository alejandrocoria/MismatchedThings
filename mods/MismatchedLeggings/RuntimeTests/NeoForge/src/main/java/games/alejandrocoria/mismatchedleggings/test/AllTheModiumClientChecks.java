package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.client.render.LeggingsGeometryCache;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** The existing hidden client, gallery and native renderer probe; no new launch framework. */
final class AllTheModiumClientChecks {
    private static int ticks;
    private static int phase;
    private static EquippedScene scene;
    private static long generation;
    private static EquipmentLayerRenderer previousRenderer;
    private static CompletableFuture<Void> reload;
    private static boolean backupConfirmed;
    private static volatile boolean captured;
    private static volatile Throwable captureFailure;

    private AllTheModiumClientChecks() {
    }

    static void tick(Minecraft client) {
        if (phase == 5) {
            return;
        }
        try {
            require(++ticks < 2400, "AllTheModium client startup/reload timed out: " + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated client window became visible");
            if (captureFailure != null) {
                throw new AssertionError("AllTheModium panel capture failed", captureFailure);
            }
            if (client.player == null && client.screen instanceof BackupConfirmScreen backup) {
                if (backupConfirmed) {
                    return;
                }
                backupConfirmed = true;
                // Only this profile's disposable copy; preserve the native experimental-world flow.
                backup.children().stream().filter(child -> child instanceof Button button
                                && button.getMessage().equals(Component.translatable("selectWorld.backupJoinSkipButton")))
                        .map(Button.class::cast).findFirst().orElseThrow().onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                return;
            }
            if (client.level == null || client.player == null || client.getOverlay() != null) {
                return;
            }
            if (phase == 0 && client.screen == null) {
                SkeletonChecks.verify();
                AllTheModiumRenderChecks.gallery();
                scene = new EquippedScene();
                client.setScreen(scene);
                phase = 1;
            } else if (phase == 1 && scene.frames >= 8) {
                proof(client, "allthemodium-render.txt");
                generation = LeggingsGeometryCache.statistics().generation();
                previousRenderer = EquippedClient.renderer;
                Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                    try (image) {
                        image.writeToFile(report("allthemodium-panel.png"));
                        captured = true;
                    } catch (Throwable failure) {
                        captureFailure = failure;
                    }
                });
                phase = 2;
            } else if (phase == 2 && captured) {
                reload = client.reloadResourcePacks();
                phase = 3;
            } else if (phase == 3 && reload.isDone()) {
                reload.join();
                require(LeggingsGeometryCache.statistics().generation() > generation, "Reload did not invalidate geometry");
                require(EquippedClient.renderer != previousRenderer, "Reload retained a stale native renderer");
                scene = new EquippedScene();
                client.setScreen(scene);
                phase = 4;
            } else if (phase == 4 && scene.frames >= 8) {
                proof(client, "allthemodium-render-after-reload.txt");
                SkeletonChecks.passed("client");
                phase = 5;
                client.stop();
            }
        } catch (Throwable failure) {
            phase = 5;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static void proof(Minecraft client, String name) throws Exception {
        Files.writeString(report(name), AllTheModiumRenderChecks.verify(client));
    }

    private static Path report(String name) {
        return Path.of(System.getProperty("leggings.test.report"), name);
    }
}
