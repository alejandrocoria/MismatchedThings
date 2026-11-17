package games.alejandrocoria.mismatchedthings.internal.compatibility.neovitae;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedthings.api.compatibility.neovitae.NeoVitaeHellforgedService;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** One native reader for all registered products, independent of the infusion binding. */
public final class NeoForgeNeoVitaeHellforgedService implements NeoVitaeHellforgedService {
    private static final Logger LOG = LoggerFactory.getLogger("mismatchedthings/neovitae");
    private static final TagKey<Item> ELIGIBLE = TagKey.create(Registries.ITEM, Identifier.parse("neovitae:hellforgeable"));
    private static final ThreadLocal<Probe> PROBE = new ThreadLocal<>();
    private final Map<Item, SourceProvider> providers = new ConcurrentHashMap<>();
    private volatile @Nullable DataComponentType<Boolean> marker;
    private @Nullable Method getter;
    private volatile boolean verified;

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("neovitae") && INSTANCE instanceof NeoForgeNeoVitaeHellforgedService service) {
            bus.addListener(service::bind);
        }
    }

    @SuppressWarnings("unchecked")
    private void bind(RegisterCapabilitiesEvent event) {
        try {
            var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(Identifier.parse("neovitae:hellforged")).orElseThrow();
            if (type.codec() == null
                    || !Boolean.TRUE.equals(type.codec().parse(JsonOps.INSTANCE, new JsonPrimitive(true)).getOrThrow())
                    || !Boolean.FALSE.equals(type.codec().parse(JsonOps.INSTANCE, new JsonPrimitive(false)).getOrThrow())) {
                throw new IllegalStateException("Unsupported Hellforged component");
            }
            var helper = Class.forName("com.breakinblocks.neovitae.common.item.soul.HellforgedHelper");
            getter = helper.getMethod("isHellforged", ItemStack.class);
            marker = (DataComponentType<Boolean>) type;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            marker = null;
            LOG.warn("Neo Vitae Hellforged binding unavailable; mixed Hellforging eligibility disabled", failure);
        }
    }

    private synchronized boolean ready(ItemStack root) {
        if (verified) {
            return marker != null;
        }
        var typed = marker;
        if (typed == null || root.isEmpty()) {
            return false;
        }
        try {
            // Item prototypes are not component-bound at RegisterCapabilitiesEvent. Probe a
            // disposable copy on first gameplay use, after that native boundary has completed.
            // This checks the final transformed getter, not just its pre-transform shape.
            for (boolean value : new boolean[]{false, true}) {
                var stack = root.copy();
                stack.set(typed, value);
                var probe = new Probe();
                PROBE.set(probe);
                try {
                    if (!Boolean.valueOf(value).equals(getter.invoke(null, stack)) || !probe.observed) {
                        throw new IllegalStateException("Native Hellforged return hook unavailable");
                    }
                } finally {
                    PROBE.remove();
                }
            }
            verified = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            marker = null;
            LOG.warn("Neo Vitae Hellforged binding unavailable; mixed Hellforging eligibility disabled", failure);
            return false;
        }
    }

    @Override
    public void register(Item item, SourceProvider provider) {
        if (providers.putIfAbsent(Objects.requireNonNull(item), Objects.requireNonNull(provider)) != null) {
            throw new IllegalStateException("Duplicate Neo Vitae Hellforged product: " + item);
        }
    }

    private @Nullable Sources sources(ItemStack root) {
        var provider = providers.get(root.getItem());
        return provider == null ? null : provider.sources(root, EffectiveSide.get().isClient());
    }

    private static boolean inherited(Sources sources, DataComponentType<Boolean> type) {
        return Boolean.TRUE.equals(sources.left().get(type)) || Boolean.TRUE.equals(sources.right().get(type));
    }

    public static boolean read(boolean ordinary, ItemStack root) {
        var probe = PROBE.get();
        if (probe != null) {
            probe.observed = true;
            return ordinary;
        }
        if (ordinary || !(INSTANCE instanceof NeoForgeNeoVitaeHellforgedService service)) {
            return ordinary;
        }
        var type = service.marker;
        if (type == null || root.has(type) || !service.providers.containsKey(root.getItem()) || !service.ready(root)) {
            return ordinary;
        }
        var sources = service.sources(root);
        return sources != null && sources.shared() && inherited(sources, type);
    }

    @Override
    public boolean projects(ItemStack root) {
        var type = marker;
        if (type == null || !providers.containsKey(root.getItem()) || !ready(root)) {
            return false;
        }
        var sources = sources(root);
        if (sources == null) {
            return false;
        }
        boolean inherited = inherited(sources, type);
        Boolean owned = root.get(type);
        boolean recognized = owned != null ? owned : sources.shared() && inherited;
        // Suppressed inheritance still carries averaged native bonus IDs. Another paid forge
        // cannot replace those IDs with a full bonus; decline ONLY our tag projection instead.
        return (recognized || !inherited) && (sources.left().is(ELIGIBLE) || sources.right().is(ELIGIBLE));
    }

    @Override
    public ItemStack sourceView(ItemStack root, ItemStack source) {
        var view = source.copy();
        var type = marker;
        if (type == null || !providers.containsKey(root.getItem()) || !ready(root)) {
            return view;
        }
        var sources = sources(root);
        // Inherited upgrades keep their original asymmetric colors. Only a root-only
        // upgrade needs temporary native markers on eligible item-render copies.
        if (sources != null && Boolean.TRUE.equals(root.get(type)) && !inherited(sources, type) && source.is(ELIGIBLE)) {
            view.set(type, true);
        }
        return view;
    }

    private static final class Probe {
        private boolean observed;
    }
}
