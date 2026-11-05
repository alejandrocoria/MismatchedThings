package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.client.render.LeggingsGeometryCache;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.world.entity.player.Input;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class CreateFlyClientChecks {
    private static int ticks;
    private static boolean finished;
    private static int phase;
    private static CreateFlyRenderScene scene;
    private static long generation;
    private static CompletableFuture<Void> reload;
    private static EquipmentLayerRenderer previousRenderer;
    private static volatile boolean captured;
    private static volatile Throwable captureFailure;

    private CreateFlyClientChecks() {
    }

    static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 2400, "Create Fly client startup/reload timed out: " + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated client window became visible");
            if (captureFailure != null) {
                throw new AssertionError("Create Fly panel capture failed", captureFailure);
            }
            if (phase != 0) {
                renderingTick(client);
                return;
            }
            if (client.level == null || client.player == null || client.screen != null || client.getOverlay() != null) {
                return;
            }
            SkeletonChecks.verify();
            // LocalPlayer reads input, not Entity's shift flag used by the dedicated mock player.
            var previousInput = client.player.input.keyPresses;
            try {
                client.player.input.keyPresses = new Input(false, false, false, false, false, true, false);
                CreateFlyChecks.equippedChecks(client.player);
            } finally {
                client.player.input.keyPresses = previousInput;
            }
            CreateFlyTooltipChecks.verify(client);
            scene = new CreateFlyRenderScene();
            client.setScreen(scene);
            phase = 1;
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static void renderingTick(Minecraft client) throws Exception {
        if (client.getOverlay() != null) {
            return;
        }
        if (phase == 1 && scene.frames >= 8) {
            proof(client, "cardboard-render.txt");
            generation = LeggingsGeometryCache.statistics().generation();
            capture(client, "cardboard-panel.png");
            phase = 2;
        } else if (phase == 2 && captured) {
            previousRenderer = EquippedClient.renderer;
            reload = client.reloadResourcePacks();
            phase = 3;
        } else if (phase == 3 && reload.isDone()) {
            reload.join();
            require(LeggingsGeometryCache.statistics().generation() > generation, "Reload did not clear the geometry epoch");
            require(EquippedClient.renderer != previousRenderer, "Reload checks retained a stale native renderer");
            scene = new CreateFlyRenderScene();
            client.setScreen(scene);
            phase = 4;
        } else if (phase == 4 && scene.frames >= 8) {
            proof(client, "cardboard-render-after-reload.txt");
            capture(client, "cardboard-panel-after-reload.png");
            phase = 5;
        } else if (phase == 5 && captured) {
            SkeletonChecks.passed("client");
            finished = true;
            client.stop();
        }
    }

    private static void proof(Minecraft client, String name) throws Exception {
        Files.writeString(Path.of(System.getProperty("leggings.test.report"), name), CreateFlyRenderChecks.verify(client));
        require(EquippedClient.renderer != null, "Reload lost the native equipment renderer");
    }

    private static void capture(Minecraft client, String name) {
        captured = false;
        Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(Path.of(System.getProperty("leggings.test.report"), name));
                captured = true;
            } catch (Throwable failure) {
                captureFailure = failure;
            }
        });
    }
}
