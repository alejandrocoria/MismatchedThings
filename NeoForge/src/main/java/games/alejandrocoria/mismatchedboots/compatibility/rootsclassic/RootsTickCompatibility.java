package games.alejandrocoria.mismatchedboots.compatibility.rootsclassic;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** One audited native receiver repairs the live root; stored sources are never ticked. */
public final class RootsTickCompatibility {
    private static final Identifier SYLVAN = Identifier.parse("rootsclassic:sylvan_boots");
    private static final Identifier WILDWOOD = Identifier.parse("rootsclassic:wildwood_boots");
    private static final java.util.Set<Identifier> disabled = new java.util.HashSet<>();
    private static Boolean repairReady;

    private RootsTickCompatibility() {}

    public static void tick(ItemStack root, ServerLevel level, Entity entity, EquipmentSlot slot) {
        var equipped = root.get(DataComponents.EQUIPPABLE);
        if (equipped == null || equipped.slot() != EquipmentSlot.FEET) { return; }
        Identifier id = MixedBootsSources.anySource(root, source -> source.is(BuiltInRegistries.ITEM.getValue(SYLVAN)))
                ? SYLVAN : MixedBootsSources.anySource(root, source -> source.is(BuiltInRegistries.ITEM.getValue(WILDWOOD)))
                ? WILDWOOD : null;
        if (id == null || disabled.contains(id)) { return; }
        Item receiver = BuiltInRegistries.ITEM.getValue(id);
        String expected = id.equals(SYLVAN) ? RootsTickShape.SYLVAN : RootsTickShape.WILDWOOD;
        boolean ready;
        try {
            ready = receiver != null && receiver.getClass().getName().equals(expected.replace('/', '.'))
                    && receiver instanceof RootsTickReadiness proof && proof.mismatchedboots$rootsTickReady()
                    && repairReady(receiver);
        } catch (LinkageError failure) {
            disable(id, "pre-invocation linkage failure: " + failure.getClass().getSimpleName());
            return;
        }
        if (!ready) { disable(id, "native receiver or repair stack-consumer proof unavailable"); return; }
        // The native method can already mutate damage/Terra: never catch-and-retry a second source here.
        receiver.inventoryTick(root, level, entity, slot);
    }

    private static boolean repairReady(Item receiver) {
        if (repairReady == null) {
            try {
                Class<?> util = Class.forName(RootsTickShape.UTIL.replace('/', '.'), false, receiver.getClass().getClassLoader());
                var getter = util.getMethod(RootsTickShape.REPAIR_PROOF);
                repairReady = getter.getReturnType() == boolean.class && java.lang.reflect.Modifier.isStatic(getter.getModifiers())
                        && Boolean.TRUE.equals(getter.invoke(null));
            } catch (ReflectiveOperationException | LinkageError unavailable) { repairReady = false; }
        }
        return repairReady;
    }

    private static void disable(Identifier id, String reason) {
        if (disabled.add(id)) { Constants.LOG.warn("Roots Classic mixed {} tick disabled: {}", id, reason); }
    }
}
