package games.alejandrocoria.mismatchedleggings.test.iceandfire;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import java.util.List;
import java.util.Set;

/** Late mutations, after normal injectors: admission alone must never authorize recognition. */
public final class TideControlPlugin implements IMixinConfigPlugin, IExtension {
    public static String actualOrder;

    @Override public void preApply(ITargetClassContext context) {
        if (!context.getClassNode().name.equals("com/iafenvoy/iceandfire/item/armor/SeaSerpentArmorItem")) { return; }
        try {
            // Inspect the actual sorted application set, including the empty production marker.
            var field = context.getClass().getDeclaredField("mixins");
            field.setAccessible(true);
            var applied = (Iterable<?>) field.get(context);
            int legs = -1, boots = -1, index = 0;
            var names = new java.util.ArrayList<String>();
            for (var candidate : applied) {
                var info = (IMixinInfo) candidate;
                names.add(info.getClassName() + "@" + info.getPriority());
                if (info.getClassName().endsWith(".IceAndFireTideLegsMixin")) {
                    legs = index;
                    int expected = System.getProperty("leggings.test.iafTideOrder").equals("before") ? 900 : 1100;
                    if (info.getPriority() != expected) { throw new AssertionError("Stale production mixin priority"); }
                }
                if (info.getClassName().endsWith(".IceAndFireTideFeetMixin") && info.getClassName().contains("mismatchedboots")) { boots = index; }
                index++;
            }
            if (legs < 0 || Boolean.getBoolean("leggings.test.boots") && (boots < 0
                    || (legs < boots) != System.getProperty("leggings.test.iafTideOrder").equals("before"))) {
                throw new AssertionError("Unexpected actual packaged mixin order: " + names + "; legs=" + legs + "; boots=" + boots);
            }
            actualOrder = "LEGS index=" + legs + "; FEET index=" + boots;
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot verify actual application order", failure); }
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return Boolean.getBoolean("leggings.test.iafTide")
                && !System.getProperty("leggings.test.iafTideVariant").equals("absent");
    }

    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        String variant = System.getProperty("leggings.test.iafTideVariant", "normal");
        for (var method : node.methods) {
            if (method.name.equals("inventoryTick") && (variant.equals("entry") && node.name.endsWith("/SeaSerpentArmorItem") || variant.equals("base") && node.name.equals("net/minecraft/world/item/Item"))) {
                method.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "games/alejandrocoria/mismatchedleggings/test/iceandfire/TideControlPlugin", "observe", "()V", false));
            }
            if (variant.equals("count") && method.name.contains("mismatchedleggings$tideLegsResult")) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode field && field.name.equals("LEGS")) { field.name = "HEAD"; }
                }
            }
            if (variant.equals("publication") && method.name.equals("mismatchedleggings$tideEntryReady")) {
                method.access |= Opcodes.ACC_FINAL;
                for (var instruction : method.instructions.toArray()) {
                    if (instruction.getOpcode() == Opcodes.ICONST_0) { method.instructions.set(instruction, new InsnNode(Opcodes.ICONST_1)); }
                }
            }
        }
        if (variant.equals("compatible") && node.name.endsWith("/SeaSerpentArmorItem") && !node.methods.stream().anyMatch(m -> m.name.equals("fixture$result"))) {
            var handler = new org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PRIVATE, "fixture$result", "(Z)Z", null, null);
            handler.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
            handler.instructions.add(new InsnNode(Opcodes.IRETURN));
            handler.maxStack = 1; handler.maxLocals = 2;
            node.methods.add(handler);
            var tick = node.methods.stream().filter(m -> m.name.equals("inventoryTick")).findFirst().orElseThrow();
            for (var instruction : tick.instructions.toArray()) {
                if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF && type.desc.equals(node.name)) {
                    var chain = new InsnList();
                    chain.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    chain.add(new InsnNode(Opcodes.SWAP));
                    chain.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, handler.name, handler.desc, false));
                    tick.instructions.insert(instruction, chain);
                }
            }
        }
    }

    public static void observe() {}
    @Override public void onLoad(String name) {
        if (!Boolean.getBoolean("leggings.test.iafTide")) { return; }
        var transformer = (IMixinTransformer) MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        ((Extensions) transformer.getExtensions()).add(this);
    }
    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void postApply(ITargetClassContext context) {}
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
