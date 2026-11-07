package games.alejandrocoria.mismatchedleggings.compatibility.rootsclassic;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/** One audited native receiver repairs the live root; stored sources are never ticked. */
public final class RootsTickCompatibility {
    private static final Identifier SYLVAN = Identifier.parse("rootsclassic:sylvan_tunic");
    private static final Identifier WILDWOOD = Identifier.parse("rootsclassic:wildwood_leggings");
    private static final Set<Identifier> disabled = new HashSet<>();
    private static Boolean repairReady;

    private RootsTickCompatibility() {
    }

    public static void tick(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot slot) {
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (data == null) {
            return;
        }
        var left = BuiltInRegistries.ITEM.getKey(data.left().source().getItem());
        var right = BuiltInRegistries.ITEM.getKey(data.right().source().getItem());
        Identifier id = SYLVAN.equals(left) || SYLVAN.equals(right) ? SYLVAN
                : WILDWOOD.equals(left) || WILDWOOD.equals(right) ? WILDWOOD : null;
        if (id == null || disabled.contains(id)) {
            return;
        }

        Item receiver = BuiltInRegistries.ITEM.getValue(id);
        String expected = id.equals(SYLVAN) ? RootsTickShape.SYLVAN : RootsTickShape.WILDWOOD;
        boolean ready;
        try {
            ready = receiver != null && receiver.getClass().getName().equals(expected.replace('/', '.'))
                    && receiver instanceof RootsTickReadiness proof && proof.mismatchedleggings$rootsTickReady()
                    && repairReady(receiver);
        } catch (LinkageError failure) {
            disable(id, "pre-invocation linkage failure: " + failure.getClass().getSimpleName());
            return;
        }
        if (!ready) {
            disable(id, "native receiver or repair stack-consumer proof unavailable");
            return;
        }
        // Native code can already mutate damage/Terra: never catch-and-retry another source.
        // Keep the original slot gate, RNG order and held-item behavior inside the native method.
        receiver.inventoryTick(root, level, entity, slot);
    }

    private static boolean repairReady(Item receiver) {
        if (repairReady == null) {
            try {
                Class<?> util = Class.forName(RootsTickShape.UTIL.replace('/', '.'), false, receiver.getClass().getClassLoader());
                var getter = util.getMethod(RootsTickShape.REPAIR_PROOF);
                repairReady = getter.getReturnType() == boolean.class && Modifier.isStatic(getter.getModifiers())
                        && Boolean.TRUE.equals(getter.invoke(null));
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                repairReady = false;
            }
        }
        return repairReady;
    }

    private static void disable(Identifier id, String reason) {
        if (disabled.add(id)) {
            LeggingsContent.LOG.warn("Roots Classic mixed leggings {} tick disabled: {}", id, reason);
        }
    }
}
