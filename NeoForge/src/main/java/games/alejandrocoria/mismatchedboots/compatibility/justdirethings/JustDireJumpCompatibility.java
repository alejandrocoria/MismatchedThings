package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Passive queries only: provenance authorizes a tier; root settings and wear stay live. */
public final class JustDireJumpCompatibility {
    private static Api api;
    private static boolean attempted;

    private JustDireJumpCompatibility() {}

    public static void jump(Player player, ItemStack root) {
        int value = value(root);
        if (value > 0 && !player.isInWater() && !player.isFallFlying()) {
            player.moveRelative(value / 7.5f, new Vec3(0, 1, 0));
        }
    }

    public static void fall(LivingFallEvent event, ItemStack root) {
        int value = value(root);
        if (value > 0) { event.setDistance(event.getDistance() - value); }
    }

    static int value(ItemStack root) {
        if (root.isEmpty() || root.getCount() != 1 || !(root.getItem() instanceof MixedBootsItem)) { return 0; }
        var data = ModContent.mixedBootsDataType().map(root::get).orElse(null);
        if (data == null) { return 0; }
        ItemStack left = data.left().source();
        ItemStack right = data.right().source();
        if (!BootHalf.isValidSource(left) || !BootHalf.isValidSource(right)) { return 0; }
        Api binding = binding();
        if (binding == null) { return 0; }
        try {
            if ((int) binding.resource.invokeExact(root, binding.jump) < 0) { return 0; }
            return Math.max(binding.value(left, root), binding.value(right, root));
        } catch (Throwable failure) {
            if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) { throw fatal; }
            // The guarded optional calls above are read-only; no movement/fall change has occurred.
            // Never catch failures around the actual player operation or other native gameplay.
            synchronized (JustDireJumpCompatibility.class) {
                if (api != null) {
                    api = null;
                    Constants.LOG.warn("Just Dire Things passive Jump queries failed; compatibility disabled", failure);
                }
            }
            return 0;
        }
    }

    private static synchronized Api binding() {
        if (attempted) { return api; }
        attempted = true;
        try {
            ClassLoader loader = JustDireJumpCompatibility.class.getClassLoader();
            Class<?> ability = Class.forName(JustDireJumpShape.ABILITY.replace('/', '.'), true, loader);
            Object jump = ability.getField("JUMPBOOST").get(null);
            Class<?> tool = Class.forName(JustDireJumpShape.TOOL.replace('/', '.'), true, loader);
            Class<?> helpers = Class.forName(JustDireJumpShape.HELPERS.replace('/', '.'), true, loader);
            var lookup = MethodHandles.publicLookup();
            api = new Api(jump,
                    lookup.findVirtual(tool, "canUseAbility", MethodType.methodType(boolean.class, ItemStack.class, ability))
                            .asType(MethodType.methodType(boolean.class, Object.class, ItemStack.class, Object.class)),
                    lookup.findStatic(tool, "getToolValue", MethodType.methodType(int.class, ItemStack.class, String.class)),
                    lookup.findStatic(helpers, "testUseTool", MethodType.methodType(int.class, ItemStack.class, ability))
                            .asType(MethodType.methodType(int.class, ItemStack.class, Object.class)),
                    component("tool_enabled"), component("jumpboost_toggle"), component("jumpboost_value"),
                    item("ferricore_boots"), item("blazegold_boots"));
            if (!tool.isInstance(api.ferricore) || !tool.isInstance(api.blazegold)) {
                throw new IllegalStateException("Unsupported non-powered boot API");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            api = null;
            Constants.LOG.warn("Could not bind Just Dire Things passive Jump API; compatibility disabled", failure);
        }
        return api;
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", path))
                .orElseThrow().value();
    }

    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value();
    }

    private record Api(Object jump, MethodHandle usable, MethodHandle slider, MethodHandle resource,
                       DataComponentType<Boolean> enabled, DataComponentType<Boolean> toggle,
                       DataComponentType<Integer> value, Item ferricore, Item blazegold) {
        int value(ItemStack source, ItemStack root) throws Throwable {
            if (source.getItem() != ferricore && source.getItem() != blazegold) { return 0; }
            // Defensive source copy retains installed provenance and native tier, never source wear as a wallet.
            source.set(enabled, root.getOrDefault(enabled, true));
            source.set(toggle, root.getOrDefault(toggle, true));
            if (root.has(value)) { source.set(value, root.get(value)); }
            if (!(boolean) usable.invokeExact((Object) source.getItem(), source, jump)) { return 0; }
            return (int) slider.invokeExact(source, "jumpboost");
        }
    }
}
