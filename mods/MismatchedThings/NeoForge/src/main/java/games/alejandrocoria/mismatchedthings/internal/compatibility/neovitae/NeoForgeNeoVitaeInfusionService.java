package games.alejandrocoria.mismatchedthings.internal.compatibility.neovitae;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedthings.api.compatibility.neovitae.NeoVitaeInfusionService;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Native component binding only; no dependency on an armor product or Neo Vitae classes. */
public final class NeoForgeNeoVitaeInfusionService implements NeoVitaeInfusionService {
    private static final Logger LOG = LoggerFactory.getLogger("mismatchedthings/neovitae");
    private volatile NeoVitaeInfusionRules.Binding binding;

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("neovitae") && NeoVitaeInfusionService.INSTANCE instanceof NeoForgeNeoVitaeInfusionService service) {
            // All native components are registered by this point; no energy capability is installed here.
            bus.addListener(service::bind);
        }
    }

    private void bind(RegisterCapabilitiesEvent event) {
        try {
            var capacity = component("spiritus_max", Double.class, new JsonPrimitive(1D));
            var amount = component("spiritus_amount", Double.class, new JsonPrimitive(0D));
            var type = component("spiritus_type", Object.class, new JsonPrimitive("raw"));
            var bloodMending = component("blood_mending", Boolean.class, new JsonPrimitive(true));
            Object raw = type.codec().parse(JsonOps.INSTANCE, new JsonPrimitive("raw")).getOrThrow();
            if (!(raw instanceof Enum<?> rawType)) {
                throw new IllegalStateException("Unsupported Spiritus type schema");
            }
            binding = new NeoVitaeInfusionRules.Binding(capacity, amount, type, bloodMending, rawType);
        } catch (RuntimeException | LinkageError failure) {
            LOG.warn("Could not bind Neo Vitae infusion components; mixed infusion composition disabled", failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> component(String path, Class<T> valueClass, JsonPrimitive sample) {
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(Identifier.fromNamespaceAndPath("neovitae", path))
                .orElseThrow(() -> new IllegalStateException("Missing Neo Vitae component: " + path));
        if (type.codec() == null || !valueClass.isInstance(type.codec().parse(JsonOps.INSTANCE, sample).getOrThrow())) {
            throw new IllegalStateException("Unsupported Neo Vitae component: " + path);
        }
        return (DataComponentType<T>) type;
    }

    @Override
    public void initializeCreation(ItemStack output, ItemStack owner, ItemStack other) {
        var api = binding;
        if (api != null) {
            NeoVitaeInfusionRules.initializeCreation(output, owner, other, api);
        }
    }

    @Override
    public void preserveState(ItemStack previous, ItemStack output) {
        var api = binding;
        if (api != null) {
            NeoVitaeInfusionRules.preserveState(previous, output, api);
        }
    }
}
