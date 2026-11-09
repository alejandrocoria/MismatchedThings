package games.alejandrocoria.mismatchedleggings.test.iafspike;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class SpikePlugin implements IMixinConfigPlugin {
    @Override
    public List<String> getMixins() {
        if (!Boolean.getBoolean("leggings.test.iafSpike") || System.getProperty("leggings.test.iafVariant").equals("absent")) {
            return List.of();
        }
        return List.of("DamageCandidate", "LightningCandidate", "TideCandidate", "TickCandidate", "EffectsWitness");
    }

    @Override
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        String variant = System.getProperty("leggings.test.iafVariant", "normal");
        if (!target.endsWith("ServerEvents") || !mixin.endsWith(variant.equals("damage") ? "DamageCandidate" : "LightningCandidate")) {
            return;
        }
        for (var method : node.methods) {
            if (variant.equals("damage") && method.name.equals("onEntityDamage")) {
                var selected = new java.util.ArrayList<org.objectweb.asm.tree.AbstractInsnNode>();
                // Change only the native LEGS Troll operand; native Scale/Steel and FEET remain intact.
                for (var instruction : method.instructions) {
                    if (instruction instanceof TypeInsnNode type && type.desc.endsWith("/TrollArmorItem")) {
                        var previous = instruction.getPrevious().getPrevious().getPrevious();
                        if (previous instanceof FieldInsnNode slot && slot.name.equals("LEGS")) { selected.add(type); }
                    }
                }
                if (selected.size() != 1) { throw new AssertionError("Missing damage refusal control"); }
                ((TypeInsnNode) selected.getFirst()).desc = "java/lang/String";
            } else if (variant.equals("lightning") && method.name.equals("onLivingHurt")) {
                int changed = 0;
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode field && field.name.equals("DRAGONSTEEL_LIGHTNING_LEGGINGS")) {
                        field.name = "DRAGONSTEEL_FIRE_LEGGINGS";
                        changed++;
                    }
                }
                if (changed != 1) { throw new AssertionError("Missing Lightning refusal control"); }
            }
        }
    }

    @Override public void onLoad(String name) {}
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
