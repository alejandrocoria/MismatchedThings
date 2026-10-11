package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Bounded server settings contract. Native UI hooks/input are a separate readiness boundary. */
public final class JustDireSettingsCompatibility {
    private static volatile Api api;
    private JustDireSettingsCompatibility() {}

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("justdirethings")) { bus.addListener(JustDireSettingsCompatibility::bind); }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
            var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
            var params = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.AbilityParams");
            var record = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding");
            if (!record.isRecord()) { throw new IllegalStateException("Unsupported native binding record"); }
            var lookup = MethodHandles.publicLookup();
            var abilities = new ArrayList<Setting>();
            for (String name : List.of("stepheight", "jumpboost", "negatefalldamage", "groundstomp")) {
                abilities.add(new Setting(name, ability.getField(name.toUpperCase(Locale.ROOT)).get(null),
                        scalar(name + "_upgrade_installed", false), scalar(name + "_toggle", true), scalar(name + "_value", 1)));
            }
            DataComponentType<List<Object>> bindings = component("ability_bindings");
            if (bindings.codec() == null || bindings.streamCodec() == null) { throw new IllegalStateException("Unsupported binding codecs"); }
            Object decoded = bindings.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(
                    "[{\"ability_name\":\"groundstomp\",\"key\":71,\"is_mouse\":false,\"require_equipped\":true}]")).getOrThrow();
            if (!(decoded instanceof List<?> records) || records.size() != 1 || !record.isInstance(records.getFirst())) {
                throw new IllegalStateException("Unsupported native binding value");
            }
            api = new Api(Class.forName("com.direwolf20.justdirethings.common.containers.ToolSettingContainer"), tool,
                    List.copyOf(abilities), lookup.findVirtual(tool, "getAbilities", MethodType.methodType(java.util.EnumSet.class))
                    .asType(MethodType.methodType(Set.class, Item.class)),
                    lookup.findVirtual(tool, "getAbilityParams", MethodType.methodType(params, ability))
                            .asType(MethodType.methodType(Object.class, Item.class, Object.class)),
                    getter(lookup, params, "minSlider", int.class), getter(lookup, params, "maxSlider", int.class),
                    getter(lookup, params, "increment", int.class), getter(lookup, params, "defaultValue", int.class),
                    accessor(lookup, record, "abilityName", String.class), accessor(lookup, record, "key", int.class),
                    accessor(lookup, record, "isMouse", boolean.class), accessor(lookup, record, "requireEquipped", boolean.class),
                    lookup.findConstructor(record, MethodType.methodType(void.class, String.class, int.class, boolean.class, boolean.class))
                            .asType(MethodType.methodType(Object.class, String.class, int.class, boolean.class, boolean.class)),
                    bindings, scalar("tool_enabled", true), scalar("groundstomp_bindingmode", 0), scalar("groundstomp_custom_setting", 0));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Constants.LOG.warn("Could not bind Just Dire Things mixed settings API; new settings/input routes disabled", failure);
        }
    }

    public static boolean available() { return api != null; }
    static boolean nativeMenu(AbstractContainerMenu menu) { return api != null && menu.getClass() == api.menu; }

    /** Maximum permanently supported configured tier, independent of FE and live toggles. */
    public static Metadata metadata(ItemStack root, String name) {
        Profile profile = profile(root, name);
        return profile == null ? null : profile.metadata;
    }

    private static Profile profile(ItemStack root, String name) {
        Api binding = api;
        if (binding == null || !mixed(root)) { return null; }
        Setting setting = binding.settings.stream().filter(candidate -> candidate.name.equals(name)).findFirst().orElse(null);
        if (setting == null || name.equals("groundstomp") && !JustDireGroundStompCompatibility.available()) { return null; }
        try {
            var data = ModContent.getMixedBootsData(root).orElseThrow();
            Profile selected = null;
            for (ItemStack source : List.of(data.left().source(), data.right().source())) {
                if (!BootHalf.isValidSource(source)) { return null; }
                Identifier id = BuiltInRegistries.ITEM.getKey(source.getItem());
                if (!id.getNamespace().equals("justdirethings") || !binding.tool.isInstance(source.getItem())
                        || !List.of("ferricore_boots", "blazegold_boots", "celestigem_boots", "eclipsealloy_boots").contains(id.getPath())
                        || name.equals("jumpboost") && !List.of("ferricore_boots", "blazegold_boots").contains(id.getPath())
                        || !((Set<?>) binding.abilities.invokeExact(source.getItem())).contains(setting.ability)) { continue; }
                Object params = binding.params.invokeExact(source.getItem(), setting.ability);
                int min = (int) binding.minimum.invokeExact(params), max = (int) binding.maximum.invokeExact(params);
                int increment = (int) binding.increment.invokeExact(params), defaults = (int) binding.defaultValue.invokeExact(params);
                boolean toggleOnly = (name.equals("stepheight") || name.equals("negatefalldamage"))
                        && min == -1 && max == -1 && increment == -1 && defaults == -1;
                if (!toggleOnly && (min < 0 || max < min || increment <= 0 || defaults < min || defaults > max)) {
                    throw new IllegalStateException("Unsupported native mixed settings parameters");
                }
                if (selected == null || max > selected.metadata.maximum) {
                    selected = new Profile(source.getItem(), new Metadata(min, max, increment, defaults));
                }
            }
            return api == binding ? selected : null;
        } catch (Throwable failure) { disable(binding, failure); return null; }
    }

    public static boolean mixed(ItemStack root) {
        return !root.isEmpty() && root.getCount() == 1 && root.getItem() instanceof MixedBootsItem
                && ModContent.getMixedBootsData(root).isPresent();
    }

    public static boolean keyboardBinding(ItemStack root, int key) {
        Api binding = api;
        if (binding == null || !mixed(root) || key < 32 || key > 348 || root.getOrDefault(binding.mode, 0) != 2) { return false; }
        try {
            for (Object record : root.getOrDefault(binding.bindings, List.of())) {
                if ("groundstomp".equals((String) binding.name.invokeExact(record)) && (int) binding.key.invokeExact(record) == key
                        && !(boolean) binding.mouse.invokeExact(record) && (boolean) binding.equipped.invokeExact(record)) { return true; }
            }
            return false;
        } catch (Throwable failure) { disable(binding, failure); return false; }
    }

    static int particles(ItemStack root) { Api binding = api; return binding == null ? -1 : root.getOrDefault(binding.particles, 0); }

    /** UI-only admission identity; never replace the selected inventory stack with this item. */
    public static Item metadataItem(ItemStack root) {
        for (String name : List.of("groundstomp", "stepheight", "jumpboost", "negatefalldamage")) {
            Profile profile = profile(root, name);
            if (profile != null) { return profile.item; }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static EnumSet<?> screenAbilities(ItemStack root) {
        Api binding = api;
        if (binding == null) { return null; }
        EnumSet result = EnumSet.noneOf(((Enum) binding.settings.getFirst().ability).getDeclaringClass());
        for (Setting setting : binding.settings) {
            if (metadata(root, setting.name) != null) { result.add((Enum) setting.ability); }
        }
        return api == binding ? result : null;
    }

    /** Each native widget needs its own strongest eligible tier, not the admission item's tier. */
    public static Object screenParameters(ItemStack root, Object ability) {
        Api binding = api;
        if (binding == null) { return null; }
        Setting setting = binding.settings.stream().filter(candidate -> candidate.ability == ability).findFirst().orElse(null);
        Profile profile = setting == null ? null : profile(root, setting.name);
        if (profile == null || api != binding) { return null; }
        try { return binding.params.invokeExact(profile.item, ability); }
        catch (Throwable failure) { disable(binding, failure); return null; }
    }

    public static int screenValue(ItemStack root, String name) {
        Api binding = api;
        Metadata metadata = metadata(root, name);
        if (binding == null || metadata == null) { return 0; }
        Setting setting = binding.settings.stream().filter(candidate -> candidate.name.equals(name)).findFirst().orElseThrow();
        return Math.clamp(root.getOrDefault(setting.value, metadata.defaultValue), metadata.minimum, metadata.maximum);
    }

    /** Prepare on a copy and publish once only after all optional reads and validation succeed. */
    static boolean edit(ItemStack root, JustDireActiveNetwork.SettingsRequest request) {
        Api binding = api;
        if (binding == null || !mixed(root)) { return false; }
        ItemStack changed = root.copy();
        try {
            if (request.operation() == JustDireActiveNetwork.TOOL_TOGGLE) {
                if (!request.ability().isEmpty() || binding.settings.stream().noneMatch(setting -> metadata(root, setting.name) != null)) { return false; }
                changed.set(binding.enabled, !root.getOrDefault(binding.enabled, true));
            } else {
                Setting setting = binding.settings.stream().filter(candidate -> candidate.name.equals(request.ability())).findFirst().orElse(null);
                Metadata metadata = metadata(root, request.ability());
                if (setting == null || metadata == null || !root.getOrDefault(setting.installed, false)) { return false; }
                switch (request.operation()) {
                    case JustDireActiveNetwork.TOGGLE -> changed.set(setting.toggle, !root.getOrDefault(setting.toggle, true));
                    case JustDireActiveNetwork.VALUE -> {
                        // Negate's production consumer has no numeric control; do not offer a dead slider.
                        if (!metadata.hasSlider() || setting.name.equals("negatefalldamage") || request.value() < metadata.minimum || request.value() > metadata.maximum
                                || (request.value() - metadata.minimum) % metadata.increment != 0) { return false; }
                        changed.set(setting.value, request.value());
                    }
                    case JustDireActiveNetwork.PARTICLES -> {
                        if (!setting.name.equals("groundstomp") || request.value() < 0 || request.value() > 1) { return false; }
                        changed.set(binding.particles, request.value());
                    }
                    case JustDireActiveNetwork.BINDING -> {
                        if (!setting.name.equals("groundstomp") || (request.value() != 0 && request.value() != 2)
                                || request.mouse() || !request.equipped() || (request.key() != -1 && (request.key() < 32 || request.key() > 348))) { return false; }
                        var next = new ArrayList<Object>();
                        for (Object record : root.getOrDefault(binding.bindings, List.of())) {
                            if (!"groundstomp".equals((String) binding.name.invokeExact(record))) { next.add(record); }
                        }
                        if (request.value() == 2 && request.key() != -1) {
                            next.add(binding.create.invokeExact("groundstomp", request.key(), false, true));
                        }
                        changed.set(binding.bindings, List.copyOf(next)); changed.set(binding.mode, request.value());
                    }
                    default -> { return false; }
                }
            }
        } catch (Throwable failure) { disable(binding, failure); return false; }
        if (api != binding) { return false; }
        // Only the allowed root-owned fields are published. Sources, resources and timers are never edits.
        for (Setting setting : binding.settings) {
            copyField(changed, root, setting.toggle); copyField(changed, root, setting.value);
        }
        copyField(changed, root, binding.enabled); copyField(changed, root, binding.bindings);
        copyField(changed, root, binding.mode); copyField(changed, root, binding.particles);
        return true;
    }

    private static <T> void copyField(ItemStack from, ItemStack to, DataComponentType<T> type) {
        if (from.has(type)) { to.set(type, from.get(type)); } else { to.remove(type); }
    }

    private static void disable(Api binding, Throwable failure) {
        if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) { throw fatal; }
        synchronized (JustDireSettingsCompatibility.class) {
            if (api == binding) {
                api = null;
                Constants.LOG.warn("Just Dire Things mixed settings queries failed; new settings/input routes disabled", failure);
            }
        }
    }

    private static MethodHandle getter(MethodHandles.Lookup lookup, Class<?> owner, String field, Class<?> type) throws ReflectiveOperationException {
        return lookup.findGetter(owner, field, type).asType(MethodType.methodType(type, Object.class));
    }
    private static MethodHandle accessor(MethodHandles.Lookup lookup, Class<?> owner, String field, Class<?> type) throws ReflectiveOperationException {
        return lookup.findVirtual(owner, field, MethodType.methodType(type)).asType(MethodType.methodType(type, Object.class));
    }
    private static <T> DataComponentType<T> scalar(String path, T sample) {
        DataComponentType<T> type = component(path);
        var json = sample instanceof Boolean value ? new JsonPrimitive(value) : new JsonPrimitive((Integer) sample);
        if (type.codec() == null || type.streamCodec() == null || !sample.getClass().isInstance(type.codec().parse(JsonOps.INSTANCE, json).getOrThrow())) {
            throw new IllegalStateException("Unsupported mixed settings component: " + path);
        }
        return type;
    }
    @SuppressWarnings("unchecked") private static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value();
    }
    public record Metadata(int minimum, int maximum, int increment, int defaultValue) {
        public boolean hasSlider() { return minimum >= 0 && increment > 0; }
    }
    private record Setting(String name, Object ability, DataComponentType<Boolean> installed,
                           DataComponentType<Boolean> toggle, DataComponentType<Integer> value) {}
    private record Profile(Item item, Metadata metadata) {}
    private record Api(Class<?> menu, Class<?> tool, List<Setting> settings, MethodHandle abilities, MethodHandle params,
                       MethodHandle minimum, MethodHandle maximum, MethodHandle increment, MethodHandle defaultValue,
                       MethodHandle name, MethodHandle key, MethodHandle mouse, MethodHandle equipped, MethodHandle create,
                       DataComponentType<List<Object>> bindings, DataComponentType<Boolean> enabled,
                       DataComponentType<Integer> mode, DataComponentType<Integer> particles) {}
}
