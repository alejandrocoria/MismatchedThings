package games.alejandrocoria.mismatchedleggings.test.silentgear;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Observes normal dedicated S2C packets. Never creates effects or touches a server player. */
final class WearerLifecycleClient {
    private static int step;
    private static int waiting;
    private static double nativeSpeed;
    private static double nativeJump;

    static void rejoined(Minecraft client) throws Exception {
        if (Files.exists(StateChecks.report("wearer-rejoin.ok")) || !Files.exists(StateChecks.report("wearer-rejoin.settled"))
                || !WearerLifecycle.snapshot(client.player).equals(StateChecks.read(StateChecks.report("wearer-rejoin.nbt")))) { return; }
        require(++waiting < 120, "Saved worn roots did not resume ordinary effect packets after reconnect");
        if (!WearerLifecycle.effectsMatch(client.player, 3, WearerLifecycle.count(5), true)) { return; }
        waiting = 0;
        Files.writeString(StateChecks.report("wearer-rejoin.ok"), "Original saved worn roots resumed real dedicated effect packets before fixture setup\n");
    }

    static void tick(Minecraft client) throws Exception {
        if (step == 24 || !Files.exists(StateChecks.report("wearer-" + step + ".settled"))
                || !WearerLifecycle.snapshot(client.player).equals(StateChecks.read(StateChecks.report("wearer-" + step + ".nbt")))) { return; }
        int family = step / 6, mode = step % 6;
        int count = WearerLifecycle.count(mode);
        require(++waiting < 120, "Dedicated effect packets did not converge: " + family + "/" + mode + "; " + client.player.getActiveEffects());
        if (!WearerLifecycle.effectsMatch(client.player, family, count, mode != 4)) { return; }
        waiting = 0;
        require(client.player.hasEffect(MobEffects.LUCK), "Client lost unrelated ordinary potion");
        var before = WearerLifecycle.snapshot(client.player);
        if (family == 3) {
            double speed = client.player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            double jump;
            var movement = client.player.getDeltaMovement();
            try {
                client.player.setDeltaMovement(Vec3.ZERO);
                client.player.jumpFromGround();
                jump = client.player.getDeltaMovement().y;
            } finally { client.player.setDeltaMovement(movement); }
            int speedLevel = mode == 4 ? 0 : Math.max(0, count - 1);
            int jumpLevel = mode == 4 ? 0 : Math.max(1, count);
            require(Math.abs(speed / client.player.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() - (1 + .2 * speedLevel)) < 1E-6,
                    "Received Speed did not change actual movement attribute");
            if (mode == 0) { nativeSpeed = speed; nativeJump = jump; }
            require(Math.abs(jump - (nativeJump - .1 * (4 - jumpLevel))) < 1E-6, "Received Jump Boost differs from native jump control");
            if (mode == 1 || mode == 2) { require(Math.abs(speed - nativeSpeed) < 1E-6, "Mixed movement differs from native full set"); }
        }
        if (family == 3 && (mode == 1 || mode == 2)) {
            StateRendering.verify(client, java.util.List.of(client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS)));
        }
        require(before.equals(WearerLifecycle.snapshot(client.player)), "Client wearer observation wrote item state");
        Files.writeString(StateChecks.report("wearer-" + step + ".ok"), WearerLifecycle.FAMILIES.get(family) + "/" + mode
                + ": ordinary effects/equipment packets, native movement/jump and unchanged items\n");
        step++;
    }
}
