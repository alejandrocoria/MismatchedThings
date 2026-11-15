package games.alejandrocoria.mismatchedthings.internal.compatibility.mysticalagriculture;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysticalRefundShapeTest implements Opcodes {
    private static final String ADAPTER = "games/alejandrocoria/mismatchedthings/internal/compatibility/mysticalagriculture/MysticalRefundObserver";
    private static final String CAPTURE = "L" + ADAPTER + "$Capture;";
    private static final String REF = "Lcom/llamalad7/mixinextras/sugar/ref/LocalRef;";
    private static final String MIXIN = "games.alejandrocoria.mismatchedthings.mixin.compatibility.mysticalagriculture.MysticalRefundMixin";

    @Test
    void earlyProofNeedsNoGameLoaderOrNativeModClasses() throws Exception {
        var urls = new URL[]{location(MysticalRefundShape.class), location(ClassReader.class),
                location(ClassNode.class), location(Analyzer.class)};
        try (var loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            var proof = Class.forName(MysticalRefundShape.class.getName(), true, loader);
            var nodeType = Class.forName(ClassNode.class.getName(), true, loader);
            var unknown = nodeType.getConstructor().newInstance();
            for (boolean fabric : List.of(false, true)) {
                var shape = proof.getConstructor(boolean.class).newInstance(fabric);
                assertFalse((boolean) proof.getMethod("entry", nodeType).invoke(shape, unknown));
                assertFalse((boolean) proof.getMethod("publish", nodeType).invoke(shape, unknown));
            }
        }
    }

    @Test
    void nativeBoundaryNeedsActualObserverAndAcceptsUnrelatedChanges() throws Exception {
        var node = nativeStack();
        var shape = shape(node);
        assertTrue(shape.entry(node));
        assertFalse(shape.publish(node));
        node = transformed();
        shape = shape(node);
        node.methods.removeIf(m -> m.name.equals("inventoryTick"));
        apply(node).instructions.insert(new InsnNode(NOP));
        assertTrue(shape.publish(node));
    }

    @Test
    void finalObserverPublishesOnlyWithAllStagesAndCorrectOperands() throws Exception {
        var intact = transformed();
        assertTrue(shape(intact).publish(intact));
        for (String fault : List.of("count", "condition", "damage", "missingArm", "missingConsumed", "missingComplete",
                "wrongOwner", "wrongRoot", "bypassReady", "countWrite", "throwCatch", "callback", "earlyReturn",
                "wrongCapture", "wrongSharedLocal", "completeBeforeCallback", "missingMarker", "changedMarker", "changedHandler")) {
            var node = transformed();
            var apply = apply(node);
            switch (fault) {
                case "count" -> apply.instructions.set(call(apply, "shrink").getPrevious(), new InsnNode(ICONST_2));
                case "condition" -> call(apply, "isBroken").name = "isEmpty";
                case "damage" -> apply.instructions.set(call(apply, "setDamageValue").getPrevious(), new InsnNode(ICONST_0));
                case "missingArm" -> removeInvocation(apply, "arm");
                case "missingConsumed" -> removeInvocation(apply, "consumed");
                case "missingComplete" -> removeInvocation(apply, "complete");
                case "wrongOwner" -> {
                    var capture = method(node, "capture");
                    capture.instructions.set(real(capture).get(4), new InsnNode(ACONST_NULL));
                }
                case "wrongRoot" -> {
                    var capture = method(node, "capture");
                    capture.instructions.set(real(capture).get(2), new InsnNode(ACONST_NULL));
                }
                case "bypassReady" -> {
                    var capture = method(node, "capture");
                    capture.instructions.set(real(capture).get(1), new InsnNode(ICONST_1));
                }
                case "countWrite" -> {
                    var count = method(node, "setCount");
                    count.instructions.set(real(count).get(1), new InsnNode(ICONST_0));
                }
                case "throwCatch" -> {
                    var start = new LabelNode();
                    var end = new LabelNode();
                    var handler = new LabelNode();
                    apply.instructions.insert(start);
                    apply.instructions.add(end);
                    apply.instructions.add(handler);
                    apply.instructions.add(new InsnNode(ATHROW));
                    apply.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, "java/lang/Throwable"));
                }
                case "callback" -> call(apply, "accept").owner = "foreign/Callback";
                case "earlyReturn" -> apply.instructions.insert(call(apply, "shrink"), new InsnNode(RETURN));
                case "wrongCapture" -> apply.instructions.set(call(apply, "capture").getPrevious(), new InsnNode(ACONST_NULL));
                case "wrongSharedLocal" -> {
                    var other = new InsnList();
                    other.add(new TypeInsnNode(NEW, "com/llamalad7/mixinextras/sugar/impl/ref/generated/LocalRefImpl"));
                    other.add(new InsnNode(DUP));
                    other.add(new MethodInsnNode(INVOKESPECIAL, "com/llamalad7/mixinextras/sugar/impl/ref/generated/LocalRefImpl", "<init>", "()V", false));
                    other.add(new VarInsnNode(ASTORE, 9));
                    apply.instructions.insert(other);
                    apply.instructions.set(call(apply, "consumed").getPrevious(), new VarInsnNode(ALOAD, 9));
                }
                case "completeBeforeCallback" -> {
                    var invocation = call(apply, "complete");
                    var block = new InsnList();
                    AbstractInsnNode first = invocation;
                    for (int index = 0; index < 6; index++) {
                        first = first.getPrevious();
                    }
                    while (true) {
                        var next = first.getNext();
                        apply.instructions.remove(first);
                        block.add(first);
                        if (first == invocation) {
                            break;
                        }
                        first = next;
                    }
                    apply.instructions.insertBefore(call(apply, "accept"), block);
                }
                case "missingMarker" -> node.methods.remove(method(node, MysticalRefundShape.READY));
                case "changedMarker" -> method(node, MysticalRefundShape.READY).instructions.set(
                        real(method(node, MysticalRefundShape.READY)).getFirst(), new InsnNode(ICONST_1));
                case "changedHandler" -> method(node, "complete").instructions.insert(
                        new MethodInsnNode(INVOKESTATIC, "foreign/Events", "deliver", "()V", false));
            }
            assertFalse(shape(node).publish(node), fault);
            if (!fault.equals("missingMarker")) {
                assertEquals(ICONST_0, real(method(node, MysticalRefundShape.READY)).getFirst().getOpcode(), fault);
            }
            for (String kind : List.of("capture", "arm", "consumed", "complete")) {
                assertEquals(List.of(RETURN), real(method(node, kind)).stream().map(AbstractInsnNode::getOpcode).toList(), fault);
            }
        }
    }

    @Test
    void preBreakHooksStayBeforeLastObservationWithoutReplayingThem() throws Exception {
        var node = transformed();
        var apply = apply(node);
        var hook = new MethodInsnNode(INVOKESTATIC, "foreign/Events", "before", "()V", false);
        apply.instructions.insertBefore(call(apply, "shrink"), hook);
        assertTrue(shape(node).publish(node));
        assertTrue(apply.instructions.indexOf(hook) < apply.instructions.indexOf(call(apply, "arm")));
        assertEquals(1, real(apply).stream().filter(i -> i == hook).count());
    }

    @Test
    void unrelatedSharedLocalObserverDoesNotDisableRefunds() throws Exception {
        var node = transformed();
        var peer = new MethodNode(ACC_PRIVATE, "peer", method(node, "arm").desc, null, null);
        peer.instructions.add(new InsnNode(RETURN));
        node.methods.add(peer);
        apply(node).instructions.insertBefore(call(apply(node), "setDamageValue"), invoke(node, "peer", peer.desc));
        assertTrue(shape(node).publish(node));
    }

    @Test
    void unknownConsumingRouterAndChangedCountHelpersRefuseLocally() throws Exception {
        var node = transformed();
        call(apply(node), "shrink").owner = "foreign/Router";
        assertFalse(shape(node).publish(node));
        node = transformed();
        method(node, "grow").instructions.insert(new MethodInsnNode(INVOKESTATIC, "foreign/Events", "reenter", "()V", false));
        assertFalse(shape(node).publish(node));
    }

    @Test
    void refusalNeutralizesOnlyOwnedHandlersNotForeignCallbacks() throws Exception {
        var node = transformed();
        var peer = new MethodNode(ACC_PRIVATE, "foreignObserver", method(node, "arm").desc, null, null);
        peer.instructions.add(new InsnNode(RETURN));
        node.methods.add(peer);
        var ordinary = real(apply(node));
        call(method(node, "capture"), "capture").name = "unknownCapture";
        assertFalse(shape(node).publish(node));
        assertEquals(List.of(RETURN), real(peer).stream().map(AbstractInsnNode::getOpcode).toList());
        assertEquals(ordinary, real(apply(node)));
        assertNotNull(call(apply(node), "shrink"));
        assertNotNull(call(apply(node), "accept"));
    }

    private static ClassNode transformed() throws Exception {
        var node = nativeStack();
        var shape = shape(node);
        var apply = apply(node);
        var getter = new MethodNode(ACC_PRIVATE | ACC_STATIC, MysticalRefundShape.READY, "()Z", null, null);
        getter.instructions.add(new InsnNode(ICONST_0));
        getter.instructions.add(new InsnNode(IRETURN));
        owned(getter);
        node.methods.add(getter);
        String desc = "(I" + shape.ENTITY + "Ljava/util/function/Consumer;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;" + REF + ")V";
        for (String kind : List.of("capture", "arm", "consumed", "complete")) {
            var handler = new MethodNode(ACC_PRIVATE, kind, desc, null, null);
            owned(handler);
            node.methods.add(handler);
            handler.instructions.add(new VarInsnNode(ALOAD, 5));
            if (kind.equals("capture")) {
                handler.instructions.add(new MethodInsnNode(INVOKESTATIC, node.name, MysticalRefundShape.READY, "()Z", false));
                handler.instructions.add(new VarInsnNode(ALOAD, 0));
                handler.instructions.add(new VarInsnNode(ILOAD, 1));
                handler.instructions.add(new VarInsnNode(ALOAD, 2));
                handler.instructions.add(new MethodInsnNode(INVOKESTATIC, ADAPTER, kind, "(Z" + MysticalRefundShape.STACK + "ILnet/minecraft/world/entity/LivingEntity;)" + CAPTURE, false));
                handler.instructions.add(new MethodInsnNode(INVOKEINTERFACE, "com/llamalad7/mixinextras/sugar/ref/LocalRef", "set", "(Ljava/lang/Object;)V", true));
            } else {
                handler.instructions.add(new MethodInsnNode(INVOKEINTERFACE, "com/llamalad7/mixinextras/sugar/ref/LocalRef", "get", "()Ljava/lang/Object;", true));
                handler.instructions.add(new TypeInsnNode(CHECKCAST, ADAPTER + "$Capture"));
                handler.instructions.add(new MethodInsnNode(INVOKESTATIC, ADAPTER, kind, "(" + CAPTURE + ")V", false));
            }
            handler.instructions.add(new InsnNode(RETURN));
        }
        var start = new InsnList();
        String impl = "com/llamalad7/mixinextras/sugar/impl/ref/generated/LocalRefImpl";
        start.add(new TypeInsnNode(NEW, impl));
        start.add(new InsnNode(DUP));
        start.add(new MethodInsnNode(INVOKESPECIAL, impl, "<init>", "()V", false));
        start.add(new VarInsnNode(ASTORE, 8));
        start.add(invoke(node, "capture", desc));
        apply.instructions.insert(start);
        var shrink = call(apply, "shrink");
        apply.instructions.insertBefore(shrink, invoke(node, "arm", desc));
        apply.instructions.insert(shrink, invoke(node, "consumed", desc));
        for (var instruction : apply.instructions.toArray()) {
            if (instruction.getOpcode() == RETURN) {
                apply.instructions.insertBefore(instruction, invoke(node, "complete", desc));
            }
        }
        return node;
    }
    private static void removeInvocation(MethodNode method, String name) {
        AbstractInsnNode current = call(method, name);
        for (int i = 0; i < 7; i++) {
            var previous = current.getPrevious();
            method.instructions.remove(current);
            current = previous;
        }
    }
    private static InsnList invoke(ClassNode node, String kind, String desc) {
        var code = new InsnList();
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new VarInsnNode(ILOAD, 1));
        code.add(new VarInsnNode(ALOAD, 2));
        code.add(new VarInsnNode(ALOAD, 3));
        code.add(new InsnNode(ACONST_NULL));
        code.add(new VarInsnNode(ALOAD, 8));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, node.name, kind, desc, false));
        return code;
    }

    private static void owned(MethodNode node) {
        var annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        annotation.values = new ArrayList<>(List.of("mixin", MIXIN));
        node.visibleAnnotations = new ArrayList<>(List.of(annotation));
    }

    private static List<AbstractInsnNode> real(MethodNode node) {
        return Arrays.stream(node.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0 && instruction.getOpcode() != NOP).toList();
    }

    private static MethodInsnNode call(MethodNode node, String name) {
        return Arrays.stream(node.instructions.toArray())
                     .filter(instruction -> instruction instanceof MethodInsnNode method && method.name.equals(name))
                     .map(MethodInsnNode.class::cast)
                     .findFirst().orElseThrow();
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static MethodNode apply(ClassNode node) {
        var shape = shape(node);
        return node.methods.stream().filter(method -> method.name.equals("applyDamage") && method.desc.equals(shape.APPLY)).findFirst().orElseThrow();
    }

    private static MysticalRefundShape shape(ClassNode node) {
        return new MysticalRefundShape(node.methods.stream().noneMatch(method -> method.name.equals("applyDamage") && method.desc.contains("/LivingEntity;")));
    }

    private static ClassNode nativeStack() throws Exception {
        var node = new ClassNode();
        try (var stream = MysticalRefundShapeTest.class.getClassLoader().getResourceAsStream(MysticalRefundShape.OWNER + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }

    private static URL location(Class<?> type) {
        return type.getProtectionDomain().getCodeSource().getLocation();
    }
}
