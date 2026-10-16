package games.alejandrocoria.mismatchedboots.compatibility.justdirethings;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Only native selection, metadata and bounded callback seams; no full screen fingerprint. */
public final class JustDireSettingsScreenShape implements Opcodes {
    public static final String SCREEN = "com/direwolf20/justdirethings/client/screens/ToolSettingScreen";
    private static final String TOOL = "com/direwolf20/justdirethings/common/items/interfaces/ToggleableTool";
    private static final String STACK = "net/minecraft/world/item/ItemStack";
    private static final String MIXIN = "games.alejandrocoria.mismatchedboots.mixin.JustDireSettingsScreenMixin";
    private static final String READY = "mismatchedboots$jdtSettingsReady";
    private static final Map<String, String> METHODS = Map.ofEntries(
            Map.entry("<init>", "(Lcom/direwolf20/justdirethings/common/containers/ToolSettingContainer;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/chat/Component;)V"),
            Map.entry("init", "()V"), Map.entry("refreshButtons", "()V"), Map.entry("clearMaps", "()V"),
            Map.entry("refreshToolAndSlots", "()V"),
            Map.entry("mouseClicked", "(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"),
            Map.entry("toggleSetting", "(Ljava/lang/String;)V"), Map.entry("cycleSetting", "(Ljava/lang/String;)V"),
            Map.entry("setSetting", "(Ljava/lang/String;I)V"), Map.entry("toggleRender", "(Ljava/lang/String;I)V"),
            Map.entry("setCustomSetting2", "(Ljava/lang/String;I)V"), Map.entry("sendBinding", "(Ljava/lang/String;IIZ)V"));
    private static final Map<String, Set<String>> HOOKS = Map.ofEntries(
            Map.entry("<init>", Set.of("Metadata")), Map.entry("init", Set.of("Metadata", "Abilities")),
            Map.entry("refreshButtons", Set.of("Metadata", "Parameters", "Value", "RefreshGuard", "Restrictions")),
            Map.entry("mouseClicked", Set.of("Metadata", "Abilities", "MouseCaptureGuard", "Equipped")),
            Map.entry("refreshToolAndSlots", Set.of("Select")),
            Map.entry("toggleSetting", Set.of("Toggle")), Map.entry("setSetting", Set.of("Set")),
            Map.entry("toggleRender", Set.of("Particles")), Map.entry("sendBinding", Set.of("Binding")),
            Map.entry("cycleSetting", Set.of("Unsupported")), Map.entry("setCustomSetting2", Set.of("Unsupported")));

    private JustDireSettingsScreenShape() {}

    static boolean entry(ClassNode node) {
        if (!"net/minecraft/client/gui/screens/inventory/AbstractContainerScreen".equals(node.superName)) { return false; }
        for (var field : Map.of("tool", "L" + STACK + ";", "toolSlot", "I", "abilities", "Ljava/util/EnumSet;",
                "bindingEnabled", "Z", "requireEquipped", "Z").entrySet()) {
            if (node.fields.stream().noneMatch(f -> f.name.equals(field.getKey()) && f.desc.equals(field.getValue()) && (f.access & ACC_STATIC) == 0)) { return false; }
        }
        for (String field : List.of("leftRightClickButtons", "bindingButtons", "requireEquippedButtons", "buttonToAbilityMap")) {
            if (node.fields.stream().noneMatch(f -> f.name.equals(field) && f.desc.equals("Ljava/util/Map;") && (f.access & (ACC_FINAL | ACC_STATIC)) == ACC_FINAL)) { return false; }
        }
        if (METHODS.entrySet().stream().anyMatch(entry -> node.methods.stream().noneMatch(m -> m.name.equals(entry.getKey()) && m.desc.equals(entry.getValue())
                && (m.access & (ACC_STATIC | ACC_ABSTRACT | ACC_NATIVE)) == 0))) { return false; }
        return calls(node, "init", TOOL, "getAbilities") && calls(node, "mouseClicked", TOOL, "getAbilities")
                && calls(node, "refreshButtons", TOOL, "getAbilityParams") && calls(node, "refreshButtons", TOOL, "getToolValue")
                && List.of("<init>", "init", "refreshButtons", "mouseClicked").stream().allMatch(name -> calls(node, name, STACK, "getItem"));
    }

    private static boolean calls(ClassNode node, String method, String owner, String name) {
        return node.methods.stream().filter(m -> m.name.equals(method) && m.desc.equals(METHODS.get(method))).anyMatch(m -> {
            for (var instruction : m.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) { return true; }
            }
            return false;
        });
    }

    static boolean publish(ClassNode node) {
        MethodNode ready = node.methods.stream().filter(m -> m.name.equals(READY) && m.desc.equals("()Z")).findFirst().orElse(null);
        if (ready == null) { return false; }
        var code = new java.util.ArrayList<AbstractInsnNode>();
        for (var instruction : ready.instructions) { if (instruction.getOpcode() >= 0) { code.add(instruction); } }
        boolean intact = owned(ready) && ready.access == (ACC_PRIVATE | ACC_STATIC) && code.size() == 2
                && code.getFirst().getOpcode() == ICONST_0 && code.getLast().getOpcode() == IRETURN;
        ready.instructions.clear(); ready.instructions.add(new InsnNode(ICONST_0)); ready.instructions.add(new InsnNode(IRETURN));
        ready.tryCatchBlocks.clear(); ready.maxStack = 1;
        if (!intact) { return false; }
        for (var requirement : HOOKS.entrySet()) {
            for (String hook : requirement.getValue()) {
                if (node.methods.stream().filter(m -> m.name.equals(requirement.getKey()) && m.desc.equals(METHODS.get(requirement.getKey())))
                        .noneMatch(m -> reaches(node, m, "mismatchedboots$jdt" + hook, new HashSet<>()))) { return false; }
            }
        }
        ready.instructions.set(ready.instructions.getFirst(), new InsnNode(ICONST_1));
        return true;
    }

    private static boolean reaches(ClassNode node, MethodNode method, String handler, Set<String> visited) {
        if (!visited.add(method.name + method.desc)) { return false; }
        if (method.name.contains(handler) && owned(method)) { return true; }
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(node.name)
                    && reaches(node, call.name, call.desc, handler, visited)) { return true; }
            // Stacked WrapOperations pass the inner bridge as a lambda, not a direct invocation.
            if (instruction instanceof InvokeDynamicInsnNode call
                    && call.desc.endsWith(")Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;")) {
                for (Object argument : call.bsmArgs) {
                    if (argument instanceof Handle handle && handle.getOwner().equals(node.name)
                            && reaches(node, handle.getName(), handle.getDesc(), handler, visited)) { return true; }
                }
            }
        }
        return false;
    }

    private static boolean reaches(ClassNode node, String name, String desc, String handler, Set<String> visited) {
        MethodNode next = node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElse(null);
        return next != null && reaches(node, next, handler, visited);
    }

    private static boolean owned(MethodNode method) {
        return method.visibleAnnotations != null && method.visibleAnnotations.stream().anyMatch(a ->
                a.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && a.values != null && a.values.contains(MIXIN));
    }
}
