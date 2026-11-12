package games.alejandrocoria.mismatchedleggings.compatibility.justdirethings;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
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
public final class JustDireSettings {
    private static volatile Api api;

    private JustDireSettings() {
    }

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("justdirethings")) {
            bus.addListener(JustDireSettings::bind);
        }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
            var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
            var params = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.AbilityParams");
            var record = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding");
            if (!record.isRecord()) {
                throw new IllegalStateException("Unsupported native binding record");
            }

            var lookup = MethodHandles.publicLookup();
            var abilities = new ArrayList<Setting>();
            for (String name : List.of("walkspeed", "runspeed", "swimspeed", "phase")) {
                abilities.add(new Setting(name, ability.getField(name.toUpperCase(Locale.ROOT)).get(null),
                                          scalar(name + "_upgrade_installed", false), scalar(name + "_toggle", true), name.equals("phase") ? null : scalar(name + "_value", 1),
                                          scalar(name + "_bindingmode", 0)));
            }

            DataComponentType<List<Object>> bindings = component("ability_bindings");
            if (bindings.codec() == null || bindings.streamCodec() == null) {
                throw new IllegalStateException("Unsupported binding codecs");
            }

            Object decoded = bindings.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(
                    "[{\"ability_name\":\"walkspeed\",\"key\":71,\"is_mouse\":false,\"require_equipped\":true}]")).getOrThrow();
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
                          accessor(lookup, record, "abilityName", String.class), accessor(lookup, record, "requireEquipped", boolean.class),
                          accessor(lookup, record, "key", int.class), accessor(lookup, record, "isMouse", boolean.class),
                          lookup.findConstructor(record, MethodType.methodType(void.class, String.class, int.class, boolean.class, boolean.class))
                                .asType(MethodType.methodType(Object.class, String.class, int.class, boolean.class, boolean.class)),
                          bindings);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            LeggingsContent.LOG.warn("Could not bind Just Dire Things mixed settings API; leggings settings disabled", failure);
        }
    }

    public static boolean available() {
        return api != null;
    }

    static boolean nativeMenu(AbstractContainerMenu menu) {
        return api != null && menu.getClass() == api.menu;
    }

    /** Maximum permanently supported configured tier, independent of FE and live toggles. */
    public static Metadata metadata(ItemStack root, String name) {
        Profile profile = profile(root, name);
        return profile == null ? null : profile.metadata;
    }

    private static Profile profile(ItemStack root, String name) {
        Api binding = api;
        if (binding == null || !mixed(root)) {
            return null;
        }

        Setting setting = binding.settings.stream().filter(candidate -> candidate.name.equals(name)).findFirst().orElse(null);
        if (setting == null) {
            return null;
        }

        try {
            var data = MixedLeggingsStructure.validData(root).orElseThrow();
            Profile selected = null;
            for (ItemStack source : List.of(data.left().source(), data.right().source())) {
                if (source.getCount() != 1) {
                    return null;
                }

                if (!JustDireAbilities.nativeItem(source) || !binding.tool.isInstance(source.getItem())
                        || !((Set<?>) binding.abilities.invokeExact(source.getItem())).contains(setting.ability)) {
                    continue;
                }

                Object params = binding.params.invokeExact(source.getItem(), setting.ability);
                Metadata metadata = nativeMetadata(binding, params, name.equals("phase"));
                if (selected == null || metadata.maximum > selected.metadata.maximum) {
                    selected = new Profile(source.getItem(), metadata, params);
                }
            }

            return api == binding ? selected : null;
        } catch (Throwable failure) {
            disable(binding, failure);
            return null;
        }
    }

    private static Metadata nativeMetadata(Api binding, Object params, boolean allowToggleOnly) throws Throwable {
        int min = (int) binding.minimum.invokeExact(params);
        int max = (int) binding.maximum.invokeExact(params);
        int increment = (int) binding.increment.invokeExact(params);
        int defaults = (int) binding.defaultValue.invokeExact(params);
        boolean toggleOnly = allowToggleOnly && min == -1 && max == -1 && increment == -1 && defaults == -1;
        if (!toggleOnly && (min < 0 || max < min || increment <= 0 || defaults < min || defaults > max)) {
            throw new IllegalStateException("Unsupported native mixed settings parameters");
        }
        return new Metadata(min, max, increment, defaults);
    }

    public static boolean mixed(ItemStack root) {
        return root.getCount() == 1 && MixedLeggingsStructure.validData(root).isPresent();
    }

    /** GLFW key/button bounds, excluding the native mouse route's ordinary left/right clicks. */
    public static boolean validBindingInput(int key, boolean mouse) {
        return mouse ? key >= 2 && key <= 7 : key >= 32 && key <= 348;
    }

    /** Native passive order, once per family, independent of global enablement or charge. */
    public static List<String> boundPassives(ItemStack root, int key, boolean mouse, boolean equipped) {
        Api binding = api;
        if (binding == null || !mixed(root) || !validBindingInput(key, mouse)) {
            return List.of();
        }
        try {
            var names = new ArrayList<String>();
            for (Setting setting : binding.settings) {
                if (!root.getOrDefault(setting.installed, false) || root.getOrDefault(setting.mode, 0) != 2
                        || metadata(root, setting.name) == null) {
                    continue;
                }
                for (Object record : root.getOrDefault(binding.bindings, List.of())) {
                    if (setting.name.equals((String) binding.name.invokeExact(record))
                            && key == (int) binding.key.invokeExact(record) && mouse == (boolean) binding.mouse.invokeExact(record)
                            && (equipped || !(boolean) binding.equipped.invokeExact(record))) {
                        names.add(setting.name);
                        break;
                    }
                }
            }
            return api == binding ? List.copyOf(names) : List.of();
        } catch (Throwable failure) {
            disable(binding, failure);
            return List.of();
        }
    }

    static boolean toggleBoundPassives(ServerPlayer player, ItemStack root, int key, boolean mouse, boolean equipped) {
        Api binding = api;
        var names = boundPassives(root, key, mouse, equipped);
        if (binding == null || names.isEmpty() || api != binding) {
            return false;
        }
        for (Setting setting : binding.settings) {
            if (names.contains(setting.name)) {
                boolean enabled = !root.getOrDefault(setting.toggle, true);
                root.set(setting.toggle, enabled);
                player.sendOverlayMessage(Component.translatable("justdirethings.ability",
                        Component.translatable("justdirethings.ability." + setting.name),
                        Component.translatable(enabled ? "justdirethings.enabled" : "justdirethings.disabled")));
            }
        }
        return true;
    }

    /** Native callbacks retain a per-row record as well as the live root; keep their local view coherent. */
    public static Object screenBinding(String name, int key, boolean mouse, boolean equipped) {
        Api binding = api;
        if (binding == null) {
            return null;
        }
        try {
            return binding.create.invokeExact(name, key, mouse, equipped);
        } catch (Throwable failure) {
            disable(binding, failure);
            return null;
        }
    }

    public static boolean screenEquipped(Object record) {
        Api binding = api;
        if (binding == null || record == null) {
            return true;
        }
        try {
            return (boolean) binding.equipped.invokeExact(record);
        } catch (Throwable failure) {
            disable(binding, failure);
            return true;
        }
    }

    /** UI-only admission identity; never replace the selected inventory stack with this item. */
    public static Item metadataItem(ItemStack root) {
        for (String name : List.of("walkspeed", "runspeed", "swimspeed", "phase")) {
            Profile profile = profile(root, name);
            if (profile != null) {
                return profile.item;
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static EnumSet<?> screenAbilities(ItemStack root) {
        Api binding = api;
        if (binding == null) {
            return null;
        }

        EnumSet result = EnumSet.noneOf(((Enum) binding.settings.getFirst().ability).getDeclaringClass());
        for (Setting setting : binding.settings) {
            if (metadata(root, setting.name) != null) {
                result.add((Enum) setting.ability);
            }
        }

        return api == binding ? result : null;
    }

    /** Each native widget needs its own strongest eligible tier, not the admission item's tier. */
    public static Object screenParameters(ItemStack root, Object ability) {
        Api binding = api;
        if (binding == null) {
            return null;
        }
        Setting setting = binding.settings.stream().filter(candidate -> candidate.ability == ability).findFirst().orElse(null);
        Profile profile = setting == null ? null : profile(root, setting.name);
        if (profile == null || api != binding) {
            return null;
        }
        return profile.parameters;
    }

    public static int screenValue(ItemStack root, String name) {
        Api binding = api;
        Metadata metadata = metadata(root, name);
        if (binding == null || metadata == null) {
            return 0;
        }
        if (name.equals("phase")) { return metadata.defaultValue; }
        Setting setting = binding.settings.stream().filter(candidate -> candidate.name.equals(name)).findFirst().orElseThrow();
        return Math.clamp(root.getOrDefault(setting.value, metadata.defaultValue), metadata.minimum, metadata.maximum);
    }

    /** Prepare on a copy and publish once only after all optional reads and validation succeed. */
    static boolean edit(ItemStack root, JustDireSettingsNetwork.SettingsRequest request) {
        Api binding = api;
        if (binding == null || !mixed(root)) {
            return false;
        }

        ItemStack changed = root.copy();
        try {
            Setting setting = binding.settings.stream().filter(candidate -> candidate.name.equals(request.ability())).findFirst().orElse(null);
            Metadata metadata = metadata(root, request.ability());
            if (setting == null || metadata == null || setting.installed != null && !root.getOrDefault(setting.installed, false)) {
                return false;
            }
            switch (request.operation()) {
                case JustDireSettingsNetwork.TOGGLE -> changed.set(setting.toggle, !root.getOrDefault(setting.toggle, true));
                case JustDireSettingsNetwork.VALUE -> {
                    if (!metadata.hasSlider() || request.value() < metadata.minimum || request.value() > metadata.maximum
                            || (request.value() - metadata.minimum) % metadata.increment != 0) {
                        return false;
                    }
                    changed.set(setting.value, request.value());
                }
                case JustDireSettingsNetwork.BINDING -> {
                    if ((request.value() != 0 && request.value() != 2)
                            || (request.key() != -1 && !validBindingInput(request.key(), request.mouse()))) {
                        return false;
                    }

                    var next = new ArrayList<Object>();
                    for (Object record : root.getOrDefault(binding.bindings, List.of())) {
                        if (!setting.name.equals((String) binding.name.invokeExact(record))) {
                            next.add(record);
                        }
                    }
                    if (request.value() == 2) {
                        next.add(binding.create.invokeExact(setting.name, request.key(), request.mouse(), request.equipped()));
                    }
                    changed.set(binding.bindings, List.copyOf(next));
                    changed.set(setting.mode, request.value());
                }
                default -> {
                    return false;
                }
            }
        } catch (Throwable failure) {
            disable(binding, failure);
            return false;
        }
        if (api != binding) {
            return false;
        }
        // Only the allowed root-owned fields are published. Sources, resources and timers are never edits.
        for (Setting setting : binding.settings) {
            copyField(changed, root, setting.toggle);
            if (setting.value != null) {
                copyField(changed, root, setting.value);
            }
            copyField(changed, root, setting.mode);
        }
        copyField(changed, root, binding.bindings);
        return true;
    }

    private static <T> void copyField(ItemStack from, ItemStack to, DataComponentType<T> type) {
        if (from.has(type)) {
            to.set(type, from.get(type));
        } else {
            to.remove(type);
        }
    }

    private static void disable(Api binding, Throwable failure) {
        if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) {
            throw fatal;
        }
        synchronized (JustDireSettings.class) {
            if (api == binding) {
                api = null;
                LeggingsContent.LOG.warn("Just Dire Things mixed settings queries failed; new settings/input routes disabled", failure);
            }
        }
    }

    private static MethodHandle getter(MethodHandles.Lookup lookup, Class<?> owner, String field, Class<?> type) throws ReflectiveOperationException {
        return lookup.findGetter(owner, field, type).asType(MethodType.methodType(type, Object.class));
    }

    private static MethodHandle accessor(MethodHandles.Lookup lookup,
                                         Class<?> owner,
                                         String field,
                                         Class<?> type) throws ReflectiveOperationException {
        return lookup.findVirtual(owner, field, MethodType.methodType(type)).asType(MethodType.methodType(type, Object.class));
    }

    private static <T> DataComponentType<T> scalar(String path, T sample) {
        DataComponentType<T> type = component(path);
        var json = sample instanceof Boolean value ? new JsonPrimitive(value) : new JsonPrimitive((Integer) sample);
        if (type.codec() == null || type.streamCodec() == null || !sample.getClass()
                                                                         .isInstance(type.codec().parse(JsonOps.INSTANCE, json).getOrThrow())) {
            throw new IllegalStateException("Unsupported mixed settings component: " + path);
        }
        return type;
    }

    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.fromNamespaceAndPath("justdirethings", path))
                                                                           .orElseThrow()
                                                                           .value();
    }

    public record Metadata(int minimum, int maximum, int increment, int defaultValue) {
        public boolean hasSlider() {
            return minimum >= 0 && maximum > minimum && increment > 0;
        }
    }

    private record Setting(String name, Object ability, DataComponentType<Boolean> installed,
                           DataComponentType<Boolean> toggle, DataComponentType<Integer> value, DataComponentType<Integer> mode) {
    }

    private record Profile(Item item, Metadata metadata, Object parameters) {
    }

    private record Api(Class<?> menu, Class<?> tool, List<Setting> settings, MethodHandle abilities, MethodHandle params,
                       MethodHandle minimum, MethodHandle maximum, MethodHandle increment, MethodHandle defaultValue,
                       MethodHandle name, MethodHandle equipped, MethodHandle key, MethodHandle mouse, MethodHandle create,
                       DataComponentType<List<Object>> bindings) {
    }
}
