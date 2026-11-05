package games.alejandrocoria.mismatchedleggings.compatibility.allthemodium;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Modifier;

/** One audited native wearer tick, without ticking or rewriting stored sources. */
public final class AllTheModiumLeggings {
    private static final ResourceKey<Item> ALLTHEMODIUM = key("allthemodium_leggings");
    private static final ResourceKey<Item> VIBRANIUM = key("vibranium_leggings");
    private static final ResourceKey<Item> UNOBTAINIUM = key("unobtainium_leggings");
    private static final String RECEIVER = "net.allthemods.allthemodium.common.items.ModiumLeggingsItem";
    private static final ClassValue<Boolean> TICK_READY = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            boolean ready = type.getName().equals(RECEIVER) && declaresTick(type);
            if (!ready) {
                LeggingsContent.LOG.warn("AllTheModium mixed leggings tick disabled: incompatible receiver {}", type.getName());
            }
            return ready;
        }
    };

    private AllTheModiumLeggings() {
    }

    public static boolean eligible(ItemStack root) {
        return receiver(root) != null;
    }

    public static void tick(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (slot != EquipmentSlot.LEGS || !(entity instanceof ServerPlayer player)
                || player.getItemBySlot(EquipmentSlot.LEGS) != root) {
            return;
        }
        Item receiver = receiver(root);
        if (receiver == null || !TICK_READY.get(receiver.getClass())) {
            return;
        }

        // In audited 4.1.1 the native method never reads the stack: only wearer effects/cadence.
        // Call once on the live root, never source snapshots or a retry after possible side effects.
        receiver.inventoryTick(root, level, entity, slot);
    }

    private static Item receiver(ItemStack root) {
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (data == null) {
            return null;
        }
        var left = data.left().source();
        if (nativeLeggings(left)) {
            return left.getItem();
        }
        var right = data.right().source();
        return nativeLeggings(right) ? right.getItem() : null;
    }

    private static boolean nativeLeggings(ItemStack source) {
        return source.is(ALLTHEMODIUM) || source.is(VIBRANIUM) || source.is(UNOBTAINIUM);
    }

    private static boolean declaresTick(Class<?> type) {
        try {
            var method = type.getDeclaredMethod("inventoryTick", ItemStack.class, ServerLevel.class, Entity.class, EquipmentSlot.class);
            int modifiers = method.getModifiers();
            return method.getReturnType() == void.class && Modifier.isPublic(modifiers)
                    && !Modifier.isStatic(modifiers) && !Modifier.isAbstract(modifiers);
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return false;
        }
    }

    private static ResourceKey<Item> key(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("allthemodium", name));
    }
}
