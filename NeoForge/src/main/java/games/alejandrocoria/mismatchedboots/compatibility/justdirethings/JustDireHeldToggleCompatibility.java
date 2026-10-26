package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

/** Held mixed fallback only: native toggles and Portal Gun V2 keep their existing input route. */
public final class JustDireHeldToggleCompatibility {
    private static volatile Api api;

    private JustDireHeldToggleCompatibility() {
    }

    public static void initialize(IEventBus bus) {
        if (ModList.get().isLoaded("justdirethings")) {
            bus.addListener(JustDireHeldToggleCompatibility::bind);
        }
    }

    private static void bind(RegisterCapabilitiesEvent event) {
        try {
            var lookup = MethodHandles.publicLookup();
            var toggleable = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableItem");
            var portal = Class.forName("com.direwolf20.justdirethings.common.items.PortalGunV2");
            api = new Api(
                    lookup.findStatic(toggleable, "getToggleableItem", MethodType.methodType(ItemStack.class, Player.class)),
                    lookup.findStatic(portal, "getPortalGunv2", MethodType.methodType(ItemStack.class, Player.class)),
                    lookup.findVirtual(toggleable, "toggleEnabled", MethodType.methodType(void.class, ItemStack.class, Player.class))
                          .asType(MethodType.methodType(void.class, Item.class, ItemStack.class, Player.class)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            disable(failure);
        }
    }

    public static InteractionHand target(Player player) {
        Api binding = api;
        if (binding == null) {
            return null;
        }

        try {
            // Do not compete with JDT's tick listener, including its special portal radial menu.
            if (!((ItemStack) binding.nativeHeld.invokeExact(player)).isEmpty()
                    || !((ItemStack) binding.portalHeld.invokeExact(player)).isEmpty()) {
                return null;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                if (provider(player.getItemInHand(hand)) != null) {
                    return hand;
                }
            }
        } catch (Throwable failure) {
            if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) {
                throw fatal;
            }
            disable(failure);
        }

        return null;
    }

    public static boolean toggle(ServerPlayer player, InteractionHand hand) {
        Api binding = api;
        if (binding == null || player.containerMenu != player.inventoryMenu || target(player) != hand) {
            return false;
        }

        ItemStack root = player.getItemInHand(hand);
        Item receiver = provider(root);
        if (receiver == null || api != binding) {
            return false;
        }

        try {
            // Audited native method only flips root TOOL_ENABLED and emits its own message/sound.
            // The native receiver is never equipped or written into inventory/source provenance.
            binding.toggle.invokeExact(receiver, root, (Player) player);
            return true;
        } catch (Throwable failure) {
            if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) {
                throw fatal;
            }
            disable(failure);
            return false;
        }
    }

    private static Item provider(ItemStack root) {
        if (!JustDireSettingsCompatibility.mixed(root)) {
            return null;
        }
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        for (ItemStack source : List.of(data.left().source(), data.right().source())) {
            if (JustDireUpgradesCompatibility.nativeBoot(source)) {
                return source.getItem();
            }
        }
        return null;
    }

    private static void disable(Throwable failure) {
        api = null;
        Constants.LOG.warn("Just Dire Things held mixed toggle unavailable; held shortcut disabled", failure);
    }

    private record Api(MethodHandle nativeHeld, MethodHandle portalHeld, MethodHandle toggle) {
    }
}
