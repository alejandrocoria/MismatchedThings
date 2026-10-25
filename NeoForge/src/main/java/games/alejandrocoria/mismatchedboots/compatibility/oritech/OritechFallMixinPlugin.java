package games.alejandrocoria.mismatchedboots.compatibility.oritech;

import games.alejandrocoria.mismatchedboots.Constants;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

public final class OritechFallMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            ClassNode target = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
            for (var method : target.methods) {
                if (OritechFallInjectionPoint.METHOD.equals(method.name)
                        && OritechFallInjectionPoint.DESCRIPTOR.equals(method.desc)
                        && (method.access & Opcodes.ACC_STATIC) != 0
                        && OritechFallInjectionPoint.select(method.desc, method.instructions) != null) {
                    return true;
                }
            }
            OritechFallInjectionPoint.warn();
        } catch (ClassNotFoundException absent) {
            // Optional mod absence is normal and silent; no optional class is linked from our hook.
        } catch (Exception | LinkageError failure) {
            Constants.LOG.warn("Could not inspect Oritech fall recognition; mixed Exo fall protection disabled", failure);
        }
        return false;
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
