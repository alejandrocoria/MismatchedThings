package games.alejandrocoria.mismatchedleggings.test.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** One relevant comparison panel, through native GUI and third-person hand extraction. */
final class EmptyItemModelScene {
    private EmptyItemModelScene() {
    }

    static void draw(GuiGraphicsExtractor graphics, Font font) {
        graphics.fill(0, 0, 640, 360, 0xFF20252C);
        graphics.text(font, "Empty source models: normal | empty left | empty right | both empty", 12, 10, 0xFFFFFFFF, false);
        var samples = EmptyItemModelChecks.samples();
        for (int product = 0; product < 2; product++) {
            int y = 45 + product * 80;
            graphics.text(font, product == 0 ? "Boots" : "Leggings", 12, y - 12, 0xFFFFFFFF, false);
            for (int index = 0; index < 4; index++) {
                int x = 95 + index * 135;
                var sample = samples.get(product * 4 + index);
                graphics.text(font, sample.label(), x - 15, y - 12, 0xFFBBBBBB, false);
                graphics.pose().pushMatrix();
                graphics.pose().translate(x, y).scale(2.5F);
                graphics.item(sample.root(), 0, 0);
                graphics.pose().popMatrix();
            }
        }
        graphics.text(font, "Held: Boots (both source orders)", 24, 181, 0xFFFFFFFF, false);
        graphics.text(font, "Held: Leggings (both source orders)", 328, 181, 0xFFFFFFFF, false);
        drawStand(graphics, samples.get(1).root(), samples.get(2).root(), 30);
        drawStand(graphics, samples.get(5).root(), samples.get(6).root(), 330);
    }

    private static void drawStand(GuiGraphicsExtractor graphics, ItemStack left, ItemStack right, int x) {
        var client = Minecraft.getInstance();
        var stand = new ArmorStandRenderState();
        stand.entityType = EntityType.ARMOR_STAND;
        stand.boundingBoxHeight = 1.9F;
        stand.boundingBoxWidth = 0.6F;
        stand.showArms = true;
        stand.rightHandItemStack = left;
        stand.leftHandItemStack = right;
        client.getItemModelResolver().updateForTopItem(stand.rightHandItemState, left,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, client.level, client.player, 0);
        client.getItemModelResolver().updateForTopItem(stand.leftHandItemState, right,
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND, client.level, client.player, 0);
        graphics.entity(stand, 65, new Vector3f(0, 1, 0), new Quaternionf().rotateZ((float) Math.PI),
                new Quaternionf(), x, 195, x + 260, 350);
    }
}
