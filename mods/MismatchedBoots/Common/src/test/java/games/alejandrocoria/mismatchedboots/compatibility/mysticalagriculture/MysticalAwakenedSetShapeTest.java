package games.alejandrocoria.mismatchedboots.compatibility.mysticalagriculture;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MysticalAwakenedSetShapeTest implements Opcodes {
    @Test
    void pinnedNativeComparisonsAndCompatibleOtherSlotEditsAreAcceptedOnce() throws Exception {
        for (String loader : List.of("Fabric", "NeoForge")) {
            for (boolean peerFirst : List.of(false, true)) {
                var node = nativeClass(loader);
                var method = predicate(node);
                method.instructions.insert(new MethodInsnNode(INVOKESTATIC, "fixture/Observer", "note", "()V", false));
                method.instructions.insert(new InsnNode(NOP));
                var unrelated = new InsnList();
                unrelated.add(new FieldInsnNode(GETSTATIC, "net/minecraft/world/entity/EquipmentSlot", "FEET", "Lnet/minecraft/world/entity/EquipmentSlot;"));
                unrelated.add(new InsnNode(POP)); method.instructions.insert(unrelated);
                if (peerFirst) { peer(method); }
                assertTrue(MysticalAwakenedSetShape.apply(node), loader);
                if (!peerFirst) { peer(method); }
                assertEquals(1, java.util.Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof MethodInsnNode c
                        && c.owner.equals("games/alejandrocoria/mismatchedboots/compatibility/mysticalagriculture/MysticalAwakenedSetCompatibility")).count());
                assertFalse(MysticalAwakenedSetShape.apply(node), "must not insert twice");
            }
        }
    }

    @Test
    void fabricHolderSpecializationIsAccepted() throws Exception {
        var node = nativeClass("Fabric");
        var method = predicate(node);
        var field = slotItem(method);
        method.instructions.insert(field, new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/world/item/Item",
                "builtInRegistryHolder", "()Lnet/minecraft/core/Holder$Reference;", false));
        ((MethodInsnNode) next(next(field))).desc = "(Lnet/minecraft/core/Holder;)Z";
        assertTrue(MysticalAwakenedSetShape.apply(node));
    }

    @Test
    void incompatibleOrAmbiguousLocalBoundariesRefuseWithoutMutation() throws Exception {
        for (String loader : List.of("Fabric", "NeoForge")) {
            for (String fault : List.of("slot", "receiver", "player", "item", "comparison", "consumer", "ambiguous", "duplicate-method", "reassigned-result")) {
                var node = nativeClass(loader);
                var method = predicate(node);
                var item = slotItem(method);
                var slot = java.util.Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof FieldInsnNode f
                        && f.name.equals("FEET")).map(n -> (FieldInsnNode) n).findFirst().orElseThrow();
                var compare = next(item);
                while (!(compare instanceof MethodInsnNode c && c.name.equals("is"))) { compare = next(compare); }
                var store = (VarInsnNode) next(compare);
                switch (fault) {
                    case "slot" -> slot.name = "HEAD";
                    case "receiver" -> ((MethodInsnNode) next(slot)).owner = "fixture/Other";
                    case "player" -> {
                        method.instructions.insert(new VarInsnNode(ASTORE, 0));
                        method.instructions.insert(new InsnNode(ACONST_NULL));
                    }
                    case "item" -> item.name = "AWAKENED_SUPREMIUM_HELMET";
                    case "comparison" -> ((MethodInsnNode) compare).name = "opaque";
                    case "consumer" -> {
                        for (var n : method.instructions) {
                            if (n instanceof VarInsnNode v && v.getOpcode() == ILOAD && v.var == store.var) {
                                ((JumpInsnNode) next(v)).setOpcode(IFNE); break;
                            }
                        }
                    }
                    case "ambiguous" -> {
                        var extra = new InsnList();
                        for (var n = slot.getPrevious(); n != compare.getNext(); n = n.getNext()) {
                            if (n.getOpcode() >= 0) { extra.add(n.clone(new java.util.HashMap<>())); }
                        }
                        for (var n : method.instructions) {
                            if (n instanceof VarInsnNode v && v.getOpcode() == ILOAD && v.var == store.var) {
                                extra.add(new JumpInsnNode(IFEQ, ((JumpInsnNode) next(v)).label));
                                method.instructions.insertBefore(v, extra); break;
                            }
                        }
                    }
                    case "duplicate-method" -> node.methods.add(new MethodNode(ACC_PRIVATE | ACC_STATIC, method.name, method.desc, null, null));
                    case "reassigned-result" -> {
                        var extra = new InsnList(); extra.add(new InsnNode(ICONST_1)); extra.add(new VarInsnNode(ISTORE, store.var));
                        method.instructions.insert(store, extra);
                    }
                }
                byte[] before = bytes(node);
                assertFalse(MysticalAwakenedSetShape.apply(node), loader + "/" + fault);
                assertArrayEquals(before, bytes(node), "refusal must leave native code intact");
            }
        }
    }

    private static void peer(MethodNode method) {
        // An independent HEAD extension can run before or after our own edit.
        for (var n : method.instructions.toArray()) {
            if (n instanceof MethodInsnNode c && c.name.equals("is")) {
                method.instructions.insert(c, new MethodInsnNode(INVOKESTATIC, "fixture/HeadAdapter", "recognize", "(Z)Z", false));
                return;
            }
        }
        fail("native HEAD comparison absent");
    }
    private static ClassNode nativeClass(String loader) throws Exception {
        var file = Path.of(System.getProperty("mystical.set.artifact." + loader));
        try (var jar = new JarFile(file.toFile())) {
            var node = new ClassNode();
            new ClassReader(jar.getInputStream(jar.getJarEntry(MysticalAwakenedSetShape.OWNER + ".class"))).accept(node, 0);
            return node;
        }
    }
    private static MethodNode predicate(ClassNode node) {
        return node.methods.stream().filter(m -> m.name.equals("hasAwakenedSupremiumSet")).findFirst().orElseThrow();
    }
    private static FieldInsnNode slotItem(MethodNode method) {
        return java.util.Arrays.stream(method.instructions.toArray()).filter(n -> n instanceof FieldInsnNode f
                && f.name.equals("AWAKENED_SUPREMIUM_BOOTS")).map(n -> (FieldInsnNode) n).findFirst().orElseThrow();
    }
    private static AbstractInsnNode next(AbstractInsnNode n) {
        do { n = n.getNext(); } while (n != null && n.getOpcode() < 0);
        return n;
    }
    private static byte[] bytes(ClassNode node) {
        var writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray();
    }
}
