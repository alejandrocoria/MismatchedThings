package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.silentchaos512.gear.api.traits.TraitActionContext;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.gear.trait.effect.CancelEffectsTraitEffect;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Feasibility only: native query extension plus two actor-only leaves, never full native ticking. */
public final class SilentGearRemainingCandidate {
    private static final Set<String> DISABLED = new HashSet<>();
    private static Object sources;
    public static boolean refuseVoid;
    public static boolean failPoison;
    public static int calls;

    private SilentGearRemainingCandidate() {}

    private static List<SourceTrait> traits(ItemStack root) throws ReflectiveOperationException {
        // Use the actual product's reader, without creating a JPMS split package in the test mod.
        var type = Class.forName("games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearEffectSources");
        if (sources == null) {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            sources = constructor.newInstance();
        }
        var read = type.getDeclaredMethod("traits", ItemStack.class);
        read.setAccessible(true);
        var result = new java.util.ArrayList<SourceTrait>();
        for (var value : (List<?>) read.invoke(sources, root)) {
            var id = value.getClass().getDeclaredMethod("id");
            var level = value.getClass().getDeclaredMethod("level");
            var instance = value.getClass().getDeclaredMethod("nativeInstance");
            id.setAccessible(true);
            level.setAccessible(true);
            instance.setAccessible(true);
            result.add(new SourceTrait((net.minecraft.resources.Identifier) id.invoke(value), (int) level.invoke(value), (TraitInstance) instance.invoke(value)));
        }
        return result;
    }

    public static boolean voidWard(boolean original, PlayerTickEvent.Pre event, boolean proof) {
        if (original || !proof || refuseVoid || event.getEntity().level().isClientSide()) { return original; }
        var root = event.getEntity().getItemBySlot(EquipmentSlot.LEGS);
        if (MixedLeggingsStructure.validData(root).isEmpty()) { return false; }
        try { return traits(root).stream().anyMatch(t -> t.id().toString().equals("silentgear:void_ward")); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    public static void tick(ItemStack root, ServerLevel level, Entity actor, EquipmentSlot slot) {
        if (!(actor instanceof Player player) || player.level() != level
                || slot != EquipmentSlot.LEGS && slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND
                || MixedLeggingsStructure.validData(root).isEmpty()) { return; }
        try {
            for (var resolved : traits(root)) {
                String id = resolved.id().toString();
                String target = switch (id) {
                    case "silentgear:cure_poison" -> "poison";
                    case "silentgear:cure_wither" -> "wither";
                    default -> null;
                };
                if (target == null || DISABLED.contains(id)) { continue; }
                try {
                    var instance = resolved.nativeInstance();
                    var definition = instance.getTrait();
                    var effects = definition.getEffects();
                    if (resolved.level() != 1 || definition.getMaxLevel() != 1 || !definition.getConditions().isEmpty()
                            || !instance.getConditions().isEmpty() || effects.size() != 1 || effects.getFirst().getClass() != CancelEffectsTraitEffect.class) {
                        throw new IllegalStateException("Unsupported synthetic cure shape");
                    }
                    var field = CancelEffectsTraitEffect.class.getDeclaredField("effects");
                    field.setAccessible(true);
                    var holders = (List<?>) field.get(effects.getFirst());
                    if (holders.size() != 1 || !(holders.getFirst() instanceof Holder<?> holder)
                            || !holder.is(net.minecraft.resources.Identifier.withDefaultNamespace(target))) { throw new IllegalStateException("Unsupported target"); }
                    var copy = root.copy();
                    var before = copy.copy();
                    calls++;
                    ((CancelEffectsTraitEffect) effects.getFirst()).inventoryTick(new TraitActionContext(player, instance, copy), level, actor, true);
                    if (!ItemStack.matches(copy, before)) { throw new AssertionError("Cure wrote its disposable root"); }
                    if (target.equals("poison") && failPoison) { throw new IllegalStateException("Deliberate failure after native actor mutation"); }
                } catch (Exception failure) {
                    DISABLED.add(id); // Actor may already have changed. Never replay the leaf.
                }
            }
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    public static boolean poisonDisabled() { return DISABLED.contains("silentgear:cure_poison"); }
    private record SourceTrait(net.minecraft.resources.Identifier id, int level, TraitInstance nativeInstance) {}
}
