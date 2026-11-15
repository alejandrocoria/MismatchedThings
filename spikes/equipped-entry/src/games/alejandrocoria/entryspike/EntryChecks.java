package games.alejandrocoria.entryspike;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import games.alejandrocoria.mismatchedleggings.test.render.RecordingSubmits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real baked native armor-layer entry and native submissions, not a replacement equipment renderer. */
public final class EntryChecks {
    public static final List<Call> calls = new ArrayList<>();
    public static boolean active;
    public static int lookups;
    public static int providers;
    public static int forwards;
    private static int ticks;
    private static int stage;
    private static int shutdownTicks;
    private static CompletableFuture<Void> reload;
    private static final StringBuilder REPORT = new StringBuilder();

    private EntryChecks() {}

    public static void tick(Minecraft client) {
        if (stage == 3) { return; }
        try {
            require(++ticks < 2400, "Entry spike timed out");
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated client became visible");
            if (stage == 2) {
                if (++shutdownTicks >= 5 && client.level == null && !client.hasSingleplayerServer()) {
                    stage = 3;
                    SkeletonChecks.passed("client");
                    client.stop();
                }
                return;
            }
            if (client.level == null || client.player == null || client.screen != null || client.getOverlay() != null) { return; }
            if (stage == 0) {
                run(client);
                reload = client.reloadResourcePacks();
                stage = 1;
            } else if (stage == 1 && reload.isDone()) {
                reload.join();
                run(client);
                Files.writeString(Path.of(System.getProperty("leggings.test.report"), "entry-spike.txt"), REPORT.toString());
                client.disconnectWithSavingScreen();
                stage = 2;
            }
        } catch (Throwable failure) {
            active = false;
            stage = 3;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static ItemStack pair(boolean boots, ItemStack left, ItemStack right) throws Exception {
        String product = boots ? "boots" : "leggings";
        String name = boots ? "Boots" : "Leggings";
        var factory = Class.forName("games.alejandrocoria.mismatched" + product + ".composition.Mixed" + name + "Factory");
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, left, right);
        return (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
    }

    private static boolean installed(String product) {
        return BuiltInRegistries.ITEM.containsKey(Identifier.parse("mismatched" + product + ":mixed_" + product));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RecordingSubmits submit(Minecraft client, HumanoidRenderState state) throws Exception {
        var renderer = client.getEntityRenderDispatcher().getRenderer(state);
        require(renderer instanceof LivingEntityRenderer, "No native living renderer for " + state.getClass());
        var field = LivingEntityRenderer.class.getDeclaredField("layers");
        field.setAccessible(true);
        var layers = (List<?>) field.get(renderer);
        var armor = (HumanoidArmorLayer) layers.stream().filter(HumanoidArmorLayer.class::isInstance).findFirst().orElseThrow();
        var output = new RecordingSubmits();
        calls.clear();
        lookups = providers = forwards = 0;
        active = true;
        try {
            armor.submit(new PoseStack(), output, 15728880, state, 0, 0);
            for (var node : output.nodes) {
                ((Model) node.model()).setupAnim(state);
            }
        } finally { active = false; }
        require(calls.stream().allMatch(call -> call.light == 15728880 && call.outline == state.outlineColor), "Entry lost light/outline");
        if (Boolean.getBoolean("entry.spike.neoforge")) {
            require(providers == calls.size(), "Native extensions resolved twice or bypassed: " + providers + " / " + calls.size());
        }
        return output;
    }

    private static List<HumanoidRenderState> states() {
        var adult = new ZombieRenderState(); adult.entityType = EntityType.ZOMBIE; adult.isCrouching = true;
        var baby = new ZombieRenderState(); baby.entityType = EntityType.ZOMBIE; baby.isBaby = true;
        var stand = new ArmorStandRenderState(); stand.entityType = EntityType.ARMOR_STAND;
        var small = new ArmorStandRenderState(); small.entityType = EntityType.ARMOR_STAND; small.isBaby = true; small.isSmall = true;
        var player = new AvatarRenderState(); player.entityType = EntityType.PLAYER; player.walkAnimationPos = 1.4F; player.walkAnimationSpeed = .7F;
        for (int i = 0; i < 32; i++) {
            player.skin = DefaultPlayerSkin.get(new UUID(0, i));
            if (player.skin.model().name().equals("WIDE")) { break; }
        }
        require(player.skin.model().name().equals("WIDE"), "No native wide skin selected");
        var slim = new AvatarRenderState(); slim.entityType = EntityType.PLAYER; slim.isPassenger = true;
        for (int i = 0; i < 32; i++) {
            slim.skin = DefaultPlayerSkin.get(new UUID(0, i));
            if (slim.skin.model().name().equals("SLIM")) { break; }
        }
        require(slim.skin.model().name().equals("SLIM"), "No native slim skin selected");
        return List.of(adult, baby, stand, small, player, slim);
    }

    private static void run(Minecraft client) throws Exception {
        boolean boots = installed("boots");
        boolean legs = installed("leggings");
        String graph = System.getProperty("entry.spike.graph");
        boolean baseline = System.getProperty("entry.spike.mode").equals("baseline");
        require(boots == !graph.equals("legs") && legs == !graph.equals("boots"), "Wrong actual installed graph");
        witness(boots, legs, baseline);
        int orientation = 0;
        for (var state : states()) {
            state.outlineColor = 0xFF123456;
            state.chestEquipment = new ItemStack(Items.IRON_CHESTPLATE);
            state.headEquipment = new ItemStack(Items.IRON_HELMET);
            state.legsEquipment = legs ? pair(false, new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.GOLDEN_LEGGINGS))
                    : new ItemStack(Items.IRON_LEGGINGS);
            boolean inverse = orientation++ % 2 == 1;
            var first = inverse ? Items.GOLDEN_BOOTS : Items.IRON_BOOTS;
            var second = inverse ? Items.IRON_BOOTS : Items.GOLDEN_BOOTS;
            state.feetEquipment = boots ? pair(true, new ItemStack(first), new ItemStack(second))
                    : new ItemStack(Items.IRON_BOOTS);
            var feetBefore = state.feetEquipment.copy();
            var legsBefore = state.legsEquipment.copy();
            submit(client, state);
            require(calls.size() == 4 + (boots ? 1 : 0) + (legs ? 1 : 0), "Duplicate/missing original or source calls: " + calls);
            require(calls.stream().noneMatch(call -> id(call.stack).startsWith("mismatched")), "Split root leaked to native draw");
            require(lookups == 1, "Successful FEET should select the native model once");
            if (boots) {
                var feet = calls.stream().filter(call -> call.stack.is(Items.IRON_BOOTS) || call.stack.is(Items.GOLDEN_BOOTS)).toList();
                require(feet.size() == 2 && feet.get(0).stack.is(first) && feet.get(1).stack.is(second), "Source submission orientation changed");
                require(feet.get(0).model.root() != feet.get(1).model.root(), "Both source views own the same foot");
            }
            require(ItemStack.matches(feetBefore, state.feetEquipment) && ItemStack.matches(legsBefore, state.legsEquipment), "Entry mutated original roots");
            var expectedFeet = state.isBaby && state.entityType != EntityType.ARMOR_STAND
                    ? EquipmentClientInfo.LayerType.HUMANOID_BABY : EquipmentClientInfo.LayerType.HUMANOID;
            require(calls.stream().filter(call -> call.stack.is(Items.IRON_BOOTS) || call.stack.is(Items.GOLDEN_BOOTS))
                    .allMatch(call -> call.layer == expectedFeet), "Incorrect native age/stand layer");
            if (Boolean.getBoolean("entry.spike.forward")) {
                require(forwards == 2 + (boots ? 0 : 1) + (legs ? 0 : 1), "Foreign forwarding lost native slots or received cancelled mixed roots");
            }
        }
        if (boots) { bootsControls(client, baseline); }
        if (Boolean.getBoolean("entry.spike.native")) { nativeControls(client); }
        REPORT.append("PASS phase=").append(stage).append(" graph=").append(graph).append(" mode=").append(baseline ? "baseline" : "candidate")
                .append(" order=").append(System.getProperty("entry.spike.order")).append(" actual layer entry, age/stand/wide+slim player, mixed/native slots and read-only roots; NF providers counted where present\n");
    }

    private static void witness(boolean boots, boolean legs, boolean baseline) throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(Path.of(".mixin.out/class/net/minecraft/client/renderer/entity/layers/HumanoidArmorLayer.class"))).accept(node, 0);
        var owners = new HashMap<String, Integer>();
        for (var method : node.methods) {
            if (method.visibleAnnotations == null) { continue; }
            for (var annotation : method.visibleAnnotations) {
                if (!annotation.desc.endsWith("/MixinMerged;") || annotation.values == null) { continue; }
                int owner = annotation.values.indexOf("mixin") + 1;
                int priority = annotation.values.indexOf("priority") + 1;
                if (owner > 0 && priority > 0) { owners.put(annotation.values.get(owner).toString(), (Integer) annotation.values.get(priority)); }
            }
        }
        String original = "games.alejandrocoria.mismatchedboots.client.mixin.HumanoidArmorLayerMixin";
        String candidate = "games.alejandrocoria.entryspike.mixin.BootsEntryMixin";
        String leggings = "games.alejandrocoria.mismatchedleggings.mixin.client.ArmorLayerMixin";
        require(owners.containsKey(original) == (boots && baseline), "Unexpected old exclusive redirect application");
        require(owners.containsKey(candidate) == (boots && !baseline), "Candidate entry did not match installed graph");
        require(owners.containsKey(leggings) == legs, "Leggings entry did not match installed graph");
        if (boots && !baseline) {
            require(owners.get(candidate) == (System.getProperty("entry.spike.order").equals("after") ? 900 : 1100), "Candidate relative priority not exercised");
        }
        if (legs) { require(owners.get(leggings) == 1000, "Unexpected native Leggings priority"); }
        REPORT.append("Actual merged entries: old=").append(owners.get(original)).append(" candidate=").append(owners.get(candidate))
                .append(" leggings=").append(owners.get(leggings)).append('\n');
    }

