package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

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

/** Independent optional client bridge: incomplete local hooks leave actual mixed items unselectable. */
public final class JustDireSettingsScreenMixinPlugin implements IMixinConfigPlugin, IExtension {
    private boolean available;

    @Override
    public void onLoad(String mixinPackage) {
        boolean present = false;
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(JustDireSettingsScreenShape.SCREEN.replace('/', '.'));
            present = true;
            available = JustDireSettingsScreenShape.entry(node);
            Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (available && transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
                extensions.add(this);
            } else {
                available = false;
            }
            if (!available) {
                Constants.LOG.warn("Just Dire Things native settings local seams incompatible; mixed screen bridge disabled");
            }
        } catch (ClassNotFoundException absent) {
            if (present) {
                Constants.LOG.warn("Just Dire Things native settings API incomplete; mixed screen bridge disabled", absent);
            }
        } catch (Exception | LinkageError failure) {
            Constants.LOG.warn("Could not inspect Just Dire Things native settings seams; mixed screen bridge disabled", failure);
        }
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        return available;
    }

    @Override
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (node.name.equals(JustDireSettingsScreenShape.SCREEN) && !JustDireSettingsScreenShape.publish(node)) {
            Constants.LOG.warn("Just Dire Things native settings transformed hooks incomplete; mixed screen bridge disabled");
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
