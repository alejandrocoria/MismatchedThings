package games.alejandrocoria.mismatchedleggings.test;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Corrupt only this product's unpublished permission seam; native callbacks remain healthy controls. */
public final class JustDireFaultPlugin implements IMixinConfigPlugin {
    private IMixinConfigPlugin wearSpike;
    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (mixin.endsWith("JustDireArmorWearDebitProbe")) {
            return Boolean.getBoolean("leggings.test.jdtWear");
        }
        if (mixin.contains("WearSpike")) {
            return wearSpike != null && wearSpike.shouldApplyMixin(target, mixin);
        }
        if (mixin.contains("JustDireHud")) {
            return Boolean.getBoolean("leggings.test.jdtHud")
                    && (!mixin.endsWith("FaultProbe") || System.getProperty("leggings.test.jdtFault", "").equals("hud"));
        }
        if (mixin.endsWith("JustDireDecoySoundProbe")) {
            return Boolean.getBoolean("leggings.test.jdtDecoy") || Boolean.getBoolean("leggings.test.jdtHud");
        }
        if (mixin.endsWith("JustDireDecoyDebitProbe")) { return Boolean.getBoolean("leggings.test.jdtDecoy"); }
        if (mixin.endsWith("JustDirePresentationInputProbe")) {
            return Boolean.getBoolean("leggings.test.jdtPresentation");
        }
        String fault = System.getProperty("leggings.test.jdtFault", "");
        return fault.equals("movement") && target.endsWith("AbilityMethods")
                || fault.equals("attributes") && target.endsWith("CommonHooks")
                || fault.equals("screen") && target.endsWith("ToolSettingScreen");
    }

    @Override
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (mixin.endsWith("JustDireArmorWearDebitProbe")) {
            return;
        }
        if (mixin.contains("WearSpike")) {
            wearSpike.postApply(target, node, mixin, info);
            return;
        }
        if (mixin.endsWith("JustDireHudFaultProbe")) {
            var handler = node.methods.stream().filter(method -> method.name.contains("mismatchedleggings$justDireCooldownView")).findFirst().orElseThrow();
            for (var instruction : handler.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.endsWith("/client/compatibility/justdirethings/JustDireCooldownHud")
                        && call.name.equals("view")) {
                    handler.instructions.set(call.getPrevious(), new FieldInsnNode(Opcodes.GETSTATIC,
                            "net/minecraft/world/entity/EquipmentSlot", "FEET", "Lnet/minecraft/world/entity/EquipmentSlot;"));
                    return;
                }
            }
            throw new IllegalStateException("Missing owned HUD slot seam");
        }
        if (mixin.contains("JustDireHud")) {
            return;
        }
        if (mixin.contains("JustDireDecoy") && mixin.endsWith("Probe")) { return; }
        if (mixin.endsWith("JustDirePresentationInputProbe")) { return; }
        String getter = target.endsWith("AbilityMethods") ? "mismatchedleggings$jdtMovementReady"
                : target.endsWith("ToolSettingScreen") ? "mismatchedleggings$jdtSettingsReady" : "mismatchedleggings$justDireAttributesReady";
        var method = node.methods.stream().filter(value -> value.name.equals(getter)).findFirst().orElseThrow();
        method.instructions.set(method.instructions.getFirst(), new InsnNode(Opcodes.ICONST_1));
    }

    @Override public void onLoad(String name) {
        if (Boolean.getBoolean("leggings.test.wearSpike")) {
            try {
                wearSpike = (IMixinConfigPlugin) Class.forName("games.alejandrocoria.mismatchedleggings.test.WearSpikePlugin")
                        .getConstructor().newInstance();
                wearSpike.onLoad(name);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Missing isolated wear spike plugin", failure);
            }
        }
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() {
        if (Boolean.getBoolean("leggings.test.jdtWear")) {
            return List.of("JustDireArmorWearDebitProbe");
        }
        if (wearSpike != null) {
            return wearSpike.getMixins();
        }
        if (Boolean.getBoolean("leggings.test.jdtHud")) {
            if (!net.neoforged.fml.loading.FMLEnvironment.getDist().isClient()) {
                return List.of("JustDireDecoySoundProbe");
            }

            return System.getProperty("leggings.test.jdtFault", "").equals("hud")
                    ? List.of("JustDireHudTransparentProbe", "JustDireHudFaultProbe", "JustDireHudDrawProbe")
                    : List.of("JustDireHudTransparentProbe", "JustDireHudDrawProbe");
        }
        if (Boolean.getBoolean("leggings.test.jdtDecoy")) { return List.of("JustDireDecoySoundProbe", "JustDireDecoyDebitProbe"); }
        return null;
    }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
