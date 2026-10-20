package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearDefenseApi;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.traitOf;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.view;

/** Production B only: no replacement event listener, damage dispatcher or ready-getter override. */
public final class SilentGearIncomingTest {
    private static int calls, failOn;
    private static Reload reload;
    private SilentGearIncomingTest() {}

    public static void called() {
        calls++;
        if (calls == failOn) { throw new IllegalStateException("Controlled read-only failure after native defensive result"); }
    }

    static void run(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report, String variant) throws Throwable {
        SilentGearTraitSpike.initialize(api); clear(player);
        var heat = api.boot("crimson_iron"); var bounce = SilentGearBounceTest.lined(heat, api, player);
        var roots = NeoForgeSilentGearTest.workbench(player, bounce, new ItemStack(Items.DIAMOND_BOOTS));
        boolean admitted = !List.of("defense-incoming", "defense-negate").contains(variant);
        for (var root : roots) {
            equip(player, root); event(player, player.damageSources().lava(), 20, admitted ? 16.8F : 20);
            event(player, player.damageSources().flyIntoWall(), 20, admitted ? 16 : 20);
        }
        if (!admitted) {
            equip(player, bounce); event(player, player.damageSources().lava(), 20, 16.8F);
            report.append("B shared-flow/negate refusal: both mixed inverses inert; native fire control retained\n"); return;
        }
        if (variant.equals("defense-data") || variant.equals("defense-throw")) {
            failures(player, api, bounce, roots, variant, report); return;
        }
        equip(player, heat); event(player, player.damageSources().lava(), 20, 16.8F);
        equip(player, bounce); event(player, player.damageSources().flyIntoWall(), 20, 16);
        var samples = new ArrayList<>(roots);
        samples.addAll(NeoForgeSilentGearTest.workbench(player, bounce, bounce));
        var low = SilentGearRecognitionTest.trait(heat, api, "heat_resistant", 1);
        var high = SilentGearRecognitionTest.trait(heat, api, "heat_resistant", 5);
        var unequal = NeoForgeSilentGearTest.workbench(player, low, high);
        for (var root : unequal) { equip(player, root); event(player, player.damageSources().lava(), 20, 16); }
        var helmet = api.armor("helmet", "helmet_plates", "crimson_iron");
        for (var root : samples) {
            require(root.has(DataComponents.DAMAGE_RESISTANT) == (samples.indexOf(root) >= 2), "BOTH-source fireproofing changed");
            equip(player, root); calls = 0; event(player, player.damageSources().lava(), 20, 16.8F);
            require(calls == 2, "Two distinct selected IDs must run once each, independent of duplicate sources");
            event(player, player.damageSources().fall(), 20, 20); event(player, player.damageSources().generic(), 20, 20);
            event(player, player.damageSources().playerAttack(player), 20, 20);
            player.setItemSlot(EquipmentSlot.HEAD, helmet.copy());
            event(player, player.damageSources().lava(), 20, 13.6F);
            event(player, player.damageSources().lava(), 8, 5.44F, 20);
            clear(player); player.setItemSlot(EquipmentSlot.MAINHAND, root); event(player, player.damageSources().lava(), 20, 20);
        }
        // Two native selected effects overlap on the same live tag; subtraction uses one common base, not the remainder.
        var binding = new SilentGearDefenseApi(); var bounceEffect = binding.admittedEffect(view(bounce).get("silentgear:bounce"));
        var tag = bounceEffect.getClass().getDeclaredField("damageType"); tag.setAccessible(true); Object savedTag = tag.get(bounceEffect);
        try {
            tag.set(bounceEffect, DamageTypeTags.IS_FIRE);
            equip(player, roots.getFirst()); calls = 0; event(player, player.damageSources().lava(), 20, 12.8F);
            require(calls == 2, "Overlapping effects not dispatched once");
            player.setItemSlot(EquipmentSlot.HEAD, helmet); event(player, player.damageSources().lava(), 20, 9.6F);
        } finally { tag.set(bounceEffect, savedTag); }
        offensive(player, api, roots.getFirst(), helmet);
        var heatEffect = binding.admittedEffect(view(heat).get("silentgear:heat_resistant"));
        var scale = heatEffect.getClass().getDeclaredField("negatedDamageScale"); scale.setAccessible(true); float savedScale = scale.getFloat(heatEffect);
        try {
            scale.setFloat(heatEffect, .5F); equip(player, roots.getFirst()); var zero = event(player, player.damageSources().lava(), 20, 0);
            require(zero.isCanceled(), "Native zero clamp did not cancel positive original damage");
            var empty = event(player, player.damageSources().lava(), 0, 0); require(!empty.isCanceled(), "Zero original damage incorrectly canceled");
        } finally { scale.setFloat(heatEffect, savedScale); }
        equip(player, roots.getFirst()); calls = 0;
        var canceled = new LivingIncomingDamageEvent(player, new DamageContainer(player.damageSources().lava(), 20)); canceled.setCanceled(true); NeoForge.EVENT_BUS.post(canceled);
        require(canceled.isCanceled() && canceled.getAmount() == 20 && calls == 0, "Pre-canceled event acquired mixed work");
        for (String invalid : List.of("count", "slot", "data")) {
            var root = roots.getFirst().copy();
            if (invalid.equals("count")) { root.setCount(2); } else if (invalid.equals("slot")) { root.remove(DataComponents.EQUIPPABLE); }
            else { root.remove(games.alejandrocoria.mismatchedboots.content.ModContent.mixedBootsDataType().orElseThrow()); }
            equip(player, root); calls = 0; event(player, player.damageSources().lava(), 20, 20); require(calls == 0, "Malformed mixed incoming admitted");
        }
        var vanilla = NeoForgeSilentGearTest.workbench(player, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        equip(player, vanilla); calls = 0; event(player, player.damageSources().lava(), 20, 20); require(calls == 0, "No-SG root gained defensive traits");
        realHealth(player, heat, roots, true); realHealth(player, bounce, roots, false); clear(player);
        report.append("B production: native controls, both paid inverses/duplicates, explicit Heat I/V max, fire/wall positives vs fall/melee/generic, native helmet/common-base and real Adamant attacking-tool branch, overlapping effects, clamp/zero/pre-cancel, malformed/held/no-SG negatives, read-only snapshots/writer tripwire and real fire/collision health paths\n");
    }

    private static void offensive(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, ItemStack root, ItemStack helmet) throws Throwable {
        var attacker = NeoForgeJustDireNegateFallTest.player(player.level().getServer(), GameType.SURVIVAL);
        var sword = SilentGearRecognitionTest.trait(api.armor("sword", "sword_blade", "iron"), api, "adamant", 2);
        attacker.setItemSlot(EquipmentSlot.MAINHAND, sword); var before = sword.copy();
        var health = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH); double saved = health.getBaseValue();
        try {
            health.setBaseValue(40); player.setHealth(player.getMaxHealth());
            // Adamant tests maximum health >21, not current-health percentage; use a genuine eligible victim.
            var source = new DamageSource(player.damageSources().lava().typeHolder(), attacker);
            clear(player); event(player, source, 20, 24);
            equip(player, root); player.setItemSlot(EquipmentSlot.HEAD, helmet.copy()); event(player, source, 20, 24 * .68F);
            require(ItemStack.matches(before, sword), "Native offensive control mutated attacker gear");
        } finally { health.setBaseValue(saved); player.setHealth(player.getMaxHealth()); clear(attacker); }
    }

