package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Native comparison and fixture construction only; mixed queries/action/ticking belong to production. */
final class JustDireGroundStompTestSupport {
    private static Object stomp;
    private static Method action, cooldown;

    private JustDireGroundStompTestSupport() {
    }

    static void initializeApi() throws ReflectiveOperationException {
        var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
        stomp = ability.getField("GROUNDSTOMP").get(null);
        action = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.AbilityMethods")
                      .getMethod("groundstomp", Level.class, net.minecraft.world.entity.player.Player.class, ItemStack.class);
        cooldown = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool")
                        .getMethod("getAnyCooldown", ItemStack.class, ability);
        require((int) ability.getMethod("getFeCost").invoke(stomp) == 5_000
                        && (int) ability.getMethod("getDurabilityCost").invoke(stomp) == 25, "Native costs changed");
    }

    static NativeResult nativeUse(ServerPlayer player, ItemStack boot, double[] distances) throws ReflectiveOperationException {
        player.setItemSlot(EquipmentSlot.FEET, boot);
        List<Mob> targets = new ArrayList<>();
        try {
            for (double distance : distances) {
                var mob = EntityType.ZOMBIE.create(player.level(), EntitySpawnReason.COMMAND);
                require(mob != null, "Could not create native target");
                mob.setNoAi(true);
                mob.setNoGravity(true);
                mob.setPos(player.getX() + distance, player.getY(), player.getZ());
                mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
                require(player.level().addFreshEntity(mob)
                                && player.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox()).contains(mob),
                        "Native target is not visible to actual world query");
                targets.add(mob);
            }
            boolean returned = (boolean) action.invoke(null, player.level(), player, boot);
            List<Double> motions = targets.stream().map(mob -> mob.getDeltaMovement().horizontalDistance()).toList();
            return new NativeResult(returned, motions.isEmpty() ? 0 : motions.getFirst(), motions);
        } finally {
            targets.forEach(Mob::discard);
        }
    }

    static ServerPlayer player(MinecraftServer server, GameType mode) {
        var player = NeoForgeJustDireNegateFallTest.player(server, mode);
        server.overworld().getChunk(0, 0);
        player.setPos(8, 100, 8);
        player.setOnGround(false);
        player.setDeltaMovement(Vec3.ZERO);
        return player;
    }

    static ItemStack boots(String tier, int charge) {
        var stack = NeoForgeJustDireResourcesTest.boots(tier, charge);
        JustDireAttributeTestSupport.set(stack, "groundstomp_upgrade_installed", true);
        return stack;
    }

    static int timer(ItemStack stack) throws ReflectiveOperationException {
        return (int) cooldown.invoke(null, stack, stomp);
    }

    private static void require(boolean condition, String text) {
        if (!condition) {
            throw new AssertionError(text);
        }
    }

    record NativeResult(boolean returned, double motion, List<Double> motions) {
    }
}
