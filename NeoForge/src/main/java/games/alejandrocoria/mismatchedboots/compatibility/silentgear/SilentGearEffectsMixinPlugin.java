package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

public final class SilentGearEffectsMixinPlugin implements IMixinConfigPlugin, IExtension {
    @Override public void onLoad(String name) {
        Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) { extensions.add(this); }
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        try { MixinService.getService().getBytecodeProvider().getClassNode(target); return true; }
        catch (Exception | LinkageError absent) { return false; }
    }
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        SilentGearDefenseShape.publish(node);
        SilentGearWielderShape.publish(node);
        SilentGearMagnetShape.publish(node);
        if (node.name.equals(SilentGearCureShape.OWNER)) {
            SilentGearEffectShape.publish(node, "mismatchedboots$sgCureReady", SilentGearCureShape.admitted(node));
        }
        if (node.name.equals(SilentGearEffectShape.REPAIR)) {
            SilentGearEffectShape.publish(node, "mismatchedboots$sgRepairReady", SilentGearEffectShape.repair(node));
        } else if (node.name.equals(SilentGearEffectShape.WEAR)) {
            SilentGearEffectShape.publish(node, "mismatchedboots$sgWearReady", SilentGearEffectShape.wear(node));
        } else if (node.name.equals(SilentGearRecognitionShape.ARMOR)) {
            SilentGearEffectShape.publish(node, "mismatchedboots$sgBrilliantReady", SilentGearRecognitionShape.brilliant(node));
        } else if (node.name.equals(SilentGearRecognitionShape.EVENTS)) {
            SilentGearEffectShape.publish(node, "mismatchedboots$sgMoonReady", SilentGearRecognitionShape.moonwalker(node));
            SilentGearEffectShape.publish(node, "mismatchedboots$sgVoidReady", SilentGearRecognitionShape.voidWard(node));
        }
    }
    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void preApply(ITargetClassContext context) {}
    @Override public void postApply(ITargetClassContext context) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
