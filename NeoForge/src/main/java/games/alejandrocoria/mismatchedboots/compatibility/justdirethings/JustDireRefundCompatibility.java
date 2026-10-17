package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Conserved root entitlement, returned only by the independently guarded actual-break observer. */
public final class JustDireRefundCompatibility {
    private static final DeferredRegister<DataComponentType<?>> TYPES = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Constants.MOD_ID);
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<JustDireRefundCredits>> CREDITS =
            TYPES.register("jdt_refundable_upgrades", () -> DataComponentType.<JustDireRefundCredits>builder()
                    .persistent(JustDireRefundCredits.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(JustDireRefundCredits.CODEC)).build());
    private static volatile Map<String, Refund> refunds;
    private static final ThreadLocal<Set<ItemStack>> IN_FLIGHT = ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private JustDireRefundCompatibility() {}

    public static void initialize(IEventBus bus) { TYPES.register(bus); }
    public static DataComponentType<JustDireRefundCredits> component() { return CREDITS.get(); }
    public static boolean available() { return refunds != null; }

    /** Called only after the existing installed-upgrade binder has published its validated metadata. */
    static void bindItems() {
        try {
            var bound = new LinkedHashMap<String, Refund>();
            for (String name : JustDireRefundCredits.NAMES) {
                var item = BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", "upgrade_" + name)).orElseThrow().value();
                bound.put(name, new Refund(item, JustDireUpgradesCompatibility.installedFlag(name)));
            }
            refunds = Map.copyOf(bound);
        } catch (RuntimeException | LinkageError failure) {
            Constants.LOG.warn("Could not bind Just Dire Things refund items; upgrade refunds disabled", failure);
        }
    }

    static void initializeCredits(ItemStack output, List<String> ownerPurchases) {
        // The factory first copies owner components. Never trust an own credit forged onto a native input.
        output.remove(component());
        if (!ownerPurchases.isEmpty()) { output.set(component(), new JustDireRefundCredits(ownerPurchases)); }
    }

    static void preserve(ItemStack previous, ItemStack output) {
        // Explicitly override generic projection, including absence, even after optional binding refusal.
        output.remove(component());
        if (previous.has(component())) { output.set(component(), previous.get(component())); }
    }

    static void addInstallation(ItemStack output, String name) {
        var previous = output.get(component());
        var names = new ArrayList<>(previous == null ? List.<String>of() : previous.names());
        names.add(name);
        output.set(component(), new JustDireRefundCredits(names));
    }

    public static void route(boolean ready, ItemStack root, int count, LivingEntity owner, Operation<Void> ordinary) {
        var capture = ready ? capture(root, count, owner) : null;
        if (capture == null) { ordinary.call(root, count); return; }
        // A foreign shrink wrapper can reenter before count reaches zero. Ownership lasts only this call.
        var inFlight = IN_FLIGHT.get();
        if (!inFlight.add(root)) { ordinary.call(root, count); return; }
        try {
            ordinary.call(root, count);
            complete(capture);
        } finally {
            inFlight.remove(root);
            if (inFlight.isEmpty()) { IN_FLIGHT.remove(); }
        }
    }

    private static Capture capture(ItemStack root, int count, LivingEntity owner) {
        var binding = refunds;
        if (binding == null || !(owner instanceof ServerPlayer player) || player.hasInfiniteMaterials() || player.isSpectator()
                || count != 1 || root.getCount() != 1
                || !(root.getItem() instanceof MixedBootsItem)) { return null; }
        // Actual inventory actions may break boots outside FEET. Follow one exact supported slot,
        // not a menu/action permission: combat/direct breaks also refund while another menu is open.
        JustDireAbilityTarget target = null;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (JustDireAbilityTarget.supportedSlot(slot) && player.getInventory().getItem(slot) == root) {
                target = new JustDireAbilityTarget(player, slot, root); break;
            }
        }
        if (target == null) { return null; }
        try {
            if (ModContent.getMixedBootsData(root).isEmpty() || !root.isDamageableItem() || root.getMaxDamage() <= 0
                    || root.getDamageValue() < root.getMaxDamage()) { return null; }
            var credits = root.get(component());
            if (credits == null || credits.names().isEmpty()) { return null; }
            var items = new ArrayList<Item>();
            for (String name : credits.names()) {
                var refund = binding.get(name);
                if (refund == null || !root.getOrDefault(refund.installed, false)) { return null; }
                items.add(refund.item);
            }
            // Empty-stack getters hide their components. Retain the live map now, plus detached native lists.
            return new Capture(root, player, target, binding, JustDireActiveState.snapshot(root), root.getComponents(), List.copyOf(items));
        } catch (RuntimeException malformedState) { return null; }
    }

    private static void complete(Capture capture) {
        if (refunds != capture.binding || !capture.root.isEmpty()
                || !capture.target.present()) { return; }
        var shadow = new ItemStack(capture.before.getItem(), 1);
        List.copyOf(shadow.getComponents().keySet()).forEach(shadow::remove);
        shadow.applyComponents(capture.live);
        if (!ItemStack.matches(capture.before, shadow)) { return; }
        // This local authorization is consumed before world callbacks; never retry a cancelled/throwing drop.
        for (Item item : capture.items) { capture.player.drop(new ItemStack(item), true); }
    }

    private record Refund(Item item, DataComponentType<Boolean> installed) {}
    private record Capture(ItemStack root, ServerPlayer player, JustDireAbilityTarget target, Map<String, Refund> binding,
                           ItemStack before, DataComponentMap live, List<Item> items) {}
}
