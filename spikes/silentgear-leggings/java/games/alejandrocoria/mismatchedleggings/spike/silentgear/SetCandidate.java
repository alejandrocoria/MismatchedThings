package games.alejandrocoria.mismatchedleggings.spike.silentgear;

import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearWearerApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.silentchaos512.gear.api.traits.TraitActionContext;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.gear.trait.effect.WielderEffectTraitEffect;

import java.util.List;

/** Isolated four-family Wielder proof; no numeric/state engine or production dispatch. */
public final class SetCandidate {
    public static int calls;

    private SetCandidate() {}

    private static SilentGearWearerApi api;

    static SilentGearWearerApi api() throws ReflectiveOperationException {
        if (api == null) { api = new SilentGearWearerApi(); }
        return api;
    }

    static List<TraitInstance> traits(ItemStack root) {
        try { return api().traits(root).stream().map(t -> (TraitInstance) t.nativeInstance()).toList(); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    public static boolean recognizes(Object context, EquipmentSlot slot, ItemStack root) {
        if (slot != EquipmentSlot.LEGS) { return false; }
        try { return api().recognizes(root, context); }
        catch (Throwable failure) { throw new IllegalStateException(failure); }
    }

    public static boolean unrecognized(Object context, EquipmentSlot slot, ItemStack root) {
        return recognizes(context, slot, root); // Fault profile changes provenance, not runtime semantics.
    }

    static ItemStack carrier(ItemStack root) {
        try { return api().carrier(root); }
        catch (Throwable failure) { throw new IllegalStateException(failure); }
    }

    static void wearer(ItemStack root, ServerPlayer player) {
        var carrier = carrier(root);
        for (var trait : traits(root)) {
            if (!List.of("adamant", "aquatic", "flame_ward", "stellar").contains(trait.getTraitId().getPath())) { continue; }
            List<?> admitted;
            try { admitted = api().admittedEffects(trait); }
            catch (Throwable failure) { throw new IllegalStateException(failure); }
            for (var effect : admitted) {
                if (effect instanceof WielderEffectTraitEffect wielder && ((SetAccess) wielder).spike$ready()) {
                    var before = carrier.copy();
                    calls++;
                    wielder.inventoryTick(new TraitActionContext(player, trait, carrier), player.level(), player, true);
                    SpikeChecks.require(ItemStack.matches(before, carrier), "Wielder mutated its disposable carrier");
                }
            }
        }
    }

}
