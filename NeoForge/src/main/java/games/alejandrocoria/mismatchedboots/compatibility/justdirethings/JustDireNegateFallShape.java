package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;
import java.util.Set;

import static games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpShape.ABILITY;
import static games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpShape.FALL;
import static games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpShape.HANDLER;
import static games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpShape.HELPERS;
import static games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpShape.SERVER_PLAYER;
import static games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpShape.SLOT;
import static games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpShape.STACK;
import static games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpShape.TOOL;
import static games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpShape.code;
import static games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpShape.feetRead;
import static games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpShape.installed;
import static games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpShape.method;
import static games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpShape.owned;
import static games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpShape.query;

/** Local hand/FEET consumers and spending permission, independent of passive Jump availability. */
final class JustDireNegateFallShape implements Opcodes {
    static final String GETTER = "mismatchedboots$justDireNegateFallReady";
    private static final String EVENT = "net/neoforged/neoforge/event/entity/living/LivingFallEvent";
    private static final String ADAPTER = "games/alejandrocoria/mismatchedboots/compatibility/justdirethings/JustDireJumpCompatibility";
    private static final String DISPATCH = "(L" + EVENT + ";L" + STACK + ";Z)V";

    private JustDireNegateFallShape() {}

    static boolean api(ClassNode tool, ClassNode ability) {
        return query(tool, "canUseAbility", "(L" + STACK + ";L" + ABILITY + ";)Z",
                Set.of("hasAbility", "hasUpgrade", "getEnabled", "getSetting", "getName"))
                && query(ability, "getFeCost", "()I", Set.of());
    }

    static boolean flow(ClassNode handler, boolean transformed) {
        MethodNode fall = method(handler, "LivingFallDamage", FALL);
        if (fall == null || !fall.tryCatchBlocks.isEmpty()) { return false; }
        List<AbstractInsnNode> nodes = code(fall.instructions);
        try {
            int feet = -1;
            for (int i = 1; i < nodes.size(); i++) {
                if (nodes.get(i) instanceof FieldInsnNode f && f.owner.equals(SLOT) && f.name.equals("FEET")) {
                    if (feet >= 0 || f.getOpcode() != GETSTATIC || !f.desc.equals("L" + SLOT + ";")) { return false; }
                    feet = i;
                }
            }
            if (feet < 1 || !(nodes.get(feet - 1) instanceof VarInsnNode p) || p.getOpcode() != ALOAD) { return false; }
            if (transformed ? !installed(handler, "LivingFallDamage", FALL, "fall", SERVER_PLAYER)
                    : feetRead(FALL, fall.instructions) != nodes.get(feet + 1)) { return false; }
            int hand = -1;
            for (int i = 1; i < feet; i++) {
                if (nodes.get(i) instanceof MethodInsnNode c && c.owner.equals(SERVER_PLAYER) && c.name.equals("getMainHandItem")) {
                    if (hand >= 0) { return false; }
                    hand = i - 1;
                }
            }
            if (hand < 0) { return false; }
            Cursor hands = new Cursor(nodes, hand);
            hands.hand(p.var, "getMainHandItem");
            hands.hand(p.var, "getOffhandItem");
            if (hands.index != feet - 1) { return false; }

            // Only the equipment operation is transformed. Skip its certified bridge/spills,
            // then check both native interface rejections against that returned stack.
            int afterRead = feet + 2;
            if (transformed) {
                afterRead = -1;
                for (int i = feet + 1; i < nodes.size(); i++) {
                    if (nodes.get(i) instanceof MethodInsnNode c && c.getOpcode() == INVOKESTATIC && c.owner.equals(HANDLER)
                            && c.desc.startsWith("(L" + SERVER_PLAYER + ";L" + SLOT + ";") && c.desc.endsWith(")L" + STACK + ";")) {
                        afterRead = i + 1; break;
                    }
                }
            }
            if (afterRead < 0) { return false; }
            Cursor tail = new Cursor(nodes, afterRead);
            int boots = tail.variable(ASTORE);
            LabelNode negateRejected = tail.ability(boots, "NEGATEFALLDAMAGE");
            tail.load(ALOAD, 0); tail.call(EVENT, "getDistance", "()D"); tail.op(D2I);
            int multiplier = tail.variable(ISTORE);
            tail.load(ALOAD, boots); tail.load(ALOAD, p.var); tail.field("NEGATEFALLDAMAGE"); tail.load(ILOAD, multiplier);
            tail.call(HELPERS, "damageTool", "(L" + STACK + ";Lnet/minecraft/world/entity/LivingEntity;L" + ABILITY + ";I)V");
            tail.zeroReturn(); tail.target(negateRejected);
            LabelNode jumpRejected = tail.ability(boots, "JUMPBOOST");
            tail.load(ALOAD, boots); tail.field("JUMPBOOST"); tail.call(ABILITY, "getName", "()Ljava/lang/String;");
            tail.call(TOOL, "getToolValue", "(L" + STACK + ";Ljava/lang/String;)I");
            int value = tail.variable(ISTORE);
            tail.load(ALOAD, 0); tail.load(ALOAD, 0); tail.call(EVENT, "getDistance", "()D");
            tail.load(ILOAD, value); tail.op(I2D); tail.op(DSUB); tail.call(EVENT, "setDistance", "(D)V");
            tail.op(RETURN); tail.target(jumpRejected); tail.op(RETURN);
            // An unrelated prelude may return early, but must not jump into the protected
            // region and bypass a hand consumer (or enter the spending path a second way).
            int handStart = hand;
            for (int i = 0; i < hand; i++) {
                AbstractInsnNode node = nodes.get(i);
                if (node instanceof JumpInsnNode branch && bypassesHands(nodes, branch.label, hand, tail.index - 1)) { return false; }
                if (node instanceof TableSwitchInsnNode table) {
                    if (bypassesHands(nodes, table.dflt, hand, tail.index - 1)
                            || table.labels.stream().anyMatch(label -> bypassesHands(nodes, label, handStart, tail.index - 1))) { return false; }
                }
                if (node instanceof LookupSwitchInsnNode lookup) {
                    if (bypassesHands(nodes, lookup.dflt, hand, tail.index - 1)
                            || lookup.labels.stream().anyMatch(label -> bypassesHands(nodes, label, handStart, tail.index - 1))) { return false; }
                }
            }
            return true;
        } catch (IllegalArgumentException | IndexOutOfBoundsException incompatible) { return false; }
    }

