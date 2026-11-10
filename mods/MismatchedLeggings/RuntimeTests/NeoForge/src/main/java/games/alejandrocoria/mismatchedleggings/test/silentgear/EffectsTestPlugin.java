package games.alejandrocoria.mismatchedleggings.test.silentgear;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Controlled real competing transformations, before production final-export readiness publication. */
public final class EffectsTestPlugin implements IMixinConfigPlugin, org.spongepowered.asm.mixin.transformer.ext.IExtension, Opcodes {
    public static String actualOrder;

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (!Boolean.getBoolean("leggings.test.sgFoundation") || Boolean.getBoolean("leggings.test.sgFoundationAbsent")) { return false; }
        if (mixin.endsWith("OxidationBoundaryMixin")) { return System.getProperty("leggings.test.sgLiveVariant", "").equals("boundary"); }
        return !mixin.endsWith("EffectsFaultMixin") || !System.getProperty("leggings.test.sgEffectsVariant", "").isEmpty();
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        String variant = System.getProperty("leggings.test.sgEffectsVariant");
        if (!mixin.endsWith("EffectsFaultMixin")) { return; }
        for (MethodNode method : node.methods) {
            if (method.name.equals("inventoryTick") || method.name.equals("onDurabilityDamage")) {
                method.instructions.insert(new InsnNode(NOP));
                for (var instruction : method.instructions) {
                    if (variant.equals("wear-delta") && method.name.equals("onDurabilityDamage") && instruction.getOpcode() == IADD) {
                        method.instructions.set(instruction, new InsnNode(ISUB));
                    }
                }
            }
        }

        for (var method : node.methods) {
            if (variant.equals("repair") && method.name.contains("mismatchedleggings$sgRepairCondition")) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.name.equals("captureRepair")) {
                        call.owner = "games/alejandrocoria/mismatchedleggings/test/silentgear/EffectsChecks";
                        call.name = "repairAlias";
                    }
                }
            }
            if (variant.equals("wear") && method.name.equals("mismatchedleggings$sgWearReady")) {
                method.instructions.insertBefore(method.instructions.getFirst(), new InsnNode(NOP));
                for (var instruction : method.instructions) {
                    if (instruction.getOpcode() == ICONST_0) {
                        method.instructions.set(instruction, new MethodInsnNode(INVOKESTATIC,
                                "games/alejandrocoria/mismatchedleggings/test/silentgear/EffectsChecks", "falseAlias", "()Z", false));
                    }
                }
            }
        }

        var marker = new MethodNode(ACC_PUBLIC | ACC_STATIC, "sgtest$modified", "()Z", null, null);
        marker.instructions.add(new InsnNode(ICONST_1));
        marker.instructions.add(new InsnNode(IRETURN));
        marker.maxStack = 1;
        node.methods.add(marker);
    }

    @Override
    public void onLoad(String name) {
        if (Boolean.getBoolean("leggings.test.sgFoundation") && !Boolean.getBoolean("leggings.test.sgFoundationAbsent")) {
            var transformer = (org.spongepowered.asm.mixin.transformer.IMixinTransformer)
                    org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            ((org.spongepowered.asm.mixin.transformer.ext.Extensions) transformer.getExtensions()).add(this);
        }
    }

    @Override public boolean checkActive(org.spongepowered.asm.mixin.MixinEnvironment environment) { return true; }
    @Override public void postApply(org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext context) {}
    @Override public void export(org.spongepowered.asm.mixin.MixinEnvironment environment, String name, boolean force, ClassNode node) {}

    @Override public void preApply(org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext context) {
        if (!context.getClassNode().name.endsWith("/SelfRepairTraitEffect")) { return; }
        try {
            var field = context.getClass().getDeclaredField("mixins");
            field.setAccessible(true);
            int legs = -1, boots = -1, index = 0;
            var names = new java.util.ArrayList<String>();
            for (var entry : (Iterable<?>) field.get(context)) {
                var info = (IMixinInfo) entry;
                names.add(info.getClassName() + "@" + info.getPriority());
                if (info.getClassName().equals("games.alejandrocoria.mismatchedleggings.mixin.compatibility.silentgear.SilentGearSelfRepairMixin")) { legs = index; }
                if (info.getClassName().equals("games.alejandrocoria.mismatchedboots.mixin.compatibility.silentgear.SilentGearSelfRepairMixin")) { boots = index; }
                index++;
            }
            String order = System.getProperty("leggings.test.sgEffectsOrder", "default");
            if (legs < 0 || Boolean.getBoolean("leggings.test.boots") && (boots < 0
                    || !order.equals("default") && (legs < boots) != order.equals("before"))) {
                throw new AssertionError("Unexpected repair mixin order " + names);
            }
            actualOrder = names.toString();
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> mine, Set<String> others) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }
}
