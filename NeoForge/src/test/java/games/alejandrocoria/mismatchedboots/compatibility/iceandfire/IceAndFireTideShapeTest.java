package games.alejandrocoria.mismatchedboots.compatibility.iceandfire;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class IceAndFireTideShapeTest implements Opcodes {
    @Test
    void emptyBaseAndUntouchedGettersAreRequiredForPublication() {
        ClassNode base = new ClassNode();
        base.name = IceAndFireTideShape.ITEM;
        MethodNode tick = new MethodNode(ACC_PUBLIC, "inventoryTick", IceAndFireTideShape.TICK, null, null);
        tick.instructions.add(new InsnNode(RETURN));
        base.methods.add(tick);
        MethodNode getter = new MethodNode(ACC_PUBLIC, "mismatchedboots$tideBaseReady", "()Z", null, null);
        getter.instructions.add(new InsnNode(ICONST_0));
        getter.instructions.add(new InsnNode(IRETURN));
        base.methods.add(getter);
        assertNull(IceAndFireTideShape.baseFailure(base));
        assertTrue(IceAndFireTideShape.prepareGetter(base, getter.name));
        assertTrue(IceAndFireTideShape.publish(base, getter.name));
        assertFalse(IceAndFireTideShape.prepareGetter(base, getter.name));
        assertEquals(ICONST_0, IceAndFireTideShape.code(getter).getFirst().getOpcode(), "Altered publication must be reset on rejection");
        assertTrue(IceAndFireTideShape.publish(base, getter.name));
        getter.access |= ACC_FINAL;
        assertFalse(IceAndFireTideShape.prepareGetter(base, getter.name));
        assertEquals(ICONST_0, IceAndFireTideShape.code(getter).getFirst().getOpcode(), "Altered getter access must not retain live true permission");
        tick.instructions.insert(new VarInsnNode(ALOAD, 1));
        assertNotNull(IceAndFireTideShape.baseFailure(base), "A carrier-reading base cannot grant dispatch permission");
        base.methods.remove(getter);
        assertFalse(IceAndFireTideShape.publish(base, getter.name), "Missing final-checkpoint getter stays inert");
    }

    @Test
    void pinnedConsumerRequiresActualCompiledOrHookAndCarrierProvenance() throws Exception {
        ClassNode nativeEntry = nativeEntry();
        assertNotNull(IceAndFireTideShape.entryFailure(nativeEntry), "Counting alone is not installed recognition");
        ClassNode accepted = withHook(copy(nativeEntry));
        assertNull(IceAndFireTideShape.entryFailure(accepted));
        ClassNode changed = copy(accepted);
        for (var node : entry(changed).instructions) {
            if (node instanceof IntInsnNode constant && constant.operand == 50) {
                constant.operand = 75;
            }
            if (node instanceof VarInsnNode local && local.var >= 5) {
                local.var += 13;
            }
        }
        assertNull(IceAndFireTideShape.entryFailure(changed), "Balance constants and local numbering are not identity fingerprints");
        changed = copy(accepted);
        entry(changed).instructions.insert(new VarInsnNode(ALOAD, 1));
        assertNotNull(IceAndFireTideShape.entryFailure(changed), "Unknown carrier reads cannot be certified");
        changed = copy(accepted);
        MethodNode hook = changed.methods.stream().filter(m -> m.name.equals("mismatchedboots$tideFeetResult")).findFirst().orElseThrow();
        for (var node : hook.instructions) {
            if (node instanceof JumpInsnNode jump && jump.getOpcode() == IFNE) {
                jump.setOpcode(IFEQ);
                break;
            }
        }
        assertNotNull(IceAndFireTideShape.entryFailure(changed), "Recognition call without native-positive OR semantics is insufficient");
        changed = copy(accepted);
        entry(changed).instructions.insertBefore(entry(changed).instructions.getLast(),
                                                 new MethodInsnNode(INVOKESTATIC, "fixture/ExtraWork", "run", "()V", false));
        assertNotNull(IceAndFireTideShape.entryFailure(changed), "Unfamiliar native entry work must fail closed");
    }

    @Test
    void missingChangedAmbiguousFeetOperationCannotGrantReadiness() throws Exception {
        for (String mutation : new String[]{"missing", "slot", "type", "duplicate"}) {
            ClassNode node = withHook(nativeEntry());
            MethodNode entry = entry(node);
            FieldInsnNode feet = null;
            for (var instruction : entry.instructions) {
                if (instruction instanceof FieldInsnNode field && field.name.equals("FEET")) {
                    feet = field;
                    break;
                }
            }
            assertNotNull(feet);
            AbstractInsnNode predicate = feet;
            while (!(predicate instanceof TypeInsnNode)) {
                predicate = predicate.getNext();
            }
            switch (mutation) {
                case "missing" -> entry.instructions.remove(predicate);
                case "slot" -> feet.name = "LEGS";
                case "type" -> ((TypeInsnNode) predicate).desc = IceAndFireTideShape.ITEM;
                case "duplicate" -> entry.instructions.insert(predicate, new TypeInsnNode(INSTANCEOF, IceAndFireTideShape.ARMOR));
            }
            assertNotNull(IceAndFireTideShape.entryFailure(node), mutation);
        }
    }

    @Test
    void productionCheckpointsKeepMissingVerificationPublicationAndHooksInert() throws Exception {
        ClassNode base = new ClassNode();
        base.name = IceAndFireTideShape.ITEM;
        MethodNode tick = new MethodNode(ACC_PUBLIC, "inventoryTick", IceAndFireTideShape.TICK, null, null);
        tick.instructions.add(new InsnNode(RETURN));
        base.methods.add(tick);
        base.methods.add(compiledGetter("IceAndFireTideBaseMixin", "mismatchedboots$tideBaseReady"));
        ClassNode armor = withHook(nativeEntry());
        armor.methods.add(compiledGetter("IceAndFireTideEntryMixin", "mismatchedboots$tideEntryReady"));
        var plugin = new IceAndFireTideMixinPlugin();

        // The actual compiled getters cannot grant permission if registration/export never happens.
        assertReadiness(base, "mismatchedboots$tideBaseReady", false);
        assertReadiness(armor, "mismatchedboots$tideEntryReady", false);
        plugin.export(null, base.name, false, base);
        assertReadiness(base, "mismatchedboots$tideBaseReady", true);
        assertReadiness(armor, "mismatchedboots$tideEntryReady", false, "A sibling/base checkpoint is not entry publication");
        plugin.export(null, armor.name, false, armor);
        assertReadiness(armor, "mismatchedboots$tideEntryReady", true);

        ClassNode missingHook = nativeEntry();
        missingHook.methods.add(compiledGetter("IceAndFireTideEntryMixin", "mismatchedboots$tideEntryReady"));
        plugin.export(null, missingHook.name, false, missingHook);
        assertReadiness(missingHook, "mismatchedboots$tideEntryReady", false, "Native tick without our hook stays inert");
        armor.methods.removeIf(method -> method.name.equals("mismatchedboots$tideEntryReady"));
        plugin.export(null, armor.name, false, armor);
        assertNull(IceAndFireTideShape.method(armor, "mismatchedboots$tideEntryReady", "()Z"),
                   "A missing getter must not be recreated or grant permission");
    }

    @Test
    void productionGuardRejectsUnsupportedConsumerProvenanceAndSemantics() throws Exception {
        ClassNode accepted = withHook(nativeEntry());
        for (String mutation : new String[]{"hierarchy", "entryAccess", "field", "entity", "count",
                                            "effect", "wetness", "carrierAlias", "carrierMutation", "ownedSlot"}) {
            ClassNode node = copy(accepted);
            MethodNode tick = entry(node);
            switch (mutation) {
                case "hierarchy" -> node.superName = "java/lang/Object";
                case "entryAccess" -> tick.access = ACC_PRIVATE;
                case "field" -> node.fields.removeIf(field -> field.name.equals("type"));
                case "entity" -> {
                    for (var instruction : tick.instructions) {
                        if (instruction instanceof VarInsnNode load && load.var == 3
                                && load.getNext() instanceof TypeInsnNode) {
                            load.var = 1;
                            break;
                        }
                    }
                }
                case "count" -> {
                    for (var instruction : tick.instructions) {
                        if (instruction.getOpcode() == IADD) {
                            tick.instructions.set(instruction, new InsnNode(ISUB));
                            break;
                        }
                    }
                }
                case "effect" -> {
                    for (var instruction : tick.instructions) {
                        if (instruction instanceof FieldInsnNode field && field.name.equals("STRENGTH")) {
                            field.name = "SPEED";
                        }
                    }
                }
                case "wetness" -> {
                    for (var instruction : tick.instructions) {
                        if (instruction instanceof MethodInsnNode call && call.name.equals("isInWaterOrRain")) {
                            call.name = "isInWater";
                        }
                    }
                }
                case "carrierAlias" -> {
                    InsnList alias = new InsnList();
                    alias.add(new VarInsnNode(ALOAD, 1));
                    alias.add(new VarInsnNode(ASTORE, 19));
                    tick.instructions.insert(alias);
                }
                case "carrierMutation" -> {
                    InsnList write = new InsnList();
                    write.add(new VarInsnNode(ALOAD, 1));
                    write.add(new InsnNode(ICONST_0));
                    write.add(new MethodInsnNode(INVOKEVIRTUAL, IceAndFireTideShape.STACK, "setCount", "(I)V", false));
                    tick.instructions.insert(write);
                }
                case "ownedSlot" -> {
                    MethodNode handler = IceAndFireTideShape.method(node, "mismatchedboots$tideFeetResult",
                                                                    "(ZLnet/minecraft/world/entity/Entity;)Z");
                    for (var instruction : handler.instructions) {
                        if (instruction instanceof FieldInsnNode field && field.name.equals("FEET")) {
                            field.name = "HEAD";
                        }
                    }
                }
            }
            assertNotNull(IceAndFireTideShape.entryFailure(node), mutation);
        }

        ClassNode changed = copy(accepted);
        for (var instruction : entry(changed).instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("mismatchedboots$tideFeetResult")) {
                call.setOpcode(INVOKEVIRTUAL);
            }
        }
        assertNull(IceAndFireTideShape.entryFailure(changed), "Supported MixinExtras dispatch difference is not an incompatibility");
    }

    private static MethodNode compiledGetter(String mixinName, String getterName) throws Exception {
        ClassNode mixin = new ClassNode();
        try (var stream = IceAndFireTideShapeTest.class.getResourceAsStream(
                "/games/alejandrocoria/mismatchedboots/mixin/compatibility/iceandfire/" + mixinName + ".class")) {
            new ClassReader(stream).accept(mixin, 0);
        }
        return IceAndFireTideShape.method(mixin, getterName, "()Z");
    }

    private static void assertReadiness(ClassNode node, String getter, boolean ready, String... message) {
        MethodNode method = IceAndFireTideShape.method(node, getter, "()Z");
        assertNotNull(method);
        assertEquals(ready ? ICONST_1 : ICONST_0, IceAndFireTideShape.code(method).getFirst().getOpcode(),
                     message.length == 0 ? getter : message[0]);
    }

    private static ClassNode nativeEntry() throws Exception {
        String artifact = System.getProperty("mismatchedboots.test.iceAndFireGuardJar");
        assumeTrue(artifact != null, "Pinned native-consumer characterization is opt-in");
        try (JarFile jar = new JarFile(Path.of(artifact).toFile())) {
            return IceAndFireGuardFixture.read(jar, IceAndFireTideShape.ARMOR);
        }
    }

    private static ClassNode withHook(ClassNode nativeEntry) throws Exception {
        // Copies the real compiled production handler; not a hand-written substitute for its OR body.
        ClassNode mixin = new ClassNode();
        try (var stream = getClassResource()) {
            new ClassReader(stream).accept(mixin, 0);
        }

        MethodNode handler = mixin.methods.stream().filter(m -> m.name.equals("mismatchedboots$tideFeetResult")).findFirst().orElseThrow();
        nativeEntry.methods.add(handler);
        MethodNode entry = entry(nativeEntry);
        for (var instruction : entry.instructions) {
            if (instruction instanceof FieldInsnNode field && field.name.equals("FEET")) {
                AbstractInsnNode predicate = field;
                while (!(predicate instanceof TypeInsnNode)) {
                    predicate = predicate.getNext();
                }
                InsnList call = new InsnList();
                call.add(new VarInsnNode(ALOAD, 0));
                call.add(new InsnNode(SWAP));
                call.add(new VarInsnNode(ALOAD, 3));
                call.add(new MethodInsnNode(INVOKESPECIAL, IceAndFireTideShape.ARMOR, handler.name, handler.desc, false));
                entry.instructions.insert(predicate, call);
                return nativeEntry;
            }
        }
        throw new AssertionError("Missing pinned native FEET predicate");
    }

    private static java.io.InputStream getClassResource() {
        return IceAndFireTideShapeTest.class.getResourceAsStream(
                "/games/alejandrocoria/mismatchedboots/mixin/compatibility/iceandfire/IceAndFireTideFeetMixin.class");
    }

    private static ClassNode copy(ClassNode node) {
        ClassNode copy = new ClassNode();
        node.accept(copy);
        return copy;
    }

    private static MethodNode entry(ClassNode node) {
        return IceAndFireTideShape.method(node, "inventoryTick", IceAndFireTideShape.TICK);
    }
}
