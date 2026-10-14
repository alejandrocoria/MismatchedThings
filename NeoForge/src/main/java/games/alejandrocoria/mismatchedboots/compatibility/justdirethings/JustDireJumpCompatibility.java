package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Read-only Jump queries and one coordinated fall dispatcher; Negate owns its separate FE commit. */
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

    public static void fall(LivingFallEvent event, ItemStack root, boolean negateReady) {
        if (negateReady && JustDireNegateFallCompatibility.tryNegate(event, root)) { return; }
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
            return Math.max(binding.value(left, root), binding.value(right, root));
        } catch (Throwable failure) {
            if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) { throw fatal; }
            // The guarded optional calls above are read-only; no movement/fall change has occurred.
            // Never catch failures around the actual player operation or other native gameplay.
            synchronized (JustDireJumpCompatibility.class) {
                if (api != null) {
                    api = null;
                    Constants.LOG.warn("Just Dire Things Jump queries failed; compatibility disabled", failure);
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
            Class<?> powered = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.PoweredItem", true, loader);
            var lookup = MethodHandles.publicLookup();
            api = new Api(jump,
                    lookup.findVirtual(tool, "canUseAbility", MethodType.methodType(boolean.class, ItemStack.class, ability))
                            .asType(MethodType.methodType(boolean.class, Object.class, ItemStack.class, Object.class)),
                    lookup.findStatic(tool, "getToolValue", MethodType.methodType(int.class, ItemStack.class, String.class)),
                    lookup.findStatic(helpers, "testUseTool", MethodType.methodType(int.class, ItemStack.class, ability))
                            .asType(MethodType.methodType(int.class, ItemStack.class, Object.class)),
                    component("tool_enabled"), component("jumpboost_toggle"), component("jumpboost_value"), component("forge_energy"),
                    item("ferricore_boots"), item("blazegold_boots"), item("celestigem_boots"), item("eclipsealloy_boots"));
            if (!tool.isInstance(api.ferricore) || !tool.isInstance(api.blazegold)
                    || !tool.isInstance(api.celestigem) || !tool.isInstance(api.eclipse)
                    || powered.isInstance(api.ferricore) || powered.isInstance(api.blazegold)
                    || !powered.isInstance(api.celestigem) || !powered.isInstance(api.eclipse)) {
                throw new IllegalStateException("Unsupported native Jump boot API");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            api = null;
            Constants.LOG.warn("Could not bind Just Dire Things Jump API; compatibility disabled", failure);
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
                       DataComponentType<Integer> value, DataComponentType<Integer> energy,
                       Item ferricore, Item blazegold, Item celestigem, Item eclipse) {
        int value(ItemStack source, ItemStack root) throws Throwable {
            boolean powered = source.getItem() == celestigem || source.getItem() == eclipse;
            if (!powered && source.getItem() != ferricore && source.getItem() != blazegold) { return 0; }
            // Tier stays native; installation and live controls belong exclusively to root.
            if (!JustDireUpgradesCompatibility.overlayInstallation(source, root)) { return 0; }
            source.set(enabled, root.getOrDefault(enabled, true));
            source.set(toggle, root.getOrDefault(toggle, true));
            source.remove(value);
            if (root.has(value)) { source.set(value, root.get(value)); } // Otherwise use this tier's native default, not historical settings.
            if (!(boolean) usable.invokeExact((Object) source.getItem(), source, jump)) { return 0; }
            // Native item identity selects FE or wear. A global mixed-root wear veto would
            // wrongly reject charged powered providers; frozen source resources are never live.
            if (powered) {
                var wallet = ItemAccess.forStack(root).getCapability(Capabilities.Energy.ITEM);
                if (wallet == null) { return 0; }
                source.set(energy, wallet.getAmountAsInt());
            } else {
                source.remove(DataComponents.MAX_DAMAGE);
                source.remove(DataComponents.DAMAGE);
                if (root.has(DataComponents.MAX_DAMAGE)) { source.set(DataComponents.MAX_DAMAGE, root.get(DataComponents.MAX_DAMAGE)); }
                if (root.has(DataComponents.DAMAGE)) { source.set(DataComponents.DAMAGE, root.get(DataComponents.DAMAGE)); }
            }
            if ((int) resource.invokeExact(source, jump) < 0) { return 0; }
            return (int) slider.invokeExact(source, "jumpboost");
        }
    }
}
