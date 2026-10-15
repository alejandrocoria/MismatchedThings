package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Root-owned FE only. Does not impersonate PoweredItem or forward damage/ability consumers. */
public final class JustDireResourcesCompatibility {
    private static volatile Api api;

    private JustDireResourcesCompatibility() {}

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("justdirethings")) { bus.addListener(JustDireResourcesCompatibility::register); }
    }

    private static void register(RegisterCapabilitiesEvent event) {
        try {
            Class<?> powered = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.PoweredItem");
            Item celestigem = item("celestigem_boots");
            Item eclipsealloy = item("eclipsealloy_boots");
            if (!powered.isInstance(celestigem) || !powered.isInstance(eclipsealloy)) {
                throw new IllegalStateException("Unsupported powered boot API");
            }
            DataComponentType<Integer> energy = energyComponent();
            if (energy == null || energy.codec() == null || !(energy.codec().parse(JsonOps.INSTANCE, new JsonPrimitive(0))
                    .getOrThrow() instanceof Integer)) {
                throw new IllegalStateException("Unsupported native FE component");
            }
            api = new Api(energy, celestigem, eclipsealloy, MethodHandles.publicLookup()
                    .findVirtual(powered, "getMaxEnergy", MethodType.methodType(int.class))
                    .asType(MethodType.methodType(int.class, Item.class)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Constants.LOG.warn("Could not bind Just Dire Things storage API; compatibility disabled", failure);
            return;
        }
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> {
            Api binding = api;
            int capacity = binding == null ? 0 : rootCapacity(stack, binding);
            // Lookup uses a count-one ItemResource, not the access's actual slot amount.
            return api == binding && capacity > 0 && access.getAmount() == 1 && validCharge(stack, binding, capacity)
                    ? new LiveHandler(access) : null;
        }, BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID));
    }

    public static void initializeCharge(ItemStack output) {
        Api binding = api;
        if (binding == null) { return; }
        MixedBootsData data = output.get(ModContent.mixedBootsDataType().orElseThrow());
        ItemStack left = data.left().source();
        ItemStack right = data.right().source();
        int leftCapacity = sourceCapacity(left, binding);
        int rightCapacity = sourceCapacity(right, binding);
        if (api != binding) { return; } // Optional read failed before charge validation or mutation.
        int charge = (int) (((long) sourceCharge(left, binding, leftCapacity)
                + sourceCharge(right, binding, rightCapacity)) / 2);
        if (Math.max(leftCapacity, rightCapacity) > 0) { output.set(binding.energy, charge); }
    }

    public static void preserveCharge(ItemStack previous, ItemStack output) {
        Api binding = api;
        if (binding == null) { return; }
        int oldCapacity = rootCapacity(previous, binding);
        int newCapacity = rootCapacity(output, binding);
        var data = output.get(ModContent.mixedBootsDataType().orElseThrow());
        // Validate native source state, but never initialize live charge from it during recomposition.
        ItemStack left = data.left().source();
        ItemStack right = data.right().source();
        int leftCapacity = sourceCapacity(left, binding);
        int rightCapacity = sourceCapacity(right, binding);
        if (api != binding) { return; }
        sourceCharge(left, binding, leftCapacity);
        sourceCharge(right, binding, rightCapacity);
        if (oldCapacity == 0 && newCapacity == 0) { return; }
        if (!validCharge(previous, binding, oldCapacity) && oldCapacity > 0) {
            throw new IllegalArgumentException("Invalid live Just Dire Things FE charge");
        }
        // Equality with an old owner projection, zero and explicit absence are all live state.
        // An unrelated root's forged FE is not seed energy when it gains its first powered source.
        if (newCapacity > 0 && oldCapacity > 0 && previous.has(binding.energy)) {
            output.set(binding.energy, Math.min(previous.get(binding.energy), newCapacity));
        } else { output.remove(binding.energy); }
    }

    /** Wear reuses storage's audited identities/schema; it must not bind a second optional wallet. */
    static WearProfile wearProfile(ItemStack root) {
        Api binding = api;
        if (binding == null || rootCapacity(root, binding) <= 0 || api != binding) { return null; }
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        int providers = 0;
        for (var source : java.util.List.of(data.left().source(), data.right().source())) {
            if (source.getItem() == binding.celestigem || source.getItem() == binding.eclipsealloy) { providers++; }
        }
        return new WearProfile(providers, binding.energy, binding);
    }

    static boolean current(WearProfile profile) { return api == profile.binding; }

    record WearProfile(int providers, DataComponentType<Integer> energy, Object binding) {}

    private static int rootCapacity(ItemStack root, Api binding) {
        if (root.isEmpty() || root.getCount() != 1 || !(root.getItem() instanceof MixedBootsItem)) { return 0; }
        var data = ModContent.mixedBootsDataType().map(root::get).orElse(null);
        if (data == null) { return 0; }
        ItemStack left = data.left().source();
        ItemStack right = data.right().source();
        if (!BootHalf.isValidSource(left) || !BootHalf.isValidSource(right)) { return 0; }
        return Math.max(sourceCapacity(left, binding), sourceCapacity(right, binding));
    }

    private static int sourceCapacity(ItemStack source, Api binding) {
        if (source.getItem() != binding.celestigem && source.getItem() != binding.eclipsealloy) { return 0; }
        try {
            int capacity = (int) binding.capacity.invokeExact(source.getItem());
            if (capacity <= 0) { throw new IllegalStateException("Invalid native FE capacity"); }
            return capacity;
        } catch (Throwable failure) {
            if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) { throw fatal; }
            synchronized (JustDireResourcesCompatibility.class) {
                if (api != null) {
                    api = null;
                    Constants.LOG.warn("Just Dire Things capacity query failed; storage compatibility disabled", failure);
                }
            }
            return 0;
        }
    }

    private static int sourceCharge(ItemStack source, Api binding, int capacity) {
        if (capacity == 0) { return 0; }
        if (!validCharge(source, binding, capacity)) { throw new IllegalArgumentException("Invalid native Just Dire Things FE charge"); }
        return source.getOrDefault(binding.energy, 0);
    }

    private static boolean validCharge(ItemStack stack, Api binding, int capacity) {
        int charge = stack.getOrDefault(binding.energy, 0);
        return charge >= 0 && charge <= capacity;
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value();
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<Integer> energyComponent() {
        return (DataComponentType<Integer>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(Identifier.fromNamespaceAndPath("justdirethings", "forge_energy")).orElseThrow().value();
    }

    private record Api(DataComponentType<Integer> energy, Item celestigem, Item eclipsealloy, MethodHandle capacity) {}

    /** A retained handler follows the current resource/tier/count of its access, never a captured stack. */
    private record LiveHandler(ItemAccess access) implements EnergyHandler {
        private EnergyHandler current() {
            Api binding = api;
            if (binding == null || access.getAmount() != 1) { return null; }
            ItemStack root = access.getResource().toStack();
            int capacity = rootCapacity(root, binding);
            return api == binding && capacity > 0 && validCharge(root, binding, capacity)
                    ? new ItemAccessEnergyHandler(access, binding.energy, capacity) : null;
        }
        @Override public long getAmountAsLong() { var handler = current(); return handler == null ? 0 : handler.getAmountAsLong(); }
        @Override public long getCapacityAsLong() { var handler = current(); return handler == null ? 0 : handler.getCapacityAsLong(); }
        @Override public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            var handler = current();
            return handler == null ? 0 : handler.insert(amount, transaction);
        }
        @Override public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            var handler = current();
            return handler == null ? 0 : handler.extract(amount, transaction);
        }
    }
}
