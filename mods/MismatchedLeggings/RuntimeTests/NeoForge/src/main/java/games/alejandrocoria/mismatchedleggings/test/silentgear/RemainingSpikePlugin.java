package games.alejandrocoria.mismatchedleggings.test.silentgear;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
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

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.NOP;

/** Opt-in only. Derives the candidate's own final route permission and records actual sibling order. */
public final class RemainingSpikePlugin implements IMixinConfigPlugin, IExtension {
    private static final String EVENTS = "net/silentchaos512/gear/event/GearEvents";
    private static final String PRE = "Lnet/neoforged/neoforge/event/tick/PlayerTickEvent$Pre;";
    static String actualOrder = "";

    private static boolean enabled() { return Boolean.getBoolean("leggings.test.sgRemainingSpike"); }

    @Override public void onLoad(String name) {
        if (enabled() && MixinEnvironment.getCurrentEnvironment().getActiveTransformer() instanceof IMixinTransformer mixin
                && mixin.getExtensions() instanceof Extensions extensions) { extensions.add(this); }
    }

    @Override public List<String> getMixins() {
        return enabled() ? List.of("RemainingVoidMixin", "RemainingTickMixin", "RemainingRescueWitnessMixin", "RemainingRemovalWitnessMixin") : List.of();
    }

    @Override public void preApply(ITargetClassContext context) {
        if (!context.getClassNode().name.equals(EVENTS)) { return; }
        try {
            var field = context.getClass().getDeclaredField("mixins");
            field.setAccessible(true);
            var names = new ArrayList<String>();
            for (var entry : (Iterable<?>) field.get(context)) { names.add(((IMixinInfo) entry).getClassName()); }
            int own = names.indexOf("games.alejandrocoria.mismatchedleggings.test.silentgear.remaining.RemainingVoidMixin");
            int boots = names.indexOf("games.alejandrocoria.mismatchedboots.mixin.compatibility.silentgear.SilentGearVoidWardMixin");
            if (own < 0 || Boolean.getBoolean("leggings.test.boots") && (boots < 0
                    || (own < boots) != System.getProperty("leggings.test.sgRemainingOrder").equals("before"))) {
                throw new AssertionError("Unexpected actual remaining-effects order: " + names);
            }
            actualOrder = names.toString();
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode owner) {
        if (!owner.name.equals(EVENTS)) { return; }
        var method = owner.methods.stream().filter(m -> m.name.equals("onPlayerTick") && m.desc.equals("(" + PRE + ")V")).findFirst().orElseThrow();
        var code = code(method);
        boolean valid = false;
        int own = 0;
        for (int i = 2; i < code.size(); i++) {
            if (!(code.get(i) instanceof MethodInsnNode query) || !query.owner.equals("net/silentchaos512/gear/util/TraitHelper")
                    || !query.name.equals("hasTraitArmor") || !(code.get(i - 1) instanceof FieldInsnNode field)
                    || field.getOpcode() != GETSTATIC || !field.owner.equals("net/silentchaos512/gear/util/Const$Traits") || !field.name.equals("VOID_WARD")
                    || !(code.get(i - 2) instanceof VarInsnNode player) || player.getOpcode() != ALOAD) { continue; }
            int cursor = i + 1;
            while (cursor + 1 < code.size() && load(code.get(cursor), ALOAD, 0)
                    && code.get(cursor + 1) instanceof MethodInsnNode hook && hook.owner.equals(EVENTS)
                    && hook.getOpcode() == INVOKESTATIC && hook.desc.equals("(Z" + PRE + ")Z")) {
                if (hook.name.contains("mismatchedleggings$remainingVoid")) {
                    var route = owner.methods.stream().filter(m -> m.name.equals(hook.name)).findFirst().orElseThrow();
                    var body = code(route);
                    valid = body.size() == 5 && load(body.get(0), ILOAD, 0) && load(body.get(1), ALOAD, 1)
                            && body.get(2) instanceof MethodInsnNode ready && ready.name.equals("mismatchedleggings$remainingReady")
                            && body.get(3) instanceof MethodInsnNode target && target.owner.equals("games/alejandrocoria/mismatchedleggings/test/silentgear/SilentGearRemainingCandidate")
                            && target.name.equals("voidWard") && body.get(4).getOpcode() == IRETURN;
                    own++;
                }
                cursor += 2;
            }
            valid &= cursor < code.size() && code.get(cursor) instanceof JumpInsnNode branch && branch.getOpcode() == IFEQ;
        }
        var getter = owner.methods.stream().filter(m -> m.name.equals("mismatchedleggings$remainingReady")).findFirst().orElseThrow();
        getter.instructions.clear();
        getter.instructions.add(new InsnNode(valid && own == 1 ? ICONST_1 : ICONST_0));
        getter.instructions.add(new InsnNode(IRETURN));
    }

    private static List<org.objectweb.asm.tree.AbstractInsnNode> code(MethodNode method) {
        var result = new ArrayList<org.objectweb.asm.tree.AbstractInsnNode>();
        for (var instruction : method.instructions) { if (instruction.getOpcode() >= 0 && instruction.getOpcode() != NOP) { result.add(instruction); } }
        return result;
    }
    private static boolean load(org.objectweb.asm.tree.AbstractInsnNode node, int opcode, int index) {
        return node instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == index;
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return enabled(); }
    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void postApply(ITargetClassContext context) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
}
