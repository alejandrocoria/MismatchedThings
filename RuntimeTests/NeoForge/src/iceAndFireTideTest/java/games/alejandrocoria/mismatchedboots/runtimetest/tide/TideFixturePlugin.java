package games.alejandrocoria.mismatchedboots.runtimetest.tide;

import games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireTideMixinPlugin;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Selects real competing mixins only; never grants production dispatch permission. */
public final class TideFixturePlugin implements IMixinConfigPlugin {
    static final String MODE = System.getProperty("mismatchedboots.runtimetest.tideOwners", "");
    static final String ARMOR = "com/iafenvoy/iceandfire/item/armor/SeaSerpentArmorItem";
    static final String TICK = "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EquipmentSlot;)V";

    @Override public void onLoad(String mixinPackage) {}
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (mixin.endsWith("TideProductionRuntimeMixin")) { return true; }
        if (!new IceAndFireTideMixinPlugin().shouldApplyMixin(target, mixin)) { return false; }
        return mixin.endsWith("TideLateEntryMixin") && MODE.equals("late")
                || mixin.endsWith("TidePublicationChangeMixin") && MODE.equals("publicationChanged")
                || mixin.endsWith("TideBaseChangeMixin") && MODE.startsWith("base")
                || mixin.endsWith("TideExtraWorkMixin") && MODE.equals("extraWork")
                || mixin.endsWith("TideChainHighMixin") && MODE.equals("chainHigh")
                || mixin.endsWith("TideChainLowMixin") && MODE.equals("chainLow")
                || mixin.endsWith("TidePredicateChangeMixin") && MODE.startsWith("predicate");
    }

    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (mixin.endsWith("TidePredicateChangeMixin")) { TidePredicateMutation.apply(node, MODE); }
    }

    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!mixin.endsWith("TidePublicationChangeMixin")) { return; }
        var getter = node.methods.stream().filter(method -> method.name.equals("mismatchedboots$tideEntryReady")
                && method.desc.equals("()Z")).findFirst().orElseThrow();
        getter.access |= Opcodes.ACC_FINAL;
        getter.instructions.clear();
        getter.instructions.add(new InsnNode(Opcodes.ICONST_1));
        getter.instructions.add(new InsnNode(Opcodes.IRETURN));
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
}
