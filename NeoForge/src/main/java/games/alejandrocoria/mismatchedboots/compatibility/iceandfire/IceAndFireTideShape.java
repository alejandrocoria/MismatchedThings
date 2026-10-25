package games.alejandrocoria.mismatchedboots.compatibility.iceandfire;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A small grammar for this audited consumer, not an arbitrary wrapper/data-flow normalizer. */
final class IceAndFireTideShape implements Opcodes {
    static final String ARMOR = "com/iafenvoy/iceandfire/item/armor/SeaSerpentArmorItem";
    static final String ITEM = "net/minecraft/world/item/Item";
    static final String STACK = ITEM + "Stack";
    static final String ENTITY = "net/minecraft/world/entity/Entity";
    static final String PLAYER = "net/minecraft/world/entity/player/Player";
    static final String SLOT = "net/minecraft/world/entity/EquipmentSlot";
    static final String TYPE = "net/minecraft/world/item/equipment/ArmorType";
    static final String EFFECT = "net/minecraft/world/effect/MobEffectInstance";
    static final String EFFECTS = "net/minecraft/world/effect/MobEffects";
    static final String TICK = "(L" + STACK + ";Lnet/minecraft/server/level/ServerLevel;L" + ENTITY + ";L" + SLOT + ";)V";
    private static final String ADAPTER = "games/alejandrocoria/mismatchedboots/compatibility/iceandfire/IceAndFireTideCompatibility";

    static String entryFailure(ClassNode owner) {
        try {
            require(ITEM.equals(owner.superName), "native hierarchy");
            require(owner.fields.stream().anyMatch(f -> f.name.equals("type") && f.desc.equals("L" + TYPE + ";")
                    && f.access == (ACC_PRIVATE | ACC_FINAL)), "native slot field");
            MethodNode entry = method(owner, "inventoryTick", TICK);
            require(entry != null && entry.access == ACC_PUBLIC && noHandlers(entry), "native entry API");
            Cursor c = new Cursor(entry);
            // The carrier (argument 1) is consumed only here. The rest of the grammar cannot load/alias it.
            for (int argument = 0; argument <= 4; argument++) {
                c.var(ALOAD, argument);
            }
            c.call(INVOKESPECIAL, ITEM, "inventoryTick", TICK);
            c.var(ALOAD, 3);
            c.type(INSTANCEOF, PLAYER);
            int exit = c.jump(IFEQ);
            c.var(ALOAD, 3);
            c.type(CHECKCAST, PLAYER);
            int player = c.local(ASTORE);
            Set<Integer> locals = new HashSet<>();
            require(player >= 5 && locals.add(player), "player local");
            c.var(ALOAD, 4);
            c.var(ALOAD, 0);
            c.field(GETFIELD, ARMOR, "type", "L" + TYPE + ";");
            c.call(INVOKEVIRTUAL, TYPE, "getSlot", "()L" + SLOT + ";");
            require(c.jump(IF_ACMPNE) == exit, "receiver slot gate");
            int[] terms = new int[4];
            String[] slots = {"HEAD", "CHEST", "LEGS", "FEET"};
            boolean installedOwnHook = false;
            for (int index = 0; index < slots.length; index++) {
                c.var(ALOAD, player);
                c.field(GETSTATIC, SLOT, slots[index], "L" + SLOT + ";");
                c.call(INVOKEVIRTUAL, PLAYER, "getItemBySlot", "(L" + SLOT + ";)L" + STACK + ";");
                c.call(INVOKEVIRTUAL, STACK, "getItem", "()L" + ITEM + ";");
                c.type(INSTANCEOF, ARMOR);
                int ownHooks = 0;
                int chain = 0;
                while (c.opcode() == ALOAD) {
                    require(++chain <= 8, "predicate chain bound");
                    c.var(ALOAD, 0);
                    c.op(SWAP);
                    boolean entity = c.opcode() == ALOAD;
                    if (entity) {
                        c.var(ALOAD, 3);
                    }
                    MethodInsnNode call = c.takeCall();
                    require((call.getOpcode() == INVOKEVIRTUAL || call.getOpcode() == INVOKESPECIAL) && call.owner.equals(ARMOR) && !call.itf
                                    && call.desc.equals(entity ? "(ZL" + ENTITY + ";)Z" : "(Z)Z"), "predicate result callback");
                    MethodNode handler = method(owner, call.name, call.desc);
                    require(handler != null && (handler.access & (ACC_STATIC | ACC_ABSTRACT | ACC_NATIVE)) == 0,
                            "predicate callback dispatch");
                    // MixinExtras 0.5.4 emits SPECIAL for these private handlers; 0.5.5 emits VIRTUAL.
                    require(call.getOpcode() != INVOKESPECIAL || (handler.access & ACC_PRIVATE) != 0, "special callback must be private");
                    if (ownHook(handler, slots[index])) {
                        ownHooks++;
                    }
                }
                require(ownHooks <= 1, "duplicate own predicate hook");
                if (slots[index].equals("FEET")) {
                    installedOwnHook = ownHooks == 1;
                }
                int zero = c.jump(IFEQ);
                c.constant(1);
                int store = c.jump(GOTO);
                require(c.position == zero, "boolean false branch");
                c.constant(0);
                require(c.position == store, "boolean join");
                terms[index] = c.local(ISTORE);
                require(terms[index] >= 5 && locals.add(terms[index]), "distinct boolean count terms");
            }
            require(installedOwnHook, "installed own FEET result hook");
            c.var(ILOAD, terms[0]);
            c.var(ILOAD, terms[1]);
            c.op(IADD);
            c.var(ILOAD, terms[2]);
            c.op(IADD);
            c.var(ILOAD, terms[3]);
            c.op(IADD);
            c.constant(1);
            c.op(ISUB);
            int count = c.local(ISTORE);
            require(count >= 5 && locals.add(count), "strength count local");
            c.var(ILOAD, count);
            require(c.jump(IFLT) == exit, "nonempty armor gate");
            effect(c, player, "WATER_BREATHING", -1);
            c.var(ALOAD, player);
            c.call(INVOKEVIRTUAL, PLAYER, "isInWaterOrRain", "()Z");
            require(c.jump(IFEQ) == exit, "native wetness gate");
            effect(c, player, "STRENGTH", count);
            require(c.position == exit, "shared native exit");
            c.op(RETURN);
            require(c.position == c.code.size(), "extra native tick work");
            return null;
        } catch (Mismatch rejected) {
            return rejected.getMessage();
        }
    }

