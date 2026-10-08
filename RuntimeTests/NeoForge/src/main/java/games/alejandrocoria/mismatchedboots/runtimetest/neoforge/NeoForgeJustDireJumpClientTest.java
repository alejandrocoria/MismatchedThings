package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** A real hidden client's local jump, separately from the server's movement/damage gate. */
final class NeoForgeJustDireJumpClientTest {
    private static int ticks;
    private static boolean finished;

    static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            if (client.player == null || client.level == null) {
                if (++ticks > 3600) { throw new AssertionError("Client did not open the disposable jump world"); }
                return;
            }
            finished = true;
            if (client.options.getSoundSourceVolume(SoundSource.MASTER) != 0
                    || GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) != GLFW.GLFW_FALSE) {
                throw new AssertionError("Jump client was not hidden and muted");
            }
            var player = client.player;
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            player.setDeltaMovement(Vec3.ZERO); player.jumpFromGround();
            double control = player.getDeltaMovement().y;
            for (String tier : new String[]{"ferricore", "blazegold"}) {
                ItemStack original = NeoForgeJustDireJumpTest.boots(tier, true);
                var pair = MixedBootsFactory.createPair(original, new ItemStack(Items.IRON_BOOTS));
                int value = tier.equals("ferricore") ? 1 : 2;
                for (ItemStack stack : new ItemStack[]{original, pair.primary(), pair.inverse()}) {
                    ItemStack before = stack.copy();
                    player.setItemSlot(EquipmentSlot.FEET, stack);
                    player.setDeltaMovement(Vec3.ZERO); player.jumpFromGround();
                    if (Math.abs(player.getDeltaMovement().y - control - value / 7.5f) > 1e-7
                            || !ItemStack.matches(before, stack)) {
                        throw new AssertionError("Client passive jump differed from native value or mutated the stack: " + tier);
                    }
                }
            }
            NetworkAcceptanceFiles.write("just-dire-jump-client-report.txt", "6 real client jump cases; Ferricore/Blazegold native controls; "
                    + "both mixed orientations; exactly one native-equivalent contribution; unchanged stacks; hidden/muted client\n");
            NetworkAcceptanceFiles.write("just-dire-jump-client-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            finished = true;
            NetworkAcceptanceFiles.failure("just-dire-jump-client", failure);
        }
        if (finished) { client.stop(); }
    }
}
