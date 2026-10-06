package games.alejandrocoria.mismatchedboots.compatibility.iceandfire;

import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import games.alejandrocoria.mismatchedboots.mixin.IceAndFireTideArmorAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/** Audited Tide consumer only: never forwards a source item's tick or lends its snapshot. */
public final class IceAndFireTideCompatibility {
    private static final String NATIVE_CLASS = "com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem";
    private static final Identifier RECEIVER_ID = Identifier.parse("iceandfire:tide_blue_boots");
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static Item receiver;
    private static volatile boolean disabled;

    private IceAndFireTideCompatibility() {}

    public static void initialize() {
        var api = IceAndFireTideApi.inspect();
        if (!api.available()) {
            disabled = true;
            if (api.present()) { disable(api.reason()); }
        }
    }

    public static boolean recognizes(Entity entity, EquipmentSlot slot) {
        if (disabled || slot != EquipmentSlot.FEET || !(entity instanceof Player player)) { return false; }
        try {
            return MixedBootsSources.anySource(player.getItemBySlot(slot), source -> nativeBoots(source.getItem()))
                    && ready();
        } catch (LinkageError failure) {
            // Preflight only: native gameplay may already have side effects and must not be retried.
            disable("pre-invocation linkage failure: " + failure.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean nativeBoots(Item item) {
        return item != null && item.getClass().getName().equals(NATIVE_CLASS)
                && item instanceof IceAndFireTideArmorAccess access
                && access.mismatchedboots$tideArmorType() != null
                && access.mismatchedboots$tideArmorType().getSlot() == EquipmentSlot.FEET;
    }

    private static boolean ready() {
        if (receiver == null) { receiver = resolveReceiver(); }
        boolean ready = nativeBoots(receiver)
                && receiver instanceof IceAndFireTideReadiness entry && entry.mismatchedboots$tideEntryReady()
                && receiver instanceof IceAndFireTideReadiness.Base base && base.mismatchedboots$tideBaseReady();
        if (!ready) { disable("fixed receiver or final entry/base readiness unavailable"); }
        return ready;
    }

    private static Item resolveReceiver() {
        return BuiltInRegistries.ITEM.get(RECEIVER_ID).map(holder -> holder.value()).orElse(null);
    }

    private static void disable(String reason) {
        disabled = true;
        if (WARNED.compareAndSet(false, true)) {
            LoggerFactory.getLogger(IceAndFireTideCompatibility.class).warn("Ice and Fire Tide adapter disabled: {}", reason);
        }
    }

    public static void activate(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot tickSlot) {
        if (tickSlot != EquipmentSlot.FEET || !(entity instanceof Player player)
                || player.getItemBySlot(EquipmentSlot.FEET) != root || !recognizes(player, EquipmentSlot.FEET)) { return; }
        // Final proof permits this fixed receiver + disposable carrier, not arbitrary item dispatch.
        // Equipment and both stored sources remain owned by the player/root throughout the call.
        receiver.inventoryTick(new ItemStack(receiver), level, player, EquipmentSlot.FEET);
    }
}
