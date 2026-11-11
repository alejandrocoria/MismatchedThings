package games.alejandrocoria.mismatchedleggings.test.silentgear;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Observes actual transformations only; never changes a production proof or readiness getter. */
public final class DefenseSpikePlugin implements IMixinConfigPlugin, IExtension {
    private static final String EVENTS = "net/silentchaos512/gear/event/GearEvents";
    static String actualOrder = "";
    static String finalFlow = "";
    static boolean routingMutation;

    @Override
    public void onLoad(String name) {
        if (System.getProperty("leggings.test.sgDefenseSpike", "").isEmpty()) { return; }
        var transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
            extensions.add(this);
        }
    }

    @Override
    public List<String> getMixins() {
        String mode = System.getProperty("leggings.test.sgDefenseSpike", "");
        var mixins = new ArrayList<String>();
        if (mode.equals("incoming") || mode.equals("full")) { mixins.add("SilentGearDefenseSpikeIncomingMixin"); }
        if (mode.equals("full")) { mixins.add("SilentGearDefenseSpikeBounceMixin"); }
        if (mode.equals("full") || mode.equals("off")) {
            mixins.addAll(List.of("SilentGearDefenseSpikeWearMixin", "SilentGearDefenseSpikeNativeWearMixin", "SilentGearDefenseSpikeWriterMixin"));
        }
        return mixins;
    }

    @Override
    public void preApply(ITargetClassContext context) {
        if (!context.getClassNode().name.equals(EVENTS)) { return; }
        try {
            var field = context.getClass().getDeclaredField("mixins");
            field.setAccessible(true);
            var names = new ArrayList<String>();
            int legs = -1;
            int boots = -1;
            int bounceLegs = -1;
            int bounceBoots = -1;
            for (var entry : (Iterable<?>) field.get(context)) {
                var info = (IMixinInfo) entry;
                if (info.getClassName().endsWith(".SilentGearDefenseSpikeIncomingMixin")) { legs = names.size(); }
                if (info.getClassName().endsWith(".SilentGearIncomingMixin")) { boots = names.size(); }
                if (info.getClassName().endsWith(".SilentGearDefenseSpikeBounceMixin")) { bounceLegs = names.size(); }
                if (info.getClassName().endsWith(".SilentGearBounceMixin")) { bounceBoots = names.size(); }
                names.add(info.getClassName() + "@" + info.getPriority());
            }
            String mode = System.getProperty("leggings.test.sgDefenseSpike");
            if (mode.equals("incoming") || mode.equals("full")) {
                boolean before = System.getProperty("leggings.test.sgDefenseOrder").equals("before");
                if (legs < 0 || Boolean.getBoolean("leggings.test.boots") && (boots < 0 || (legs < boots) != before)) {
                    throw new AssertionError("Unexpected incoming mixin order: " + names);
                }
                if (mode.equals("full") && (bounceLegs < 0 || Boolean.getBoolean("leggings.test.boots")
                        && (bounceBoots < 0 || (bounceLegs < bounceBoots) != before))) { throw new AssertionError("Unexpected Bounce order: " + names); }
            }
            actualOrder = names.toString();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    @Override
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (!node.name.equals(EVENTS)) { return; }
        var report = new StringBuilder();
        for (var method : node.methods) {
            if (!method.name.equals("onLivingIncomingDamage") && !method.name.contains("Incoming") && !method.name.contains("mixinextras$bridge")
                    && !method.name.equals("onLivingFall") && !method.name.equals("onPlayerHurt") && !method.name.contains("spikeFall") && !method.name.contains("spikeRetaliation")) { continue; }
            report.append(method.name).append(method.desc).append('\n');
            int index = 0;
            for (var insn : method.instructions) {
                if (insn.getOpcode() < 0) { continue; }
                report.append(index++).append(": ").append(insn.getOpcode());
                if (insn instanceof MethodInsnNode call) { report.append(' ').append(call.owner).append('.').append(call.name).append(call.desc); }
                if (insn instanceof TypeInsnNode type) { report.append(' ').append(type.desc); }
                if (insn instanceof VarInsnNode variable) { report.append(" local=").append(variable.var); }
                if (insn instanceof InvokeDynamicInsnNode dynamic) { report.append(' ').append(dynamic.name).append(dynamic.desc).append(' ').append(java.util.Arrays.toString(dynamic.bsmArgs)); }
                if (insn instanceof IntInsnNode number) { report.append(" value=").append(number.operand); }
                if (insn instanceof LdcInsnNode constant) { report.append(' ').append(constant.cst); }
                report.append('\n');
            }
        }
        finalFlow = report.toString();
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override
    public void postApply(ITargetClassContext context) {
        if (context.getClassNode().name.equals(EVENTS) && System.getProperty("leggings.test.sgDefenseMutation", "").equals("compatible")) {
            for (var method : context.getClassNode().methods) {
                if (List.of("onLivingIncomingDamage", "onLivingFall", "onPlayerHurt").contains(method.name)) {
                    method.instructions.insert(new InsnNode(org.objectweb.asm.Opcodes.NOP));
                }
            }
        }
        if (context.getClassNode().name.equals(EVENTS) && System.getProperty("leggings.test.sgDefenseMutation", "").equals("safety")) {
            int changed = 0;
            for (var method : context.getClassNode().methods) {
                if (!method.name.contains("mismatchedboots$sgFallGate")) { continue; }
                for (var insn : method.instructions) {
                    if (insn instanceof MethodInsnNode getter && getter.name.equals("mismatchedboots$sgSafeFallReady")) {
                        getter.name = "mismatchedboots$sgIncomingReady";
                        changed++;
                    }
                }
            }
            if (changed != 1) { throw new AssertionError("Missing unique safe-fall contract mutation"); }
        }
        if (!context.getClassNode().name.equals(EVENTS) || !System.getProperty("leggings.test.sgDefenseMutation", "").equals("routing")) { return; }
        int changed = 0;
        for (var method : context.getClassNode().methods) {
            if (!method.name.contains("mismatchedboots$sgIncomingArmor")) { continue; }
            var code = java.util.Arrays.stream(method.instructions.toArray()).filter(n -> n.getOpcode() >= 0).toList();
            if (code.size() != 7 || !(code.get(1) instanceof VarInsnNode event) || event.var != 1) {
                throw new AssertionError("Unexpected owned predicate routing fixture");
            }
            // Valid bytecode but wrong context. Production proof must refuse before mixed Boots can use it.
            method.instructions.set(event, new InsnNode(org.objectweb.asm.Opcodes.ACONST_NULL));
            changed++;
        }
        if (changed != 1) { throw new AssertionError("Missing unique owned routing mutation"); }
        routingMutation = true;
    }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
}
