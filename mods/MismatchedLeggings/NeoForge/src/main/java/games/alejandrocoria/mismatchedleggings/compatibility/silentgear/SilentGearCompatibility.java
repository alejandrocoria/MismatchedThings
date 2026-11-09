package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/** Optional native identity binding. Registration and saved-state decoding never load SG classes. */
public final class SilentGearCompatibility {
    private static final DeferredRegister<DataComponentType<?>> TYPES = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, LeggingsContent.MOD_ID);
    private static final Supplier<DataComponentType<SilentGearState>> STATE = TYPES.register("silent_gear_state", () ->
            DataComponentType.<SilentGearState>builder().persistent(SilentGearState.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(SilentGearState.CODEC)).build());
    private static volatile SourceApi sourceApi;

    private SilentGearCompatibility() {}

    public static DataComponentType<SilentGearState> state() {
        return STATE.get();
    }

    static boolean available() {
        return sourceApi != null;
    }

    public static void initialize(IEventBus bus) {
        TYPES.register(bus);
        SilentGearEffects.initialize(bus);
        if (ModList.get().isLoaded("silentgear")) {
            bus.addListener((RegisterCapabilitiesEvent event) -> {
                try {
                    sourceApi = new SourceApi();
                } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                    LeggingsContent.LOG.warn("Could not bind Silent Gear leggings source API", failure);
                }
            });
        }
    }

    public static boolean leggings(ItemStack source) {
        var binding = sourceApi;
        if (binding == null) {
            return false;
        }
        try {
            var equipment = source.get(DataComponents.EQUIPPABLE);
            return source.getCount() == 1 && binding.armor.isInstance(source.getItem())
                    && equipment != null && equipment.slot() == EquipmentSlot.LEGS
                    && binding.gearType.invoke(source.getItem()) == binding.leggings;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            sourceApi = null;
            LeggingsContent.LOG.warn("Could not read Silent Gear leggings source identity", failure);
            return false;
        }
    }

    static ItemStack carrier(ItemStack root) {
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (data == null) { return ItemStack.EMPTY; }
        var left = data.left().source();
        var right = data.right().source();
        return leggings(left) ? left.copy() : leggings(right) ? right.copy() : ItemStack.EMPTY;
    }

    public static ItemAttributeModifiers normalize(ItemStack source, ItemAttributeModifiers attributes) {
        return leggings(source) ? normalize(attributes) : attributes;
    }

    static ItemAttributeModifiers normalize(ItemAttributeModifiers attributes) {
        return new ItemAttributeModifiers(attributes.modifiers().stream().distinct().toList());
    }

    static Class<?> nativeClass(String path) throws ClassNotFoundException {
        return Class.forName("net.silentchaos512.gear." + path);
    }

    @SuppressWarnings("unchecked")
    static DataComponentType<Object> component(String name) throws ReflectiveOperationException {
        return (DataComponentType<Object>) ((Supplier<?>) nativeClass("setup.SgDataComponents").getField(name).get(null)).get();
    }

    private static final class SourceApi {
        private final Class<?> armor = nativeClass("api.item.GearArmor");
        private final Method gearType = armor.getMethod("getGearType");
        private final Object leggings = ((Supplier<?>) nativeClass("setup.gear.GearTypes").getField("LEGGINGS").get(null)).get();

        private SourceApi() throws ReflectiveOperationException {}
    }
}
