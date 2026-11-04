package games.alejandrocoria.mismatchedleggings.test.render;

import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

public final class ItemClient {
    public static final EnumSet<ItemDisplayContext> submitted = EnumSet.noneOf(ItemDisplayContext.class);
    public static Runnable platformChecks = () -> {};
    private static int ticks;
    private static int stage;
    private static int phaseTicks;
    private static ItemScene scene;
    private static CompletableFuture<Void> reload;
    private static volatile boolean captured;
    private static volatile Throwable captureFailure;
    private static Object previousModelIdentity;
    private static final boolean EMPTY_MODELS = Boolean.getBoolean("leggings.test.emptyItems");

    private ItemClient() {}

    public static void tick(Minecraft client) {
        if (stage == 7) { return; }
        try {
            require(++ticks < 2400, "Item client timed out at stage " + stage + ": " + submitted);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated item window became visible");
            if (stage == 0 && client.level != null && client.player != null && client.screen == null && client.getOverlay() == null) {
                ItemChecks.run(client);
                platformChecks.run();
                if (EMPTY_MODELS) {
                    EmptyItemModelChecks.run(client);
                    EmptyItemModelChecks.resetSubmissions();
                }
                previousModelIdentity = ItemChecks.resolve(ItemChecks.model("red"), ItemDisplayContext.GUI).getModelIdentity();
                scene = new ItemScene();
                client.setScreen(scene);
                stage = 1;
            } else if (stage == 1 && scene.frames >= 10) {
                require(EMPTY_MODELS ? EmptyItemModelChecks.thirdPersonComplete() : submitted.containsAll(EnumSet.of(ItemDisplayContext.GROUND, ItemDisplayContext.FIXED,
                        ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND)),
                        "Native entity item submissions were missing: " + submitted);
                Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                    try (image) {
                        image.writeToFile(Path.of(System.getProperty("leggings.test.report"), EMPTY_MODELS ? "empty-item-models.png" : "items-panel.png"));
                        captured = true;
                    } catch (Throwable failure) {
                        captureFailure = failure;
                    }
                });
                stage = 2;
            } else if (stage == 2 && captured) {
                client.setScreen(null);
                client.options.setCameraType(CameraType.FIRST_PERSON);
                phaseTicks = 0;
                stage = 3;
            } else if (stage == 3) {
                holdItems(client);
                if (++phaseTicks > 50 && (EMPTY_MODELS ? EmptyItemModelChecks.firstPersonComplete() : submitted.containsAll(EnumSet.of(
                        ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.FIRST_PERSON_LEFT_HAND)))) {
                    reload = client.reloadResourcePacks();
                    stage = 4;
                }
            } else if (stage == 4 && reload.isDone() && client.getOverlay() == null) {
                reload.join();
                require(!previousModelIdentity.equals(ItemChecks.resolve(ItemChecks.model("red"), ItemDisplayContext.GUI).getModelIdentity()),
                        "Reload retained an obsolete native item-model identity");
                submitted.clear();
                ItemChecks.run(client);
                platformChecks.run();
                if (EMPTY_MODELS) {
                    EmptyItemModelChecks.run(client);
                    EmptyItemModelChecks.resetSubmissions();
                }
                scene = new ItemScene();
                client.setScreen(scene);
                stage = 5;
            } else if (stage == 5 && scene.frames >= 10) {
                require(EMPTY_MODELS ? EmptyItemModelChecks.thirdPersonComplete() : submitted.containsAll(EnumSet.of(ItemDisplayContext.GROUND, ItemDisplayContext.FIXED,
                        ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND)),
                        "Reload lost entity item submissions");
                client.setScreen(null);
                phaseTicks = 0;
                stage = 6;
            } else if (stage == 6) {
                holdItems(client);
                if (++phaseTicks > 50 && (EMPTY_MODELS ? EmptyItemModelChecks.firstPersonComplete() : submitted.containsAll(EnumSet.of(
                        ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.FIRST_PERSON_LEFT_HAND)))) {
                    SkeletonChecks.passed("client");
                    stage = 7;
                    client.stop();
                }
            }
            if (captureFailure != null) { throw new AssertionError("Item screenshot failed", captureFailure); }
        } catch (Throwable failure) {
            stage = 7;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static void holdItems(Minecraft client) {
        if (EMPTY_MODELS) {
            EmptyItemModelChecks.hold(client, phaseTicks);
            return;
        }
        var pair = RenderFixture.pair(ItemChecks.model("red"), ItemChecks.model("blue"));
        client.player.setItemInHand(InteractionHand.MAIN_HAND, pair);
        client.player.setItemInHand(InteractionHand.OFF_HAND, pair.copy());
    }
}
