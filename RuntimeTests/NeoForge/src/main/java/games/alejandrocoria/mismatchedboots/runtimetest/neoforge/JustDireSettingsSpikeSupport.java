package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireUpgradesCompatibility;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;

/** Disposable Ground Stomp metadata views; never an inventory/tool identity or mutation target. */
public final class JustDireSettingsSpikeSupport {
    private static final String TOOL = "com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool";
    private static final String ABILITY = "com.direwolf20.justdirethings.common.items.interfaces.Ability";

    private JustDireSettingsSpikeSupport() {}

    public static boolean mixed(ItemStack stack) {
        return stack.getItem() instanceof MixedBootsItem && stack.getCount() == 1
                && ModContent.getMixedBootsData(stack).isPresent();
    }

    public static ItemStack view(ItemStack root) {
        if (!mixed(root) || System.getProperty("mismatchedboots.runtimetest.justDireSettingsVariant", "live").equals("apiMissing")) {
            return null;
        }
        try {
            var data = ModContent.getMixedBootsData(root).orElseThrow();
            ItemStack selected = null;
            int highest = 0;
            for (var source : List.of(data.left().source(), data.right().source())) {
                if (!JustDireUpgradesCompatibility.nativeBoot(source)) { continue; }
                var abilities = (EnumSet<?>) Class.forName(TOOL).getMethod("getAbilities").invoke(source.getItem());
                if (!abilities.contains(ability())) { continue; }
                int maximum = parameter(source, "maxSlider");
                if (maximum > highest) { selected = source; highest = maximum; }
            }
            if (selected == null || !JustDireUpgradesCompatibility.overlayInstallation(selected, root)) { return null; }
            for (String path : List.of("groundstomp_toggle", "groundstomp_value", "groundstomp_custom_setting",
                    "groundstomp_bindingmode", "ability_bindings", "tool_enabled")) {
                var type = JustDireAttributeTestSupport.<Object>component(path);
                selected.remove(type);
                if (root.has(type)) { selected.set(type, root.get(type)); }
            }
            return selected;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned settings API changed", failure); }
    }

    static Object ability() throws ReflectiveOperationException { return Class.forName(ABILITY).getField("GROUNDSTOMP").get(null); }

    static int parameter(ItemStack view, String field) throws ReflectiveOperationException {
        var params = Class.forName(TOOL).getMethod("getAbilityParams", Class.forName(ABILITY)).invoke(view.getItem(), ability());
        return params.getClass().getField(field).getInt(params);
    }

    public static int value(ItemStack root) {
        var view = view(root);
        if (view == null) { throw new IllegalStateException("Unavailable mixed settings view"); }
        try { return (int) Class.forName(TOOL).getMethod("getToolValue", ItemStack.class, String.class).invoke(null, view, "groundstomp"); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static EnumSet<?> abilities() {
        try { return EnumSet.of((Enum) ability()); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
}
