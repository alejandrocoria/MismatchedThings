package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import games.alejandrocoria.mismatchedboots.Constants;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

/** Independent optional menu guard; no active-ability or client-screen readiness. */
public final class JustDireSettingsMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(target);
            boolean compatible = node.methods.stream().anyMatch(method -> method.name.equals("getItemSlots")
                    && method.desc.equals("(Lnet/minecraft/world/item/ItemStack;)Lnet/neoforged/neoforge/transfer/ResourceHandler;")
                    && (method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0);
            if (!compatible) {
                Constants.LOG.warn("Just Dire Things settings inventory query changed; empty-stack guard disabled");
            }
            return compatible;
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (Exception | LinkageError failure) {
            Constants.LOG.warn("Could not inspect Just Dire Things settings inventory query; empty-stack guard disabled", failure);
            return false;
        }
    }

    @Override
    public void onLoad(String mixinPackage) {
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
