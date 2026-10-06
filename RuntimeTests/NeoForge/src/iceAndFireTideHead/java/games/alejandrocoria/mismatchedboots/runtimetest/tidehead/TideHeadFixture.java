package games.alejandrocoria.mismatchedboots.runtimetest.tidehead;

import games.alejandrocoria.mismatchedboots.runtimetest.tidehead.mixin.ArmorAccess;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Independent HEAD fixture. No FEET classes, data, mod IDs, readiness or dispatcher. */
@Mod("tide_head_owner")
public final class TideHeadFixture {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("tide_head_owner");
    private static final java.util.function.Supplier<Item> ROOT = ITEMS.registerItem("mixed_head", Root::new);
    private static Item receiver;
    private static int activations;
    private static int warnings;
    private static boolean warned;
    private static boolean disabled;
    private static int bindingAttempts;

    public TideHeadFixture(IEventBus bus) {
        if (System.getProperty("mismatchedboots.runtimetest.tideOwners", "").isEmpty()) {
            throw new IllegalStateException("HEAD owner is an isolated test fixture, not a gameplay mod");
        }
        ITEMS.register(bus);
        var api = TideOptionalApi.inspect();
        if (!api.available()) {
            disabled = true;
            if (api.present()) { disable(api.reason()); }
        }
    }

    public static int activations() { return activations; }
    public static int warnings() { return warnings; }
    public static int bindingAttempts() { return bindingAttempts; }
    public static int apiAvailable() { return TideOptionalApi.inspect().available() ? 1 : 0; }

    public static boolean recognizes(Entity entity, EquipmentSlot slot) {
        if (disabled || slot != EquipmentSlot.HEAD || !(entity instanceof Player player)) { return false; }
        try {
            return qualifies(player.getItemBySlot(slot)) && ready();
        } catch (LinkageError failure) {
            disable("pre-invocation linkage failure: " + failure.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean ready() {
        if (receiver == null) {
            bindingAttempts++;
            String fault = System.getProperty("mismatchedboots.runtimetest.tideHeadReceiver", "");
            if (fault.equals("linkage")) { throw new NoSuchMethodError("Injected fixed HEAD receiver binding failure"); }
            receiver = fault.equals("replaced") ? Items.IRON_HELMET : item("iceandfire:tide_blue_helmet");
        }
        boolean result = nativeSource(receiver) && receiver instanceof Ready entry && entry.tideHead$entryReady()
                && receiver instanceof Ready.Base base && base.tideHead$baseReady();
        if (!result) { disable("fixed receiver or entry/base readiness unavailable"); }
        return result;
    }

    private static void disable(String reason) {
        disabled = true;
        if (!warned) {
            warned = true; warnings++;
            org.slf4j.LoggerFactory.getLogger(TideHeadFixture.class).warn("TEST-ONLY HEAD Tide adapter disabled: {}", reason);
        }
    }

    private static boolean qualifies(ItemStack root) {
        if (root.isEmpty() || root.getCount() != 1 || root.getItem() != ROOT.get()) { return false; }
        CustomData data = root.get(DataComponents.CUSTOM_DATA);
        if (data == null) { return false; }
        CompoundTag tag = data.copyTag();
        Item first = item(tag.getStringOr("primary_head", ""));
        Item second = item(tag.getStringOr("secondary_head", ""));
        return validSource(first) && validSource(second) && (nativeSource(first) || nativeSource(second));
    }

    private static boolean validSource(Item item) {
        var component = item == null ? null : item.components().get(DataComponents.EQUIPPABLE);
        return component != null && component.slot() == EquipmentSlot.HEAD;
    }

    private static boolean nativeSource(Item item) {
        return item != null && item.getClass().getName().equals("com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem")
                && item instanceof ArmorAccess access && access.tideHead$armorType() != null
                && access.tideHead$armorType().getSlot() == EquipmentSlot.HEAD;
    }

    private static Item item(String id) {
        Identifier identifier = Identifier.tryParse(id);
        return identifier == null ? null : BuiltInRegistries.ITEM.get(identifier).map(h -> h.value()).orElse(null);
    }

    private static final class Root extends Item {
        Root(Properties properties) { super(properties.stacksTo(1)); }

        @Override public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
            super.inventoryTick(stack, level, entity, slot);
            if (!(entity instanceof ServerPlayer player) || slot != EquipmentSlot.HEAD
                    || player.getItemBySlot(slot) != stack || !recognizes(player, slot)) { return; }
            // Native invocation is deliberately outside the preflight LinkageError catch.
            ItemStack disposable = new ItemStack(receiver);
            ItemStack before = disposable.copy();
            receiver.inventoryTick(disposable, level, entity, slot);
            if (!ItemStack.matches(before, disposable)) { throw new AssertionError("HEAD bridge mutated carrier"); }
            activations++;
        }
    }
}
