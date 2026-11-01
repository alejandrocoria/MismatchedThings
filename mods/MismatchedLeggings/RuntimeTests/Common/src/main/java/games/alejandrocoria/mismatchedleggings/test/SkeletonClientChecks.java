package games.alejandrocoria.mismatchedleggings.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;

public final class SkeletonClientChecks {
    private static int ticks;
    private static ItemScene scene;
    private static boolean finished;

    private SkeletonClientChecks() {
    }

    public static void tick(Minecraft client) {
        if (RecoveryFixture.enabled()) {
            RecoveryClientChecks.tick(client);
            return;
        }
        if (Boolean.getBoolean("leggings.test.smithing")) {
            SmithingClientChecks.tick(client);
            return;
        }
        if (Boolean.getBoolean("leggings.test.grindstone")) {
            GrindstoneClientChecks.tick(client);
            return;
        }
        if (Boolean.getBoolean("leggings.test.repair")) {
            RepairClientChecks.tick(client);
            return;
        }
        if (Boolean.getBoolean("leggings.test.presentation")) {
            PresentationClient.tick(client);
            return;
        }
        if (Boolean.getBoolean("leggings.test.items")) {
            games.alejandrocoria.mismatchedleggings.test.render.ItemClient.tick(client);
            return;
        }
        if (Boolean.getBoolean("leggings.test.equipped")) {
            games.alejandrocoria.mismatchedleggings.test.render.EquippedClient.tick(client);
            return;
        }
        if (Boolean.getBoolean("leggings.test.workbench")) {
            WorkbenchClientChecks.tick(client);
            return;
        }
        if (finished) {
            return;
        }
        try {
            SkeletonChecks.require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated client window became visible");
            SkeletonChecks.require(++ticks < 1800, "Client startup timed out: " + client.screen);
            if (scene == null) {
                // Item components are datapack-bound: a title-screen stack is not a valid gameplay test.
                if (client.level == null || client.player == null || client.screen != null || client.getOverlay() != null) {
                    return;
                }
                var stack = SkeletonChecks.verify();
                SkeletonChecks.require(stack.getHoverName().getString().equals("Mismatched Leggings"), "Missing item translation");
                SkeletonChecks.require(stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL).stream()
                        .anyMatch(line -> line.getString().equals("Invalid mixed leggings data")), "Missing bare-item diagnostic");
                scene = new ItemScene(SourceDataChecks.clientRoundTrip(client.level.registryAccess()));
                client.setScreen(scene);
            } else if (scene.frames >= 5) {
                SkeletonChecks.passed("client");
                finished = true;
                client.stop();
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static final class ItemScene extends Screen {
        private final ItemStack stack;
        private int frames;

        private ItemScene(ItemStack stack) {
            super(Component.literal("Leggings skeleton test"));
            this.stack = stack;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.item(stack, width / 2, height / 2);
            frames++;
        }
    }
}
