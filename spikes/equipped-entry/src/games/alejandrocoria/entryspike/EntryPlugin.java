package games.alejandrocoria.entryspike;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class EntryPlugin implements IMixinConfigPlugin {
    public void onLoad(String name) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String name) {
        if (name.endsWith("BootsEntryMixin")) {
            return !System.getProperty("entry.spike.graph").equals("legs")
                    && !System.getProperty("entry.spike.mode").equals("baseline");
        }
        if (name.endsWith("ForeignForwardMixin")) {
            return Boolean.getBoolean("entry.spike.forward");
        }
        return true;
    }
    public void acceptTargets(Set<String> mine, Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String name, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String name, IMixinInfo info) {}
}
