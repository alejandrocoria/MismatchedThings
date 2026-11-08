package games.alejandrocoria.mismatchedthings.internal.compatibility.undergarden;

import games.alejandrocoria.mismatchedthings.api.compatibility.undergarden.RogdoriumInfusionService;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/** Optional registered-codec binding; neither native classes nor product classes are loaded here. */
public final class NeoForgeRogdoriumInfusionService implements RogdoriumInfusionService {
    private static final Logger LOG = LoggerFactory.getLogger("mismatchedthings/undergarden");
    private static final Identifier COMPONENT = Identifier.parse("undergarden:rogdorium_infusion");
    private final AtomicBoolean warned = new AtomicBoolean();
    private volatile DataComponentType<Object> component;
    private volatile RogdoriumInfusionRules.Binding binding;

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("undergarden") && RogdoriumInfusionService.INSTANCE instanceof NeoForgeRogdoriumInfusionService service) {
            bus.addListener(service::bind);
        }
    }

    private void bind(RegisterCapabilitiesEvent event) {
        component = registeredComponent();
        try {
            if (component == null) {
                throw new IllegalStateException("Missing registered Rogdorium infusion component");
            }
            binding = RogdoriumInfusionRules.bind(component);
        } catch (RuntimeException | LinkageError failure) {
            warn(failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<Object> registeredComponent() {
        return (DataComponentType<Object>) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(COMPONENT);
    }

    @Override
    public void initializeCreation(ItemStack output, ItemStack owner, ItemStack other) {
        var type = component;
        if (type == null) {
            return;
        }
        try {
            var api = binding;
            if (api != null) {
                RogdoriumInfusionRules.initializeCreation(output, owner, other, api);
            } else if (owner.has(type) || other.has(type)) {
                throw new IllegalArgumentException("Unsupported Rogdorium infusion binding");
            } else {
                output.remove(type);
            }
        } catch (RuntimeException | LinkageError failure) {
            warn(failure);
            // Product recipe previews already treat IllegalArgumentException as an unpaid refusal.
            throw failure instanceof IllegalArgumentException invalid ? invalid
                    : new IllegalArgumentException("Cannot compose Rogdorium infusion", failure);
        }
    }

    @Override
    public void preserveState(ItemStack previous, ItemStack output) {
        var type = component;
        if (type != null) {
            // Preservation must still work when a newer native codec no longer matches our creation binding.
            RogdoriumInfusionRules.preserveState(previous, output, type);
        }
    }

    private void warn(Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            LOG.warn("Mixed Rogdorium infusion creation refused: {}", failure.toString());
        }
    }
}