    static String baseFailure(ClassNode owner) {
        MethodNode method = method(owner, "inventoryTick", TICK);
        if (method == null || method.access != ACC_PUBLIC || !noHandlers(method)) {
            return "base tick API";
        }
        List<AbstractInsnNode> code = code(method);
        return code.size() == 1 && code.getFirst().getOpcode() == RETURN ? null : "base tick is not empty";
    }

    static boolean publish(ClassNode owner, String getter) {
        MethodNode method = method(owner, getter, "()Z");
        if (method == null || method.access != ACC_PUBLIC || !noHandlers(method)) {
            return false;
        }
        List<AbstractInsnNode> code = code(method);
        if (code.size() != 2 || code.getFirst().getOpcode() != ICONST_0 || code.getLast().getOpcode() != IRETURN) {
            return false;
        }
        method.instructions.set(code.getFirst(), new InsnNode(ICONST_1));
        return true;
    }

    static boolean prepareGetter(ClassNode owner, String getter) {
        MethodNode method = method(owner, getter, "()Z");
        if (method == null) {
            return false;
        }
        List<AbstractInsnNode> code = code(method);
        if (method.access == ACC_PUBLIC && noHandlers(method) && code.size() == 2 && code.getFirst().getOpcode() == ICONST_0 && code.getLast()
                                                                                                                                    .getOpcode() == IRETURN) {
            return true;
        }
        // A changed owned getter cannot retain a stale true result when final validation rejects it.
        // Reset access too: e.g. PUBLIC|FINAL would otherwise remain callable with a stale true body.
        method.access = ACC_PUBLIC;
        method.instructions.clear();
        method.instructions.add(new InsnNode(ICONST_0));
        method.instructions.add(new InsnNode(IRETURN));
        method.maxStack = Math.max(method.maxStack, 1);
        method.maxLocals = Math.max(method.maxLocals, 1);
        if (method.tryCatchBlocks != null) {
            method.tryCatchBlocks.clear();
        }
        method.localVariables = null;
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
        return false;
    }

    private static boolean ownHook(MethodNode handler, String slot) {
        // Verify our actual OR body/provenance, not merely the existence of a recognition call.
        // This does not certify third-party callbacks' purity or intentional-veto semantics.
        try {
            require(handler.desc.equals("(ZL" + ENTITY + ";)Z") && noHandlers(handler), "own hook API");
            Cursor c = new Cursor(handler);
            c.var(ILOAD, 1);
            int yes = c.jump(IFNE);
            c.var(ALOAD, 2);
            c.field(GETSTATIC, SLOT, slot, "L" + SLOT + ";");
            c.call(INVOKESTATIC, ADAPTER, "recognizes", "(L" + ENTITY + ";L" + SLOT + ";)Z");
            int no = c.jump(IFEQ);
            require(c.position == yes, "own native-positive branch");
            c.constant(1);
            int join = c.jump(GOTO);
            require(c.position == no, "own false branch");
            c.constant(0);
            require(c.position == join, "own result join");
            c.op(IRETURN);
            require(c.position == c.code.size(), "extra own hook work");
            return true;
        } catch (Mismatch rejected) {
            return false;
        }
    }

