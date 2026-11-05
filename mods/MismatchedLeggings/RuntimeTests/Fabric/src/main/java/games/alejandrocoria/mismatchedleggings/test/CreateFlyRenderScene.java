package games.alejandrocoria.mismatchedleggings.test;

import com.zurrtum.create.AllItems;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Only actual Cardboard assets, production mixed rendering and native whole-player boxes. */
final class CreateFlyRenderScene extends Screen {
    private static final String[] LABELS = {"Native Cardboard", "Cardboard / gold", "Gold / Cardboard", "Native Sentry trim",
            "Trim / gold", "Gold / trim", "Mixed walking", "Mixed crouching (no set)",
            "Native box", "Mixed leggings box", "Mixed set box", "Trim + root foil / both hands"};
    private final HumanoidRenderState[] states = new HumanoidRenderState[12];
    int frames;

    CreateFlyRenderScene() {
        super(Component.literal("Packaged Create Fly rendering"));
        for (int index = 0; index < states.length; index++) {
            var legs = leggings(index);
            HumanoidRenderState state;
            if (index >= 8 && index <= 10) {
                var feet = index == 10 && Boolean.getBoolean("leggings.test.boots")
                        ? BootsCoexistence.pair(AllItems.CARDBOARD_BOOTS.getDefaultInstance(), new ItemStack(Items.IRON_BOOTS)).getFirst()
                        : AllItems.CARDBOARD_BOOTS.getDefaultInstance();
                state = CreateFlyRenderChecks.box(legs, feet);
            } else if (index == 6 || index == 7) {
                var avatar = new AvatarRenderState();
                avatar.entityType = EntityType.PLAYER;
                avatar.isCrouching = index == 7;
                avatar.pose = index == 7 ? Pose.CROUCHING : Pose.STANDING;
                avatar.walkAnimationPos = 1.4F;
                avatar.walkAnimationSpeed = index == 6 ? 0.7F : 0;
                state = avatar;
            } else {
                var stand = new ArmorStandRenderState();
                stand.entityType = EntityType.ARMOR_STAND;
                if (index == 11) {
                    stand.showArms = true;
                    stand.rightHandItemStack = legs;
                    stand.leftHandItemStack = legs.copy();
                    var client = Minecraft.getInstance();
                    client.getItemModelResolver().updateForTopItem(stand.rightHandItemState, legs,
                            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, client.level, client.player, 0);
                    client.getItemModelResolver().updateForTopItem(stand.leftHandItemState, legs,
                            ItemDisplayContext.THIRD_PERSON_LEFT_HAND, client.level, client.player, 0);
                }
                state = stand;
            }
            state.boundingBoxWidth = 0.6F;
            state.boundingBoxHeight = 1.9F;
            state.ageInTicks = 100;
            state.legsEquipment = legs;
            states[index] = state;
        }
    }

    private static ItemStack leggings(int index) {
        var source = CreateFlyRenderChecks.source(index >= 3 && index <= 5 || index == 11);
        if (index == 0 || index == 3 || index == 8) {
            return source;
        }
        var gold = new ItemStack(Items.GOLDEN_LEGGINGS);
        var pair = index == 10 ? MixedLeggingsFactory.createPair(source, source)
                : MixedLeggingsFactory.createPair(source, gold);
        var root = index == 2 || index == 5 ? pair.inverse() : pair.primary();
        root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, index == 11);
        return root;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF20252C);
        int cellWidth = width / 4;
        int cellHeight = height / 3;
        for (int index = 0; index < states.length; index++) {
            int x = index % 4 * cellWidth;
            int y = index / 4 * cellHeight;
            graphics.text(font, LABELS[index], x + 4, y + 5, 0xFFFFFFFF, false);
            graphics.entity(states[index], Math.min(60, (cellHeight - 35) / 2), new Vector3f(0, 1, 0),
                    new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(),
                    x + 3, y + 19, x + cellWidth - 3, y + cellHeight - 3);
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + cellWidth - 33, y + 25).scale(1.5F);
            graphics.item(states[index].legsEquipment, 0, 0);
            graphics.pose().popMatrix();
        }
        frames++;
    }
}
