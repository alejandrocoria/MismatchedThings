package games.alejandrocoria.mismatchedleggings.test.iceandfire;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import java.util.List;
import java.util.Set;

/** Late mutations, after normal injectors: admission alone must never authorize recognition. */
public final class DamageControlPlugin implements IMixinConfigPlugin, IExtension {
    public static String actualOrder;

    @Override public void preApply(ITargetClassContext context) {
        if (!context.getClassNode().name.equals("com/iafenvoy/iceandfire/event/handler/ServerEvents")) { return; }
        try {
            // Inspect the actual sorted application set, including the empty production marker.
            var field = context.getClass().getDeclaredField("mixins");
            field.setAccessible(true);
            var applied = (Iterable<?>) field.get(context);
            int legs = -1, boots = -1, index = 0;
            var names = new java.util.ArrayList<String>();
            for (var candidate : applied) {
                var info = (IMixinInfo) candidate;
                names.add(info.getClassName() + "@" + info.getPriority());
                if (info.getClassName().endsWith(".IceAndFireDamageTargetMixin")) {
                    legs = index;
                    int expected = System.getProperty("leggings.test.iafDamageOrder").equals("before") ? 900 : 1100;
                    if (info.getPriority() != expected) { throw new AssertionError("Stale production mixin priority"); }
                }
                if (info.getClassName().endsWith(".IceAndFireDamageMixin") && info.getClassName().contains("mismatchedboots")) { boots = index; }
                index++;
            }
            if (legs < 0 || Boolean.getBoolean("leggings.test.boots") && (boots < 0
                    || (legs < boots) != System.getProperty("leggings.test.iafDamageOrder").equals("before"))) {
                throw new AssertionError("Unexpected actual packaged mixin order: " + names + "; legs=" + legs + "; boots=" + boots);
            }
            actualOrder = "LEGS index=" + legs + "; FEET index=" + boots;
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot verify actual application order", failure); }
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        return Boolean.getBoolean("leggings.test.iafDamage")
                && !System.getProperty("leggings.test.iafDamageVariant").equals("absent");
    }

    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        String variant = System.getProperty("leggings.test.iafDamageVariant", "normal");
        for (var method : node.methods) {
            if (!method.name.equals("onEntityDamage") && !method.name.equals("onLivingHurt")) { continue; }
            if (variant.equals("compatible")) {
                var unrelated = new InsnList();
                unrelated.add(new VarInsnNode(Opcodes.ALOAD, 0));
                unrelated.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                        "net/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre", "getEntity",
                        "()Lnet/minecraft/world/entity/LivingEntity;", false));
                unrelated.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/entity/EquipmentSlot", "LEGS",
                        "Lnet/minecraft/world/entity/EquipmentSlot;"));
                unrelated.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/LivingEntity", "getItemBySlot",
                        "(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;", false));
                unrelated.add(new InsnNode(Opcodes.POP));
                method.instructions.insert(unrelated);
                method.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "games/alejandrocoria/mismatchedleggings/test/iceandfire/DamageControlPlugin", "observe", "()V", false));
                // An unrelated slot's result chain must not invalidate the LEGS boundary.
                for (var instruction : method.instructions.toArray()) {
                    if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF) {
                        method.instructions.insert(type, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "games/alejandrocoria/mismatchedleggings/test/iceandfire/DamageControlPlugin", "identity", "(Z)Z", false));
                        break;
                    }
                }
            }
            if (method.name.equals("onEntityDamage") && variant.startsWith("damage-")) {
                for (var instruction : method.instructions.toArray()) {
                    if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.INSTANCEOF
                            && type.desc.endsWith("/TrollArmorItem")
                            && previous(previous(previous(type))) instanceof FieldInsnNode slot && slot.name.equals("LEGS")) {
                        if (variant.equals("damage-class")) { type.desc = "java/lang/String"; }
                        else { method.instructions.insert(type, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "games/alejandrocoria/mismatchedleggings/test/iceandfire/DamageControlPlugin", "identity", "(Z)Z", false)); }
                    }
                }
            }
            if (method.name.equals("onLivingHurt") && variant.startsWith("lightning-")) {
                for (var instruction : method.instructions.toArray()) {
                    if (instruction instanceof FieldInsnNode item && item.name.equals("DRAGONSTEEL_LIGHTNING_LEGGINGS")) {
                        if (variant.equals("lightning-item")) { item.name = "DRAGONSTEEL_FIRE_LEGGINGS"; }
                        else {
                            var predicate = item;
                            AbstractInsnNode call = predicate;
                            do { call = call.getNext(); } while (!(call instanceof MethodInsnNode m && m.name.equals("is")));
                            method.instructions.insert(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                    "games/alejandrocoria/mismatchedleggings/test/iceandfire/DamageControlPlugin", "identity", "(Z)Z", false));
                        }
                    }
                }
            }
        }
    }

    public static boolean identity(boolean value) { return value; }
    public static void observe() {}
    private static AbstractInsnNode previous(AbstractInsnNode node) {
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        return node;
    }
    @Override public void onLoad(String name) {
        if (!Boolean.getBoolean("leggings.test.iafDamage")) { return; }
        var transformer = (IMixinTransformer) MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        ((Extensions) transformer.getExtensions()).add(this);
    }
    @Override public boolean checkActive(MixinEnvironment environment) { return true; }
    @Override public void postApply(ITargetClassContext context) {}
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
