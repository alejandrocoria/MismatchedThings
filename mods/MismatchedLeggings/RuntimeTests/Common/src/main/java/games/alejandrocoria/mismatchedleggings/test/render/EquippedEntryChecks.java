package games.alejandrocoria.mismatchedleggings.test.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.render.EquippedChecks.require;

/** Exact native-slot/source accounting alongside the maintained rendered scene. */
public final class EquippedEntryChecks {
    private static final int LIGHT = 0x00F000F0;
    private static final List<Call> CALLS = new ArrayList<>();
    private static final List<ItemStack> FORWARDED = new ArrayList<>();
    private static boolean active;

    private EquippedEntryChecks() {
    }

    public static void recordCall(ItemStack stack, int light, int outline) {
        if (active) {
            CALLS.add(new Call(stack.copy(), light, outline));
        }
    }

    public static void recordForward(ItemStack stack) {
        if (active) {
            FORWARDED.add(stack.copy());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void run(Minecraft client, String phase) {
        boolean boots = Boolean.getBoolean("leggings.test.boots");
        try {
            var state = new ZombieRenderState();
            state.entityType = EntityType.ZOMBIE;
            state.isCrouching = true;
            state.outlineColor = 0xFF123456;
            state.headEquipment = new ItemStack(Items.IRON_HELMET);
            state.chestEquipment = new ItemStack(Items.IRON_CHESTPLATE);
            var renderer = client.getEntityRenderDispatcher().getRenderer(state);
            require(renderer instanceof LivingEntityRenderer, "Missing baked native living renderer");
            var field = LivingEntityRenderer.class.getDeclaredField("layers");
            field.setAccessible(true);
            var layers = (List<?>) field.get(renderer);
            var armor = (HumanoidArmorLayer) layers.stream().filter(HumanoidArmorLayer.class::isInstance).findFirst().orElseThrow();
            var ops = client.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);

            for (int orientation = 0; orientation < 2; orientation++) {
                state.legsEquipment = RenderFixture.pair(orientation);
                var first = orientation == 0 ? Items.IRON_BOOTS : Items.GOLDEN_BOOTS;
                var second = orientation == 0 ? Items.GOLDEN_BOOTS : Items.IRON_BOOTS;
                state.feetEquipment = boots ? BootsCoexistence.pair(new ItemStack(first), new ItemStack(second)).getFirst()
                        : new ItemStack(Items.IRON_BOOTS);
                var legsBefore = ItemStack.CODEC.encodeStart(ops, state.legsEquipment).getOrThrow();
                var feetBefore = ItemStack.CODEC.encodeStart(ops, state.feetEquipment).getOrThrow();
                var output = new RecordingSubmits();
                CALLS.clear();
                FORWARDED.clear();
                active = true;
                try {
                    armor.submit(new PoseStack(), output, LIGHT, state, 0, 0);
                    for (var node : output.nodes) {
                        ((Model) node.model()).setupAnim(state);
                    }
                } finally {
                    active = false;
                }

                require(CALLS.size() == (boots ? 6 : 5), "Missing or duplicated native/source armor calls");
                require(CALLS.stream().allMatch(call -> call.light == LIGHT && call.outline == state.outlineColor),
                        "Entry lost native light/outline");
                require(CALLS.stream().filter(call -> call.stack.is(Items.IRON_HELMET)).count() == 1
                                && CALLS.stream().filter(call -> call.stack.is(Items.IRON_CHESTPLATE)).count() == 1,
                        "Sibling entry intercepted or duplicated HEAD/CHEST");
                var legs = CALLS.stream().filter(call -> call.stack.is(Items.IRON_LEGGINGS) || call.stack.is(Items.GOLDEN_LEGGINGS)).toList();
                require(legs.size() == 2 && legs.getFirst().stack.is(orientation == 0 ? Items.IRON_LEGGINGS : Items.GOLDEN_LEGGINGS)
                                && legs.getLast().stack.is(orientation == 0 ? Items.GOLDEN_LEGGINGS : Items.IRON_LEGGINGS),
                        "LEGS entry lost source order");
                var feet = CALLS.stream().filter(call -> call.stack.is(Items.IRON_BOOTS) || call.stack.is(Items.GOLDEN_BOOTS)).toList();
                require(boots ? feet.size() == 2 && feet.getFirst().stack.is(first) && feet.getLast().stack.is(second)
                              : feet.size() == 1 && feet.getFirst().stack.is(Items.IRON_BOOTS),
                        "FEET entry lost its independent native/mixed behavior");
                require(FORWARDED.size() == (boots ? 2 : 3)
                                && FORWARDED.stream().filter(stack -> stack.is(Items.IRON_HELMET)).count() == 1
                                && FORWARDED.stream().filter(stack -> stack.is(Items.IRON_CHESTPLATE)).count() == 1
                                && FORWARDED.stream().allMatch(stack -> stack.is(Items.IRON_HELMET)
                                        || stack.is(Items.IRON_CHESTPLATE) || (!boots && stack.is(Items.IRON_BOOTS))),
                        "Compatible forwarder lost native slots or received a cancelled mixed root");
                require(!output.nodes.isEmpty() && legsBefore.equals(ItemStack.CODEC.encodeStart(ops, state.legsEquipment).getOrThrow())
                                && feetBefore.equals(ItemStack.CODEC.encodeStart(ops, state.feetEquipment).getOrThrow()),
                        "Native entry lost deferred submissions or changed source histories");
            }
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "armor-entry-" + phase + ".txt"),
                    "Actual armor-layer entry; both source orders; native HEAD/CHEST; "
                            + (boots ? "independent mixed FEET/LEGS" : "mixed LEGS/native FEET")
                            + "; original-call forwarder once per unclaimed slot; unchanged serialized histories\n");
        } catch (Exception failure) {
            throw new AssertionError("Packaged armor-layer entry check failed", failure);
        }
    }

    private record Call(ItemStack stack, int light, int outline) {
    }
}
