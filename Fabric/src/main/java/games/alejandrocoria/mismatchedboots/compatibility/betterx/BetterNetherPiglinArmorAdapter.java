package games.alejandrocoria.mismatchedboots.compatibility.betterx;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class BetterNetherPiglinArmorAdapter {
    private static final String[] SET_FIELDS = {
            "CINCINNASITE_SET",
            "NETHER_RUBY_SET",
            "FLAMING_RUBY_SET"
    };

    private static Boundary boundary;
    private static boolean boundaryResolved;

    private BetterNetherPiglinArmorAdapter() {
    }

    public static void augment(
            LivingEntity entity,
            CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) {
            return;
        }

        Boundary api = boundary();
        if (api == null || !api.isEnabled()) {
            return;
        }

        for (EquipmentSlot slot : EquipmentSlotGroup.ARMOR) {
            ItemStack worn = entity.getItemBySlot(slot);
            if (!worn.is(ModContent.MIXED_BOOTS_KEY)
                    || !(worn.getItem() instanceof MixedBootsItem)) {
                continue;
            }
            if (MixedBootsSources.anySource(worn, source -> api.contains(source.getItem()))) {
                callback.setReturnValue(true);
                return;
            }
        }
    }

    private static synchronized Boundary boundary() {
        if (boundaryResolved) {
            return boundary;
        }
        boundaryResolved = true;
        try {
            ClassLoader loader = BetterNetherPiglinArmorAdapter.class.getClassLoader();
            Class<?> configs = Class.forName(
                    "org.betterx.betternether.config.Configs", true, loader);
            Object gameRules = configs.getField("GAME_RULES").get(null);
            Object enabled = gameRules.getClass()
                    .getField("piglinIgnoreNetherArmor")
                    .get(gameRules);
            Method getValue = enabled.getClass().getMethod("get");

            Class<?> equipmentItems = Class.forName(
                    "org.betterx.betternether.registry.item.NetherEquipmentItems", true, loader);
            Object[] sets = new Object[SET_FIELDS.length];
            for (int index = 0; index < SET_FIELDS.length; index++) {
                Field field = equipmentItems.getField(SET_FIELDS[index]);
                sets[index] = field.get(null);
            }
            Method getArmorPieces = sets[0].getClass().getMethod("getArmorPieces");
            boundary = new Boundary(enabled, getValue, sets, getArmorPieces);
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException
                 | IllegalAccessException | LinkageError exception) {
            Constants.LOG.warn(
                    "Could not resolve Better Nether runtime API; compatibility disabled", exception);
        }
        return boundary;
    }

    private record Boundary(
            Object enabledValue,
            Method getValue,
            Object[] sets,
            Method getArmorPieces) {
        boolean isEnabled() {
            try {
                return Boolean.TRUE.equals(getValue.invoke(enabledValue));
            } catch (IllegalAccessException | InvocationTargetException exception) {
                Constants.LOG.warn(
                        "Could not read Better Nether piglin configuration; compatibility disabled",
                        exception);
                return false;
            }
        }

        boolean contains(Item item) {
            try {
                for (Object set : sets) {
                    Item[] armorPieces = (Item[]) getArmorPieces.invoke(set);
                    for (Item armorPiece : armorPieces) {
                        if (armorPiece == item) {
                            return true;
                        }
                    }
                }
                return false;
            } catch (IllegalAccessException | InvocationTargetException | ClassCastException exception) {
                Constants.LOG.warn(
                        "Could not query Better Nether armor sets; compatibility disabled",
                        exception);
                return false;
            }
        }
    }
}
