package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** A fixture-only independent helmet adapter, plus observable same-FEET wrapper chains. */
public final class IceAndFireCoexistenceFixture {
    public static final String CASE = System.getProperty("mismatchedboots.runtimetest.iceAndFireCoexistence", "");
    private static final String SOURCE = "fixture_helmet_source";
    public static int highFeet;
    public static int lowFeet;
    public static int highLightning;
    public static int lowLightning;
    public static int logCalls;

    private IceAndFireCoexistenceFixture() {
    }

    public static ItemStack helmet(Item source) {
        ItemStack root = new ItemStack(Items.IRON_HELMET);
        CompoundTag tag = new CompoundTag();
        tag.putString(SOURCE, BuiltInRegistries.ITEM.getKey(source).toString());
        root.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return root;
    }

    private static Item source(ItemStack root) {
        if (!root.is(Items.IRON_HELMET)) {
            return null;
        }
        var data = root.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        String id = data.copyTag().getStringOr(SOURCE, "");
        return id.isEmpty() ? null : BuiltInRegistries.ITEM.get(Identifier.parse(id)).map(holder -> holder.value()).orElse(null);
    }

    public static boolean head(boolean high, Object item, Operation<Boolean> original, LivingDamageEvent.Pre event) {
        if (original.call(item)) {
            return true;
        }
        Item source = source(event.getEntity().getItemBySlot(EquipmentSlot.HEAD));
        return ownsHead(high) && source != null && original.call(source);
    }

    public static boolean lightningHead(boolean high, ItemStack stack, Object exactItem, Operation<Boolean> original) {
        return original.call(stack, exactItem) || ownsHead(high) && source(stack) == exactItem;
    }

    private static boolean ownsHead(boolean high) {
        return (high ? "high" : "low").equals(CASE);
    }
}
