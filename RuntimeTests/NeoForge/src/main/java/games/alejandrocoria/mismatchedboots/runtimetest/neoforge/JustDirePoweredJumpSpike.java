package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireUpgradesCompatibility;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;

import java.lang.reflect.Method;
import java.util.List;

/** Opt-in query-only candidate: native tier checks with current root resources, never a second dispatcher. */
public final class JustDirePoweredJumpSpike {
    public static int queries;
    static String lastQuery = "none";
    private static Object jump;
    private static Method usable, slider;

    private JustDirePoweredJumpSpike() {}

    static synchronized void bind() throws ReflectiveOperationException {
        if (jump != null) { return; }
        var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
        var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
        jump = ability.getField("JUMPBOOST").get(null);
        usable = tool.getMethod("canUseAbilityAndDurability", ItemStack.class, ability);
        slider = tool.getMethod("getToolValue", ItemStack.class, String.class);
        require((int) ability.getMethod("getFeCost").invoke(jump) == 5
                && (int) ability.getMethod("getDurabilityCost").invoke(jump) == 1, "Pinned Jump resource metadata changed");
    }

    public static int value(ItemStack root) {
        if (root.getCount() != 1 || !(root.getItem() instanceof MixedBootsItem)) { return 0; }
        var data = ModContent.getMixedBootsData(root).orElse(null);
        if (data == null) { return 0; }
        var left = data.left().source(); var right = data.right().source();
        if (!BootHalf.isValidSource(left) || !BootHalf.isValidSource(right)) { return 0; }
        queries++;
        lastQuery = "root FE=" + root.getOrDefault(JustDireAttributeTestSupport.<Integer>component("forge_energy"), 0);
        try { bind(); return Math.max(provider(left, root), provider(right, root)); }
        catch (ReflectiveOperationException failure) { throw new AssertionError("Powered Jump research binding failed", failure); }
    }

    private static int provider(ItemStack view, ItemStack root) throws ReflectiveOperationException {
        var id = BuiltInRegistries.ITEM.getKey(view.getItem());
        if (!id.getNamespace().equals("justdirethings") || !List.of("ferricore_boots", "blazegold_boots",
                "celestigem_boots", "eclipsealloy_boots").contains(id.getPath())) { return 0; }
        if (!JustDireUpgradesCompatibility.overlayInstallation(view, root)) { return 0; }
        for (String control : List.of("tool_enabled", "jumpboost_toggle")) {
            JustDireAttributeTestSupport.set(view, control, root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component(control), true));
        }
        var setting = JustDireAttributeTestSupport.<Integer>component("jumpboost_value");
        view.remove(setting);
        if (root.has(setting)) { view.set(setting, root.get(setting)); }
        var energy = JustDireAttributeTestSupport.<Integer>component("forge_energy");
        if (id.getPath().equals("celestigem_boots") || id.getPath().equals("eclipsealloy_boots")) {
            var wallet = ItemAccess.forStack(root).getCapability(Capabilities.Energy.ITEM);
            if (wallet == null) { lastQuery += "; " + id + " no root wallet"; return 0; }
            view.set(energy, wallet.getAmountAsInt());
        }
        // Only detached query copies receive live wear. Native PoweredItem uses FE, not this durability branch.
        view.remove(DataComponents.MAX_DAMAGE); view.remove(DataComponents.DAMAGE);
        if (root.has(DataComponents.MAX_DAMAGE)) { view.set(DataComponents.MAX_DAMAGE, root.get(DataComponents.MAX_DAMAGE)); }
        if (root.has(DataComponents.DAMAGE)) { view.set(DataComponents.DAMAGE, root.get(DataComponents.DAMAGE)); }
        boolean allowed = (boolean) usable.invoke(view.getItem(), view, jump);
        lastQuery += "; " + id + " view FE=" + view.getOrDefault(energy, 0) + " installed="
                + view.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("jumpboost_upgrade_installed"), false) + " allowed=" + allowed;
        if (!allowed) { return 0; }
        return (int) slider.invoke(null, view, "jumpboost");
    }

    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
