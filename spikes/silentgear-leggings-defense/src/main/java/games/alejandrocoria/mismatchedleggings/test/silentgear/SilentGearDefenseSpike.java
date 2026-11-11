package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.gear.trait.effect.NegateDamageTraitEffect;
import net.silentchaos512.gear.util.TraitHelper;

import java.nio.file.Files;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Pinned-artifact feasibility only. This candidate deliberately supplies no production readiness proof. */
public final class SilentGearDefenseSpike {
    private static boolean finished;
    private static boolean started;
    static int calls;
    private static final StringBuilder REPORT = new StringBuilder();

    public static boolean armor(boolean original, LivingIncomingDamageEvent event, EquipmentSlot slot, ItemStack stack) {
        return original || slot == EquipmentSlot.LEGS && event.getEntity().getItemBySlot(slot) == stack
                && MixedLeggingsStructure.validData(stack).isPresent();
    }

    public static Object incoming(ItemStack stack, Object current, Object function, Operation<Object> original,
                                  LivingIncomingDamageEvent event, float base) {
        if (!MixedLeggingsStructure.isMixedItem(stack)) { return original.call(stack, current, function); }
        if (MixedLeggingsStructure.validData(stack).isEmpty() || event.getEntity().getItemBySlot(EquipmentSlot.LEGS) != stack) { return current; }
        try {
            // Reuse the shipped condition/max-level resolver rather than building a second trait-selection engine.
            float amount = (Float) current;
            for (var instance : selected(stack)) {
                if (!List.of("silentgear:heat_resistant", "silentgear:bounce").contains(instance.getTraitId().toString())) { continue; }
                for (var effect : instance.getTrait().getEffects()) {
                    if (effect.getClass() == NegateDamageTraitEffect.class) {
                        calls++;
                        amount = ((NegateDamageTraitEffect) effect).onEntityIncomingDamage(stack.copy(), instance.getLevel(),
                                event.getEntity(), event.getSource(), amount, base);
                    }
                }
            }
            return amount;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Spike selection failed", failure);
        }
    }

    public static void tick(MinecraftServer server) {
        if (finished || server.getTickCount() < 20) { return; }
        try {
            if (!started) {
                started = true;
                initial(server, REPORT);
                if (mode().equals("full")) { SilentGearDefenseSpikeIncomingCases.run(server, REPORT); }
            }
            if ((mode().equals("full") || mode().equals("off")) && !SilentGearDefenseSpikeBounce.step(server, REPORT)) { return; }
            Files.writeString(StateChecks.report("probe.ok"), "scoped observation complete; see defense-spike.txt for coverage\n");
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        }
        finished = true;
        try {
            Files.writeString(StateChecks.report("defense-spike.txt"), REPORT);
            Files.writeString(StateChecks.report("defense-flow.txt"), DefenseSpikePlugin.finalFlow);
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        finally { server.halt(false); }
    }

    private static void initial(MinecraftServer server, StringBuilder report) throws Exception {
        boolean enabled = !mode().equals("off");
        boolean boots = Boolean.getBoolean("leggings.test.boots");
        boolean refusal = System.getProperty("leggings.test.sgDefenseMutation", "").equals("routing");
        require(ModList.get().isLoaded("mismatchedboots") == boots, "Wrong sibling graph");
        if (enabled || boots) {
            require(!DefenseSpikePlugin.actualOrder.isEmpty() && !DefenseSpikePlugin.finalFlow.isEmpty(), "Missing actual transformation witness");
        }
        require(DefenseSpikePlugin.routingMutation == refusal, "Missing/unexpected deliberate event-routing mutation");
        report.append("mode=").append(mode()).append('\n');
        report.append("actualOrder=").append(DefenseSpikePlugin.actualOrder).append('\n');
        boolean bootsIncoming = true;
        if (boots) {
            for (String group : List.of("Incoming", "SafeFall", "BounceFall", "Retaliation", "Moon")) {
                boolean ready = ready("event.GearEvents", "mismatchedboots$sg" + group + "Ready");
                report.append("boots.").append(group.toLowerCase(java.util.Locale.ROOT)).append('=').append(ready).append('\n');
                if (group.equals("Incoming")) { bootsIncoming = ready; }
                else { require(ready == !(group.equals("SafeFall") && mutation().equals("safety")), "Unexpected Boots readiness: " + group); }
            }
            require(ready("gear.trait.effect.NegateDamageTraitEffect", "mismatchedboots$sgNegateReady"), "Boots negate leaf proof lost");
            require(bootsIncoming != refusal, "Boots incoming proof did not match the unchanged/invalid transport contract");
        }
        var player = FoundationChecks.player(server);
        var heat = FoundationChecks.armor("crimson_iron");
        require(level(heat, "heat_resistant") == 4, "Actual native LEGS Heat Resistant IV missing");
        player.setItemSlot(EquipmentSlot.LEGS, heat);
        hit(player, player.damageSources().lava(), 16.8F, 0);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);

        var recipeClass = Class.forName("net.silentchaos512.gear.crafting.recipe.GearPartSwapRecipe");
        var recipe = recipeClass.getField("INSTANCE").get(null);
        var input = CraftingInput.of(2, 1, List.of(heat, FoundationChecks.part("lining", "slime")));
        require((boolean) recipeClass.getMethod("matches", CraftingInput.class, Level.class).invoke(recipe, input, player.level()), "Native slime lining recipe rejected LEGS");
        var lined = (ItemStack) recipeClass.getMethod("assemble", CraftingInput.class).invoke(recipe, input);
        require(level(lined, "bounce") == 1 && level(lined, "heat_resistant") == 4, "Computed native lined LEGS levels differ");
        player.setItemSlot(EquipmentSlot.LEGS, lined);
        hit(player, player.damageSources().flyIntoWall(), 16, 0);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);

