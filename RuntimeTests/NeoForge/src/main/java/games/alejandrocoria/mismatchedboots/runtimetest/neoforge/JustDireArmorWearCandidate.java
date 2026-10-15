package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Disposable consumer spike, deliberately absent from the production artifact. */
public final class JustDireArmorWearCandidate {
    private static final Set<ServerPlayer> executing = Collections.newSetFromMap(new IdentityHashMap<>());
    static Runnable afterExtract;
    static int interceptions, payments;

    private JustDireArmorWearCandidate() {}

    /** Equivalent ordinary path used only to remove the actual injection target in a fault JVM. */
    public static void ordinary(ItemStack root, int amount, LivingEntity entity, EquipmentSlot slot) {
        root.hurtAndBreak(amount, entity, slot);
    }

    /** True consumes this request; false delegates to the original ordinary wear path. */
    public static boolean consume(ItemStack root, int amount, LivingEntity entity, EquipmentSlot slot, float originalDamage) {
        if (!Boolean.getBoolean("mismatchedboots.runtimetest.armorWearConsumerReady")
                || slot != EquipmentSlot.FEET || !(entity instanceof ServerPlayer player)
                || !(root.getItem() instanceof MixedBootsItem) || root.getCount() != 1
                || player.getItemBySlot(slot) != root || originalDamage <= 0) { return false; }
        var data = ModContent.getMixedBootsData(root).orElse(null);
        if (data == null) { return false; }
        var left = data.left().source(); var right = data.right().source();
        if (!BootHalf.isValidSource(left) || !BootHalf.isValidSource(right)) { return false; }
        int providers = (powered(left) ? 1 : 0) + (powered(right) ? 1 : 0);
        if (providers == 0) { return false; }
        interceptions++;
        // Ordinary no-damage/immunity checks precede quoting. A revived originally protected entry
        // delegates above: do not invent an FE bill or override that foreign event decision.
        if (amount <= 0 || !root.isDamageableItem() || player.hasInfiniteMaterials()
                || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) { return true; }
        // Synchronous recursive calls cannot spend the same root twice while its debit is staged.
        if (!executing.add(player)) { return true; }
        try {
            // Prototype choice: scale the actual integer consumer amount, not the earlier event float.
            long raw = (long) amount * 100;
            if (raw > Integer.MAX_VALUE) { return false; }
            int unbreaking = root.getEnchantmentLevel(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.UNBREAKING));
            if (unbreaking < 0) { return false; }
            int bill = (int) Math.max(0, raw - raw * Math.min(1, unbreaking * .1));
            var before = root.copy();
            var access = ItemAccess.forStack(root);
            var energy = access.getCapability(Capabilities.Energy.ITEM);
            if (energy == null) { return false; }
            int available = energy.getAmountAsInt();
            if (providers == 1 && available < bill) { return false; }
            int debit = Math.min(available, bill);
            if (debit == 0) { return true; }
            var staged = before.copy(); staged.set(JustDireResourceTestSupport.energyComponent(), available - debit);
            try (var transaction = Transaction.openRoot()) {
                if (energy.extract(debit, transaction) != debit) { return false; }
                Runnable callback = afterExtract; afterExtract = null;
                if (callback != null) { callback.run(); }
                // Stack access stages a resource, not the live root. Abort without copying a snapshot
                // over an independent live edit or applying fallback wear to a detached old stack.
                if (player.getItemBySlot(slot) != root || !ItemStack.matches(root, before)
                        || !ItemStack.matches(access.getResource().toStack(), staged)) { return true; }
                transaction.commit();
            }
            payments++;
            return true;
        } finally { executing.remove(player); }
    }

    private static boolean powered(ItemStack source) {
        return source.is(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", "celestigem_boots")))
                || source.is(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", "eclipsealloy_boots")));
    }
}
