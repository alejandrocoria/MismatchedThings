package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
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

/** Research hooks exist only in the optional exact-artifact server, never production. */
public final class SilentGearTraitSpikePlugin implements IMixinConfigPlugin, IExtension, Opcodes {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (mixin.endsWith("SilentGearSetPeerMixin")) {
            return Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTest")
                    && System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", "").equals("wearer-compatible");
        }
        return Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTest")
                && (mixin.endsWith("SilentGearRecalculationWitnessMixin")
                || System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", "").equals("research")
                || System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", "").startsWith("wearer"));
    }
    @Override public void onLoad(String name) {
        Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) { extensions.add(this); }
    }
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) { SilentGearWearerFixture.publish(node); }
    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void preApply(ITargetClassContext context) {}
    @Override public void postApply(ITargetClassContext context) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        String variant = System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", "");
        if (!mixin.endsWith("SilentGearSetSpikeMixin") || !variant.startsWith("wearer")) { return; }
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
