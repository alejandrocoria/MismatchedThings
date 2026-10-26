package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** One live-root payment, authorized by root installation, native tier and final fall-flow permission. */
final class JustDireNegateFallCompatibility {
    private static Api api;
    private static boolean attempted;
    private static boolean invalidBillWarned;

    private JustDireNegateFallCompatibility() {
    }

    static boolean tryNegate(LivingFallEvent event, ItemStack root) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !equipped(player, root)
                || !(root.getItem() instanceof MixedBootsItem)) {
            return false;
        }

        var data = ModContent.suspendedBootsData(root).isPresent() ? null
                                                                   : ModContent.mixedBootsDataType().map(root::get).orElse(null);
        if (data == null) {
            return false;
        }

        ItemStack left = data.left().source();
        ItemStack right = data.right().source();
        if (!BootHalf.isValidSource(left) || !BootHalf.isValidSource(right)) {
            return false;
        }

        Api binding = binding();
        if (binding == null) {
            return false;
        }

        Quote quote;
        try {
            // All optional native calls are read-only and precede both FE and event mutation.
            // Preserve native continuation's interface rejection even if another mod alters the item class.
            if (binding.tool.isInstance(root.getItem())) {
                return false;
            }
            if (!binding.usable(left, root) && !binding.usable(right, root)) {
                return false;
            }

            int base = (int) binding.cost.invokeExact(binding.negate);
            double distance = event.getDistance();
            if (base <= 0 || !Double.isFinite(distance) || distance < 0 || distance > Integer.MAX_VALUE
                    || (long) base * (int) distance > Integer.MAX_VALUE) {
                invalidBill();
                return false;
            }

            EnergyHandler energy = ItemAccess.forStack(root).getCapability(Capabilities.Energy.ITEM);
            if (energy == null) {
                return false;
            }

            int available = energy.getAmountAsInt();
            if (available < base) {
                return false;
            }

            int unbreaking = root.getEnchantmentLevel(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                                                            .getOrThrow(Enchantments.UNBREAKING));
            if (unbreaking < 0) {
                invalidBill();
                return false;
            }

            int raw = base * (int) distance;
            double discount = Math.min(1.0, unbreaking * 0.1);
            int requested = (int) Math.max(0.0, raw - raw * discount);
            GameType mode = player.gameMode.getGameModeForPlayer();
            quote = new Quote(energy, available, mode == GameType.CREATIVE || mode == GameType.SPECTATOR ? 0 : requested);
        } catch (Throwable failure) {
            if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) {
                throw fatal;
            }
            synchronized (JustDireNegateFallCompatibility.class) {
                if (api != null) {
                    api = null;
                    Constants.LOG.warn("Just Dire Things Negate Fall queries failed; compatibility disabled", failure);
                }
            }
            return false;
        }
        if (!equipped(player, root) || quote.energy.getAmountAsInt() != quote.available) {
            return false;
        }
        if (quote.requested > 0) {
            // Direct access preserves cursed armor: a slot exchange would try to remove the item.
            // No failure catch spans this mutation/commit boundary or the subsequent event update.
            try (var transaction = Transaction.openRoot()) {
                if (quote.energy.extract(quote.requested, transaction) != Math.min(quote.requested, quote.available)
                        || !equipped(player, root)) {
                    return false;
                }
                transaction.commit();
            }
        }
        event.setDistance(0);
        return true;
    }

    private static boolean equipped(ServerPlayer player, ItemStack root) {
        return !root.isEmpty() && root.getCount() == 1 && player.getItemBySlot(EquipmentSlot.FEET) == root;
    }

    private static synchronized void invalidBill() {
        if (!invalidBillWarned) {
            invalidBillWarned = true;
            Constants.LOG.warn("Just Dire Things Negate Fall rejected an unsupported distance/cost; ordinary fall fallback retained");
        }
    }

    private static synchronized Api binding() {
        if (attempted) {
            return api;
        }
        attempted = true;
        try {
            ClassLoader loader = JustDireNegateFallCompatibility.class.getClassLoader();
            Class<?> ability = Class.forName(JustDireJumpShape.ABILITY.replace('/', '.'), true, loader);
            Class<?> tool = Class.forName(JustDireJumpShape.TOOL.replace('/', '.'), true, loader);
            var lookup = MethodHandles.publicLookup();
            api = new Api(tool, ability.getField("NEGATEFALLDAMAGE").get(null),
                          lookup.findVirtual(ability, "getFeCost", MethodType.methodType(int.class))
                                .asType(MethodType.methodType(int.class, Object.class)),
                          lookup.findVirtual(tool, "canUseAbility", MethodType.methodType(boolean.class, ItemStack.class, ability))
                                .asType(MethodType.methodType(boolean.class, Object.class, ItemStack.class, Object.class)),
                          component("tool_enabled"), component("negatefalldamage_toggle"), item("celestigem_boots"), item("eclipsealloy_boots"));
            if (!tool.isInstance(api.celestigem) || !tool.isInstance(api.eclipsealloy)) {
                throw new IllegalStateException("Unsupported powered boot API");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            api = null;
            Constants.LOG.warn("Could not bind Just Dire Things Negate Fall API; compatibility disabled", failure);
        }

        return api;
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value();
    }

    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value();
    }

    private record Quote(EnergyHandler energy, int available, int requested) {
    }

    private record Api(Class<?> tool, Object negate, MethodHandle cost, MethodHandle canUse,
                       DataComponentType<Boolean> enabled, DataComponentType<Boolean> toggle, Item celestigem, Item eclipsealloy) {
        boolean usable(ItemStack source, ItemStack root) throws Throwable {
            if (source.getItem() != celestigem && source.getItem() != eclipsealloy) {
                return false;
            }
            if (!JustDireUpgradesCompatibility.overlayInstallation(source, root)) {
                return false;
            }
            source.set(enabled, root.getOrDefault(enabled, true));
            source.set(toggle, root.getOrDefault(toggle, true));
            return (boolean) canUse.invokeExact((Object) source.getItem(), source, negate);
        }
    }
}
