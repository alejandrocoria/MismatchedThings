package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Real incoming hits, resource transfers and XP pickup; no substitute wear/payment implementation. */
final class JustDireArmorWearTestSupport {
    private JustDireArmorWearTestSupport() {
    }

    static void hit(ServerPlayer player, int remaining, int addedWear) {
        var root = player.getItemBySlot(EquipmentSlot.FEET);
        var before = root.copy();
        var server = player.level().getServer();
        var difficulty = player.level().getDifficulty();
        var motion = player.getDeltaMovement();
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        server.setDifficulty(Difficulty.NORMAL, true);
        try {
            require(player.hurtServer(player.level(), player.damageSources().mobAttack(new Zombie(EntityType.ZOMBIE, player.level())), 8),
                    "Lifecycle incoming hit rejected: clientLoaded=" + player.connection.hasClientLoaded()
                            + ", changingDimension=" + player.isChangingDimension() + ", mode=" + player.gameMode.getGameModeForPlayer());
            require(player.getItemBySlot(EquipmentSlot.FEET) == root, "Incoming hit replaced the equipped root");
            state(before, root, remaining, before.getDamageValue() + addedWear);
        } finally {
            server.setDifficulty(difficulty, true);
            player.setHealth(player.getMaxHealth());
            player.invulnerableTime = 0;
            player.setDeltaMovement(motion);
        }
        // Do not replace equipment or force a broadcast: normal slot/equipment ticks must deliver the debit.
    }

    static void recharge(ServerPlayer player, int amount) {
        var root = player.getItemBySlot(EquipmentSlot.FEET);
        var before = root.copy();
        var donor = NeoForgeJustDireResourcesTest.boots("eclipsealloy", amount);
        require(EnergyHandlerUtil.move(JustDireResourceTestSupport.handler(donor), JustDireResourceTestSupport.handler(root), amount, null) == amount
                        && charge(donor) == 0, "Native donor transfer did not conserve FE");
        state(before, root, charge(before) + amount, before.getDamageValue());
    }

    static void extract(ServerPlayer player, int amount) {
        var root = player.getItemBySlot(EquipmentSlot.FEET);
        var before = root.copy();
        try (var transaction = Transaction.openRoot()) {
            require(JustDireResourceTestSupport.handler(root).extract(amount, transaction) == amount, "Lifecycle extraction failed");
            transaction.commit();
        }
        state(before, root, charge(before) - amount, before.getDamageValue());
    }

    static void mend(ServerPlayer player) {
        var root = player.getItemBySlot(EquipmentSlot.FEET);
        root.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
        var before = root.copy();
        var orb = new ExperienceOrb(EntityType.EXPERIENCE_ORB, player.level());
        orb.setValue(5);
        orb.playerTouch(player);
        require(root.getDamageValue() < before.getDamageValue(), "Actual XP pickup did not repair prior root wear");
        state(before, root, charge(before), root.getDamageValue());
    }

    private static int charge(ItemStack root) {
        return root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0);
    }

    private static void state(ItemStack before, ItemStack root, int energy, int damage) {
        var allowed = before.copy();
        allowed.set(JustDireResourceTestSupport.energyComponent(), energy);
        allowed.setDamageValue(damage);
        require(ItemStack.matches(allowed, root),
                "Lifecycle FE/wear/history/control mismatch: FE=" + charge(root) + ", wear=" + root.getDamageValue());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
