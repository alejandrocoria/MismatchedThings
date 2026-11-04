package games.alejandrocoria.mismatchedleggings.test.render;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Actual GUI extraction plus native hand/frame/drop renderer submissions in one bounded panel. */
public final class ItemScene extends Screen {
    public int frames;
    private final ItemStack[] samples;

    public ItemScene() {
        super(Component.literal("Leggings item rendering"));
        ItemStack special = new ItemStack(Items.IRON_LEGGINGS);
        special.set(net.minecraft.core.component.DataComponents.ITEM_MODEL,
                net.minecraft.resources.Identifier.withDefaultNamespace("shield"));
        samples = new ItemStack[]{new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.GOLDEN_LEGGINGS),
                RenderFixture.pair(0), RenderFixture.pair(1), RenderFixture.pair(2),
                RenderFixture.pair(special, new ItemStack(Items.GOLDEN_LEGGINGS)),
                new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID))};
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (Boolean.getBoolean("leggings.test.emptyItems")) {
            EmptyItemModelScene.draw(graphics, font);
            frames++;
            return;
        }
        var client = Minecraft.getInstance();
        var resolver = client.getItemModelResolver();
        graphics.fill(0, 0, width, height, 0xFF20252C);
        graphics.text(font, "Native iron / gold | mixed / inverse | dye + trim + foil | fallback | bare", 12, 12, 0xFFFFFFFF, false);
        for (int i = 0; i < samples.length; i++) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(18 + i * 85, 40).scale(3);
            graphics.item(samples[i], 0, 0);
            graphics.pose().popMatrix();
        }
        // Exercise the native rectangular GUI fallback and the rotated/nonrectangular composition.
        graphics.pose().pushMatrix();
        graphics.pose().translate(25, 130).rotate(0.2F).scale(2);
        graphics.item(samples[5], 0, 0);
        graphics.pose().popMatrix();
        var root = RenderFixture.pair(ItemChecks.model("red"), ItemChecks.model("blue"));
        var stand = new ArmorStandRenderState();
        stand.entityType = EntityType.ARMOR_STAND;
        stand.boundingBoxHeight = 1.9F;
        stand.boundingBoxWidth = 0.6F;
        stand.showArms = true;
        stand.rightHandItemStack = root;
        stand.leftHandItemStack = root.copy();
        resolver.updateForTopItem(stand.rightHandItemState, root, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, client.level, client.player, 0);
        resolver.updateForTopItem(stand.leftHandItemState, root, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, client.level, client.player, 0);
        var drop = new ItemEntityRenderState();
        drop.entityType = EntityType.ITEM;
        drop.count = 1;
        drop.ageInTicks = 30;
        resolver.updateForTopItem(drop.item, root, ItemDisplayContext.GROUND, client.level, client.player, 0);
        var frame = new ItemFrameRenderState();
        frame.entityType = EntityType.ITEM_FRAME;
        frame.isInvisible = true;
        resolver.updateForTopItem(frame.item, root, ItemDisplayContext.FIXED, client.level, client.player, 0);
        var rotation = new Quaternionf().rotateZ((float) Math.PI);
        graphics.entity(stand, 70, new Vector3f(0, 1, 0), rotation, new Quaternionf(), 120, 115, 250, 330);
        graphics.entity(drop, 100, new Vector3f(), rotation, new Quaternionf(), 280, 145, 400, 300);
        graphics.entity(frame, 100, new Vector3f(), rotation, new Quaternionf(), 450, 145, 590, 300);
        frames++;
    }
}
