package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Independent witness, compatible slot peer and controlled faults; no candidate set engine. */
public final class SilentGearTraitSpikePlugin implements IMixinConfigPlugin, Opcodes {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (mixin.contains("SilentGearTooltip")) {
            return Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTooltipTest");
        }
        if (mixin.endsWith("SilentGearVoidWardWitnessMixin") || mixin.endsWith("SilentGearRemainingTickWitnessMixin") || mixin.endsWith("SilentGearCureWitnessMixin")) {
            return Boolean.getBoolean("mismatchedboots.runtimetest.silentGearRemainingTest")
                    || Boolean.getBoolean("mismatchedboots.runtimetest.silentGearClient");
        }
        if (mixin.endsWith("SilentGearIncomingWitnessMixin")) {
            return Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTest")
                    && System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", "").startsWith("defense-");
        }
        if (mixin.endsWith("SilentGearDefensePeerMixin")) {
            String variant = System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", "");
            return Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTest") && variant.startsWith("defense-") && !List.of("defense-foundation", "defense-bounce", "defense-joint").contains(variant);
        }
        if (mixin.endsWith("SilentGearDefenseWearWitnessMixin")) {
            return Boolean.getBoolean("mismatchedboots.runtimetest.silentGearClient")
                    || Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTest")
                    && System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", "").startsWith("defense-");
        }
        if (mixin.endsWith("SilentGearMagnetPeerMixin")) {
            return Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTest")
                    && List.of("magnet-compatible", "magnet-effect", "magnet-queue").contains(System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", ""));
        }
        if (mixin.endsWith("SilentGearMagnetWitnessMixin")) {
            return Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTest")
                    && System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", "").startsWith("magnet-");
        }
        if (mixin.endsWith("SilentGearSetPeerMixin") || mixin.endsWith("SilentGearSetFaultMixin")) {
            return Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTest")
                    && (mixin.endsWith("SilentGearSetPeerMixin")
                    ? System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", "").equals("wearer-compatible")
                    : System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", "").startsWith("wearer-"));
        }
        return (Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTest") || Boolean.getBoolean("mismatchedboots.runtimetest.silentGearClient")
                || Boolean.getBoolean("mismatchedboots.runtimetest.silentGearRemainingTest")
                || Boolean.getBoolean("mismatchedboots.runtimetest.silentGearTooltipTest"))
                && mixin.endsWith("SilentGearRecalculationWitnessMixin");
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (mixin.endsWith("SilentGearVoidWardWitnessMixin")) { voidWard(node); return; }
        String variant = System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", "");
        if (mixin.endsWith("SilentGearDefensePeerMixin")) { SilentGearDefensePeer.transform(node, variant); return; }
        if (mixin.endsWith("SilentGearMagnetPeerMixin")) { magnet(node, variant); return; }
        if (!mixin.endsWith("SilentGearSetFaultMixin") || variant.equals("wearer-definition")) { return; }
        for (var method : node.methods) {
            if (!method.name.equals("getSetPieceCount")) { continue; }
            method.instructions.insert(new InsnNode(NOP));
            for (var insn : method.instructions) {
                if (variant.equals("wearer-type") && insn instanceof TypeInsnNode type
                        && type.desc.equals("net/silentchaos512/gear/api/item/GearArmor")) { type.desc = "net/silentchaos512/gear/item/gear/GearArmorItem"; }
                if (variant.equals("wearer-trait") && insn instanceof MethodInsnNode call
                        && call.owner.equals("net/silentchaos512/gear/util/TraitHelper") && call.name.equals("hasTrait")) {
                    call.name = "sgtest$traitAlias"; call.owner = node.name;
                }
            }
        }
        if (variant.equals("wearer-effect")) {
            for (var method : node.methods) {
                if (!method.name.equals("applyEffects")) { continue; }
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.name.equals("addEffect")) {
                        var alias = new MethodNode(ACC_PUBLIC | ACC_STATIC, "sgtest$actorAlias",
                                "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/effect/MobEffectInstance;)Z", null, null);
                        alias.instructions.add(new VarInsnNode(ALOAD, 0)); alias.instructions.add(new VarInsnNode(ALOAD, 1));
                        alias.instructions.add(new MethodInsnNode(call.getOpcode(), call.owner, call.name, call.desc, call.itf));
                        alias.instructions.add(new InsnNode(IRETURN)); alias.maxLocals = 2; alias.maxStack = 2;
                        node.methods.add(alias);
                        call.setOpcode(INVOKESTATIC); call.owner = node.name; call.name = alias.name; call.desc = alias.desc;
                        break;
                    }
                }
                break;
            }
        }
        // Alias preserves native behavior, but removes the exact paired consumer our proof needs.
        if (variant.equals("wearer-trait")) {
            var alias = new MethodNode(ACC_PUBLIC | ACC_STATIC,
                    "sgtest$traitAlias", "(Lnet/minecraft/world/item/ItemInstance;Lnet/silentchaos512/gear/gear/trait/Trait;)Z", null, null);
            alias.instructions.add(new VarInsnNode(ALOAD, 0));
            alias.instructions.add(new VarInsnNode(ALOAD, 1));
            alias.instructions.add(new MethodInsnNode(INVOKESTATIC,
                    "net/silentchaos512/gear/util/TraitHelper", "hasTrait", alias.desc, false));
            alias.instructions.add(new InsnNode(IRETURN)); alias.maxStack = 2; alias.maxLocals = 2;
            node.methods.add(alias);
        }
        var marker = new MethodNode(ACC_PUBLIC | ACC_STATIC, "sgtest$modified", "()Z", null, null);
        marker.instructions.add(new InsnNode(ICONST_1));
        marker.instructions.add(new InsnNode(IRETURN)); marker.maxStack = 1; node.methods.add(marker);
    }

    private static void voidWard(ClassNode owner) {
        String variant = System.getProperty("mismatchedboots.runtimetest.silentGearVoidVariant", "");
        if (variant.isEmpty()) { return; }
        var method = owner.methods.stream().filter(value -> value.name.equals("onPlayerTick")).findFirst().orElseThrow();
        MethodInsnNode query = null;
        for (var node : method.instructions) {
            if (node instanceof MethodInsnNode call && call.name.equals("hasTraitArmor")
                    && call.getPrevious() instanceof FieldInsnNode field && field.name.equals("VOID_WARD")) { query = call; break; }
        }
        if (query == null) { throw new IllegalStateException("Void Ward peer query unavailable"); }
        if (variant.equals("refused")) {
            // Preserve every native result, but remove the exact proved query behind our injected hook.
            var alias = new MethodNode(ACC_PRIVATE | ACC_STATIC, "sgtest$voidAlias", query.desc, null, null);
            alias.instructions.add(new VarInsnNode(ALOAD, 0)); alias.instructions.add(new VarInsnNode(ALOAD, 1));
            alias.instructions.add(new MethodInsnNode(INVOKESTATIC, query.owner, query.name, query.desc, false));
            alias.instructions.add(new InsnNode(IRETURN)); alias.maxLocals = alias.maxStack = 2; owner.methods.add(alias);
            query.owner = owner.name; query.name = alias.name;
        } else {
            method.instructions.insert(new InsnNode(NOP));
            var extra = new InsnList(); extra.add(new VarInsnNode(ALOAD, 0)); extra.add(new InsnNode(POP));
            method.instructions.insert(extra);
            var identity = new MethodNode(ACC_PRIVATE | ACC_STATIC, "sgtest$voidIdentity",
                    "(ZLnet/neoforged/neoforge/event/tick/PlayerTickEvent$Pre;)Z", null, null);
            identity.instructions.add(new VarInsnNode(ILOAD, 0)); identity.instructions.add(new InsnNode(IRETURN));
            identity.maxLocals = 2; identity.maxStack = 1; owner.methods.add(identity);
            var chain = new InsnList(); chain.add(new VarInsnNode(ALOAD, 0));
            chain.add(new MethodInsnNode(INVOKESTATIC, owner.name, identity.name, identity.desc, false));
            method.instructions.insert(query, chain);
        }
    }

    private static void magnet(ClassNode owner, String variant) {
        boolean tracker = owner.name.endsWith("MagnetPullTracker");
        for (var method : owner.methods) {
            if (method.name.equals("inventoryTick") || method.name.equals("pushItem") || method.name.equals("onLevelTick")) { method.instructions.insert(new InsnNode(NOP)); }
        }
        // A lambda's generated spelling is not its contract. Exercise a real renamed target plus an unrelated method.
        if (variant.equals("magnet-compatible")) {
            for (var method : owner.methods) {
                if (!method.name.equals("tickMagnet") && !method.name.equals("onLevelTick")) { continue; }
                for (var node : method.instructions) {
                    if (!(node instanceof InvokeDynamicInsnNode dynamic) || dynamic.bsmArgs.length != 3
                            || !(dynamic.bsmArgs[1] instanceof Handle target) || !target.getOwner().equals(owner.name)) { continue; }
                    var lambda = owner.methods.stream().filter(value -> value.name.equals(target.getName()) && value.desc.equals(target.getDesc())).findFirst().orElseThrow();
                    lambda.name = "sgtest$renamedLambda";
                    dynamic.bsmArgs[1] = new Handle(target.getTag(), target.getOwner(), lambda.name, target.getDesc(), target.isInterface());
                }
            }
        } else if (tracker == variant.equals("magnet-queue")) {
            String caller = tracker ? "lambda$onLevelTick$0" : "canAffectItem";
            MethodInsnNode original = null;
            for (var method : owner.methods) {
                if (!method.name.equals(caller)) { continue; }
                for (var node : method.instructions) {
                    if (node instanceof MethodInsnNode call && call.name.equals(tracker ? "push" : "test")) { original = call; break; }
                }
            }
            if (original == null) { throw new IllegalStateException("Native magnet fault site unavailable"); }
            String descriptor = tracker ? "(Lnet/minecraft/world/entity/item/ItemEntity;DDD)V"
                    : "(Lnet/minecraft/world/item/crafting/Ingredient;Lnet/minecraft/world/item/ItemStack;)Z";
            var alias = new MethodNode(ACC_PRIVATE | ACC_STATIC, "sgtest$unprovedAlias", descriptor, null, null);
            alias.instructions.add(new VarInsnNode(ALOAD, 0));
            if (tracker) {
                alias.instructions.add(new VarInsnNode(DLOAD, 1)); alias.instructions.add(new VarInsnNode(DLOAD, 3)); alias.instructions.add(new VarInsnNode(DLOAD, 5));
            } else { alias.instructions.add(new VarInsnNode(ALOAD, 1)); }
            alias.instructions.add(new MethodInsnNode(original.getOpcode(), original.owner, original.name, original.desc, original.itf));
            alias.instructions.add(new InsnNode(tracker ? RETURN : IRETURN)); alias.maxLocals = tracker ? 7 : 2; alias.maxStack = alias.maxLocals;
            owner.methods.add(alias); original.setOpcode(INVOKESTATIC); original.owner = owner.name; original.name = alias.name; original.desc = descriptor; original.itf = false;
        }
    }
}
