package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Optional native wearer bindings; Wielder routing remains separate from read-only carrier preparation. */
public final class SilentGearWearerApi {
    static final Set<String> WIELDER_IDS = Set.of("silentgear:adamant", "silentgear:aquatic", "silentgear:flame_ward", "silentgear:stellar");
    private final Class<?> wielder;
    private final MethodHandle context, definition, effects, trait, id, tick;
    private final Carriers carriers;

    public SilentGearWearerApi() throws ReflectiveOperationException {
        var lookup = MethodHandles.publicLookup();
        carriers = new Carriers();
        wielder = nativeClass("gear.trait.effect.WielderEffectTraitEffect");
        Class<?> instance = nativeClass("api.traits.TraitInstance"), ctx = nativeClass("api.traits.TraitActionContext");
        Class<?> nativeTrait = nativeClass("gear.trait.Trait");
        context = lookup.findConstructor(ctx, MethodType.methodType(void.class, Player.class, instance, ItemStack.class));
        definition = lookup.findVirtual(instance, "getTrait", MethodType.methodType(nativeTrait));
        id = lookup.findVirtual(instance, "getTraitId", MethodType.methodType(Identifier.class));
        trait = lookup.findVirtual(ctx, "trait", MethodType.methodType(nativeTrait));
        effects = lookup.findVirtual(nativeTrait, "getEffects", MethodType.methodType(List.class));
        tick = lookup.findVirtual(wielder, "inventoryTick", MethodType.methodType(void.class, ctx, Level.class, Entity.class, boolean.class));
    }

    public ItemStack carrier(ItemStack root) throws Throwable { return carriers.carrier(root); }

    public boolean recognizes(ItemStack root, Object ctx) throws Throwable {
        Object requested = trait.invoke(ctx);
        for (var entry : SilentGearCompatibility.effectiveTraits(root)) {
            if (WIELDER_IDS.contains(entry.id().toString()) && definition.invoke(entry.nativeInstance()) == requested) { return true; }
        }
        return false;
    }

    public List<?> effects(Object instance) throws Throwable { return (List<?>) effects.invoke(definition.invoke(instance)); }
    public boolean isWielder(Object effect) { return effect.getClass() == wielder; }
    public void tick(Object effect, Player player, Object instance, ItemStack carrier, boolean selected) throws Throwable {
        if (!isWielder(effect) || carrier.getItem().getClass() != carriers.armor || !WIELDER_IDS.contains(id.invoke(instance).toString())
                || !effects(instance).contains(effect)) { throw new IllegalArgumentException("Unaudited wearer receiver/effect"); }
        if (!(effect instanceof SilentGearWearerReadiness proof) || !proof.mismatchedboots$sgSetReady()) {
            throw new IllegalStateException("Paired set proof unavailable");
        }
        tick.invoke(effect, context.invoke(player, instance, carrier), player.level(), player, selected);
    }

    /** Brilliant can bind this read-only subset without requiring Wielder classes or contexts. */
    public static final class Carriers {
        private final Class<?> armor;
        private final MethodHandle properties, propertiesConstructor, traitList;
        private final DataComponentType<Object> component;
        private final Object traitKey;

        @SuppressWarnings("unchecked")
        public Carriers() throws ReflectiveOperationException {
            var lookup = MethodHandles.publicLookup();
            armor = nativeClass("item.gear.GearArmorItem");
            Class<?> props = nativeClass("core.component.GearPropertiesData");
            properties = lookup.findVirtual(props, "properties", MethodType.methodType(Map.class));
            propertiesConstructor = lookup.findConstructor(props, MethodType.methodType(void.class, Map.class));
            traitList = lookup.findConstructor(nativeClass("api.property.TraitListPropertyValue"), MethodType.methodType(void.class, List.class));
            component = (DataComponentType<Object>) ((Supplier<?>) nativeClass("setup.SgDataComponents").getField("GEAR_PROPERTIES").get(null)).get();
            traitKey = ((Supplier<?>) nativeClass("setup.gear.GearProperties").getField("TRAITS").get(null)).get();
        }

        public ItemStack carrier(ItemStack root) throws Throwable {
            var traits = SilentGearCompatibility.effectiveTraits(root);
            if (traits.isEmpty()) { return ItemStack.EMPTY; }
            ItemStack copy = SilentGearCompatibility.carrier(root);
            // Foreign GearArmor implementations may override the callback with unrelated behavior.
            if (copy.isEmpty() || copy.getItem().getClass() != armor || !copy.has(component)) { throw new IllegalStateException("Unsupported native wearer receiver"); }
            var values = new HashMap<Object, Object>((Map<?, ?>) properties.invoke(copy.get(component)));
            values.put(traitKey, traitList.invoke(traits.stream().map(SilentGearCompatibility.Trait::nativeInstance).toList()));
            copy.set(component, propertiesConstructor.invoke(values));
            return copy;
        }
    }

    private static Class<?> nativeClass(String name) throws ClassNotFoundException { return Class.forName("net.silentchaos512.gear." + name); }
}
