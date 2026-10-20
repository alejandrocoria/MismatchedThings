package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Independent witness, compatible slot peer and controlled faults; no candidate set engine. */
public final class SilentGearTraitSpikePlugin implements IMixinConfigPlugin, Opcodes {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (mixin.endsWith("SilentGearSetPeerMixin") || mixin.endsWith("SilentGearSetFaultMixin")) {
            return Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTest")
                    && (mixin.endsWith("SilentGearSetPeerMixin")
                    ? System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", "").equals("wearer-compatible")
                    : System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", "").startsWith("wearer-"));
        }
        return Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTest")
                && mixin.endsWith("SilentGearRecalculationWitnessMixin");
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        String variant = System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", "");
        if (!mixin.endsWith("SilentGearSetFaultMixin") || variant.equals("wearer-definition")) { return; }
        for (var method : node.methods) {
            if (!method.name.equals("getSetPieceCount")) { continue; }
            method.instructions.insert(new InsnNode(NOP));
            for (var insn : method.instructions) {
                if (variant.equals("wearer-type") && insn instanceof TypeInsnNode type
                        && type.desc.equals("net/silentchaos512/gear/api/item/GearArmor")) { type.desc = "net/silentchaos512/gear/item/gear/GearArmorItem"; }
                if (variant.equals("wearer-trait") && insn instanceof MethodInsnNode call
                        && call.owner.equals("net/silentchaos512/gear/util/TraitHelper") && call.name.equals("hasTrait")) {
                    call.name = "sgtest$traitAlias"; call.owner = node.name;
                }
            }
        }
        if (variant.equals("wearer-effect")) {
            for (var method : node.methods) {
                if (!method.name.equals("applyEffects")) { continue; }
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.name.equals("addEffect")) {
                        var alias = new MethodNode(ACC_PUBLIC | ACC_STATIC, "sgtest$actorAlias",
                                "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/effect/MobEffectInstance;)Z", null, null);
                        alias.instructions.add(new VarInsnNode(ALOAD, 0)); alias.instructions.add(new VarInsnNode(ALOAD, 1));
                        alias.instructions.add(new MethodInsnNode(call.getOpcode(), call.owner, call.name, call.desc, call.itf));
                        alias.instructions.add(new InsnNode(IRETURN)); alias.maxLocals = 2; alias.maxStack = 2;
                        node.methods.add(alias);
                        call.setOpcode(INVOKESTATIC); call.owner = node.name; call.name = alias.name; call.desc = alias.desc;
                        break;
                    }
                }
                break;
            }
        }
        // Alias preserves native behavior, but removes the exact paired consumer our proof needs.
        if (variant.equals("wearer-trait")) {
            var alias = new MethodNode(ACC_PUBLIC | ACC_STATIC,
                    "sgtest$traitAlias", "(Lnet/minecraft/world/item/ItemInstance;Lnet/silentchaos512/gear/gear/trait/Trait;)Z", null, null);
            alias.instructions.add(new VarInsnNode(ALOAD, 0));
            alias.instructions.add(new VarInsnNode(ALOAD, 1));
            alias.instructions.add(new MethodInsnNode(INVOKESTATIC,
                    "net/silentchaos512/gear/util/TraitHelper", "hasTrait", alias.desc, false));
            alias.instructions.add(new InsnNode(IRETURN)); alias.maxStack = 2; alias.maxLocals = 2;
            node.methods.add(alias);
        }
        var marker = new MethodNode(ACC_PUBLIC | ACC_STATIC, "sgtest$modified", "()Z", null, null);
        marker.instructions.add(new InsnNode(ICONST_1));
        marker.instructions.add(new InsnNode(IRETURN)); marker.maxStack = 1; node.methods.add(marker);
    }
}
