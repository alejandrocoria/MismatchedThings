package games.alejandrocoria.mismatchedboots.compatibility.aether;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Final local query provenance, not a fingerprint of native corrosion/damage bodies. */
final class AetherAlkahestShape implements Opcodes {
    static final String FLUID = "com/aetherteam/aetherii/block/fluid/AlkahestFluid";
    static final String EVENTS = "com/aetherteam/aetherii/AetherIIEventListeners";
    static final String ADAPTER = "games/alejandrocoria/mismatchedboots/compatibility/aether/AetherAlkahestProtection";
    static final String STACK = "net/minecraft/world/item/ItemStack";
    static final String ENTITY = "net/minecraft/world/entity/LivingEntity";
    static final String ITEM_ENTITY = "net/minecraft/world/entity/item/ItemEntity";
    static final String EVENT = "net/neoforged/neoforge/event/entity/living/ArmorHurtEvent";
    static final String COMPONENTS = "com/aetherteam/aetherii/item/components/AetherIIDataComponents";
    static final String CONTACT = "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V";
    static final String ARMOR = "(L" + EVENT + ";)V";
    static final String HAS = "(Ljava/util/function/Supplier;)Z";

    private AetherAlkahestShape() {}

    static boolean apply(ClassNode owner) throws AnalyzerException {
        boolean armor = owner.name.equals(EVENTS);
        if (!armor && !owner.name.equals(FLUID)) { return false; }
        var method = method(owner, armor ? "onArmorDamaged" : "entityInside", armor ? ARMOR : CONTACT);
        if (method == null || ((method.access & ACC_STATIC) != 0) != armor) { return false; }
        for (var n : method.instructions) { if (n instanceof MethodInsnNode c && c.owner.equals(ADAPTER)) { return false; } }
        var flow = new Flow(owner, method); var sites = new ArrayList<Site>(); var kinds = new HashSet<String>();
        for (var n : method.instructions.toArray()) {
            if (!(n instanceof JumpInsnNode branch) || branch.getOpcode() != (armor ? IFEQ : IFNE)) { continue; }
            var query = flow.query(flow.argument(branch, 0, 1));
            if (query == null || !field(flow.base(flow.argument(query, 1, 2)), COMPONENTS, "REINFORCEMENT_TIER",
                    "Lnet/neoforged/neoforge/registries/DeferredHolder;")) { continue; }
            String kind = armor ? armorSite(flow, branch, query) : fluidSite(flow, branch, query);
            if (kind == null) { continue; }
            if (!kinds.add(kind)) { return false; }
            sites.add(new Site(branch, query));
        }
        if (!kinds.equals(armor ? Set.of("armor") : Set.of("drop", "main", "off"))) { return false; }
        // Prove the whole domain before editing any query; a partial fluid match grants nothing.
        for (var site : sites) {
            int stack = method.maxLocals++, supplier = method.maxLocals++;
            var save = new InsnList(); save.add(new InsnNode(DUP2));
            save.add(new VarInsnNode(ASTORE, supplier)); save.add(new VarInsnNode(ASTORE, stack));
            method.instructions.insertBefore(site.query, save);
            var extend = new InsnList(); extend.add(new VarInsnNode(ALOAD, stack)); extend.add(new VarInsnNode(ALOAD, supplier));
            extend.add(new InsnNode(armor ? ICONST_1 : ICONST_0));
            extend.add(new MethodInsnNode(INVOKESTATIC, ADAPTER, "matches", "(ZL" + STACK + ";Ljava/util/function/Supplier;Z)Z", false));
            method.instructions.insertBefore(site.branch, extend);
        }
        // Aether deliberately still owns getLastDamageSource timing, including first-hit/carryover defects.
        // See local-docs/AETHER-II-COMPATIBILITY-EXPLORATION.md section 14.3; do not replace its damage source here.
        method.maxStack += 4; return true;
    }

