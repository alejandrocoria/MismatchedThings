package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import games.alejandrocoria.mismatchedleggings.test.render.RenderFixture;
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

/** Native control and real armor-layer/deferred production rendering, never a prototype interception. */
final class BetterEndRenderScene extends Screen {
    private static final String[] LABELS = {"Native Crystalite", "Crystalite / iron", "Iron / Crystalite", "Double / posed small",
            "Native crouching", "Mixed crouching", "Mixed walking", "Root foil + leather trim"};
    private final HumanoidRenderState[] states = new HumanoidRenderState[8];
    int frames;

    BetterEndRenderScene() {
        super(Component.literal("Packaged Crystalite rendering"));
        BootsCoexistence.ironSeen = false;
        BootsCoexistence.goldSeen = false;
        for (int index = 0; index < states.length; index++) {
            var state = state(index);
            state.boundingBoxHeight = 1.9F;
            state.boundingBoxWidth = 0.6F;
            state.ageInTicks = 100;
            state.legsEquipment = leggings(index);
            if (Boolean.getBoolean("leggings.test.boots")) {
                state.feetEquipment = BootsCoexistence.mixed();
            }
            states[index] = state;
        }
    }

    private static HumanoidRenderState state(int index) {
        if (index == 4 || index == 5) {
            var zombie = new ZombieRenderState();
            zombie.entityType = EntityType.ZOMBIE;
            zombie.isCrouching = true;
            return zombie;
        }
        if (index == 6) {
            var player = new AvatarRenderState();
            player.entityType = EntityType.PLAYER;
            player.walkAnimationPos = 1.4F;
            player.walkAnimationSpeed = 0.7F;
            return player;
        }
        var stand = new ArmorStandRenderState();
        stand.entityType = EntityType.ARMOR_STAND;
        if (index == 3) {
            stand.isSmall = true;
            stand.isBaby = true;
            stand.bodyPose = new Rotations(15, 25, -10);
            stand.leftLegPose = new Rotations(-35, 10, -10);
            stand.rightLegPose = new Rotations(25, -10, 12);
        }
        return stand;
    }

    private static ItemStack leggings(int index) {
        var crystalite = BetterEndChecks.nativeStack("crystalite_leggings");
        if (index == 0 || index == 4) {
            return crystalite;
        }
        var other = index == 3 ? crystalite : index == 7
                ? RenderFixture.sources(2)[0] : new ItemStack(Items.IRON_LEGGINGS);
        var root = index == 2 ? RenderFixture.pair(other, crystalite) : RenderFixture.pair(crystalite, other);
        root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, index == 7);
        return root;
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
