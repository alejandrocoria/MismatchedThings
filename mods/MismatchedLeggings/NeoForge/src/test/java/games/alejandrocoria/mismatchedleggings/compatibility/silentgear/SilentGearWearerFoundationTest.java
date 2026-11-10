package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilentGearWearerFoundationTest implements Opcodes {
    @Test
    void brilliantRejectsChangedInputsWritesAndResultsButAllowsUnrelatedEdits() throws Exception {
        var compatible = read(SilentGearBrilliantShape.OWNER);
        compatible.methods.forEach(m -> m.instructions.insert(new InsnNode(NOP)));
        compatible.methods.removeIf(m -> m.name.equals("inventoryTick"));
        assertTrue(SilentGearBrilliantShape.ready(compatible));
        for (String fault : List.of("actor", "trait", "query", "result", "write")) {
            var node = read(SilentGearBrilliantShape.OWNER);
            var method = node.methods.stream().filter(m -> m.name.equals("makesPiglinsNeutral")).findFirst().orElseThrow();
            var code = SilentGearSetShape.code(method.instructions);
            switch (fault) {
                case "actor" -> ((VarInsnNode) code.getFirst()).var = 2;
                case "trait" -> ((org.objectweb.asm.tree.FieldInsnNode) code.get(1)).name = "STELLAR";
                case "query" -> ((MethodInsnNode) code.get(2)).name = "changedQuery";
                case "result" -> method.instructions.insertBefore(code.getLast(), new InsnNode(INEG));
                case "write" -> method.instructions.insert(new MethodInsnNode(INVOKESTATIC, "foreign/Actor", "write", "()V", false));
                default -> throw new AssertionError(fault);
            }
            assertFalse(SilentGearBrilliantShape.ready(node), fault);
        }
    }

    @Test
    void pinnedIndependentNativeBoundariesAreAdmitted() throws Exception {
        assertTrue(SilentGearBrilliantShape.ready(read(SilentGearBrilliantShape.OWNER)));
        assertTrue(SilentGearWielderShape.effects(read(SilentGearSetShape.OWNER)));
        assertTrue(SilentGearWielderShape.potion(read(SilentGearWielderShape.DATA)));
        assertTrue(SilentGearWielderShape.query(read(SilentGearWielderShape.QUERY)));
        assertTrue(SilentGearWielderShape.identity(read(SilentGearSetShape.PREFIX + "util/GearHelper")));
        var node = read(SilentGearSetShape.OWNER);
        assertNotNull(pair(node));
        // A native pair alone is not permission to broaden either predicate.
        assertFalse(SilentGearSetShape.finalGraph(node, "local$type", "local$trait", "local/Callback", "local$ready"));
    }

    @Test
    void changedProducerEitherPredicateAndIncrementAreRefused() throws Exception {
        for (String fault : List.of("producer", "type", "trait", "increment")) {
            var node = read(SilentGearSetShape.OWNER);
            var pair = pair(node);
            assertNotNull(pair);
            var code = node.methods.stream().filter(m -> m.name.equals("getSetPieceCount")).findFirst().orElseThrow().instructions;
            switch (fault) {
                case "producer" -> {
                    var load = pair.type().getPrevious();
                    while (!(load instanceof VarInsnNode)) { load = load.getPrevious(); }
                    ((VarInsnNode) load).var = 6;
                }
                case "type" -> code.remove(pair.type());
                case "trait" -> ((MethodInsnNode) pair.trait()).name = "changedPredicate";
                case "increment" -> {
                    for (var instruction : code) {
                        if (instruction instanceof IincInsnNode inc) { inc.incr = 2; }
                    }
                }
                default -> throw new AssertionError(fault);
            }
            assertNull(pair(node), fault);
        }
    }

    @Test
    void sameInputExtensionAndUnrelatedMethodEditsPreserveTheLocalPair() throws Exception {
        var node = read(SilentGearSetShape.OWNER);
        var pair = pair(node);
        var method = node.methods.stream().filter(m -> m.name.equals("getSetPieceCount")).findFirst().orElseThrow();
        for (var producer : List.of(pair.type(), pair.trait())) {
            var chain = new InsnList();
            chain.add(new VarInsnNode(ALOAD, 0));
            chain.add(new InsnNode(SWAP));
            for (int local : List.of(1, 2, 3, 6, 7)) { chain.add(new VarInsnNode(ALOAD, local)); }
            chain.add(new MethodInsnNode(INVOKESPECIAL, node.name, "peer", SilentGearSetShape.CALLBACK, false));
            method.instructions.insert(producer, chain);
        }
        node.methods.forEach(m -> m.instructions.insert(new InsnNode(NOP)));
        assertNotNull(pair(node));
    }

    @Test
    void missingCallbacksAndUnexpectedReachableWritesRefuseIndependently() throws Exception {
        var armor = read(SilentGearBrilliantShape.OWNER);
        armor.methods.removeIf(m -> m.name.equals("makesPiglinsNeutral"));
        assertFalse(SilentGearBrilliantShape.ready(armor));
        var wielder = read(SilentGearSetShape.OWNER);
        var tick = wielder.methods.stream().filter(m -> m.name.equals("inventoryTick")).findFirst().orElseThrow();
        tick.instructions.insert(new MethodInsnNode(INVOKESTATIC, "foreign/Actor", "write", "()V", false));
        assertFalse(SilentGearWielderShape.effects(wielder));
        assertTrue(SilentGearBrilliantShape.ready(read(SilentGearBrilliantShape.OWNER)));
        var potion = read(SilentGearWielderShape.DATA);
        potion.methods.removeIf(m -> m.name.equals("getEffectLevel"));
        assertFalse(SilentGearWielderShape.potion(potion));
    }

    private static SilentGearSetShape.Pair pair(ClassNode node) {
        var method = node.methods.stream().filter(m -> m.name.equals("getSetPieceCount")).findFirst().orElseThrow();
        return SilentGearSetShape.pair(method.desc, method.instructions, false, "local$type", "local$trait");
    }

    private static ClassNode read(String type) throws Exception {
        try (var jar = new ZipFile(System.getProperty("mismatchedleggings.test.sgLiveJar"));
             var input = jar.getInputStream(jar.getEntry(type + ".class"))) {
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
}