    private static String fluidSite(Flow flow, JumpInsnNode branch, MethodInsnNode query) throws AnalyzerException {
        var stack = flow.argument(query, 0, 2); var producer = flow.base(stack); String kind;
        if (call(producer, STACK, "copy", "()L" + STACK + ";")) {
            var get = flow.base(flow.argument(producer, 0, 1));
            if (!call(get, ITEM_ENTITY, "getItem", "()L" + STACK + ";") || !flow.parameter(flow.argument(get, 0, 1), 4)) { return null; }
            kind = "drop";
        } else {
            kind = call(producer, ENTITY, "getMainHandItem", "()L" + STACK + ";") ? "main"
                    : call(producer, ENTITY, "getOffhandItem", "()L" + STACK + ";") ? "off" : null;
            if (kind == null || !flow.parameter(flow.argument(producer, 0, 1), 4)) { return null; }
        }
        // Native tag OR tier must shield the same stack through the same boolean exit.
        for (var n : flow.method.instructions) {
            if (!(n instanceof JumpInsnNode tagBranch) || tagBranch.getOpcode() != IFNE || tagBranch.label != branch.label) { continue; }
            var tag = flow.booleanQuery(flow.argument(tagBranch, 0, 1), "is", "(Lnet/minecraft/tags/TagKey;)Z");
            if (tag != null && flow.same(stack, flow.argument(tag, 0, 2))
                    && field(flow.base(flow.argument(tag, 1, 2)), "com/aetherteam/aetherii/AetherIITags$Items", "ALKAHEST_RESISTANT_ITEM", "Lnet/minecraft/tags/TagKey;")) { return kind; }
        }
        return null;
    }

    private static String armorSite(Flow flow, JumpInsnNode branch, MethodInsnNode query) throws AnalyzerException {
        var stack = flow.base(flow.argument(query, 0, 2));
        if (!fieldValue(stack, EVENT + "$ArmorEntry", "armorItemStack", "L" + STACK + ";") || stack.getOpcode() != GETFIELD) { return null; }
        var value = flow.base(flow.argument(stack, 0, 1));
        if (!call(value, "java/util/Map$Entry", "getValue", "()Ljava/lang/Object;")) { return null; }
        var entry = flow.argument(value, 0, 1); var next = flow.base(entry);
        if (!call(next, "java/util/Iterator", "next", "()Ljava/lang/Object;")) { return null; }
        var iterator = flow.base(flow.argument(next, 0, 1));
        if (!call(iterator, "java/util/Set", "iterator", "()Ljava/util/Iterator;")) { return null; }
        var entries = flow.base(flow.argument(iterator, 0, 1));
        if (!call(entries, "java/util/Map", "entrySet", "()Ljava/util/Set;")) { return null; }
        var map = flow.base(flow.argument(entries, 0, 1));
        if (!call(map, EVENT, "getArmorMap", "()Ljava/util/Map;") || !flow.parameter(flow.argument(map, 0, 1), 0)) { return null; }
        for (var n = branch.getNext(); n != null && n != branch.label; n = n.getNext()) {
            if (!call(n, EVENT, "setNewDamage", "(Lnet/minecraft/world/entity/EquipmentSlot;F)V")) { continue; }
            var key = flow.base(flow.argument(n, 1, 3)); var amount = flow.base(flow.argument(n, 2, 3));
            if (flow.parameter(flow.argument(n, 0, 3), 0) && call(key, "java/util/Map$Entry", "getKey", "()Ljava/lang/Object;")
                    && flow.same(entry, flow.argument(key, 0, 1)) && amount != null && amount.getOpcode() == FCONST_0) { return "armor"; }
        }
        return null;
    }

    private record Site(JumpInsnNode branch, MethodInsnNode query) {}