        for (var root : FoundationChecks.paid(player, lined, new ItemStack(Items.DIAMOND_LEGGINGS))) {
            var before = root.copy();
            player.setItemSlot(EquipmentSlot.LEGS, root);
            hit(player, player.damageSources().lava(), enabled ? 16.8F : 20, enabled ? 2 : 0);
            hit(player, player.damageSources().flyIntoWall(), enabled ? 16 : 20, enabled ? 2 : 0);
            hit(player, player.damageSources().generic(), 20, enabled ? 2 : 0);
            hit(player, player.damageSources().fall(), 20, enabled ? 2 : 0);
            require(ItemStack.matches(before, root), "Incoming callback changed mixed root/history");
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        }
        report.append("native Heat IV/Bounce I, paid inverse fire/wall positives, generic/fall negatives and unchanged root passed\n");
        if (boots) {
            var gear = (GearItem) BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:boots"));
            var source = gear.construct(List.of(PartInstance.from(FoundationChecks.part("boot_plates", "crimson_iron"))));
            var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
            var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, source, new ItemStack(Items.DIAMOND_BOOTS));
            var root = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
            player.setItemSlot(EquipmentSlot.FEET, root);
            hit(player, player.damageSources().lava(), bootsIncoming ? 16.8F : 20, 0);
            report.append("actual current mixed FEET incoming amount=").append(bootsIncoming ? "16.8" : "20.0 (REFUSED)").append('\n');
            var plainPair = factory.getMethod("createPair", ItemStack.class, ItemStack.class)
                    .invoke(null, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
            var plain = (ItemStack) plainPair.getClass().getMethod("primary").invoke(plainPair);
            player.setItemSlot(EquipmentSlot.FEET, plain);
            player.setItemSlot(EquipmentSlot.LEGS, lined.copy());
            player.setDeltaMovement(0, 0, 0);
            var expected = plain.copy();
            boolean safe = !mutation().equals("safety");
            expected.setDamageValue(expected.getDamageValue() + (safe ? 3 : 0));
            var fall = new LivingFallEvent(player, 12, 1);
            NeoForge.EVENT_BUS.post(fall);
            require(fall.isCanceled() == safe && ItemStack.matches(expected, plain), "Existing Boots safe FEET wear barrier changed");
            report.append("native peer/current plain mixed FEET: charge=").append(safe ? 3 : 0).append(", safe=").append(safe).append('\n');
        }
        report.append("coexistence=").append(refusal ? "REFUSED invalid event routing (expected)" : "retained at incoming seam only").append('\n');
    }

    static String mode() { return System.getProperty("leggings.test.sgDefenseSpike", ""); }
    static String mutation() { return System.getProperty("leggings.test.sgDefenseMutation", ""); }

    static List<TraitInstance> selected(ItemStack root) throws ReflectiveOperationException {
        var type = Class.forName("games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearEffectSources");
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        var traits = type.getDeclaredMethod("traits", ItemStack.class);
        traits.setAccessible(true);
        var result = new java.util.ArrayList<TraitInstance>();
        for (var value : (List<?>) traits.invoke(constructor.newInstance(), root)) {
            var getter = value.getClass().getDeclaredMethod("nativeInstance");
            getter.setAccessible(true);
            result.add((TraitInstance) getter.invoke(value));
        }
        return result;
    }

    static boolean ready(String owner, String name) throws Exception {
        var method = Class.forName("net.silentchaos512.gear." + owner).getDeclaredMethod(name);
        method.setAccessible(true);
        return (boolean) method.invoke(null);
    }

    private static int level(ItemStack stack, String name) {
        return TraitHelper.getTraits(stack).stream().filter(t -> t.getTraitId().toString().equals("silentgear:" + name)).mapToInt(TraitInstance::getLevel).max().orElse(0);
    }

    private static void hit(ServerPlayer player, DamageSource source, float expected, int expectedCalls) {
        calls = 0;
        var event = new LivingIncomingDamageEvent(player, new DamageContainer(source, 20));
        NeoForge.EVENT_BUS.post(event);
        require(Math.abs(event.getAmount() - expected) < .0001F && calls == expectedCalls,
                "Incoming " + source + ": amount=" + event.getAmount() + ", selected calls=" + calls + ", expected " + expected + "/" + expectedCalls);
    }
}
