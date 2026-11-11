package games.alejandrocoria.mismatchedleggings.test.silentgear;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

/** Tooltip controls are opt-in and never replace a production renderer. */
public final class TooltipTestPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (!Boolean.getBoolean("leggings.test.sgTooltipClient")) {
            return false;
        }
        if (mixin.endsWith("TooltipFormatterFaultMixin")) {
            return System.getProperty("leggings.test.sgTooltipVariant", "normal").equals("formatter");
        }
        if (mixin.endsWith("TooltipBootsBaselineMixin")) {
            try {
                MixinService.getService().getBytecodeProvider().getClassNode(target);
            } catch (Exception | LinkageError absent) {
                return false;
            }
        }
        return true;
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

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    }
}
