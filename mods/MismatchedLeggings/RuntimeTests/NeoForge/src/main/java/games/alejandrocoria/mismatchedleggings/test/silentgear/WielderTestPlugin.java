package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearSetShape;
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

/** Real packaged transformation order and controlled local faults; no research recognition/dispatch. */
public final class WielderTestPlugin implements IMixinConfigPlugin, IExtension, Opcodes {
    public static String actualOrder;

    private static boolean enabled() {
        return Boolean.getBoolean("leggings.test.sgFoundation") && !Boolean.getBoolean("leggings.test.sgFoundationAbsent")
                && !System.getProperty("leggings.test.sgWielderVariant", "").isEmpty();
    }

    @Override public void onLoad(String name) {
        if (enabled()) {
            ((Extensions) ((IMixinTransformer) MixinEnvironment.getCurrentEnvironment().getActiveTransformer()).getExtensions()).add(this);
        }
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return enabled() && (!mixin.endsWith("WielderDataBoundaryMixin") || System.getProperty("leggings.test.sgWielderVariant", "").equals("potion"));
    }
    @Override public boolean checkActive(MixinEnvironment environment) { return true; }

    @Override public void preApply(ITargetClassContext context) {
        if (!context.getClassNode().name.equals(SilentGearSetShape.OWNER)) { return; }
        try {
            var field = context.getClass().getDeclaredField("mixins");
            field.setAccessible(true);
            int legs = -1, boots = -1, index = 0;
            var names = new ArrayList<String>();
            for (var entry : (Iterable<?>) field.get(context)) {
                var info = (IMixinInfo) entry;
                names.add(info.getClassName() + "@" + info.getPriority());
                if (info.getClassName().equals("games.alejandrocoria.mismatchedleggings.mixin.compatibility.silentgear.SilentGearSetMixin")) { legs = index; }
                if (info.getClassName().equals("games.alejandrocoria.mismatchedboots.mixin.compatibility.silentgear.SilentGearSetMixin")) { boots = index; }
                index++;
            }
            String order = System.getProperty("leggings.test.sgEffectsOrder", "default");
            if (legs < 0 || Boolean.getBoolean("leggings.test.boots") && (boots < 0
                    || !order.equals("default") && (legs < boots) != order.equals("before"))) {
                throw new AssertionError("Unexpected actual set application order " + names);
            }
            actualOrder = names.toString();
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @Override public void postApply(ITargetClassContext context) {
        var node = context.getClassNode();
        String variant = System.getProperty("leggings.test.sgWielderVariant", "");
        if (node.name.equals(SilentGearSetShape.OWNER + "$PotionData") && variant.equals("potion")) {
            node.methods.stream().filter(m -> m.name.equals("getEffect")).findFirst().orElseThrow().instructions.insert(
                    new MethodInsnNode(INVOKESTATIC, "games/alejandrocoria/mismatchedleggings/test/silentgear/WielderChecks", "callbackAlias", "()V", false));
        }
        if (!node.name.equals(SilentGearSetShape.OWNER)) { return; }
        for (var method : node.methods) {
            if (method.name.contains(variant.equals("type") ? "mismatchedleggings$sgSetType" : "mismatchedleggings$sgSetTrait")
                    && List.of("type", "trait").contains(variant)) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.name.equals("recognizes")) {
                        call.owner = "games/alejandrocoria/mismatchedleggings/test/silentgear/WielderChecks";
                        call.name = "recognizesAlias";
                    }
                }
            }
            if (variant.equals("callback") && method.name.equals("inventoryTick")) {
                // Runtime-identical extra call violates the audited reachable native callback surface.
                method.instructions.insert(new MethodInsnNode(INVOKESTATIC,
                        "games/alejandrocoria/mismatchedleggings/test/silentgear/WielderChecks", "callbackAlias", "()V", false));
            }
        }
        if (variant.equals("compatible")) {
            var peer = new MethodNode(ACC_PRIVATE, "sgtest$peer", SilentGearSetShape.CALLBACK, null, null);
            peer.instructions.add(new VarInsnNode(ILOAD, 1));
            peer.instructions.add(new InsnNode(IRETURN));
            peer.maxLocals = 7;
            peer.maxStack = 1;
            node.methods.add(peer);
            var count = node.methods.stream().filter(m -> m.name.equals("getSetPieceCount")).findFirst().orElseThrow();
            var pair = SilentGearSetShape.pair(count.desc, count.instructions, false, "mismatchedleggings$sgSetType", "mismatchedleggings$sgSetTrait");
            if (pair == null) { throw new AssertionError("Missing compatible native set slice"); }
            for (var producer : List.of(pair.type(), pair.trait())) {
                var chain = new InsnList();
                chain.add(new VarInsnNode(ALOAD, 0));
                chain.add(new InsnNode(SWAP));
                for (int local : List.of(1, 2, 3, 6, 7)) { chain.add(new VarInsnNode(ALOAD, local)); }
                chain.add(new MethodInsnNode(INVOKESPECIAL, node.name, peer.name, peer.desc, false));
                count.instructions.insert(producer, chain);
            }
        }
    }

    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
}
