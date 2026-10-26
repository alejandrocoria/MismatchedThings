package games.alejandrocoria.mismatchedboots.compatibility.allthemodium;

import games.alejandrocoria.mismatchedboots.Constants;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AllTheModiumMixinPlugin implements IMixinConfigPlugin {
    static final String TARGET = "net/allthemods/allthemodium/core/event/ATMEventListener";
    static final String METHOD = "getStackInSlot";
    static final String DESCRIPTOR = "(Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;";
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        try {
            if (supports(MixinService.getService().getBytecodeProvider().getClassNode(target))) {
                return true;
            }
            warn(null);
        } catch (ClassNotFoundException absent) {
            // Optional absence is normal; the item hooks never link an AllTheModium class.
        } catch (Exception | LinkageError failure) {
            warn(failure);
        }
        return false;
    }

    static boolean supports(ClassNode owner) {
        if (!owner.name.equals(TARGET)) {
            return false;
        }

        var methods = owner.methods.stream()
                                   .filter(method -> method.name.equals(METHOD) && method.desc.equals(DESCRIPTOR))
                                   .toList();
        if (methods.size() != 1 || (methods.getFirst().access & Opcodes.ACC_STATIC) == 0) {
            return false;
        }

        // Guard the helper's signature/return boundary, not unrelated damage handlers or its full body.
        for (var instruction : methods.getFirst().instructions) {
            if (instruction.getOpcode() == Opcodes.ARETURN) {
                return true;
            }
        }
        return false;
    }

    private static void warn(Throwable failure) {
        if (WARNED.compareAndSet(false, true)) {
            Constants.LOG.warn("AllTheModium mixed damage protection disabled: incompatible slot helper", failure);
        }
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
