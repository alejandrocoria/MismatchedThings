package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Controlled real competing transformations, before production final-export readiness publication. */
public final class SilentGearEffectsFaultPlugin implements IMixinConfigPlugin, Opcodes {
    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        String variant = System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", "");
        if (mixin.endsWith("SilentGearMoonQueryPeerMixin")) {
            return Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTest") && variant.equals("recognition-compatible");
        }
        if (mixin.endsWith("SilentGearRecognitionFaultMixin")) {
            return Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTest") && variant.startsWith("recognition-");
        }
        return Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTest")
                && List.of("repair", "wear", "compatible").contains(System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", ""));
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        String variant = System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant");
        if (mixin.endsWith("SilentGearRecognitionFaultMixin")) {
            recognition(node, variant);
            return;
        }
        if (mixin.endsWith("SilentGearMoonQueryPeerMixin")) {
            return;
        }
        for (MethodNode method : node.methods) {
            if (method.name.equals("inventoryTick") || method.name.equals("onDurabilityDamage")) {
                method.instructions.insert(new InsnNode(NOP));
                for (var instruction : method.instructions) {
                    if (variant.equals("repair") && instruction instanceof MethodInsnNode call
                            && call.owner.equals("net/silentchaos512/gear/util/GearHelper") && call.name.equals("attemptDamage")) {
                        call.owner = "games/alejandrocoria/mismatchedboots/runtimetest/neoforge/SilentGearEffectsTest";
                        call.name = "ignoredNativeRepair";
                    }
                    if (variant.equals("wear") && method.name.equals("onDurabilityDamage") && instruction.getOpcode() == IADD) {
                        method.instructions.set(instruction, new InsnNode(ISUB));
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

    private static void recognition(ClassNode node, String variant) {
        MethodInsnNode changed = null;
        for (var method : node.methods) {
            if (!method.name.equals("makesPiglinsNeutral") && !method.name.equals("onLivingFall")) {
                continue;
            }
            method.instructions.insert(new InsnNode(NOP));
            for (var insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.owner.equals("net/silentchaos512/gear/util/TraitHelper")
                        && (variant.equals("recognition-brilliant") && method.name.equals("makesPiglinsNeutral")
                        || variant.equals("recognition-moonwalker") && method.name.equals("onLivingFall") && changed == null)) {
                    changed = call;
                }
            }
        }
        if (changed != null) {
            var alias = new MethodNode(ACC_PUBLIC | ACC_STATIC, "sgtest$recognitionAlias", changed.desc, null, null);
            alias.instructions.add(new VarInsnNode(ALOAD, 0));
            alias.instructions.add(new VarInsnNode(ALOAD, 1));
            alias.instructions.add(new MethodInsnNode(INVOKESTATIC, changed.owner, changed.name, changed.desc, false));
            alias.instructions.add(new InsnNode(IRETURN));
            alias.maxLocals = 2;
            alias.maxStack = 2;
            node.methods.add(alias);
            changed.owner = node.name;
            changed.name = alias.name;
        }

        var marker = new MethodNode(ACC_PUBLIC | ACC_STATIC, "sgtest$recognitionChanged", "()Z", null, null);
        marker.instructions.add(new InsnNode(ICONST_1));
        marker.instructions.add(new InsnNode(IRETURN));
        marker.maxStack = 1;
        node.methods.add(marker);
    }

    @Override
    public void onLoad(String name) {
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
