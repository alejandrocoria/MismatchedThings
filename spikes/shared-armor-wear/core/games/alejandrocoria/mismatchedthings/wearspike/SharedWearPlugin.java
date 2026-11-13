package games.alejandrocoria.mismatchedthings.wearspike;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
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

/** One final causal guard in the candidate Things JAR; no optional product verifier. */
public final class SharedWearPlugin implements IMixinConfigPlugin, IExtension {
    private boolean available;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            var provider = MixinService.getService().getBytecodeProvider();
            provider.getClassNode("com.direwolf20.justdirethings.common.items.interfaces.PoweredItem");
            available = SharedWearShape.entry(provider.getClassNode(SharedWearShape.OWNER.replace('/', '.')));
            if (available) {
                var transformer = (IMixinTransformer) MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
                ((Extensions) transformer.getExtensions()).add(this);
            }
        } catch (ClassNotFoundException absent) {
            available = false;
        } catch (Exception | LinkageError failure) {
            throw new IllegalStateException("Shared wear spike preflight failed", failure);
        }
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) { return available; }
    @Override public List<String> getMixins() {
        return Boolean.getBoolean("sharedwear.transparent") ? List.of("SharedWearTransparentMixin") : null;
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (Boolean.getBoolean("sharedwear.refused")) {
            var getter = node.methods.stream().filter(method -> method.name.equals(SharedWearShape.GETTER)).findFirst().orElseThrow();
            getter.instructions.set(getter.instructions.getFirst(), new InsnNode(Opcodes.ICONST_1));
        }
    }

    @Override
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
        if (node.name.equals(SharedWearShape.OWNER)) {
            System.out.println("Shared wear final guard=" + SharedWearShape.publish(node));
        }
    }

    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void preApply(ITargetClassContext context) {}
    @Override public void postApply(ITargetClassContext context) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public String getRefMapperConfig() { return null; }
}