    // WrapMethod-created nodes may have null, rather than an empty exception table.
    private static boolean noHandlers(MethodNode method) {
        return method.tryCatchBlocks == null || method.tryCatchBlocks.isEmpty();
    }

    private static void effect(Cursor c, int player, String effect, int count) {
        c.var(ALOAD, player);
        c.type(NEW, EFFECT);
        c.op(DUP);
        c.field(GETSTATIC, EFFECTS, effect, "Lnet/minecraft/core/Holder;");
        require(c.integer() > 0, "effect duration");
        if (count < 0) {
            require(c.integer() >= 0, "water amplifier");
        } else {
            c.var(ILOAD, count);
        }
        for (int flag = 0; flag < 2; flag++) {
            int value = c.integer();
            require(value == 0 || value == 1, "effect boolean flag");
        }
        c.call(INVOKESPECIAL, EFFECT, "<init>", "(Lnet/minecraft/core/Holder;IIZZ)V");
        c.call(INVOKEVIRTUAL, PLAYER, "addEffect", "(L" + EFFECT + ";)Z");
        c.op(POP);
    }

    static MethodNode method(ClassNode owner, String name, String desc) {
        return owner.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElse(null);
    }

    static List<AbstractInsnNode> code(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() >= 0) {
                result.add(instruction);
            }
        }
        return result;
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new Mismatch(reason);
        }
    }

    private static final class Mismatch extends RuntimeException {
        Mismatch(String reason) {
            super(reason);
        }
    }

    /** Semantic cursor ignores frames/debug and resolves branches to executable instructions. */
    private static final class Cursor {
        final List<AbstractInsnNode> code;
        int position;

        Cursor(MethodNode method) {
            code = code(method);
        }

        int opcode() {
            return position < code.size() ? code.get(position).getOpcode() : -1;
        }

        AbstractInsnNode take() {
            require(position < code.size(), "truncated native entry");
            return code.get(position++);
        }

        void op(int opcode) {
            require(take().getOpcode() == opcode, "native opcode " + opcode + " at " + (position - 1));
        }

        int local(int opcode) {
            AbstractInsnNode n = take();
            require(n instanceof VarInsnNode && n.getOpcode() == opcode, "native local");
            return ((VarInsnNode) n).var;
        }

        void var(int opcode, int local) {
            require(local(opcode) == local, "argument/local provenance");
        }

        void type(int opcode, String type) {
            AbstractInsnNode n = take();
            require(n instanceof TypeInsnNode t && t.getOpcode() == opcode && t.desc.equals(type), "native type " + type);
        }

        void field(int opcode, String owner, String name, String desc) {
            AbstractInsnNode n = take();
            require(n instanceof FieldInsnNode f && f.getOpcode() == opcode && f.owner.equals(owner) && f.name.equals(name) && f.desc.equals(desc),
                    "native field " + name);
        }

        MethodInsnNode takeCall() {
            AbstractInsnNode n = take();
            require(n instanceof MethodInsnNode, "native method call");
            return (MethodInsnNode) n;
        }

        void call(int opcode, String owner, String name, String desc) {
            MethodInsnNode c = takeCall();
            require(c.getOpcode() == opcode && c.owner.equals(owner) && c.name.equals(name) && c.desc.equals(desc) && !c.itf, "native call " + name);
        }

        int jump(int opcode) {
            AbstractInsnNode n = take();
            require(n instanceof JumpInsnNode && n.getOpcode() == opcode, "native branch");
            AbstractInsnNode target = ((JumpInsnNode) n).label;
            while (target != null && target.getOpcode() < 0) {
                target = target.getNext();
            }
            int index = code.indexOf(target);
            require(index >= 0, "native branch target");
            return index;
        }

        int integer() {
            AbstractInsnNode n = take();
            if (n.getOpcode() >= ICONST_M1 && n.getOpcode() <= ICONST_5) {
                return n.getOpcode() - ICONST_0;
            }
            if (n instanceof IntInsnNode i && (i.getOpcode() == BIPUSH || i.getOpcode() == SIPUSH)) {
                return i.operand;
            }
            if (n instanceof LdcInsnNode l && l.cst instanceof Integer value) {
                return value;
            }
            throw new Mismatch("native integer constant");
        }

        void constant(int expected) {
            require(integer() == expected, "native boolean/count constant");
        }
    }
}
