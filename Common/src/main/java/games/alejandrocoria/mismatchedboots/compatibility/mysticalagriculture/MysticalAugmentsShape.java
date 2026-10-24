package games.alejandrocoria.mismatchedboots.compatibility.mysticalagriculture;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayDeque;
import java.util.HashSet;

/** Narrow final-transform contracts, including MixinExtras' local operation/lambda bridges. */
public final class MysticalAugmentsShape implements Opcodes {
    public static final String UTILS = "com/blakebr0/mysticalagriculture/api/util/AugmentUtils";
    private static final String STACK = "Lnet/minecraft/world/item/ItemStack;";
    private static final String PLAYER = "Lnet/minecraft/world/entity/player/Player;";
    private static final String LIST = "Ljava/util/List;";
    private static final String ADAPTER = "games/alejandrocoria/mismatchedboots/compatibility/mysticalagriculture/MysticalAugmentsCompatibility";

    private MysticalAugmentsShape() {}

    public static boolean equipment(ClassNode owner) {
        var roots = owner.methods.stream().filter(m -> m.name.equals("getArmorAugments")
                && m.desc.equals("(" + PLAYER + ")" + LIST) && (m.access & ACC_STATIC) != 0).toList();
        if (roots.size() != 1) { return false; }
        var pending = new ArrayDeque<MethodNode>(); pending.add(roots.getFirst());
        var seen = new HashSet<MethodNode>();
        int reads = 0, nativeQueries = 0, bridges = 0;
        while (!pending.isEmpty()) {
            var method = pending.removeFirst();
            if (!seen.add(method)) { continue; }
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    if (call.name.equals("getItemBySlot") && call.desc.equals("(Lnet/minecraft/world/entity/EquipmentSlot;)" + STACK)) { reads++; }
                    if (call.owner.equals(UTILS) && call.name.equals("getAugments") && call.desc.equals("(" + STACK + ")" + LIST)) { nativeQueries++; }
                    if (call.owner.equals(ADAPTER) && call.name.equals("equipment")
                            && call.desc.equals("(" + PLAYER + STACK + LIST + ")" + LIST)) { bridges++; }
                    if (call.owner.equals(owner.name)) { enqueue(owner, pending, call.name, call.desc); }
                } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    for (Object argument : dynamic.bsmArgs) {
                        if (argument instanceof Handle handle && handle.getOwner().equals(owner.name)) {
                            enqueue(owner, pending, handle.getName(), handle.getDesc());
                        }
                    }
                }
            }
        }
        return reads == 1 && nativeQueries == 1 && bridges == 1;
    }

    private static void enqueue(ClassNode owner, ArrayDeque<MethodNode> pending, String name, String descriptor) {
        owner.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).forEach(pending::add);
    }

    public static boolean healthCleanup(ClassNode owner) {
        var roots = owner.methods.stream().filter(m -> m.name.equals("onPlayerTick")
                && m.desc.equals("(Lnet/minecraft/world/level/Level;" + PLAYER
                + "Lcom/blakebr0/mysticalagriculture/api/lib/AbilityCache;)V") && (m.access & ACC_STATIC) == 0).toList();
        if (roots.size() != 1) { return false; }
        var pending = new ArrayDeque<MethodNode>(); pending.add(roots.getFirst());
        var seen = new HashSet<MethodNode>();
        int registrations = 0, bridges = 0;
        while (!pending.isEmpty()) {
            var method = pending.removeFirst();
            if (!seen.add(method)) { continue; }
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.equals("com/blakebr0/mysticalagriculture/api/lib/AbilityCache") && call.name.equals("add")
                            && call.desc.equals("(Lcom/blakebr0/mysticalagriculture/api/tinkering/Augment;" + PLAYER + "Ljava/lang/Runnable;)V")) { registrations++; }
                    if (call.owner.equals(ADAPTER) && call.name.equals("refreshHealthCleanup")
                            && call.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;" + PLAYER + ")V")) { bridges++; }
                    if (call.owner.equals(owner.name)) { enqueue(owner, pending, call.name, call.desc); }
                } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    for (Object argument : dynamic.bsmArgs) {
                        if (argument instanceof Handle handle && handle.getOwner().equals(owner.name)) {
                            enqueue(owner, pending, handle.getName(), handle.getDesc());
                        }
                    }
                }
            }
        }
        // Only the local registration seam, not MA's whole tick body or balance constants.
        return registrations == 1 && bridges == 1;
    }

    public static boolean armorTick(ClassNode owner) {
        var methods = owner.methods.stream().filter(m -> m.name.equals("onArmorTick") && m.desc.equals("(" + STACK
                + "Lnet/minecraft/server/level/ServerLevel;" + PLAYER + ")V") && (m.access & ACC_STATIC) == 0).toList();
        if (methods.size() != 1) { return false; }
        // The audited built-ins never read the stack argument. Balance/actor-effect changes are not fingerprints.
        for (var instruction : methods.getFirst().instructions) {
            if (instruction instanceof VarInsnNode local && local.var == 1) { return false; }
        }
        return true;
    }

    public static void publish(ClassNode owner, String marker, boolean ready) {
        for (var method : owner.methods) {
            if (!method.name.equals(marker) || !method.desc.equals("()Z") || (method.access & ACC_STATIC) == 0) { continue; }
            method.instructions.clear(); method.tryCatchBlocks.clear();
            if (method.localVariables != null) { method.localVariables.clear(); }
            method.instructions.add(new InsnNode(ready ? ICONST_1 : ICONST_0));
            method.instructions.add(new InsnNode(IRETURN));
            method.maxStack = 1; method.maxLocals = 0;
        }
    }
}
