package games.alejandrocoria.mismatchedleggings.test;

import com.autovw.advancednetherite.core.util.ModTooltips;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class AdvancedNetheriteClientChecks {
    private static int ticks;
    private static boolean finished;

    private AdvancedNetheriteClientChecks() {
    }

    static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 1800, "Advanced Netherite client startup timed out");
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated client window became visible");
            if (client.level == null || client.player == null || client.screen != null || client.getOverlay() != null) {
                return;
            }
            require(!client.hasShiftDown(), "Unshifted tooltip test started with Shift held");
            for (String tier : new String[] {"iron", "gold", "emerald", "diamond"}) {
                var source = AdvancedNetheriteChecks.nativeLeggings(tier);
                var context = Item.TooltipContext.of(client.level);
                require(source.getTooltipLines(context, client.player, TooltipFlag.NORMAL).contains(ModTooltips.SHIFT_KEY_TOOLTIP),
                        "Native tooltip/config control did not provide the Shift hint");
                var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS));
                for (var root : new ItemStack[] {pair.primary(), pair.inverse()}) {
                    var before = root.copy();
                    for (var flag : new TooltipFlag[] {TooltipFlag.NORMAL, TooltipFlag.ADVANCED}) {
                        require(root.getTooltipLines(context, client.player, flag).contains(ModTooltips.SHIFT_KEY_TOOLTIP),
                                "Native source tooltip disappeared from mixed leggings");
                    }
                    require(ItemStack.matches(root, before), "Source tooltip mutated the mixed pair");
                }
            }
            SkeletonChecks.passed("client");
            finished = true;
            client.stop();
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }
}
