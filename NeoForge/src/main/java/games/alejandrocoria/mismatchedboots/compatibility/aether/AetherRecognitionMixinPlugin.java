package games.alejandrocoria.mismatchedboots.compatibility.aether;

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

/** Final-export local edits, after competing ordinary mixins and MixinExtras result chains. */
public final class AetherRecognitionMixinPlugin implements IMixinConfigPlugin, IExtension {
    @Override
    public void onLoad(String name) {
        Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
            extensions.add(this);
        }
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(target);
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (Exception | LinkageError failure) {
            AetherRecognition.disable(target.endsWith("EquipmentUtil"), failure);
            return false;
        }
    }

    @Override
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (!node.name.equals(AetherRecognitionShape.EQUIPMENT) && !node.name.equals(AetherRecognitionShape.SENTRY)) {
            return;
        }
        boolean sets = node.name.equals(AetherRecognitionShape.EQUIPMENT);
        try {
            if (!AetherRecognitionShape.apply(node)) {
                AetherRecognition.disable(sets, null);
            }
        } catch (Exception | LinkageError failure) {
            AetherRecognition.disable(sets, failure);
        }
    }

    @Override
    public boolean checkActive(MixinEnvironment environment) {
        return true;
    }

    @Override
    public void preApply(ITargetClassContext context) {
    }

    @Override
    public void postApply(ITargetClassContext context) {
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

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }
}
