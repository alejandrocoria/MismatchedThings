package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.component;

/** Disposable candidate, not a production API. Scopes cover one synchronous full tooltip/gather call. */
public final class AetherTooltipSpike {
    static final List<String> FAMILIES = List.of("beast_pelt", "burrukai_plate", "zanite", "arkenium", "gravitite", "neptune", "sentry");
    private static Capture current;
    static int faultCount;
    static boolean formatterFault, factoryMissing;

    record Capture(List<Component> lines, Map<FormattedText, TooltipComponent> images,
                   int calls, int nativeAttributes, int liveAttributes) {}
    private static boolean candidate;
    private static int calls, added, start, live;
    private static Map<FormattedText, TooltipComponent> images;

    private AetherTooltipSpike() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (RenderTooltipEvent.GatherComponents event) -> {
            if (current == null) { return; }
            var elements = event.getTooltipElements();
            for (int i = 0; i < elements.size(); i++) {
                var image = elements.get(i).left().map(current.images()::get).orElse(null);
                if (image != null) { elements.set(i, Either.right(image)); }
            }
        });
    }

    public static boolean replacing() { return candidate; }
    public static void beforeAttributes(List<Component> lines) {
        if (images != null) { calls++; start = lines.size(); }
    }
    public static void afterAttributes(List<Component> lines) {
        if (images != null && !candidate) { added += lines.size() - start; }
    }

    static Capture capture(Minecraft client, ItemStack stack, TooltipFlag flag, boolean shift, boolean proposal) throws Exception {
        candidate = proposal && ModContent.isMixedBoots(stack)
                && BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(Identifier.parse("aether_ii:armor_set"));
        images = new IdentityHashMap<>(); calls = 0; added = 0; live = 0;
        String before = encoded(client, stack);
        try {
            var lines = new ArrayList<>(JustDireTooltipTestSupport.capture(client, stack, flag, shift));
            if (!before.equals(encoded(client, stack))) { throw new AssertionError("Tooltip changed serialized root/history"); }
            return new Capture(lines, images, calls, added, live);
        } finally { candidate = false; images = null; }
    }

    static <T> T gathering(Capture capture, java.util.function.Supplier<T> action) {
        current = capture;
        try { return action.get(); } finally { current = null; }
    }

    public static void root(ItemStack stack, TooltipFlag flag, Consumer<Component> output) {
        if (!candidate || display(stack).hideTooltip()) { return; }
        var block = new ArrayList<Component>();
        try {
            root(Minecraft.getInstance(), stack, flag, block);
            if (formatterFault) { throw new ReflectiveOperationException("Controlled populated-block fault"); }
            block.forEach(output);
        } catch (Exception failure) {
            if (!formatterFault || !(failure instanceof ReflectiveOperationException)) {
                throw new IllegalStateException("Candidate root presentation", failure);
            }
            faultCount++; live = 0; // Scratch is not published; gameplay remains untouched.
        }
    }

    public static void source(ItemStack source, TooltipFlag flag, Consumer<Component> output) {
        if (!candidate || display(source).hideTooltip()) { return; }
        try {
            var tier = component("reinforcement_tier");
            if (display(source).shows(tier) && source.get(tier) instanceof TooltipProvider provider) {
                provider.addToTooltip(Item.TooltipContext.of(Minecraft.getInstance().level), output, flag, source);
            }
            if (!display(source).shows(component("charms"))) { return; }
            // Rebuild through the registered codec: ItemStack.copy can share the native mutable holder payload.
            var client = Minecraft.getInstance();
            var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            ItemStack copy = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, source).getOrThrow()).getOrThrow();
            var value = copy.get(component("charms"));
            if (value == null) { return; }
            var installed = new ArrayList<Object>();
            for (Object holder : (List<?>) value.getClass().getMethod("charmHolders").invoke(value)) {
                ItemStack charm = (ItemStack) holder.getClass().getMethod("getStack").invoke(holder);
                if (!charm.isEmpty()) { installed.add(holder); }
            }
            if (installed.isEmpty()) { return; }
            if (!factoryMissing && installed.size() <= 4) {
                var type = Class.forName("com.aetherteam.aetherii.client.renderer.item.tooltip.ClientCharmTooltip$CharmTooltip");
                var image = (TooltipComponent) type.getConstructor(ItemStack.class, List.class).newInstance(copy, List.copyOf(installed));
                ClientTooltipComponent.create(image); // Prove the actual registered native factory exists before publishing a marker.
                Component marker = Component.empty();
                images.put(marker, image); output.accept(marker);
            } else {
                for (Object holder : installed) {
                    ItemStack charm = (ItemStack) holder.getClass().getMethod("getStack").invoke(holder);
                    output.accept(charm.getHoverName().copy().withStyle(ChatFormatting.GRAY));
                }
            }
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Candidate source presentation", failure); }
    }

    @SuppressWarnings("unchecked")
    private static void root(Minecraft client, ItemStack root, TooltipFlag flag, List<Component> lines) throws Exception {
        if (!display(root).shows(component("armor_set"))) { return; }
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        Map<String, ItemStack> sets = new LinkedHashMap<>();
        for (var source : List.of(data.left().source(), data.right().source())) {
            String path = BuiltInRegistries.ITEM.getKey(source.getItem()).toString();
            for (var family : FAMILIES) {
                if (path.equals("aether_ii:" + family + "_boots")
                        && java.util.Objects.equals(source.get(component("armor_set")),
                        AetherIICompatibilityTest.item(family + "_boots").get(component("armor_set")))) {
                    sets.putIfAbsent(family, source);
                }
            }
        }
        boolean worn = root == client.player.getItemBySlot(EquipmentSlot.FEET);
        var api = new AetherIICompatibilityTest.NativeApi();
        for (var entry : sets.entrySet()) {
            String family = entry.getKey(); ItemStack source = entry.getValue();
            lines.add(Component.empty());
            lines.add(source.getItemName().copy().withStyle(ChatFormatting.GRAY));
            String key = source.getItem().getDescriptionId() + ".nitrogen_internals.ability.tooltip.";
            if (!family.equals("arkenium")) { lines.add(Component.translatable(key + "1")); }
            if (family.equals("sentry")) { continue; }
            var set = (TagKey<Item>) source.get(component("armor_set"));
            int count = worn ? Math.min(3, api.count(client.player, set)) : -1;
            lines.add(Component.translatable(key + "2", Component.literal(count < 0 ? "–/3" : count + "/3")
                    .withStyle(count == 3 ? ChatFormatting.WHITE : ChatFormatting.GRAY)));
            if (!worn || !display(root).shows(DataComponents.ATTRIBUTE_MODIFIERS)) { continue; }
            Class<?> accessor = Class.forName("com.aetherteam.aetherii.mixin.mixins.common.accessor.AttributeMapAccessor");
            var attributes = (Map<Holder<Attribute>, AttributeInstance>) accessor.getMethod("aether_ii$getAttributes").invoke(client.player.getAttributes());
            for (var attribute : attributes.entrySet()) {
                for (var modifier : attribute.getValue().getModifiers()) {
                    if (modifier.id().getNamespace().equals("aether_ii")
                            && modifier.id().getPath().startsWith("armor_set.ability." + family + ".")) {
                        lines.add(attribute.getKey().value().toComponent(modifier, flag)); live++;
                    }
                }
            }
        }
    }

    static TooltipDisplay display(ItemStack stack) {
        return stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
    }
    static String encoded(Minecraft client, ItemStack stack) {
        return ItemStack.CODEC.encodeStart(client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), stack).getOrThrow().toString();
    }
}
