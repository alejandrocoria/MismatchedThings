package games.alejandrocoria.mismatchedboots.compatibility.rootsclassic;

import games.alejandrocoria.mismatchedboots.Constants;
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

/** Final export checks tick proofs and edits set predicates after competing mixins/MixinExtras. */
public final class RootsMixinPlugin implements IMixinConfigPlugin, IExtension {
    private final Set<String> declinedSets = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
        } catch (Exception | LinkageError absentOrUnsupported) {
            return false;
        }
    }

    @Override
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (node.name.equals(RootsTickShape.SYLVAN) || node.name.equals(RootsTickShape.WILDWOOD)) {
            RootsTickShape.publish(node, RootsTickShape.ENTRY_PROOF, false, RootsTickShape.tick(node));
        } else if (node.name.equals(RootsTickShape.UTIL)) {
            RootsTickShape.publish(node, RootsTickShape.REPAIR_PROOF, true, RootsTickShape.repair(node));
        }
        if (RootsSetShape.target(node.name) && !RootsSetShape.apply(node) && declinedSets.add(node.name)) {
            Constants.LOG.warn("Roots Classic mixed set consumer disabled: {} (local FEET predicate unavailable)", node.name);
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
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
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
}
