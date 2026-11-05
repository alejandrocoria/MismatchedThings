package games.alejandrocoria.crystalitespike;

import games.alejandrocoria.mismatchedleggings.client.render.LeggingsGeometryCache;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

public final class SpikeClient implements ClientModInitializer {
    private static int ticks;
    private static int stage;
    private static Gallery gallery;
    private static long generation;
    private static CompletableFuture<Void> reload;
    private static volatile boolean captured;
    private static volatile Throwable captureFailure;

    public SpikeClient() {
    }

    @Override
    public void onInitializeClient() {
        // Factories must be registered before Fabric materializes renderers during resource loading.
        ArmorRenderer.register((pose, output, stack, state, slot, light, model) -> {
            throw new AssertionError("Unknown renderer must never be invoked by the bridge");
        }, Items.CHAINMAIL_LEGGINGS);
    }

    public static ItemStack mixed(boolean inverse, boolean baseline, boolean both, boolean foil) {
        var crystalite = CrystaliteBridge.nativeStack();
        var pair = MixedLeggingsFactory.createPair(crystalite,
                both ? crystalite : new ItemStack(Items.IRON_LEGGINGS), CompositionRules.defaults());
        var root = inverse ? pair.inverse() : pair.primary();
        if (baseline) {
            root.set(DataComponents.CUSTOM_NAME, CrystaliteBridge.BASELINE);
        }
        root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
        return root;
    }

    public static void tick(Minecraft client) {
        if (stage == 5) {
            return;
        }
        try {
            require(++ticks < 2400, "Crystalite spike timed out at stage " + stage);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Spike window became visible");
            if (stage == 0 && client.player != null && client.level != null && client.screen == null && client.getOverlay() == null) {
                gallery = new Gallery();
                client.setScreen(gallery);
                stage = 1;
            } else if (stage == 1 && gallery.frames >= 8 && CrystaliteBridge.equipmentRenderer != null) {
                String proof = SpikeChecks.run(client);
                Files.writeString(report("crystalite-proof.txt"), proof);
                generation = LeggingsGeometryCache.statistics().generation();
                Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                    try (image) {
                        image.writeToFile(report("crystalite-panel.png"));
                        captured = true;
                    } catch (Throwable failure) {
                        captureFailure = failure;
                    }
                });
                stage = 2;
            } else if (stage == 2 && captured) {
                reload = client.reloadResourcePacks();
                stage = 3;
            } else if (stage == 3 && reload.isDone() && client.getOverlay() == null) {
                reload.join();
                require(LeggingsGeometryCache.statistics().generation() > generation, "Resource reload did not clear waist templates");
                CrystaliteBridge.reset();
                gallery = new Gallery();
                client.setScreen(gallery);
                stage = 4;
            } else if (stage == 4 && gallery.frames >= 8) {
                Files.writeString(report("crystalite-after-reload.txt"), SpikeChecks.run(client));
                SkeletonChecks.passed("client");
                stage = 5;
                client.stop();
            }
            if (captureFailure != null) {
                throw new AssertionError("Spike screenshot failed", captureFailure);
            }
        } catch (Throwable failure) {
            stage = 5;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static Path report(String file) {
        return Path.of(System.getProperty("leggings.test.report"), file);
    }

    private static final class Gallery extends Screen {
        private static final String[] LABELS = {"Native Crystalite", "Current fallback", "Spike: crystal / iron", "Spike: iron / crystal",
                "Native crouching", "Spike: walking player", "Spike: both / posed", "Spike: live root foil"};
        private final HumanoidRenderState[] states = new HumanoidRenderState[8];
        private int frames;

        private Gallery() {
            super(Component.literal("Crystalite rendering spike"));
            for (int index = 0; index < states.length; index++) {
                HumanoidRenderState state;
                if (index == 4) {
                    state = new ZombieRenderState();
                    state.entityType = EntityType.ZOMBIE;
                    state.isCrouching = true;
                } else if (index == 5) {
                    state = new AvatarRenderState();
                    state.entityType = EntityType.PLAYER;
                    state.walkAnimationPos = 1.4F;
                    state.walkAnimationSpeed = 0.7F;
                } else {
                    var stand = new ArmorStandRenderState();
                    stand.entityType = EntityType.ARMOR_STAND;
                    if (index == 6) {
                        stand.bodyPose = new Rotations(15, 25, -10);
                        stand.leftLegPose = new Rotations(-35, 10, -10);
                        stand.rightLegPose = new Rotations(25, -10, 12);
                    }
                    state = stand;
                }
                state.boundingBoxHeight = 1.9F;
                state.boundingBoxWidth = 0.6F;
                state.ageInTicks = 100;
                state.legsEquipment = index == 0 || index == 4 ? CrystaliteBridge.nativeStack()
                        : mixed(index == 3, index == 1, index == 6, index == 7);
                states[index] = state;
            }
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF20252C);
            int cellWidth = width / 4;
            int cellHeight = height / 2;
            for (int index = 0; index < states.length; index++) {
                int x = index % 4 * cellWidth;
                int y = index / 4 * cellHeight;
                graphics.text(font, LABELS[index], x + 5, y + 5, 0xFFFFFFFF, false);
                graphics.entity(states[index], Math.min(85, (cellHeight - 30) / 2), new Vector3f(0, 1.05F, 0),
                        new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(),
                        x + 4, y + 20, x + cellWidth - 4, y + cellHeight - 4);
            }
            frames++;
        }
    }
}
