package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Behavior-preserving native faults, not permission supplied by fixture instrumentation. */
public final class SilentGearDefensePeer implements Opcodes {
    public static int queryContribution;
    public static int optionalLevel(int original) { return Math.max(original, queryContribution); }
    private SilentGearDefensePeer() {}
    public static void transform(ClassNode owner, String variant) {
        boolean negate = owner.name.endsWith("NegateDamageTraitEffect");
        for (var m : owner.methods) {
            if (m.name.equals("onLivingFall") || m.name.equals("onEntityIncomingDamage") || m.name.equals("onPlayerHurt")) { m.instructions.insert(new InsnNode(NOP)); }
        }
        if (variant.equals("defense-compatible")) {
            for (var m : owner.methods) {
                if (!m.name.equals("onLivingFall")) { continue; }
                for (var n : m.instructions.toArray()) {
                    if (n instanceof MethodInsnNode call && call.name.equals("getHighestLevelArmorOrCurio")
                            && n.getPrevious() instanceof org.objectweb.asm.tree.FieldInsnNode resource && resource.name.equals("BOUNCE")) {
                        m.instructions.insert(n, new MethodInsnNode(INVOKESTATIC, "games/alejandrocoria/mismatchedboots/runtimetest/neoforge/SilentGearDefensePeer", "optionalLevel", "(I)I", false));
                    }
                }
            }
            // Renamed native lambda targets and unrelated methods are not safety regressions.
            for (var m : owner.methods) {
                if (!m.name.equals("bounceEntity") && !m.name.equals("onLivingIncomingDamage")) { continue; }
                for (var n : m.instructions) {
                    if (n instanceof InvokeDynamicInsnNode dynamic && dynamic.bsmArgs.length == 3 && dynamic.bsmArgs[1] instanceof Handle h && h.getOwner().equals(owner.name)) {
                        var lambda = owner.methods.stream().filter(value -> value.name.equals(h.getName()) && value.desc.equals(h.getDesc())).findFirst().orElseThrow();
                        lambda.name = "sgtest$renamed$" + h.getName(); dynamic.bsmArgs[1] = new Handle(h.getTag(), h.getOwner(), lambda.name, h.getDesc(), h.isInterface());
                    }
                }
            }
            var unrelated = new MethodNode(ACC_PRIVATE | ACC_STATIC, "sgtest$unrelated", "()V", null, null); unrelated.instructions.add(new InsnNode(RETURN)); owner.methods.add(unrelated); return;
        }
        String target = variant.equals("defense-fall") ? "onLivingFall" : variant.equals("defense-retaliation") ? "onPlayerHurt" : "onEntityIncomingDamage";
        MethodInsnNode selected = null;
        for (var m : owner.methods) {
            if (variant.equals("defense-incoming") && m.name.equals("onLivingIncomingDamage")) {
                for (var n : m.instructions) { if (n instanceof TypeInsnNode type && type.desc.equals("net/silentchaos512/gear/api/item/GearArmor")) { type.desc = "net/silentchaos512/gear/item/gear/GearArmorItem"; } }
            }
            if (!m.name.equals(target)) { continue; }
            for (var n : m.instructions) {
                if (n instanceof MethodInsnNode call && (negate && variant.equals("defense-negate") && call.name.equals("clamp")
                        || !negate && variant.equals("defense-fall") && call.name.equals("attemptDamage")
                        || !negate && variant.equals("defense-retaliation") && call.name.equals("getHighestLevelArmor"))) { selected = call; break; }
            }
        }
        if (selected == null) { return; }
        var alias = new MethodNode(ACC_PRIVATE | ACC_STATIC, "sgtest$unprovedAlias", selected.desc, null, null);
        int slot = 0; for (var type : org.objectweb.asm.Type.getArgumentTypes(selected.desc)) { alias.instructions.add(new VarInsnNode(type.getOpcode(ILOAD), slot)); slot += type.getSize(); }
        alias.instructions.add(new MethodInsnNode(selected.getOpcode(), selected.owner, selected.name, selected.desc, selected.itf));
        alias.instructions.add(new InsnNode(org.objectweb.asm.Type.getReturnType(selected.desc).getOpcode(IRETURN))); alias.maxLocals = slot; alias.maxStack = slot;
        owner.methods.add(alias); selected.setOpcode(INVOKESTATIC); selected.owner = owner.name; selected.name = alias.name; selected.itf = false;
    }
}