    private static final class Flow {
        private final ClassNode owner;
        private final MethodNode method;
        private final Frame<SourceValue>[] frames;
        Flow(ClassNode owner, MethodNode method) throws AnalyzerException {
            this.owner = owner; this.method = method;
            frames = new Analyzer<>(new SourceInterpreter(ASM9) {
                @Override public SourceValue copyOperation(AbstractInsnNode n, SourceValue v) {
                    return n.getOpcode() >= DUP && n.getOpcode() <= SWAP ? v : super.copyOperation(n, v);
                }
            }).analyzeAndComputeMaxs(owner.name, method);
        }
        Frame<SourceValue> at(AbstractInsnNode n) { return frames[method.instructions.indexOf(n)]; }
        SourceValue argument(AbstractInsnNode n, int index, int count) {
            var f = at(n); return f == null || f.getStackSize() < count ? null : f.getStack(f.getStackSize() - count + index);
        }
        AbstractInsnNode origin(SourceValue v) { return origin(v, new HashSet<>()); }
        private AbstractInsnNode origin(SourceValue v, Set<AbstractInsnNode> seen) {
            if (v == null || v.insns.size() != 1) { return null; }
            var n = v.insns.iterator().next(); if (!seen.add(n)) { return null; }
            if (n instanceof VarInsnNode load && n.getOpcode() >= ILOAD && n.getOpcode() <= ALOAD) {
                var old = at(n).getLocal(load.var); return old.insns.isEmpty() ? n : origin(old, seen);
            }
            if (n instanceof VarInsnNode && n.getOpcode() >= ISTORE && n.getOpcode() <= ASTORE || n.getOpcode() == CHECKCAST) { return origin(argument(n, 0, 1), seen); }
            return n;
        }
        AbstractInsnNode base(SourceValue v) throws AnalyzerException {
            var n = origin(v); var seen = new HashSet<AbstractInsnNode>();
            while (n instanceof MethodInsnNode peer && peer.owner.equals(owner.name) && seen.add(n)) {
                var args = Type.getArgumentTypes(peer.desc);
                if (args.length == 0 || !args[0].equals(Type.getReturnType(peer.desc))) { break; }
                var body = method(owner, peer.name, peer.desc);
                if (body == null || body == method) { break; }
                var proof = new Flow(owner, body); boolean returned = false, identity = true;
                for (var ret : body.instructions) {
                    if (ret.getOpcode() == ARETURN) { returned = true; identity &= proof.parameter(proof.argument(ret, 0, 1), (body.access & ACC_STATIC) == 0 ? 1 : 0); }
                }
                if (!returned || !identity) { break; }
                n = origin(argument(peer, peer.getOpcode() == INVOKESTATIC ? 0 : 1, args.length + (peer.getOpcode() == INVOKESTATIC ? 0 : 1)));
            }
            return n;
        }
        boolean parameter(SourceValue v, int local) {
            var n = origin(v); return n instanceof VarInsnNode load && load.var == local && at(n).getLocal(local).insns.isEmpty();
        }
        boolean same(SourceValue a, SourceValue b) throws AnalyzerException { var n = base(a); return n != null && n == base(b); }
        MethodInsnNode query(SourceValue v) { return booleanQuery(v, "has", HAS); }
        MethodInsnNode booleanQuery(SourceValue v, String name, String descriptor) {
            var n = origin(v); var seen = new HashSet<AbstractInsnNode>();
            while (n instanceof MethodInsnNode peer && seen.add(n)) {
                if (call(peer, STACK, name, descriptor)) { return peer; }
                var args = Type.getArgumentTypes(peer.desc);
                if (args.length == 0 || !args[0].equals(Type.BOOLEAN_TYPE) || !Type.getReturnType(peer.desc).equals(Type.BOOLEAN_TYPE)) { return null; }
                n = origin(argument(peer, peer.getOpcode() == INVOKESTATIC ? 0 : 1, args.length + (peer.getOpcode() == INVOKESTATIC ? 0 : 1)));
            }
            return null;
        }
    }

    private static MethodNode method(ClassNode owner, String name, String desc) {
        var methods = owner.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).toList();
        return methods.size() == 1 ? methods.getFirst() : null;
    }
    private static boolean call(AbstractInsnNode n, String owner, String name, String desc) {
        return n instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(name) && c.desc.equals(desc);
    }
    private static boolean field(AbstractInsnNode n, String owner, String name, String desc) { return n != null && n.getOpcode() == GETSTATIC && fieldValue(n, owner, name, desc); }
    private static boolean fieldValue(AbstractInsnNode n, String owner, String name, String desc) {
        return n instanceof FieldInsnNode f && f.owner.equals(owner) && f.name.equals(name) && f.desc.equals(desc);
    }
}
