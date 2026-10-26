package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Incoming armor wear only; ability/direct durability consumers retain their own payment rules. */
public final class JustDireArmorWearCompatibility {
    private static final Set<ServerPlayer> executing = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean queryFailed;

    private JustDireArmorWearCompatibility() {
    }

    public static void route(boolean ready, ItemStack root, int amount, LivingEntity entity, EquipmentSlot slot,
                             Operation<Void> ordinary, float originalDamage) {
        if (!ready || !consume(root, amount, entity, slot, originalDamage)) {
            ordinary.call(root, amount, entity, slot);
        }
    }

    private static boolean consume(ItemStack root, int amount, LivingEntity entity, EquipmentSlot slot, float originalDamage) {
        if (queryFailed || slot != EquipmentSlot.FEET || !(entity instanceof ServerPlayer player)
                || !(root.getItem() instanceof MixedBootsItem) || !equipped(player, root) || !(originalDamage > 0)) {
            return false;
        }
        if (amount <= 0 || !root.isDamageableItem() || player.hasInfiniteMaterials()
                || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            return false;
        }
        if (!executing.add(player)) {
            return true;
        }

        try {
            var before = root.copy();
            Quote quote;
            try {
                var profile = JustDireResourcesCompatibility.wearProfile(root);
                if (profile == null || profile.providers() == 0) {
                    return false;
                }

                int level = root.getEnchantmentLevel(player.registryAccess()
                                                           .lookupOrThrow(Registries.ENCHANTMENT)
                                                           .getOrThrow(Enchantments.UNBREAKING));
                int bill = bill(amount, level);
                if (bill < 0) {
                    return false;
                }

                var access = ItemAccess.forStack(root);
                var energy = access.getCapability(Capabilities.Energy.ITEM);
                if (energy == null) {
                    return false;
                }

                int available = energy.getAmountAsInt();
                if (available < 0) {
                    return false;
                }
                quote = new Quote(profile, access, energy, available, bill);
            } catch (RuntimeException | LinkageError failure) {
                queryFailed = true;
                Constants.LOG.warn("Just Dire Things armor wear query failed; ordinary wear retained", failure);
                return !unchanged(player, root, before);
            }
            if (!unchanged(player, root, before)) {
                return true;
            }
            if (!JustDireResourcesCompatibility.current(quote.profile)) {
                return false;
            }
            if (quote.profile.providers() == 1 && quote.available < quote.bill) {
                return false;
            }

            int debit = Math.min(quote.available, quote.bill);
            if (debit == 0) {
                return true;
            }

            var expected = before.copy();
            expected.set(quote.profile.energy(), quote.available - debit);
            boolean paid = false;
            // ItemAccess stages a resource; extraction has not yet changed the actual equipped root.
            // Never restore a snapshot over an independent edit or retry against replacement equipment.
            try (var transaction = Transaction.openRoot()) {
                int receipt = quote.energy.extract(debit, transaction);
                if (receipt == debit && unchanged(player, root, before) && JustDireResourcesCompatibility.current(quote.profile)
                        && ItemStack.matches(quote.access.getResource().toStack(), expected)) {
                    transaction.commit();
                    paid = true;
                }
            }

            return paid || !unchanged(player, root, before);
        } finally {
            executing.remove(player);
        }
    }

    /** Negative means unsupported, not a free/clamped bill. Keep NeoForge's integer wear quantization. */
    static int bill(int amount, int unbreaking) {
        long raw = (long) amount * 100;
        return amount < 0 || unbreaking < 0 || raw > Integer.MAX_VALUE ? -1
                                                                       : (int) Math.max(0, raw - raw * Math.min(1, unbreaking * .1));
    }

    private static boolean equipped(ServerPlayer player, ItemStack root) {
        return !root.isEmpty() && root.getCount() == 1 && player.getItemBySlot(EquipmentSlot.FEET) == root;
    }

    private static boolean unchanged(ServerPlayer player, ItemStack root, ItemStack before) {
        return equipped(player, root) && ItemStack.matches(root, before);
    }

    private record Quote(JustDireResourcesCompatibility.WearProfile profile, ItemAccess access, EnergyHandler energy, int available, int bill) {
    }
}
