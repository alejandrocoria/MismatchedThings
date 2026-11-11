package games.alejandrocoria.mismatchedleggings.test.silentgear;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;

import static org.objectweb.asm.Opcodes.INSTANCEOF;

/** Temporary selector matching Boots' native armor-loop seam, not a production admission proof. */
public final class SilentGearDefenseSpikeSite extends InjectionPoint {
    private final String site;

    public SilentGearDefenseSpikeSite(InjectionPointData data) {
        super(data);
        site = data.get("site", "");
    }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        if (site.equals("fall-level") || site.equals("retaliation-level")) {
            String event = site.equals("fall-level") ? "LivingFallEvent" : "LivingDamageEvent$Post";
            if (!descriptor.equals("(Lnet/neoforged/neoforge/event/entity/living/" + event + ";)V")) { return false; }
            AbstractInsnNode found = null;
            AbstractInsnNode previous = null;
            for (var insn : instructions) {
                if (insn.getOpcode() < 0) { continue; }
                if (previous instanceof FieldInsnNode id && id.name.equals("BOUNCE") && id.owner.equals("net/silentchaos512/gear/util/Const$Traits")
                        && insn instanceof MethodInsnNode call && call.owner.equals("net/silentchaos512/gear/util/TraitHelper")
                        && call.name.equals(site.equals("fall-level") ? "getHighestLevelArmorOrCurio" : "getHighestLevelArmor")) {
                    if (found != null) { return false; }
                    found = insn;
                }
                previous = insn;
            }
            if (found == null) { return false; }
            nodes.add(found);
            return true;
        }
        if (!descriptor.equals("(Lnet/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent;)V")) { return false; }
        AbstractInsnNode predicate = null;
        for (var insn : instructions) {
            if (insn instanceof TypeInsnNode type && type.getOpcode() == INSTANCEOF
                    && type.desc.equals("net/silentchaos512/gear/api/item/GearArmor")) {
                if (predicate != null) { return false; }
                predicate = insn;
            }
        }
        if (predicate == null) { return false; }
        if (site.equals("armor")) { nodes.add(predicate); return true; }
        AbstractInsnNode selected = null;
        for (var insn = predicate.getNext(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode call && call.owner.equals("net/silentchaos512/gear/util/TraitHelper")
                    && call.name.equals("activateTraits") && call.desc.equals("(Lnet/minecraft/world/item/ItemStack;Ljava/lang/Object;Lnet/silentchaos512/gear/api/traits/TraitFunction;)Ljava/lang/Object;")) {
                if (selected != null) { return false; }
                selected = insn;
            }
        }
        if (!site.equals("incoming") || selected == null) { return false; }
        nodes.add(selected);
        return true;
    }
}