    private static void realHealth(ServerPlayer owner, ItemStack nativeBoot, List<ItemStack> roots, boolean fire) {
        var stacks = new ArrayList<ItemStack>(); stacks.add(nativeBoot); stacks.addAll(roots);
        for (var stack : stacks) {
            var player = NeoForgeJustDireNegateFallTest.player(owner.level().getServer(), GameType.SURVIVAL);
            var neutral = stack.copy(); neutral.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY); neutral.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            player.setItemSlot(EquipmentSlot.FEET, neutral);
            var source = fire ? player.damageSources().lava() : player.damageSources().flyIntoWall();
            require(player.hurtServer(player.level(), source, 10), "Real defensive hurt path rejected");
            close(player.getMaxHealth() - player.getHealth(), fire ? 8.4F : 8, "Real health reduction (numeric armor deliberately neutralized)");
            clear(player);
        }
    }

    private static void failures(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, ItemStack source, List<ItemStack> roots, String variant, StringBuilder report) throws Throwable {
        var binding = new SilentGearDefenseApi(); var heatInstance = view(source).get("silentgear:heat_resistant"); var definition = traitOf(heatInstance);
        var heat = binding.admittedEffect(heatInstance); var bounce = binding.admittedEffect(view(source).get("silentgear:bounce"));
        var tag = bounce.getClass().getDeclaredField("damageType"); tag.setAccessible(true); var savedTag = tag.get(bounce);
        var effects = definition.getClass().getDeclaredField("effects"); effects.setAccessible(true); var saved = effects.get(definition);
        var helmet = api.armor("helmet", "helmet_plates", "crimson_iron");
        try {
            tag.set(bounce, DamageTypeTags.IS_FIRE);
            // Removing the required companion is unsupported for mixed admission, but remains valid native execution.
            if (variant.equals("defense-data")) { effects.set(definition, List.of(heat)); }
            for (var root : roots) {
                equip(player, root); calls = 0; failOn = variant.equals("defense-throw") ? 2 : 0;
                player.setItemSlot(EquipmentSlot.HEAD, helmet.copy());
                event(player, player.damageSources().lava(), 20, variant.equals("defense-data") ? 12.8F : 16.8F);
                require(calls == (variant.equals("defense-data") ? 1 : root == roots.getFirst() ? 2 : 0), "Refusal retried or dispatched unsupported data");
            }
            failOn = 0; effects.set(definition, saved);
            equip(player, roots.getFirst()); player.setItemSlot(EquipmentSlot.HEAD, helmet.copy()); calls = 0;
            event(player, player.damageSources().lava(), 20, variant.equals("defense-data") ? 12.8F : 16.8F);
            require(calls == (variant.equals("defense-data") ? 1 : 0), "Refusal silently rearmed/retried");
            equip(player, source); event(player, player.damageSources().lava(), 20, 12.8F);
        } finally { effects.set(definition, saved); tag.set(bounce, savedTag); failOn = 0; clear(player); }
        report.append("B ").append(variant).append(": unsupported Heat independently refuses while Bounce survives, or post-native second-call failure publishes no partial reduction and never retries; native controls retained\n");
    }

    static boolean reloadStep(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Throwable {
        SilentGearTraitSpike.initialize(api);
        if (reload == null) { reload = new Reload(player, api); return false; }
        return reload.advance(report);
    }

    private static final class Reload {
        private final ServerPlayer player;
        private final ItemStack source;
        private final List<ItemStack> roots;
        private final SilentGearDefenseApi binding = new SilentGearDefenseApi();
        private Object previous;
        private CompletableFuture<Void> future;
        private int phase;

        private Reload(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api) throws Throwable {
            this.player = player; source = SilentGearBounceTest.lined(api.boot("crimson_iron"), api, player);
            roots = NeoForgeSilentGearTest.workbench(player, source, new ItemStack(Items.DIAMOND_BOOTS));
            previous = binding.admittedEffect(view(source).get("silentgear:bounce")); future = load("minecraft:generic", .3F);
        }

        private boolean advance(StringBuilder report) throws Throwable {
            if (!future.isDone()) { return false; } future.join();
            var instance = view(source).get("silentgear:bounce"); Object current = binding.admittedEffect(instance);
            require(current != previous, "Reload retained stale native effect");
            boolean refused = false;
            try { binding.incoming(instance, previous, source.copy(), player, player.damageSources().generic(), 20, 20); }
            catch (IllegalArgumentException expected) { refused = true; }
            require(refused, "Stale effect receiver executed after reload"); previous = current;
            for (var stack : roots) {
                equip(player, stack); var before = stack.copy();
                event(player, player.damageSources().generic(), 20, phase == 0 ? 14 : 20);
                event(player, player.damageSources().flyIntoWall(), 20, phase == 0 ? 20 : 18);
                event(player, player.damageSources().lava(), 20, 16.8F);
                require(ItemStack.matches(before, stack), "Reload changed pre-existing root/source history");
            }
            equip(player, source); event(player, phase == 0 ? player.damageSources().generic() : player.damageSources().flyIntoWall(), 20, phase == 0 ? 14 : 18);
            if (phase++ == 0) { future = load("minecraft:fly_into_wall", .1F); return false; }
            clear(player); report.append("B reload: two completed real asynchronous datapack reloads change native scale/live damage tag membership on pre-existing paid inverses without recrafting; stale receivers refused; Heat and immutable histories preserved\n"); return true;
        }

        private CompletableFuture<Void> load(String damage, float scale) throws Exception {
            var server = player.level().getServer(); var pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("mismatchedboots-defense-reload");
            Files.createDirectories(pack); var format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
            Files.writeString(pack.resolve("pack.mcmeta"), """
                    {"pack":{"description":"Fixture-only defensive reload","min_format":[%d,%d],"max_format":[%d,%d]}}
                    """.formatted(format.major(), format.minor(), format.major(), format.minor()));
            var definition = pack.resolve("data/silentgear/silentgear_traits/bounce.json"); Files.createDirectories(definition.getParent());
            Files.writeString(definition, """
                    {"conditions":[],"max_level":1,"name":{"text":"Bounce fixture"},"description":{"text":"Reload fixture"},
                    "effects":[{"type":"silentgear:negate_damage","damage_type_tag":"silentgear:bounce_protects","negated_damage_scale":%s}]}
                    """.formatted(scale));
            var tag = pack.resolve("data/silentgear/tags/damage_type/bounce_protects.json"); Files.createDirectories(tag.getParent());
            Files.writeString(tag, "{\"replace\":true,\"values\":[\"" + damage + "\"]}");
            server.getPackRepository().reload(); var selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
            String id = "file/mismatchedboots-defense-reload"; require(server.getPackRepository().isAvailable(id), "Defensive datapack not discovered");
            if (!selected.contains(id)) { selected.add(id); } return server.reloadResources(selected);
        }
    }

    private static LivingIncomingDamageEvent event(ServerPlayer player, DamageSource source, float amount, float expected) { return event(player, source, amount, expected, amount); }
    private static LivingIncomingDamageEvent event(ServerPlayer player, DamageSource source, float amount, float expected, float original) {
        var snapshots = java.util.Arrays.stream(EquipmentSlot.values()).map(player::getItemBySlot).map(ItemStack::copy).toList();
        var event = new LivingIncomingDamageEvent(player, new DamageContainer(source, original)); event.setAmount(amount);
        SilentGearWearerFixture.active = true;
        try { NeoForge.EVENT_BUS.post(event); } finally { SilentGearWearerFixture.active = false; }
        close(event.getAmount(), expected, "Registered incoming math");
        for (var slot : EquipmentSlot.values()) { require(ItemStack.matches(snapshots.get(slot.ordinal()), player.getItemBySlot(slot)), "Incoming changed equipment/root/source history"); }
        return event;
    }
    private static void equip(ServerPlayer player, ItemStack root) { clear(player); player.setItemSlot(EquipmentSlot.FEET, root); }
    private static void clear(ServerPlayer player) { for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); } player.removeAllEffects(); }
    private static void close(float actual, float expected, String message) { require(Math.abs(actual - expected) < .0001F, message + ": " + actual + " vs " + expected); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
