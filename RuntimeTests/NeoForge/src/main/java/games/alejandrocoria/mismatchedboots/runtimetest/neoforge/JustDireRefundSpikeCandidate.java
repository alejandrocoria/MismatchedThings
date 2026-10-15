package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveState;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireUpgradesCompatibility;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Opt-in test candidate. No production registration, lifecycle callback or generic refund account. */
public final class JustDireRefundSpikeCandidate {
    static final List<String> NAMES = List.of("stepheight", "jumpboost", "groundstomp", "negatefalldamage", "earthquake");
    static final Codec<List<String>> CODEC = Codec.STRING.listOf(0, 5).flatXmap(
            JustDireRefundSpikeCandidate::canonical, JustDireRefundSpikeCandidate::canonical);
    private static final DeferredRegister<DataComponentType<?>> TYPES = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, "mismatchedboots_runtime_test");
    static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> CREDITS = TYPES.register("jdt_refundable_upgrades",
            () -> DataComponentType.<List<String>>builder().persistent(CODEC).networkSynchronized(ByteBufCodecs.fromCodec(CODEC)).build());
    static final List<String> trace = new ArrayList<>();
    static ItemStack faultTarget;
    static String fault = "none";
    static int captures, completions, wrappers;

    private JustDireRefundSpikeCandidate() {}

    static void register(IEventBus bus) { TYPES.register(bus); }
    private static DataResult<List<String>> canonical(List<String> names) {
        if (names.size() > 5 || names.stream().anyMatch(name -> !NAMES.contains(name)) || names.stream().distinct().count() != names.size()) {
            return DataResult.error(() -> "Unknown, duplicate or oversized JDT refund credit list");
        }
        return DataResult.success(NAMES.stream().filter(names::contains).toList());
    }
    static List<String> credits(ItemStack stack) { return stack.getOrDefault(CREDITS.get(), List.of()); }
    static void set(ItemStack stack, List<String> names) { stack.set(CREDITS.get(), canonical(names).getOrThrow()); }

    static ItemStack creation(ItemStack root) throws ReflectiveOperationException {
        var output = root.copy();
        var owner = ModContent.getMixedBootsData(root).orElseThrow().left().source();
        var names = new ArrayList<String>();
        if (JustDireUpgradesCompatibility.nativeBoot(owner)) {
            var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
            var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
            var catalog = (Set<?>) tool.getMethod("getAllAbilities").invoke(owner.getItem());
            for (String name : NAMES) {
                if (catalog.contains(ability.getField(name.toUpperCase(Locale.ROOT)).get(null)) && installed(owner, name)) { names.add(name); }
            }
        }
        set(output, names); return output;
    }

    /** Applied to defensive real menu preview/take results by the fixture, not hooked into production. */
    static ItemStack installation(ItemStack previous, ItemStack assembled, String name) {
        if (!NAMES.contains(name) || installed(previous, name) || !installed(assembled, name)) { throw new IllegalArgumentException("Not a fresh native root installation"); }
        var output = assembled.copy(); var names = new ArrayList<>(credits(previous)); names.add(name);
        set(output, names); return output;
    }

    static boolean installed(ItemStack stack, String name) {
        return stack.getOrDefault(JustDireAttributeTestSupport.<Boolean>component(name + "_upgrade_installed"), false);
    }

    public static Capture capture(ItemStack root, int count, LivingEntity owner) {
        if (!(owner instanceof ServerPlayer player) || player.hasInfiniteMaterials() || player.isSpectator()
                || count != 1 || root.getCount() != 1 || player.getItemBySlot(EquipmentSlot.FEET) != root
                || !(root.getItem() instanceof MixedBootsItem) || ModContent.getMixedBootsData(root).isEmpty()
                || root.getMaxDamage() <= 0 || root.getDamageValue() < root.getMaxDamage()) { return null; }
        var names = canonical(credits(root)).result().orElse(null);
        if (names == null || names.isEmpty() || names.stream().anyMatch(name -> !installed(root, name))) { return null; }
        captures++;
        if (root == faultTarget) { trace.add("capture"); }
        // Empty-stack getters hide components. Retain the live map before shrink, plus a detached snapshot.
        return new Capture(root, player, JustDireActiveState.snapshot(root), root.getComponents(), names);
    }

    public static void complete(Capture capture) {
        if (capture == null || capture.finished) { return; } capture.finished = true;
        if (capture.root == faultTarget) { trace.add("complete"); }
        if (!capture.root.isEmpty() || capture.player.getItemBySlot(EquipmentSlot.FEET) != capture.root) { return; }
        var shadow = new ItemStack(capture.before.getItem(), 1);
        List.copyOf(shadow.getComponents().keySet()).forEach(shadow::remove);
        shadow.applyComponents(capture.live);
        if (!ItemStack.matches(capture.before, shadow)) { return; }
        completions++;
        // Authorization is complete before world callbacks. Reentry cannot consume the now-empty root again.
        for (String name : capture.names) {
            var item = BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", "upgrade_" + name)).orElseThrow();
            capture.player.drop(new ItemStack(item), true);
        }
    }

    public static void foreignBefore(ItemStack root) {
        wrappers++;
        if (root != faultTarget) { return; } trace.add("foreign-before");
    }
    public static void foreignAfter(ItemStack root, LivingEntity owner) {
        if (root != faultTarget) { return; } trace.add("foreign-after");
        if (fault.equals("replace")) { owner.setItemSlot(EquipmentSlot.FEET, new ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS)); }
        if (fault.equals("edit")) { root.set(CREDITS.get(), List.of("earthquake")); }
        if (fault.equals("refill")) { root.setCount(1); }
    }

    public static final class Capture {
        private final ItemStack root, before;
        private final ServerPlayer player;
        private final DataComponentMap live;
        private final List<String> names;
        private boolean finished;
        private Capture(ItemStack root, ServerPlayer player, ItemStack before, DataComponentMap live, List<String> names) {
            this.root = root; this.player = player; this.before = before; this.live = live; this.names = names;
        }
    }
}
