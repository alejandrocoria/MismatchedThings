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
public final class JustDireSettingsCompatibility {
    private static volatile Api api;
    private static volatile boolean earthquakeSettingsFailed;
    private static volatile Setting lavaSetting;
    private static volatile boolean lavaSettingsFailed;
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
                        scalar(name + "_upgrade_installed", false), scalar(name + "_toggle", true), scalar(name + "_value", 1),
                        scalar(name + "_bindingmode", 0)));
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
                    lookup.findVirtual(ability, "getLocalization", MethodType.methodType(String.class))
                            .asType(MethodType.methodType(String.class, Object.class)),
                    bindings, scalar("groundstomp_custom_setting", 0));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Constants.LOG.warn("Could not bind Just Dire Things mixed settings API; new settings/input routes disabled", failure);
            return; // No Lava settings can operate without the shared API; avoid a second diagnostic.
        }
        try {
            var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
            // Intrinsic, toggle-only: neither an installed flag nor an unused numeric field exists.
            lavaSetting = new Setting("lavarepair", ability.getField("LAVAREPAIR").get(null), null,
                    scalar("lavarepair_toggle", true), null, scalar("lavarepair_bindingmode", 0));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { lavaSettingsFailure(failure); }
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
        if (name.equals("earthquake")) { return earthquakeProfile(root, binding); }
        if (name.equals("lavarepair")) { return lavaProfile(root, binding); }
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
                        || !((Set<?>) binding.abilities.invokeExact(source.getItem())).contains(setting.ability)) { continue; }
                Object params = binding.params.invokeExact(source.getItem(), setting.ability);
                Metadata metadata = nativeMetadata(binding, params, name.equals("stepheight") || name.equals("negatefalldamage"));
                if (selected == null || metadata.maximum > selected.metadata.maximum) {
                    selected = new Profile(source.getItem(), metadata, params);
                }
            }
            return api == binding ? selected : null;
        } catch (Throwable failure) { disable(binding, failure); return null; }
    }

    private static Metadata nativeMetadata(Api binding, Object params, boolean allowToggleOnly) throws Throwable {
        int min = (int) binding.minimum.invokeExact(params), max = (int) binding.maximum.invokeExact(params);
        int increment = (int) binding.increment.invokeExact(params), defaults = (int) binding.defaultValue.invokeExact(params);
        boolean toggleOnly = allowToggleOnly && min == -1 && max == -1 && increment == -1 && defaults == -1;
        if (!toggleOnly && (min < 0 || max < min || increment <= 0 || defaults < min || defaults > max)) {
            throw new IllegalStateException("Unsupported native mixed settings parameters");
        }
        return new Metadata(min, max, increment, defaults);
    }

    private static Profile earthquakeProfile(ItemStack root, Api binding) {
        if (earthquakeSettingsFailed) { return null; }
        Item item = JustDireEarthquakeCompatibility.settingsItem(root);
        if (item == null) { return null; }
        try {
            Object params = binding.params.invokeExact(item, JustDireEarthquakeCompatibility.settingAbility());
            Metadata metadata = nativeMetadata(binding, params, false);
            // Native Earthquake's numeric value is fixed and unused by its effect. A future
            // variable range needs an actual consumer review, not a dead slider on mixed boots.
            if (metadata.minimum != metadata.maximum) { throw new IllegalStateException("Unsupported native Earthquake settings range"); }
            return api == binding ? new Profile(item, metadata, params) : null;
        } catch (Throwable failure) {
            if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) { throw fatal; }
            earthquakeSettingsFailure(failure);
            // GUI parameter failures must not disable older settings or freeze a spent Earthquake clock.
            return null;
        }
    }

    private static synchronized void earthquakeSettingsFailure(Throwable failure) {
        if (!earthquakeSettingsFailed) {
            earthquakeSettingsFailed = true;
            Constants.LOG.warn("Just Dire Things Earthquake mixed settings unavailable; Earthquake metadata/input disabled", failure);
        }
    }

    private static Profile lavaProfile(ItemStack root, Api binding) {
        Setting setting = lavaSetting;
        if (setting == null || lavaSettingsFailed || !JustDireLavaRepairCompatibility.eligible(root)) { return null; }
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", "blazegold_boots"));
            Object params = binding.params.invokeExact(item, setting.ability);
            Metadata metadata = nativeMetadata(binding, params, true);
            if (metadata.minimum != -1 || metadata.maximum != -1 || metadata.increment != -1 || metadata.defaultValue != -1) {
                throw new IllegalStateException("Unsupported native Lava Repair settings range");
            }
            return api == binding && lavaSetting == setting && !lavaSettingsFailed ? new Profile(item, metadata, params) : null;
        } catch (Throwable failure) {
            if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) { throw fatal; }
            lavaSettingsFailure(failure);
            return null;
        }
    }

    private static synchronized void lavaSettingsFailure(Throwable failure) {
        if (!lavaSettingsFailed) {
            lavaSettingsFailed = true;
            Constants.LOG.warn("Just Dire Things Lava Repair mixed settings unavailable; Lava metadata/input disabled", failure);
        }
    }

    private static List<Setting> settings(Api binding) {
        Setting intrinsic = lavaSetting;
        if (intrinsic == null || lavaSettingsFailed) { return binding.settings; }
        var settings = new ArrayList<>(binding.settings);
        settings.add(2, intrinsic); // Native passive order: Step, Jump, Lava, Negate.
        return settings;
    }

    public static boolean mixed(ItemStack root) {
        return !root.isEmpty() && root.getCount() == 1 && root.getItem() instanceof MixedBootsItem
                && ModContent.getMixedBootsData(root).isPresent();
    }

    /** GLFW key/button bounds, excluding the native mouse route's ordinary left/right clicks. */
    public static boolean validBindingInput(int key, boolean mouse) {
        return mouse ? key >= 2 && key <= 7 : key >= 32 && key <= 348;
    }

    public static boolean groundStompBinding(ItemStack root, int key, boolean mouse) {
        return matchesBinding(root, "groundstomp", key, mouse);
    }

    /** Only supported native Earthquake metadata can admit this equipped input, including shared keys. */
    public static boolean earthquakeBinding(ItemStack root, int key, boolean mouse) {
        if (metadata(root, "earthquake") == null) { return false; }
        var mode = BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.fromNamespaceAndPath("justdirethings", "earthquake_bindingmode"));
        if (mode.isEmpty()) { return false; }
        @SuppressWarnings("unchecked") var type = (DataComponentType<Integer>) mode.get().value();
        return matchesBinding(root, "earthquake", key, mouse, type);
    }

    private static boolean matchesBinding(ItemStack root, String name, int key, boolean mouse) {
        Api binding = api;
        if (binding == null) { return false; }
        Setting setting = settings(binding).stream().filter(candidate -> candidate.name.equals(name)).findFirst().orElse(null);
        return setting != null && matchesBinding(root, name, key, mouse, setting.mode);
    }

    private static boolean matchesBinding(ItemStack root, String name, int key, boolean mouse, DataComponentType<Integer> mode) {
        Api binding = api;
        if (binding == null || !mixed(root) || !validBindingInput(key, mouse) || root.getOrDefault(mode, 0) != 2) { return false; }
        try {
            for (Object record : root.getOrDefault(binding.bindings, List.of())) {
                if (name.equals((String) binding.name.invokeExact(record)) && (int) binding.key.invokeExact(record) == key
                        && (boolean) binding.mouse.invokeExact(record) == mouse && (boolean) binding.equipped.invokeExact(record)) { return true; }
            }
            return false;
        } catch (Throwable failure) { disable(binding, failure); return false; }
    }

    /** Native passive order, deduplicated by ability, independent of live switches/FE/cooldowns. */
    public static List<String> boundPassives(ItemStack root, int key, boolean mouse) {
        Api binding = api;
        if (binding == null || !mixed(root)) { return List.of(); }
        var names = new ArrayList<String>();
        for (Setting setting : settings(binding)) {
            if (!setting.name.equals("groundstomp") && (setting.installed == null || root.getOrDefault(setting.installed, false))
                    && metadata(root, setting.name) != null && matchesBinding(root, setting.name, key, mouse)) {
                names.add(setting.name);
            }
        }
        return api == binding ? List.copyOf(names) : List.of();
    }

    public static boolean inputBinding(ItemStack root, int key, boolean mouse) {
        return groundStompBinding(root, key, mouse) || earthquakeBinding(root, key, mouse) || !boundPassives(root, key, mouse).isEmpty();
    }

    static boolean toggleBoundPassives(ServerPlayer player, ItemStack root, int key, boolean mouse) {
        Api binding = api;
        var names = boundPassives(root, key, mouse);
        if (binding == null || names.isEmpty()) { return false; }
        var messages = new ArrayList<Component>();
        try {
            // Resolve optional metadata before publishing any switch, never half-toggle a shared input.
            for (String name : names) {
                Setting setting = settings(binding).stream().filter(candidate -> candidate.name.equals(name)).findFirst().orElseThrow();
                String localization = (String) binding.localization.invokeExact(setting.ability);
                messages.add(Component.translatable("justdirethings.ability", Component.translatable(localization),
                        Component.translatable(root.getOrDefault(setting.toggle, true) ? "justdirethings.disabled" : "justdirethings.enabled")));
            }
        } catch (Throwable failure) { disable(binding, failure); return false; }
        if (api != binding || !JustDireGroundStompCompatibility.equipped(player, root)) { return false; }
        for (Setting setting : settings(binding)) {
            if (names.contains(setting.name)) { root.set(setting.toggle, !root.getOrDefault(setting.toggle, true)); }
        }
        messages.forEach(player::sendOverlayMessage);
        return true;
    }

    static int particles(ItemStack root) { Api binding = api; return binding == null ? -1 : root.getOrDefault(binding.particles, 0); }

    /** UI-only admission identity; never replace the selected inventory stack with this item. */
    public static Item metadataItem(ItemStack root) {
        for (String name : List.of("groundstomp", "stepheight", "jumpboost", "negatefalldamage", "earthquake", "lavarepair")) {
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
        for (Setting setting : settings(binding)) {
            if (metadata(root, setting.name) != null) { result.add((Enum) setting.ability); }
        }
        if (metadata(root, "earthquake") != null) { result.add((Enum) JustDireEarthquakeCompatibility.settingAbility()); }
        return api == binding ? result : null;
    }

    /** Each native widget needs its own strongest eligible tier, not the admission item's tier. */
    public static Object screenParameters(ItemStack root, Object ability) {
        Api binding = api;
        if (binding == null) { return null; }
        Setting setting = settings(binding).stream().filter(candidate -> candidate.ability == ability).findFirst().orElse(null);
        Profile profile = ability == JustDireEarthquakeCompatibility.settingAbility() ? profile(root, "earthquake")
                : setting == null ? null : profile(root, setting.name);
        if (profile == null || api != binding) { return null; }
        return profile.parameters;
    }

    public static int screenValue(ItemStack root, String name) {
        Api binding = api;
        Metadata metadata = metadata(root, name);
        if (binding == null || metadata == null) { return 0; }
        if (name.equals("earthquake") || name.equals("lavarepair")) { return metadata.defaultValue; }
        Setting setting = binding.settings.stream().filter(candidate -> candidate.name.equals(name)).findFirst().orElseThrow();
        return Math.clamp(root.getOrDefault(setting.value, metadata.defaultValue), metadata.minimum, metadata.maximum);
    }

    /** Prepare on a copy and publish once only after all optional reads and validation succeed. */
    static boolean edit(ItemStack root, JustDireActiveNetwork.SettingsRequest request) {
        Api binding = api;
        if (binding == null || !mixed(root)) { return false; }
        if (request.ability().equals("earthquake")) { return JustDireEarthquakeCompatibility.edit(root, request); }
        ItemStack changed = root.copy();
        try {
            Setting setting = settings(binding).stream().filter(candidate -> candidate.name.equals(request.ability())).findFirst().orElse(null);
            Metadata metadata = metadata(root, request.ability());
            if (setting == null || metadata == null || setting.installed != null && !root.getOrDefault(setting.installed, false)) { return false; }
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
                    if ((request.value() != 0 && request.value() != 2)
                            || !request.equipped() || (request.key() != -1 && !validBindingInput(request.key(), request.mouse()))) { return false; }
                    var next = new ArrayList<Object>();
                    for (Object record : root.getOrDefault(binding.bindings, List.of())) {
                        if (!setting.name.equals((String) binding.name.invokeExact(record))) { next.add(record); }
                    }
                    if (request.value() == 2 && request.key() != -1) {
                        next.add(binding.create.invokeExact(setting.name, request.key(), request.mouse(), true));
                    }
                    changed.set(binding.bindings, List.copyOf(next)); changed.set(setting.mode, request.value());
                }
                default -> { return false; }
            }
        } catch (Throwable failure) { disable(binding, failure); return false; }
        if (api != binding) { return false; }
        // Only the allowed root-owned fields are published. Sources, resources and timers are never edits.
        for (Setting setting : settings(binding)) {
            copyField(changed, root, setting.toggle);
            if (setting.value != null) { copyField(changed, root, setting.value); }
            copyField(changed, root, setting.mode);
        }
        copyField(changed, root, binding.bindings);
        copyField(changed, root, binding.particles);
        return true;
    }

    private static <T> void copyField(ItemStack from, ItemStack to, DataComponentType<T> type) {
        if (from.has(type)) { to.set(type, from.get(type)); } else { to.remove(type); }
    }

    static boolean editEarthquakeBinding(ItemStack root, JustDireActiveNetwork.SettingsRequest request, DataComponentType<Integer> mode) {
        Api binding = api;
        if (binding == null || (request.value() != 0 && request.value() != 2) || !request.equipped()
                || request.key() != -1 && !validBindingInput(request.key(), request.mouse())) { return false; }
        List<Object> next = new ArrayList<>();
        try {
            for (Object record : root.getOrDefault(binding.bindings, List.of())) {
                if (!"earthquake".equals((String) binding.name.invokeExact(record))) { next.add(record); }
            }
            if (request.value() == 2 && request.key() != -1) {
                next.add(binding.create.invokeExact("earthquake", request.key(), request.mouse(), true));
            }
        } catch (Throwable failure) { disable(binding, failure); return false; }
        if (api != binding) { return false; }
        root.set(binding.bindings, List.copyOf(next)); root.set(mode, request.value());
        return true;
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
        public boolean hasSlider() { return minimum >= 0 && maximum > minimum && increment > 0; }
    }
    private record Setting(String name, Object ability, DataComponentType<Boolean> installed,
                           DataComponentType<Boolean> toggle, DataComponentType<Integer> value, DataComponentType<Integer> mode) {}
    private record Profile(Item item, Metadata metadata, Object parameters) {}
    private record Api(Class<?> menu, Class<?> tool, List<Setting> settings, MethodHandle abilities, MethodHandle params,
                       MethodHandle minimum, MethodHandle maximum, MethodHandle increment, MethodHandle defaultValue,
                       MethodHandle name, MethodHandle key, MethodHandle mouse, MethodHandle equipped, MethodHandle create, MethodHandle localization,
                       DataComponentType<List<Object>> bindings,
                       DataComponentType<Integer> particles) {}
}
