package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.Future;

/** A real hidden client's local jump, separately from the server's movement/damage gate. */
final class NeoForgeJustDireJumpClientTest {
    private static int ticks;
    private static boolean finished;
    private static boolean directChecked;
    private static int flightCase;
    private static int flightTicks;
    private static int settled;
    private static int waitingTicks;
    private static boolean apexChecked;
    private static double peak;
    private static double ordinaryHeight;
    private static double nativeHeight;
    private static ItemStack equipped;
    private static Future<?> job;

    static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            if (++ticks > 3600) { throw new AssertionError("Jump client timed out; screen="
                    + (client.screen == null ? "none" : client.screen.getClass().getName())
                    + "; flight=" + flightCase + "; settled=" + settled); }
            if (client.player == null || client.level == null || client.screen != null
                    || client.getCameraEntity() != client.player || !CompositionRulesClientLifecycle.areRulesSynchronized()) {
                return;
            }
            if (directChecked) {
                flight(client);
                if (finished) { client.stop(); }
                return;
            }
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
            directChecked = true;
            player.input = new KeyboardInput(client.options);
            client.options.pauseOnLostFocus = false;
            prepareFlight(client);
        } catch (Throwable failure) {
            finished = true;
            NetworkAcceptanceFiles.failure("just-dire-jump-client", failure);
        }
        if (finished) { client.stop(); }
    }

    private static void flight(Minecraft client) throws Exception {
        var player = client.player;
        if (job != null) {
            if (!job.isDone()) { return; }
            job.get(); job = null;
        }
        if (flightTicks == 0) {
            if (++waitingTicks > 400) { throw new AssertionError("Flight setup did not synchronize at case " + flightCase
                    + "; feetMatch=" + ItemStack.matches(player.getItemBySlot(EquipmentSlot.FEET), equipped)
                    + "; ground=" + player.onGround() + "; y=" + player.getY()
                    + "; FE=" + player.getItemBySlot(EquipmentSlot.FEET).getOrDefault(JustDireResourceTestSupport.energyComponent(), 0)
                    + "; armor=" + player.getAttributeValue(Attributes.ARMOR)
                    + "; expected=" + equipped.getAttributeModifiers()); }
            if (!ItemStack.matches(player.getItemBySlot(EquipmentSlot.FEET), equipped)
                    || !player.onGround() || Math.abs(player.getY() - 81) > 1e-5
                    || player.getAbilities().flying) { settled = 0; return; }
            // Only syncable attributes have a client value; knockback resistance is server-only.
            for (var attribute : List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS)) {
                double modifier = equipped.getAttributeModifiers().modifiers().stream()
                        .filter(entry -> entry.attribute().equals(attribute)).mapToDouble(entry -> entry.modifier().amount()).sum();
                if (Math.abs(player.getAttributeValue(attribute) - player.getAttributeBaseValue(attribute) - modifier) > 1e-7) {
                    settled = 0; return;
                }
            }
            if (++settled < 15) { return; }
            client.options.keyJump.setDown(true);
            flightTicks = 1;
            settled = 0;
            apexChecked = false;
            peak = 0;
            return;
        }
        client.options.keyJump.setDown(false);
        flightTicks++;
        peak = Math.max(peak, player.getY() - 81);
        if (flightTicks > 80) { throw new AssertionError("Keyboard did not complete jump case " + flightCase + "; peak=" + peak); }
        if (!apexChecked) {
            if (peak <= 0 || player.getDeltaMovement().y > 0) { return; }
            if (flightCase == 0) { ordinaryHeight = peak; }
            else if (flightCase == 1) {
                nativeHeight = peak;
                if (nativeHeight <= ordinaryHeight + 0.2) { throw new AssertionError("Native Ferricore did not boost actual jump height"); }
            } else if (Math.abs(peak - nativeHeight) > 1e-5) {
                throw new AssertionError("Hybrid actual jump height differs at case " + flightCase + ": height=" + peak
                        + " native=" + nativeHeight + " ordinary=" + ordinaryHeight);
            }
            if (!ItemStack.matches(player.getItemBySlot(EquipmentSlot.FEET), equipped)) {
                throw new AssertionError("Ascent mutated equipped boots at case " + flightCase);
            }
            apexChecked = true;
        }
        // Finish the real landing before changing FE: Negate can legitimately bill that landing.
        if (!player.onGround()) { settled = 0; return; }
        if (++settled < 10) { return; }
        if (++flightCase < 8) { prepareFlight(client); return; }
        NetworkAcceptanceFiles.write("just-dire-jump-client-report.txt", "6 direct native/mixed client jump cases; "
                + "8 actual keyboard flights with integrated-server equipment packets and normal movement ticks; "
                + "ordinary/native Ferricore height controls; Eclipse Alloy Negate + Ferricore passive Jump only; "
                + "both orientations, in-place depletion/recharge and natural attribute packets; native-equivalent height; "
                + "ordinaryHeight=" + ordinaryHeight + "; nativeHeight=" + nativeHeight
                + "; unchanged ascent stacks; hidden/muted client\n");
        NetworkAcceptanceFiles.write("just-dire-jump-client-success.txt", "neoforge\n");
        finished = true;
    }

    private static void prepareFlight(Minecraft client) {
        client.options.keyJump.setDown(false);
        flightTicks = 0; settled = 0; waitingTicks = 0;
        if (flightCase == 0) { equipped = new ItemStack(Items.IRON_BOOTS); }
        else if (flightCase == 1) { equipped = NeoForgeJustDireJumpTest.boots("ferricore", true); }
        else if (flightCase == 2 || flightCase == 5) {
            var pair = MixedBootsFactory.createPair(NeoForgeJustDireNegateFallTest.boots("eclipsealloy", 6_000),
                    NeoForgeJustDireJumpTest.boots("ferricore", true));
            equipped = flightCase < 5 ? pair.primary() : pair.inverse();
        }
        boolean balanceChange = flightCase == 3 || flightCase == 4 || flightCase == 6 || flightCase == 7;
        if (flightCase >= 2) {
            equipped = equipped.copy();
            equipped.set(JustDireResourceTestSupport.energyComponent(), (flightCase - 2) % 3 == 1 ? 0 : 3_000);
        }
        var server = client.getSingleplayerServer();
        var id = client.player.getUUID();
        job = server.submit(() -> {
            var owner = server.getPlayerList().getPlayer(id);
            if (flightCase == 0) { owner.getInventory().clearContent(); }
            owner.removeAllEffects();
            owner.setGameMode(GameType.SURVIVAL);
            if (balanceChange) {
                // Keep the actual equipped root: ordinary ticks must refresh armor after its FE changes.
                owner.getItemBySlot(EquipmentSlot.FEET).set(JustDireResourceTestSupport.energyComponent(),
                        equipped.get(JustDireResourceTestSupport.energyComponent()));
            } else { owner.setItemSlot(EquipmentSlot.FEET, equipped.copy()); }
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    server.overworld().setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3);
                    for (int y = 81; y < 88; y++) {
                        server.overworld().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            owner.setDeltaMovement(Vec3.ZERO);
            owner.connection.teleport(0.5, 81, 0.5, 0, 0);
        });
    }
}
