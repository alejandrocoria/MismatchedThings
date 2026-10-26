package games.alejandrocoria.mismatchedboots.compatibility.silentgear;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

import java.util.Collection;

/** Select the unique native fall charge; selection alone never grants execution permission. */
public final class SilentGearDefenseInjectionPoint extends InjectionPoint {
    private final String site;

    public SilentGearDefenseInjectionPoint(InjectionPointData data) {
        super(data);
        site = data.get("site", "");
    }

    @Override
    public boolean find(String descriptor, InsnList instructions, Collection<AbstractInsnNode> nodes) {
        if (site.equals("fall-level") || site.equals("retaliation-level")) {
            boolean fall = site.equals("fall-level");
            if (!(fall ? SilentGearDefenseShape.FALL : SilentGearDefenseShape.POST).equals(descriptor)) {
                return false;
            }
            var selected = SilentGearDefenseShape.bounceQuery(instructions, fall);
            if (selected == null) {
                return false;
            }
            nodes.add(selected);
            return true;
        }
        if (SilentGearDefenseShape.INCOMING.equals(descriptor)) {
            var selected = SilentGearDefenseShape.incomingSite(instructions, site);
            if (selected == null) {
                return false;
            }
            nodes.add(selected);
            return true;
        }
        if (!SilentGearDefenseShape.FALL.equals(descriptor)) {
            return false;
        }

        var selected = site.equals("wear") ? SilentGearDefenseShape.wearSite(instructions) : null;
        if (selected == null) {
            return false;
        }
        nodes.add(selected);
        return true;
    }
}
