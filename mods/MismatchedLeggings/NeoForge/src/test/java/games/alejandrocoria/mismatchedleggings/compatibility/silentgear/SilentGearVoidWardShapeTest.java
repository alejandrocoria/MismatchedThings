package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.NOP;

class SilentGearVoidWardShapeTest {
    @Test
    void pinnedNativeQueryRequiresOurOwnProvenRoute() throws Exception {
        var owner = nativeOwner();
        assertNotNull(SilentGearVoidWardShape.query(tick(owner).desc, tick(owner).instructions));
        assertFalse(SilentGearVoidWardShape.ready(owner));
        install(owner, "mismatchedleggings$sgVoidWard");
        assertTrue(SilentGearVoidWardShape.ready(owner));
    }

    @Test
    void compatiblePeerOrdersAndUnrelatedEditsKeepOurLocalProof() throws Exception {
        for (boolean peerFirst : new boolean[]{true, false}) {
            var owner = nativeOwner();
            install(owner, peerFirst ? "mismatchedleggings$sgVoidWard" : "mismatchedboots$sgVoidWard");
            install(owner, peerFirst ? "mismatchedboots$sgVoidWard" : "mismatchedleggings$sgVoidWard");
            tick(owner).instructions.insert(new InsnNode(NOP));
            assertTrue(SilentGearVoidWardShape.ready(owner));
        }
    }

    @Test
    void changedPermissionAndOriginalResultForwardingRefuseOnlyOurRoute() throws Exception {
        for (boolean permission : new boolean[]{true, false}) {
            var owner = nativeOwner();
            install(owner, "mismatchedleggings$sgVoidWard");
            var route = owner.methods.stream().filter(m -> m.name.equals("mismatchedleggings$sgVoidWard")).findFirst().orElseThrow();
            if (permission) {
                ((MethodInsnNode) route.instructions.get(2)).name = "mismatchedleggings$sgBrilliantReady";
            } else {
                route.instructions.set(route.instructions.getFirst(), new InsnNode(ICONST_0));
            }
            assertFalse(SilentGearVoidWardShape.ready(owner));
        }
    }

    @Test
    void foreignPlayerTraitAndReversedConsumerAreNotTheNativeQueryContract() throws Exception {
        var owner = nativeOwner();
        var query = SilentGearVoidWardShape.query(tick(owner).desc, tick(owner).instructions);
        for (var instruction : tick(owner).instructions) {
            if (instruction instanceof MethodInsnNode producer && producer.name.equals("getEntity")) {
                producer.name = "differentPlayer";
                break;
            }
        }
        assertNull(SilentGearVoidWardShape.query(tick(owner).desc, tick(owner).instructions));

        owner = nativeOwner();
        query = SilentGearVoidWardShape.query(tick(owner).desc, tick(owner).instructions);
        ((FieldInsnNode) query.getPrevious()).name = "BRILLIANT";
        assertNull(SilentGearVoidWardShape.query(tick(owner).desc, tick(owner).instructions));

        owner = nativeOwner();
        install(owner, "mismatchedleggings$sgVoidWard");
        query = SilentGearVoidWardShape.query(tick(owner).desc, tick(owner).instructions);
        var consumer = query.getNext();
        while (!(consumer instanceof JumpInsnNode)) {
            consumer = consumer.getNext();
        }
        ((JumpInsnNode) consumer).setOpcode(IFNE);
        assertFalse(SilentGearVoidWardShape.ready(owner));
    }

    private static void install(ClassNode owner, String name) {
        var query = SilentGearVoidWardShape.query(tick(owner).desc, tick(owner).instructions);
        var extension = new InsnList();
        extension.add(new VarInsnNode(ALOAD, 0));
        extension.add(new MethodInsnNode(INVOKESTATIC, owner.name, name, SilentGearVoidWardShape.RESULT, false));
        tick(owner).instructions.insert(query, extension);

        var route = new MethodNode(ACC_STATIC, name, SilentGearVoidWardShape.RESULT, null, null);
        route.instructions.add(new VarInsnNode(ILOAD, 0));
        route.instructions.add(new VarInsnNode(ALOAD, 1));
        route.instructions.add(new MethodInsnNode(INVOKESTATIC, owner.name, "mismatchedleggings$sgVoidReady", "()Z", false));
        route.instructions.add(new MethodInsnNode(INVOKESTATIC,
                "games/alejandrocoria/mismatchedleggings/compatibility/silentgear/SilentGearVoidWard", "recognize",
                "(ZL" + SilentGearVoidWardShape.PRE + ";Z)Z", false));
        route.instructions.add(new InsnNode(IRETURN));
        owner.methods.add(route);
    }

    private static ClassNode nativeOwner() throws Exception {
        try (var zip = new ZipFile(System.getProperty("mismatchedleggings.test.sgLiveJar"));
             var input = zip.getInputStream(zip.getEntry(SilentGearVoidWardShape.EVENTS + ".class"))) {
            var node = new ClassNode();
            new ClassReader(input.readAllBytes()).accept(node, 0);
            return node;
        }
    }

    private static MethodNode tick(ClassNode owner) {
        return owner.methods.stream().filter(m -> m.name.equals("onPlayerTick") && m.desc.equals(SilentGearVoidWardShape.TICK))
                .findFirst().orElseThrow();
    }
}
