package games.alejandrocoria.mismatchedleggings.compatibility.aether;

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
import java.util.concurrent.ConcurrentHashMap;

/** Final-transformed local gameplay proofs, independent from optional siblings and presentation. */
public final class AetherGameplayMixinPlugin implements IMixinConfigPlugin, IExtension {
    private final Set<String> examined = ConcurrentHashMap.newKeySet();

    @Override
    public void onLoad(String mixinPackage) {
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
        } catch (Exception | LinkageError absent) {
            return false;
        }
    }

    @Override
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        boolean sets = node.name.equals(AetherSetShape.EQUIPMENT);
        boolean armor = node.name.equals(AetherAlkahestShape.EVENTS);
        if ((!sets && !armor && !node.name.equals(AetherAlkahestShape.FLUID)) || !examined.add(node.name)) {
            return;
        }
        try {
            if (sets ? AetherSetShape.apply(node) : AetherAlkahestShape.apply(node)) {
                return;
            }
        } catch (Exception | LinkageError failure) {
            if (sets) {
                AetherSetRecognition.disable(failure);
            } else {
                AetherAlkahestProtection.disable(armor, failure);
            }
            return;
        }
        if (sets) {
            AetherSetRecognition.disable(null);
        } else {
            AetherAlkahestProtection.disable(armor, null);
        }
    }

    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void preApply(ITargetClassContext context) {}
    @Override public void postApply(ITargetClassContext context) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
}
