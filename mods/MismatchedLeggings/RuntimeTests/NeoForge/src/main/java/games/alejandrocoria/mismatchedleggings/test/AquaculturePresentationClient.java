package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.client.render.LeggingsGeometryCache;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Relevant gallery/reload checks followed by real world reopen; shares the existing hidden client. */
final class AquaculturePresentationClient {
    private static int phase;
    private static EquippedScene scene;
    private static long generation;
    private static EquipmentLayerRenderer previousRenderer;
    private static CompletableFuture<Void> reload;
    private static volatile boolean captured;
    private static volatile Throwable captureFailure;

    private AquaculturePresentationClient() {
    }

    static void tick(Minecraft client) throws Exception {
        if (captureFailure != null) {
            throw new AssertionError("Aquaculture panel capture failed", captureFailure);
        }
        if (phase == 5) {
            if (AquacultureSessionChecks.tick(client)) {
                // The intentional native-handler failure is last, after normal presentation/reload.
                AquacultureTooltipChecks.verify();
                SkeletonChecks.passed("client");
                phase = 6;
                client.stop();
            }
            return;
        }
        if (client.level == null || client.player == null || client.getOverlay() != null) {
            return;
        }
        if (phase == 0 && client.screen == null) {
            AquacultureRenderChecks.gallery(client);
            scene = new EquippedScene();
            client.setScreen(scene);
            phase = 1;
        } else if (phase == 1 && scene.frames >= 8) {
            proof(client, "aquaculture-render.txt");
            generation = LeggingsGeometryCache.statistics().generation();
            previousRenderer = EquippedClient.renderer;
            Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                try (image) {
                    image.writeToFile(report("aquaculture-panel.png"));
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
            require(LeggingsGeometryCache.statistics().generation() > generation, "Reload did not invalidate Aquaculture geometry");
            scene = new EquippedScene();
            client.setScreen(scene);
            phase = 4;
        } else if (phase == 4 && scene.frames >= 8) {
            require(EquippedClient.renderer != previousRenderer, "Reload retained a stale native renderer");
            proof(client, "aquaculture-render-after-reload.txt");
            client.setScreen(null);
            phase = 5;
        }
    }

    private static void proof(Minecraft client, String file) throws Exception {
        Files.writeString(report(file), AquacultureRenderChecks.verify(client));
    }

    private static Path report(String file) {
        return Path.of(System.getProperty("leggings.test.report"), file);
    }
}
