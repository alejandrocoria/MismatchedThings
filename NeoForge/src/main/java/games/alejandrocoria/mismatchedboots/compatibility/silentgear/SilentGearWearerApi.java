package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
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
    private final Field potions, potionType, potionEffect, potionDuration, potionLevels;
    private final Class<?> potionData;
    private final Object armorType;

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
        potions = field(wielder, "potions");
        potionData = nativeClass("gear.trait.effect.WielderEffectTraitEffect$PotionData");
        potionType = field(potionData, "type"); potionEffect = field(potionData, "effect");
        potionDuration = field(potionData, "duration"); potionLevels = field(potionData, "levels");
        armorType = ((Supplier<?>) nativeClass("setup.gear.GearTypes").getField("ARMOR").get(null)).get();
    }

    public ItemStack carrier(ItemStack root) throws Throwable { return carriers.carrier(root); }

    public boolean recognizes(ItemStack root, Object ctx) throws Throwable {
        Object requested = trait.invoke(ctx);
        for (var entry : SilentGearCompatibility.effectiveTraits(root)) {
            if (WIELDER_IDS.contains(entry.id().toString()) && definition.invoke(entry.nativeInstance()) == requested) {
                admittedEffects(entry.nativeInstance());
                return true;
            }
        }
        return false;
    }

    public List<?> effects(Object instance) throws Throwable { return (List<?>) effects.invoke(definition.invoke(instance)); }
    public boolean isWielder(Object effect) { return effect.getClass() == wielder; }

    /** Read current definitions (including reloads), never cache a potion table or dispatch every trait effect. */
    public List<?> admittedEffects(Object instance) throws Throwable {
        String name = id.invoke(instance).toString();
        if (!WIELDER_IDS.contains(name)) { throw new IllegalArgumentException("Unaudited wearer trait"); }
        var calls = effects(instance).stream().filter(this::isWielder).toList();
        if (calls.size() != 1) { throw new IllegalStateException("Changed Wielder definition count"); }
        var map = (Map<?, ?>) potions.get(calls.getFirst());
        if (map.size() != 1 || !map.containsKey(armorType) || !(map.get(armorType) instanceof List<?> entries)) {
            throw new IllegalStateException("Changed Wielder gear branch");
        }
        List<String> expected = switch (name) {
            case "silentgear:adamant" -> List.of("resistance");
            case "silentgear:aquatic" -> List.of("water_breathing");
            case "silentgear:flame_ward" -> List.of("fire_resistance");
            case "silentgear:stellar" -> List.of("speed", "jump_boost");
            default -> throw new IllegalArgumentException(name);
        };
        if (entries.size() != expected.size()) { throw new IllegalStateException("Changed Wielder potion count"); }
        for (int i = 0; i < entries.size(); i++) {
            Object entry = entries.get(i);
            String type = name.equals("silentgear:aquatic") || name.equals("silentgear:flame_ward") ? "FULL_SET_ONLY" : "PIECE_COUNT";
            if (entry.getClass() != potionData || !((Enum<?>) potionType.get(entry)).name().equals(type)
                    || !(potionEffect.get(entry) instanceof Holder<?> holder)
                    || !holder.is(Identifier.withDefaultNamespace(expected.get(i)))
                    || potionDuration.getInt(entry) < 1 || potionDuration.getInt(entry) > 1200
                    || !(potionLevels.get(entry) instanceof List<?> levels) || levels.isEmpty() || levels.size() > 4
                    || levels.stream().anyMatch(value -> !(value instanceof Integer level) || level < 0 || level > 256)) {
                throw new IllegalStateException("Unaudited Wielder potion definition");
            }
        }
        return calls;
    }

    public void requireProof() throws Throwable {
        for (var gate : List.of(new String[]{"gear.trait.effect.WielderEffectTraitEffect", "mismatchedboots$sgWielderReady"},
                new String[]{"gear.trait.effect.WielderEffectTraitEffect$PotionData", "mismatchedboots$sgPotionReady"},
                new String[]{"util.TraitHelper", "mismatchedboots$sgWearerQueryReady"},
                new String[]{"util.GearHelper", "mismatchedboots$sgGearIdentityReady"})) {
            var type = nativeClass(gate[0]);
            if (!(boolean) MethodHandles.privateLookupIn(type, MethodHandles.lookup()).findStatic(type, gate[1], MethodType.methodType(boolean.class)).invoke()) {
                throw new IllegalStateException("Final Wielder proof unavailable: " + gate[0]);
            }
        }
    }
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
    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field;
    }
}