    private static void bootsControls(Minecraft client, boolean baseline) throws Exception {
        var state = new ZombieRenderState(); state.entityType = EntityType.ZOMBIE;
        var unsupported = new ItemStack(Items.GOLDEN_BOOTS);
        unsupported.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build());
        state.feetEquipment = pair(true, new ItemStack(Items.IRON_BOOTS), unsupported);
        submit(client, state);
        require(calls.size() == 1 && id(calls.getFirst().stack).equals("mismatchedboots:mixed_boots"), "Fallback was not exactly one original owner call");
        require(lookups == (baseline ? 1 : 2), "Declined split's native lookup count changed: " + lookups);
        REPORT.append("Declined FEET: owner calls=1, model-set lookups=").append(lookups).append(", providers=").append(providers).append('\n');

        var valid = pair(true, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS));
        var foreign = new ItemStack(Items.DIAMOND_BOOTS);
        foreign.applyComponents(valid.getComponentsPatch());
        state.feetEquipment = foreign;
        submit(client, state);
        require(calls.size() == (baseline ? 2 : 1), "Copied foreign-data ownership control changed");
        REPORT.append("Foreign item with copied components: native calls=").append(calls.size()).append(" (baseline broad redirect versus candidate owned-item gate)\n");

        valid.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build());
        state.feetEquipment = valid;
        require(!HumanoidArmorLayer.shouldRender(valid, EquipmentSlot.FEET), "Boots native asset admission changed");
        submit(client, state);
        require(calls.isEmpty() && lookups == 0, "Absent owner asset acquired a new fallback");
        state.headEquipment = pair(true, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS));
        state.feetEquipment = ItemStack.EMPTY;
        submit(client, state);
        require(calls.isEmpty(), "Wrong-slot mixed boots claimed HEAD");
        state.headEquipment = ItemStack.EMPTY;
        state.feetEquipment = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("mismatchedboots:mixed_boots")));
        submit(client, state);
        require(calls.isEmpty(), "Bare root acquired rendering");
        state.feetEquipment.set(DataComponents.EQUIPPABLE, new ItemStack(Items.IRON_BOOTS).get(DataComponents.EQUIPPABLE));
        submit(client, state);
        require(calls.size() == 1 && id(calls.getFirst().stack).equals("mismatchedboots:mixed_boots"),
                "Invalid data with an explicit asset did not retain one ordinary owner draw");

        var root = pair(true, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS));
        var ops = client.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var raw = (CompoundTag) ItemStack.CODEC.encodeStart(ops, root).getOrThrow();
        raw.getCompoundOrEmpty("components").getCompoundOrEmpty("mismatchedboots:mixed_boots_data")
                .getCompoundOrEmpty("left").putString("id", "entry_spike:missing_boots");
        state.feetEquipment = ItemStack.CODEC.parse(ops, raw).getOrThrow();
        require(state.feetEquipment.getComponents().stream().anyMatch(component -> component.type().toString().contains("suspended")), "Control did not actually suspend");
        submit(client, state);
        require(calls.isEmpty(), "Suspended root acquired a new draw");

        var leather = new ItemStack(Items.LEATHER_BOOTS);
        leather.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3366CC));
        leather.set(DataComponents.TRIM, new ArmorTrim(client.level.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.QUARTZ),
                client.level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.COAST)));
        state.feetEquipment = pair(true, leather, new ItemStack(Items.GOLDEN_BOOTS));
        state.feetEquipment.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        var before = state.feetEquipment.copy();
        var nodes = submit(client, state);
        require(calls.size() == 2 && calls.stream().allMatch(call -> call.stack.hasFoil())
                && calls.getFirst().stack.get(DataComponents.DYED_COLOR).rgb() == 0x3366CC, "Entry lost source dye or live root foil");
        require(nodes.nodes.size() > 2 && ItemStack.matches(before, state.feetEquipment), "Native material/foil nodes missing or history changed");
        require(nodes.nodes.stream().anyMatch(node -> node.sprite() != null), "Native trim sprite was lost");
    }

    private static void nativeControls(Minecraft client) throws Exception {
        require(installed("boots"), "Native source sample needs Boots");
        for (String sample : List.of("rootsclassic:sylvan_boots", "rootsclassic:wildwood_boots", "iceandfire:dragonsteel_fire_boots")) {
            var key = Identifier.parse(sample);
            require(BuiltInRegistries.ITEM.containsKey(key), "Native sample is missing: " + sample);
            var source = new ItemStack(BuiltInRegistries.ITEM.getValue(key));
            var state = new ZombieRenderState(); state.entityType = EntityType.ZOMBIE;
            state.feetEquipment = source.copy();
            var nativeNodes = submit(client, state);
            require(!nativeNodes.nodes.isEmpty(), "Native sample has no native rendering: " + id(source));
            state.feetEquipment = pair(true, source, new ItemStack(Items.IRON_BOOTS));
            var before = state.feetEquipment.copy();
            var mixedNodes = submit(client, state);
            require(calls.size() == 2 && providers == 2 && mixedNodes.nodes.size() == nativeNodes.nodes.size() + 1,
                    "Native source lost or duplicated material nodes: " + id(source));
            require(mixedNodes.nodes.stream().allMatch(node -> node.model().getClass().getName().contains("MixedBootsEquipmentRenderer$SideModel")
                            && node.model().root().getAllParts().stream().anyMatch(part -> part.visible && !part.isEmpty())),
                    "A resolved source lost its owned visible foot geometry: " + id(source));
            require(ItemStack.matches(before, state.feetEquipment), "Native entry mutated source history");
            REPORT.append("Native control + actual mixed layer: ").append(id(source)).append(" nodes=").append(mixedNodes.nodes.size()).append(" providers=").append(providers).append('\n');
        }
    }

    private static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }

    public record Call(ItemStack stack, EquipmentClientInfo.LayerType layer, Model<?> model, int light, int outline) {}
}