    private static boolean bypassesHands(List<AbstractInsnNode> nodes, LabelNode label, int hand, int end) {
        AbstractInsnNode next = label;
        do { next = next.getNext(); } while (next != null && next.getOpcode() < 0);
        int target = nodes.indexOf(next);
        return target > hand && target < end;
    }

    static boolean publish(ClassNode handler, boolean permitted) {
        MethodNode getter = method(handler, GETTER, "()Z");
        if (getter == null) { return false; }
        var body = code(getter.instructions);
        boolean original = getter.access == (ACC_PRIVATE | ACC_STATIC) && owned(getter) && getter.tryCatchBlocks.isEmpty()
                && body.size() == 2 && body.getFirst().getOpcode() == ICONST_0 && body.getLast().getOpcode() == IRETURN;
        getter.instructions.clear();
        getter.instructions.add(new InsnNode(ICONST_0)); getter.instructions.add(new InsnNode(IRETURN));
        getter.access = ACC_PRIVATE | ACC_STATIC; getter.maxStack = Math.max(getter.maxStack, 1);
        getter.tryCatchBlocks.clear(); getter.localVariables = null;
        getter.visibleLocalVariableAnnotations = null; getter.invisibleLocalVariableAnnotations = null;
        if (!original || !permitted || !flow(handler, true) || !dispatcherPermission(handler)) { return false; }
        getter.instructions.set(getter.instructions.getFirst(), new InsnNode(ICONST_1));
        return true;
    }

    private static boolean dispatcherPermission(ClassNode handler) {
        int calls = 0;
        for (MethodNode m : handler.methods) {
            var nodes = code(m.instructions);
            for (int i = 3; i < nodes.size(); i++) {
                if (!(nodes.get(i) instanceof MethodInsnNode call) || !call.owner.equals(ADAPTER) || !call.name.equals("fall")) { continue; }
                if (!owned(m) || call.getOpcode() != INVOKESTATIC || !call.desc.equals(DISPATCH) || nodes.size() < 2
                        || !(nodes.get(nodes.size() - 2) instanceof VarInsnNode returned) || returned.getOpcode() != ALOAD
                        || nodes.getLast().getOpcode() != ARETURN) { return false; }
                int eventArgument = -1, local = 0;
                for (Type argument : Type.getArgumentTypes(m.desc)) {
                    if (argument.getDescriptor().equals("L" + EVENT + ";")) {
                        if (eventArgument >= 0) { return false; }
                        eventArgument = local;
                    }
                    local += argument.getSize();
                }
                if (!(nodes.get(i - 3) instanceof VarInsnNode event) || event.getOpcode() != ALOAD || event.var != eventArgument
                        || !(nodes.get(i - 2) instanceof VarInsnNode root) || root.getOpcode() != ALOAD || root.var != returned.var
                        || !(nodes.get(i - 1) instanceof MethodInsnNode ready) || ready.getOpcode() != INVOKESTATIC
                        || !ready.owner.equals(HANDLER) || !ready.name.equals(GETTER) || !ready.desc.equals("()Z")) { return false; }
                calls++;
            }
        }
        return calls == 1;
    }

