package games.alejandrocoria.mismatchedleggings.test.silentgear;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.NOP;

/** Actual order witness and pre-export corruptions. Never publishes a readiness value. */
public final class DefenseTestPlugin implements IMixinConfigPlugin, IExtension {
    private static final String EVENTS = "net/silentchaos512/gear/event/GearEvents";
    static String actualOrder = "";
    static int mutations;

    @Override
    public void onLoad(String name) {
        if (System.getProperty("leggings.test.sgDefenseVariant", "").isEmpty()) {
            return;
        }
        var transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
            extensions.add(this);
        }
    }

    @Override
    public void preApply(ITargetClassContext context) {
        if (!context.getClassNode().name.equals(EVENTS)) {
            return;
        }
        try {
            var field = context.getClass().getDeclaredField("mixins");
            field.setAccessible(true);
            var names = new ArrayList<String>();
            for (var entry : (Iterable<?>) field.get(context)) {
                var info = (IMixinInfo) entry;
                names.add(info.getClassName());
            }
            for (String hook : List.of("SilentGearIncomingMixin", "SilentGearBounceMixin")) {
                int legs = names.indexOf("games.alejandrocoria.mismatchedleggings.mixin.compatibility.silentgear." + hook);
                int boots = names.indexOf("games.alejandrocoria.mismatchedboots.mixin.compatibility.silentgear." + hook);
                String order = System.getProperty("leggings.test.sgEffectsOrder", "default");
                if (legs < 0 || Boolean.getBoolean("leggings.test.boots") && (boots < 0
                        || !order.equals("default") && (legs < boots) != order.equals("before"))) {
                    throw new AssertionError("Unexpected actual defensive order: " + names);
                }
            }
            actualOrder = names.toString();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    @Override
    public void postApply(ITargetClassContext context) {
        var node = context.getClassNode();
        if (!node.name.equals(EVENTS)) {
            return;
        }
        String variant = System.getProperty("leggings.test.sgDefenseVariant", "");
        for (var method : node.methods) {
            if (variant.equals("safety") && method.name.contains("mismatchedboots$sgFallGate")) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode getter && getter.name.equals("mismatchedboots$sgSafeFallReady")) {
                        getter.name = "mismatchedboots$sgIncomingReady";
                        mutations++;
                    }
                }
            }
            if (variant.equals("compatible") && List.of("onLivingIncomingDamage", "onLivingFall", "onPlayerHurt").contains(method.name)) {
                method.instructions.insert(new InsnNode(NOP));
            }
            if (variant.equals("routing") && method.name.contains("mismatchedleggings$sgIncomingArmor")) {
                var code = Arrays.stream(method.instructions.toArray()).filter(n -> n.getOpcode() >= 0).toList();
                if (code.size() != 7 || !(code.get(1) instanceof VarInsnNode event) || event.var != 1) {
                    throw new AssertionError("Unexpected owned incoming routing fixture");
                }
                method.instructions.set(event, new InsnNode(ACONST_NULL));
                mutations++;
            }
            String hook = variant.equals("fall-query") ? "mismatchedleggings$sgBounceFallLevel"
                    : variant.equals("retaliation-query") ? "mismatchedleggings$sgBounceRetaliationLevel" : "";
            if (!hook.isEmpty() && method.name.contains(hook)) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode getter && getter.desc.equals("()Z")) {
                        getter.name = "mismatchedleggings$sgIncomingReady";
                        mutations++;
                    }
                }
            }
        }
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        return !System.getProperty("leggings.test.sgDefenseVariant", "").isEmpty()
                && !Boolean.getBoolean("leggings.test.sgFoundationAbsent");
    }
    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
}
