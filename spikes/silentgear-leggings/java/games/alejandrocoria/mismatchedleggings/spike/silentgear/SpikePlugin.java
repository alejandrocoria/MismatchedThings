package games.alejandrocoria.mismatchedleggings.spike.silentgear;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SpikePlugin implements IMixinConfigPlugin, IExtension, Opcodes {
    public static String actualOrder;

    @Override
    public void onLoad(String name) {
        if (Boolean.getBoolean("leggings.test.sgSetProof")) {
            ((Extensions) ((IMixinTransformer) MixinEnvironment.getCurrentEnvironment().getActiveTransformer()).getExtensions()).add(this);
        }
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) { return Boolean.getBoolean("leggings.test.sgSetProof"); }
    @Override public boolean checkActive(MixinEnvironment environment) { return true; }

    @Override
    public void preApply(ITargetClassContext context) {
        if (!context.getClassNode().name.equals(SetShape.OWNER)) { return; }
        try {
            var field = context.getClass().getDeclaredField("mixins");
            field.setAccessible(true);
            int legs = -1;
            int boots = -1;
            int index = 0;
            var names = new ArrayList<String>();
            for (var entry : (Iterable<?>) field.get(context)) {
                var info = (IMixinInfo) entry;
                names.add(info.getClassName() + "@" + info.getPriority());
                if (info.getClassName().equals(getClass().getPackageName() + ".mixin.SetMixin")) { legs = index; }
                if (info.getClassName().endsWith(".SilentGearSetMixin")) { boots = index; }
                index++;
            }
            boolean before = System.getProperty("leggings.test.sgSetProofOrder").equals("before");
            if (legs < 0 || Boolean.getBoolean("leggings.test.boots") && (boots < 0 || (legs < boots) != before)) {
                throw new AssertionError("Unexpected actual set order " + names);
            }
            actualOrder = names.toString();
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        String mode = System.getProperty("leggings.test.sgSetProofMode");
        if (mode.equals("refuse")) {
            // Runtime-identical opaque callback: refuse our final proof, not Boots' set slice.
            for (var method : node.methods) {
                if (method.name.contains("leggings$sgSetProofTrait")) {
                    for (var instruction : method.instructions) {
                        if (instruction instanceof MethodInsnNode call && call.owner.equals(SetShape.CANDIDATE) && call.name.equals("recognizes")) {
                            call.name = "unrecognized";
                        }
                    }
                }
            }
        } else if (mode.equals("compatible")) {
            var method = new MethodNode(ACC_PRIVATE, "spike$peer", SetShape.CALLBACK, null, null);
            method.instructions.add(new VarInsnNode(ILOAD, 1));
            method.instructions.add(new InsnNode(IRETURN));
            method.maxLocals = 7;
            method.maxStack = 1;
            node.methods.add(method);
            var count = node.methods.stream().filter(m -> m.name.equals("getSetPieceCount")).findFirst().orElseThrow();
            var pair = SetShape.pair(count.desc, count.instructions, false);
            if (pair == null) { throw new AssertionError("Missing compatible-peer target"); }
            for (var producer : List.of(pair.type(), pair.trait())) {
                var chain = new InsnList();
                chain.add(new VarInsnNode(ALOAD, 0));
                chain.add(new InsnNode(SWAP));
                for (int slot : List.of(1, 2, 3, 6, 7)) { chain.add(new VarInsnNode(ALOAD, slot)); }
                chain.add(new MethodInsnNode(INVOKESPECIAL, node.name, method.name, method.desc, false));
                count.instructions.insert(producer, chain);
            }
        }
    }

    @Override
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (!node.name.equals(SetShape.OWNER)) { return; }
        boolean ready = !System.getProperty("leggings.test.sgSetProofMode").equals("baseline") && SetShape.finalGraph(node);
        var getter = node.methods.stream().filter(m -> m.name.equals("leggings$sgSetProofReady")).findFirst().orElseThrow();
        getter.instructions.clear();
        getter.instructions.add(new InsnNode(ready ? ICONST_1 : ICONST_0));
        getter.instructions.add(new InsnNode(IRETURN));
    }

    @Override public void postApply(ITargetClassContext context) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
