package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilentGearLiveShapeTest implements Opcodes {
    @Test
    void actualPinnedCallbacksAndHarmlessInstructionsAreAdmitted() throws Exception {
        for (String type : List.of(SilentGearLiveShape.OXIDATION, SilentGearLiveShape.NUMBER, SilentGearLiveShape.MATH)) {
            var node = read(type);
            assertTrue(SilentGearLiveShape.ready(node), type);
            node.methods.forEach(method -> method.instructions.insert(new InsnNode(NOP)));
            assertTrue(SilentGearLiveShape.ready(node), type);
        }
    }

    @Test
    void changedNativeCallSurfaceAndNumberMathAreRefused() throws Exception {
        var oxidation = read(SilentGearLiveShape.OXIDATION);
        var tick = oxidation.methods.stream().filter(method -> method.name.equals("inventoryTick")).findFirst().orElseThrow();
        tick.instructions.insert(new MethodInsnNode(INVOKESTATIC, "test/Foreign", "write", "()V", false));
        assertFalse(SilentGearLiveShape.ready(oxidation));
        var math = read(SilentGearLiveShape.MATH);
        var method = math.methods.stream().filter(value -> value.name.equals("getAddedValue")).findFirst().orElseThrow();
        for (var instruction : method.instructions) {
            if (instruction.getOpcode() == FMUL) { method.instructions.set(instruction, new InsnNode(FDIV)); break; }
        }
        assertFalse(SilentGearLiveShape.ready(math));
    }

    private static ClassNode read(String type) throws Exception {
        try (var jar = new ZipFile(System.getProperty("mismatchedleggings.test.sgLiveJar"));
             var input = jar.getInputStream(jar.getEntry(type + ".class"))) {
            var result = new ClassNode();
            new ClassReader(input).accept(result, 0);
            return result;
        }
    }
}
