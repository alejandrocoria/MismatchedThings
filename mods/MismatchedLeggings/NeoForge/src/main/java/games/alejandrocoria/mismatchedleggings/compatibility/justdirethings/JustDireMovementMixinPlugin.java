package games.alejandrocoria.mismatchedleggings.compatibility.justdirethings;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
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

public final class JustDireMovementMixinPlugin implements IMixinConfigPlugin, IExtension {
    private boolean available;

    @Override
    public void onLoad(String mixinPackage) {
        boolean present = false;
        try {
            var provider = MixinService.getService().getBytecodeProvider();
            provider.getClassNode("com.direwolf20.justdirethings.common.items.interfaces.PoweredTool");
            present = true;
            available = JustDireMovementShape.entry(provider.getClassNode(JustDireMovementShape.OWNER.replace('/', '.')));
            var transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (available && transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
                extensions.add(this);
            } else {
                available = false;
            }
            if (!available) {
                LeggingsContent.LOG.warn("Just Dire Things movement callback flow incompatible; movement disabled");
            }
        } catch (ClassNotFoundException absent) {
            if (present) {
                LeggingsContent.LOG.warn("Just Dire Things movement callback unavailable; movement disabled", absent);
            }
        } catch (Exception | LinkageError failure) {
            LeggingsContent.LOG.warn("Could not inspect Just Dire Things movement callback; movement disabled", failure);
        }
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        return available;
    }

    @Override
    public void export(MixinEnvironment env, String name, boolean force, ClassNode node) {
        if (node.name.equals(JustDireMovementShape.OWNER) && !JustDireMovementShape.publish(node)) {
            LeggingsContent.LOG.warn("Just Dire Things transformed movement callback flow incompatible; movement disabled");
        }
    }

    @Override
    public boolean checkActive(MixinEnvironment env) {
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
