package games.alejandrocoria.aquaspike;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class SpikePlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        String mode = System.getProperty("aqua.spike.mode", "");
        return mode.startsWith("candidate") || mode.equals("absent");
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
    public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {
    }

    @Override
    public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {
    }
}