    /** Match only the audited consumer blocks; debug nodes and local relocation do not matter. */
    private static final class Cursor {
        private final List<AbstractInsnNode> nodes;
        private int index;
        Cursor(List<AbstractInsnNode> nodes, int index) { this.nodes = nodes; this.index = index; }
        void op(int opcode) { if (nodes.get(index++).getOpcode() != opcode) { throw new IllegalArgumentException(); } }
        int variable(int opcode) {
            if (!(nodes.get(index++) instanceof VarInsnNode v) || v.getOpcode() != opcode) { throw new IllegalArgumentException(); }
            return v.var;
        }
        void load(int opcode, int variable) { if (variable(opcode) != variable) { throw new IllegalArgumentException(); } }
        void call(String owner, String name, String descriptor) {
            if (!(nodes.get(index++) instanceof MethodInsnNode c) || !c.owner.equals(owner) || !c.name.equals(name)
                    || !c.desc.equals(descriptor) || c.getOpcode() != (owner.equals(TOOL) ? (name.equals("getToolValue") ? INVOKESTATIC : INVOKEINTERFACE)
                    : owner.equals(HELPERS) ? INVOKESTATIC : INVOKEVIRTUAL)) { throw new IllegalArgumentException(); }
        }
        void type(int opcode) {
            if (!(nodes.get(index++) instanceof TypeInsnNode t) || t.getOpcode() != opcode || !t.desc.equals(TOOL)) { throw new IllegalArgumentException(); }
        }
        void field(String ability) {
            if (!(nodes.get(index++) instanceof FieldInsnNode f) || f.getOpcode() != GETSTATIC || !f.owner.equals(ABILITY)
                    || !f.name.equals(ability) || !f.desc.equals("L" + ABILITY + ";")) { throw new IllegalArgumentException(); }
        }
        LabelNode reject() {
            if (!(nodes.get(index++) instanceof JumpInsnNode j) || j.getOpcode() != IFEQ) { throw new IllegalArgumentException(); }
            return j.label;
        }
        void target(LabelNode label) {
            AbstractInsnNode next = label;
            do { next = next.getNext(); } while (next != null && next.getOpcode() < 0);
            if (next != nodes.get(index)) { throw new IllegalArgumentException(); }
        }
        void zeroReturn() { load(ALOAD, 0); op(DCONST_0); call(EVENT, "setDistance", "(D)V"); op(RETURN); }
        void hand(int player, String getter) {
            load(ALOAD, player); call(SERVER_PLAYER, getter, "()L" + STACK + ";");
            int stack = variable(ASTORE);
            load(ALOAD, stack); call(STACK, "getItem", "()Lnet/minecraft/world/item/Item;");
            int item = variable(ASTORE);
            load(ALOAD, item); type(INSTANCEOF); LabelNode rejected = reject();
            load(ALOAD, item); type(CHECKCAST); int tool = variable(ASTORE);
            load(ALOAD, tool); field("AIRBURST"); call(TOOL, "hasAbility", "(L" + ABILITY + ";)Z");
            if (reject() != rejected) { throw new IllegalArgumentException(); }
            zeroReturn(); target(rejected);
        }
        LabelNode ability(int boots, String ability) {
            load(ALOAD, boots); call(STACK, "getItem", "()Lnet/minecraft/world/item/Item;");
            int item = variable(ASTORE);
            load(ALOAD, item); type(INSTANCEOF); LabelNode rejected = reject();
            load(ALOAD, item); type(CHECKCAST); int tool = variable(ASTORE);
            load(ALOAD, tool); load(ALOAD, boots); field(ability);
            call(TOOL, "canUseAbilityAndDurability", "(L" + STACK + ";L" + ABILITY + ";)Z");
            if (reject() != rejected) { throw new IllegalArgumentException(); }
            return rejected;
        }
    }
}
