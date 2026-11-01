package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** No integrated-server access: compare complete snapshots received over actual TCP. */
final class RecoveryClientChecks {
    private static int ticks;
    private static int state;
    private static boolean finished;

    private RecoveryClientChecks() {
    }

    static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 3600, "TCP recovery client timed out at " + state + ": " + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                            && client.options.getSoundSourceVolume(SoundSource.MASTER) == 0,
                    "Recovery client must stay hidden/muted");
            require(!Files.exists(RecoveryChecks.report("server.failure.txt")), "Dedicated recovery server failed");
            if (client.player == null || client.level == null) {
                return;
            }
            require(client.getSingleplayerServer() == null, "Recovery acceptance accidentally used an integrated server");
            if (state == 0 && matches(client, "initial.nbt")) {
                var worn = client.player.getItemBySlot(EquipmentSlot.LEGS);
                boolean absent = RecoveryFixture.stage().equals("absent");
                require(LeggingsContent.suspendedLeggingsData(worn).isPresent() == absent, "Wrong TCP suspension state");
                if (absent) {
                    RecoveryChecks.inactive(worn);
                    require(client.player.getArmorValue() == 0, "TCP shell still supplies armor");
                }
                Files.writeString(RecoveryChecks.report("client-initial.ok"), "full inventory/equipment snapshot matched\n");
                state = 1;
            } else if (state == 1 && RecoveryFixture.stage().equals("absent") && client.player.isDeadOrDying()) {
                client.player.connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                state = 2;
            } else if (state == 2 && !client.player.isDeadOrDying() && matches(client, "final.nbt")) {
                RecoveryChecks.inactive(client.player.getItemBySlot(EquipmentSlot.LEGS));
                Files.writeString(RecoveryChecks.report("client-final.ok"), "native respawn/pickup/re-equip snapshot matched\n");
                state = 3;
            }
            if (state != 0 && Files.exists(RecoveryChecks.report("finish.txt"))) {
                finished = true;
                SkeletonChecks.passed("client");
                client.stop();
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static boolean matches(Minecraft client, String name) throws Exception {
        var file = RecoveryChecks.report(name);
        if (!Files.exists(RecoveryChecks.report(name + ".ready"))) {
            return false;
        }
        var expected = RecoveryChecks.read(file);
        return expected.get("legs").equals(RecoveryChecks.encode(client.player.getItemBySlot(EquipmentSlot.LEGS), client.level.registryAccess()))
                && expected.get("inventory").equals(RecoveryChecks.encode(client.player.getInventory().getItem(0), client.level.registryAccess()));
    }
}
