package games.alejandrocoria.mismatchedleggings.compatibility.justdirethings;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
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
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** One transactional root wallet. Storage does not turn the mixed item into a native PoweredItem. */
public final class JustDireResources {
    private static volatile Api api;

    private JustDireResources() {
    }

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("justdirethings")) {
            bus.addListener(JustDireResources::register);
        }
    }

    private static void register(RegisterCapabilitiesEvent event) {
        try {
            var powered = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.PoweredItem");
            var celestigem = BuiltInRegistries.ITEM.get(JustDireState.id("celestigem_leggings")).orElseThrow().value();
            var eclipse = BuiltInRegistries.ITEM.get(JustDireState.id("eclipsealloy_leggings")).orElseThrow().value();
            DataComponentType<Integer> energy = JustDireState.component("forge_energy");
            if (!powered.isInstance(celestigem) || !powered.isInstance(eclipse) || energy.codec() == null || energy.streamCodec() == null
                    || !(energy.codec().parse(JsonOps.INSTANCE, new JsonPrimitive(0)).getOrThrow() instanceof Integer)) {
                throw new IllegalStateException("Unsupported native leggings storage API");
            }
            api = new Api(energy, celestigem, eclipse, MethodHandles.publicLookup()
                    .findVirtual(powered, "getMaxEnergy", MethodType.methodType(int.class))
                    .asType(MethodType.methodType(int.class, Item.class)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            LeggingsContent.LOG.warn("Could not bind Just Dire Things leggings storage; FE compatibility disabled", failure);
            return;
        }
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> {
            Api binding = api;
            int capacity = binding == null ? 0 : rootCapacity(stack, binding);
            // Capability lookup stacks have count one; the access still owns the actual slot amount.
            return api == binding && capacity > 0 && access.getAmount() == 1 && validCharge(stack, binding, capacity)
                    ? new LiveHandler(access) : null;
        }, BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID));
    }

    static void prepare(@Nullable ItemStack previous, ItemStack output, MixedLeggingsData data) {
        Api binding = api;
        if (binding == null) {
            if (previous != null) {
                BuiltInRegistries.DATA_COMPONENT_TYPE.get(JustDireState.id("forge_energy"))
                        .ifPresent(holder -> preserve(previous, output, holder.value()));
            } else if (java.util.List.of(data.left().source(), data.right().source()).stream().anyMatch(source -> {
                var id = BuiltInRegistries.ITEM.getKey(source.getItem());
                return id.equals(JustDireState.id("celestigem_leggings")) || id.equals(JustDireState.id("eclipsealloy_leggings"));
            })) {
                throw new IllegalArgumentException("Cannot initialize powered leggings without native storage");
            }
            return;
        }
        var left = data.left().source();
        var right = data.right().source();
        int leftCapacity = sourceCapacity(left, binding);
        int rightCapacity = sourceCapacity(right, binding);
        int capacity = Math.max(leftCapacity, rightCapacity);
        // Validate participating history without treating it as the current wallet after creation.
        int leftCharge = sourceCharge(left, binding, leftCapacity);
        int rightCharge = sourceCharge(right, binding, rightCapacity);
        if (previous == null) {
            if (capacity > 0) {
                output.set(binding.energy, (int) (((long) leftCharge + rightCharge) / 2));
            }
            return;
        }
        int oldCapacity = rootCapacity(previous, binding);
        if (api != binding) {
            throw new IllegalArgumentException("Native leggings storage changed during recomposition");
        }
        if (oldCapacity > 0 && !validCharge(previous, binding, oldCapacity)) {
            throw new IllegalArgumentException("Invalid live Just Dire Things leggings charge");
        }
        if (oldCapacity == 0 && capacity == 0) {
            preserve(previous, output, binding.energy);
        } else if (oldCapacity > 0 && capacity > 0 && previous.has(binding.energy)) {
            output.set(binding.energy, Math.min(previous.get(binding.energy), capacity));
        } else {
            // Includes absent/zero live state and gaining a first provider: never import a source refill.
            output.remove(binding.energy);
        }
    }

    private static <T> void preserve(ItemStack previous, ItemStack output, DataComponentType<T> type) {
        output.remove(type);
        if (previous.has(type)) {
            output.set(type, previous.get(type));
        }
    }

    private static int rootCapacity(ItemStack root, Api binding) {
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (data == null) {
            return 0;
        }
        try {
            return Math.max(sourceCapacity(data.left().source(), binding), sourceCapacity(data.right().source(), binding));
        } catch (IllegalArgumentException unavailable) {
            // The failed native query already disabled and diagnosed this binding.
            return 0;
        }
    }

    /** Native item rendering reads this root's capability; the receiver supplies code, not state. */
    public static Item energyBarReceiver(ItemStack root) {
        Api binding = api;
        if (binding == null || root.getCount() != 1 || rootCapacity(root, binding) <= 0) {
            return null;
        }
        var data = MixedLeggingsStructure.validData(root).orElseThrow();
        return data.left().source().getItem() == binding.eclipse || data.right().source().getItem() == binding.eclipse
                ? binding.eclipse : binding.celestigem;
    }

    private static int sourceCapacity(ItemStack source, Api binding) {
        if (source.getItem() != binding.celestigem && source.getItem() != binding.eclipse) {
            return 0;
        }
        try {
            int capacity = (int) binding.capacity.invokeExact(source.getItem());
            if (capacity <= 0) {
                throw new IllegalStateException("Invalid native leggings capacity");
            }
            return capacity;
        } catch (Throwable failure) {
            if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) {
                throw fatal;
            }
            synchronized (JustDireResources.class) {
                if (api == binding) {
                    api = null;
                    LeggingsContent.LOG.warn("Just Dire Things leggings capacity query failed; storage disabled", failure);
                }
            }
            throw new IllegalArgumentException("Cannot query native leggings capacity", failure);
        }
    }

    private static int sourceCharge(ItemStack source, Api binding, int capacity) {
        if (capacity == 0) {
            return 0;
        }
        if (!validCharge(source, binding, capacity)) {
            throw new IllegalArgumentException("Invalid native leggings charge");
        }
        return source.getOrDefault(binding.energy, 0);
    }

    private static boolean validCharge(ItemStack stack, Api binding, int capacity) {
        int charge = stack.getOrDefault(binding.energy, 0);
        return charge >= 0 && charge <= capacity;
    }

    private record Api(DataComponentType<Integer> energy, Item celestigem, Item eclipse, MethodHandle capacity) {}

    /** Retained handlers revalidate current resource, amount, provenance and binding for every operation. */
    private record LiveHandler(ItemAccess access) implements EnergyHandler {
        private EnergyHandler current() {
            Api binding = api;
            if (binding == null || access.getAmount() != 1) {
                return null;
            }
            var root = access.getResource().toStack();
            int capacity = rootCapacity(root, binding);
            return api == binding && capacity > 0 && validCharge(root, binding, capacity)
                    ? new ItemAccessEnergyHandler(access, binding.energy, capacity) : null;
        }

        @Override
        public long getAmountAsLong() {
            var handler = current();
            return handler == null ? 0 : handler.getAmountAsLong();
        }

        @Override
        public long getCapacityAsLong() {
            var handler = current();
            return handler == null ? 0 : handler.getCapacityAsLong();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            var handler = current();
            return handler == null ? 0 : handler.insert(amount, transaction);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            var handler = current();
            return handler == null ? 0 : handler.extract(amount, transaction);
        }
    }
}
