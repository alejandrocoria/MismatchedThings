package games.alejandrocoria.mismatchedleggings.test.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.core.Rotations;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Real GUI entity → armor layer → deferred feature renderer, without an indev world. */
public final class EquippedScene extends Screen {
    public int frames;
    private final HumanoidRenderState[] states = new HumanoidRenderState[8];
    private static final String[] LABELS = {"Stand front: iron / gold", "Stand back: gold / iron",
            "Crouching zombie", "Baby zombie", "Small posed stand", "Walking player",
            "Seated player", "Leather + trim + foil"};

    public EquippedScene() {
        super(Component.literal("Packaged mixed leggings rendering"));
        if (!RenderFixture.gallery.isEmpty()) {
            for (int i = 0; i < states.length; i++) {
                ArmorStandRenderState stand = new ArmorStandRenderState();
                stand.entityType = EntityType.ARMOR_STAND;
                stand.boundingBoxHeight = 1.9F;
                stand.boundingBoxWidth = 0.6F;
                stand.ageInTicks = 100;
                stand.legsEquipment = RenderFixture.gallery.get(i).stack().copy();
                if (i == 7) {
                    stand.bodyPose = new Rotations(18, 25, -10);
                    stand.leftLegPose = new Rotations(-35, 10, -10);
                    stand.rightLegPose = new Rotations(25, -10, 12);
                }
                if (Boolean.getBoolean("leggings.test.boots")) { stand.feetEquipment = BootsCoexistence.mixed(); }
                states[i] = stand;
            }
            return;
        }
        states[0] = new ArmorStandRenderState();
        states[1] = new ArmorStandRenderState();
        ZombieRenderState crouching = new ZombieRenderState();
        crouching.entityType = EntityType.ZOMBIE;
        crouching.isCrouching = true;
        states[2] = crouching;
        ZombieRenderState baby = new ZombieRenderState();
        baby.entityType = EntityType.ZOMBIE;
        baby.isBaby = true;
        states[3] = baby;
        ArmorStandRenderState small = new ArmorStandRenderState();
        small.isBaby = true;
        small.isSmall = true;
        small.bodyPose = new Rotations(12, 25, -10);
        small.leftLegPose = new Rotations(-35, 10, -10);
        small.rightLegPose = new Rotations(25, -10, 12);
        states[4] = small;
        AvatarRenderState walking = new AvatarRenderState();
        walking.entityType = EntityType.PLAYER;
        walking.walkAnimationPos = 1.4F;
        walking.walkAnimationSpeed = 0.7F;
        states[5] = walking;
        AvatarRenderState seated = new AvatarRenderState();
        seated.entityType = EntityType.PLAYER;
        seated.isPassenger = true;
        states[6] = seated;
        states[7] = new ArmorStandRenderState();
        for (int i = 0; i < states.length; i++) {
            HumanoidRenderState state = states[i];
            if (state instanceof ArmorStandRenderState) { state.entityType = EntityType.ARMOR_STAND; }
            state.boundingBoxHeight = 1.9F;
            state.boundingBoxWidth = 0.6F;
            state.legsEquipment = RenderFixture.pair(i == 7 ? 2 : i % 2);
            if (Boolean.getBoolean("leggings.test.boots")) { state.feetEquipment = BootsCoexistence.mixed(); }
            state.ageInTicks = 100;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF20252C);
        int cellWidth = width / 4;
        int cellHeight = height / 2;
        for (int i = 0; i < states.length; i++) {
            int x = i % 4 * cellWidth;
            int y = i / 4 * cellHeight;
            String label = RenderFixture.gallery.isEmpty() ? LABELS[i] : RenderFixture.gallery.get(i).label();
            graphics.text(font, label, x + 5, y + 5, 0xFFFFFFFF, false);
            Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
            float angle = RenderFixture.gallery.isEmpty() ? (i == 1 ? (float) Math.PI : 0) : RenderFixture.gallery.get(i).rotation();
            rotation.rotateY(angle);
            graphics.entity(states[i], Math.min(65, (cellHeight - 30) / 2), new Vector3f(0, 1.05F, 0),
                    rotation, new Quaternionf(), x + 4, y + 18, x + cellWidth - 4, y + cellHeight - 4);
        }
        frames++;
    }
}
