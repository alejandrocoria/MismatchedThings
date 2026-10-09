package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Test-only candidate boundary: production factory/menu paths do not call this prototype. */
final class JustDireResourceSpike {
    private JustDireResourceSpike() {}

    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> {
            int capacity = capacity(stack);
            // ItemAccess capability lookup builds a count-one query stack, not the slot's actual count.
            return access.getAmount() == 1 && capacity > 0 && validCharge(stack, capacity)
                    ? new LiveHandler(access) : null;
        },
                BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID));
    }

    static MixedBootsPair createPair(ItemStack first, ItemStack second) {
        int charge = (int) (((long) sourceCharge(first) + sourceCharge(second)) / 2);
        var pair = MixedBootsFactory.createPair(first, second);
        initialize(pair.primary(), charge);
        initialize(pair.inverse(), charge);
        return pair;
    }

    static ItemStack recompose(ItemStack root, ItemStack left, ItemStack right) {
        int oldCapacity = capacity(root);
        if (!validCharge(root, oldCapacity)) { throw new IllegalArgumentException("Invalid live spike charge"); }
        int currentCharge = root.getOrDefault(energyComponent(), 0);
        boolean present = root.has(energyComponent());
        ItemStack result = MixedBootsRecomposer.recompose(root, left, right);
        int newCapacity = capacity(result);
        // Restore after the generic projection even when live charge equals its old owner value.
        // Explicit absence is empty state, not permission to initialize from a stored source.
        if (newCapacity > 0 && present) {
            result.set(energyComponent(), Math.min(currentCharge, newCapacity));
        } else {
            result.remove(energyComponent());
        }
        return result;
    }

    static EnergyHandler handler(ItemStack stack) {
        return ItemAccess.forStack(stack).getCapability(Capabilities.Energy.ITEM);
    }

    @SuppressWarnings("unchecked")
    static DataComponentType<Integer> energyComponent() {
        return (DataComponentType<Integer>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(Identifier.fromNamespaceAndPath("justdirethings", "forge_energy")).orElseThrow().value();
    }

    private static void initialize(ItemStack root, int charge) {
        if (capacity(root) > 0) { root.set(energyComponent(), charge); }
        else { root.remove(energyComponent()); }
    }

    private static boolean poweredSource(ItemStack source) {
        Identifier id = BuiltInRegistries.ITEM.getKey(source.getItem());
        return id.equals(Identifier.fromNamespaceAndPath("justdirethings", "celestigem_boots"))
                || id.equals(Identifier.fromNamespaceAndPath("justdirethings", "eclipsealloy_boots"));
    }

    private static int sourceCapacity(ItemStack source) {
        if (!poweredSource(source)) { return 0; }
        EnergyHandler handler = handler(source);
        if (handler == null || handler.getCapacityAsLong() <= 0
                || handler.getCapacityAsLong() > Integer.MAX_VALUE
                || handler.getAmountAsLong() < 0 || handler.getAmountAsLong() > handler.getCapacityAsLong()) {
            throw new IllegalArgumentException("Invalid native spike energy source");
        }
        return handler.getCapacityAsInt();
    }

    private static int sourceCharge(ItemStack source) {
        return sourceCapacity(source) > 0 ? handler(source).getAmountAsInt() : 0;
    }

    private static int capacity(ItemStack root) {
        if (root.isEmpty() || root.getCount() != 1 || !(root.getItem() instanceof MixedBootsItem)) { return 0; }
        var data = ModContent.mixedBootsDataType().map(root::get).orElse(null);
        if (data == null) { return 0; }
        ItemStack left = data.left().source();
        ItemStack right = data.right().source();
        if (!BootHalf.isValidSource(left) || !BootHalf.isValidSource(right)) { return 0; }
        try { return Math.max(sourceCapacity(left), sourceCapacity(right)); }
        catch (IllegalArgumentException invalid) { return 0; }
    }

    private static boolean validCharge(ItemStack root, int capacity) {
        int charge = root.getOrDefault(energyComponent(), 0);
        return charge >= 0 && charge <= capacity;
    }

    /** ItemAccess can change resources/tiers without changing the registered MixedBootsItem. */
    private record LiveHandler(ItemAccess access) implements EnergyHandler {
        private EnergyHandler current() {
            ItemStack root = access.getResource().toStack();
            int capacity = capacity(root);
            if (access.getAmount() != 1 || capacity <= 0 || !validCharge(root, capacity)) { return null; }
            return new ItemAccessEnergyHandler(access, energyComponent(), capacity);
        }

        @Override
        public long getAmountAsLong() {
            EnergyHandler handler = current();
            return handler == null ? 0 : handler.getAmountAsLong();
        }

        @Override
        public long getCapacityAsLong() {
            EnergyHandler handler = current();
            return handler == null ? 0 : handler.getCapacityAsLong();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            EnergyHandler handler = current();
            return handler == null ? 0 : handler.insert(amount, transaction);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            EnergyHandler handler = current();
            return handler == null ? 0 : handler.extract(amount, transaction);
        }
    }
}
