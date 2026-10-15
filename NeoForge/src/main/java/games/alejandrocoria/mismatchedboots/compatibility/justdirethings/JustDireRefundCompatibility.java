package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.Constants;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Conserved root entitlement. Actual-break observation is deliberately not enabled yet. */
public final class JustDireRefundCompatibility {
    private static final DeferredRegister<DataComponentType<?>> TYPES = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Constants.MOD_ID);
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<JustDireRefundCredits>> CREDITS =
            TYPES.register("jdt_refundable_upgrades", () -> DataComponentType.<JustDireRefundCredits>builder()
                    .persistent(JustDireRefundCredits.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(JustDireRefundCredits.CODEC)).build());
    private static volatile Map<String, Refund> refunds;

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

    private record Refund(Item item, DataComponentType<Boolean> installed) {}
}
