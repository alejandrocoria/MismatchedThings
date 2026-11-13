package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Actual packaged initialization, incoming hits and live wallet transactions; no fixture handler registration. */
public final class JustDireArmorWearChecks {
    public static String fault = "";
    private static final StringBuilder rows = new StringBuilder("case\tFE\twear\n");
    private static ServerPlayer observed;
    private static ItemStack faultRoot;
    private static ItemStack replacement;
    private static int receipt;
    private static String mutation = "none";
    private static boolean finished;

    private JustDireArmorWearChecks() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ArmorHurtEvent event) -> {
            if (event.getEntity() != observed) {
                return;
            }
            if (mutation.equals("cancel")) {
                event.setCanceled(true);
            } else if (!mutation.equals("none")) {
                event.setNewDamage(EquipmentSlot.LEGS, Float.parseFloat(mutation));
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void tick(MinecraftServer server) {
        if (finished || server.getTickCount() < 20) {
            return;
        }
        finished = true;
        try {
            JustDireFoundationChecks.graph();
            server.setDifficulty(Difficulty.NORMAL, true);
            CommonHooks.onArmorHurt(server.overworld().damageSources().generic(), new EquipmentSlot[0], 2, player(server));
            var getter = CommonHooks.class.getDeclaredMethod("mismatchedthings$justDireArmorWearReady");
            getter.setAccessible(true);
            require((boolean) getter.invoke(null), "Production shared incoming wear guard refused");
            require(java.util.Arrays.stream(CommonHooks.class.getDeclaredMethods())
                    .noneMatch(method -> method.getName().equals("mismatchedboots$justDireArmorWearReady")), "Retired Boots hook remains");

            for (String tier : List.of("celestigem", "eclipsealloy")) {
                for (int charge : List.of(1000, 199, 0)) {
                    hit(server, "native " + tier + '/' + charge, nativeLeggings(tier, charge), Math.max(0, charge - 200), 0);
                    for (boolean inverse : List.of(false, true)) {
                        boolean funded = charge >= 200;
                        hit(server, "hybrid " + tier + '/' + charge + '/' + inverse, mixed(tier, false, charge, inverse),
                                funded ? charge - 200 : charge, funded ? 0 : 2);
                        hit(server, "two powered " + tier + '/' + charge + '/' + inverse, mixed(tier, true, charge, inverse),
                                Math.max(0, charge - 200), 0);
                    }
                }
            }
            hit(server, "vanilla", new ItemStack(Items.DIAMOND_LEGGINGS), -1, 2);
            var passive = MixedLeggingsFactory.createPair(nativeLeggings("ferricore", 0), new ItemStack(Items.DIAMOND_LEGGINGS)).primary();
            passive.remove(JustDireDataComponents.FORGE_ENERGY);
            hit(server, "no powered provider", passive, -1, 2);
            passive.setDamageValue(0);
            passive.set(JustDireDataComponents.FORGE_ENERGY, 1000);
            hit(server, "root FE without entitlement", passive, 1000, 2);
            var root = mixed("eclipsealloy", false, 1000, true);
            root.set(JustDireDataComponents.TOOL_ENABLED, false);
            hit(server, "global off physical protection", root, 800, 0);
            root = mixed("eclipsealloy", false, 1000, false);
            root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 3);
            hit(server, "linear Unbreaking III", root, 860, 0);
            var nativeDiscount = nativeLeggings("eclipsealloy", 1000);
            nativeDiscount.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 3);
            hit(server, "native linear Unbreaking III", nativeDiscount, 860, 0);

            for (String change : List.of("cancel", "0.75", "1.75")) {
                mutation = change;
                hit(server, "post event " + change, mixed("eclipsealloy", false, 1000, false), change.equals("1.75") ? 900 : 1000, 0);
            }
            mutation = "none";
            direct(server);
            transactions(server);
            coexistence(server);
            Files.writeString(report("armor-wear.tsv"), rows.toString());
            Files.writeString(report("foundation.txt"), "Production incoming wear, independent payments and transactional refusals passed\n");
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            observed = null;
            fault = "";
            server.halt(false);
        }
    }

    private static void hit(MinecraftServer server, String name, ItemStack root, int energy, int wear) {
        var player = player(server);
        player.setItemSlot(EquipmentSlot.LEGS, root);
        player.doTick();
        var before = root.copy();
        observed = player;
        try {
            require(player.hurtServer(player.level(), player.damageSources().mobAttack(new Zombie(EntityType.ZOMBIE, player.level())), 8),
                    "Real incoming hit rejected: " + name);
        } finally {
            observed = null;
        }
        state(name, root, before, energy, wear);
    }

    private static void state(String name, ItemStack root, ItemStack before, int energy, int wear) {
        require(root.getDamageValue() == wear && root.getOrDefault(JustDireDataComponents.FORGE_ENERGY, -1) == energy,
                name + ": FE=" + root.getOrDefault(JustDireDataComponents.FORGE_ENERGY, -1) + ", wear=" + root.getDamageValue());
        var expected = before.copy();
        expected.setDamageValue(wear);
        if (energy >= 0) {
            expected.set(JustDireDataComponents.FORGE_ENERGY, energy);
        }
        require(ItemStack.matches(expected, root), name + ": source history or unrelated root state changed");
        rows.append(name).append('\t').append(energy).append('\t').append(wear).append('\n');
    }

    private static void direct(MinecraftServer server) {
        var player = player(server);
        var root = mixed("eclipsealloy", false, 88_680, true);
        player.setItemSlot(EquipmentSlot.LEGS, root);
        var before = root.copy();
        root.hurtAndBreak(7, player, EquipmentSlot.LEGS);
        state("direct mixed wear", root, before, 88_680, 7);
        root = nativeLeggings("eclipsealloy", 1000);
        before = root.copy();
        player.setItemSlot(EquipmentSlot.LEGS, root);
        root.hurtAndBreak(2, player, EquipmentSlot.LEGS);
        state("native direct price", root, before, 998, 0);
    }

    private static void transactions(MinecraftServer server) throws Exception {
        Field binding = binding(EquipmentSlot.LEGS);
        Object original = binding.get(null);
        for (boolean inverse : List.of(false, true)) {
            for (String mode : List.of("short", "staged", "binding", "reenter", "replace", "wallet", "component")) {
                var player = player(server);
                var root = mixed("eclipsealloy", false, 1000, inverse);
                root.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved root"));
                root.set(DataComponents.REPAIR_COST, 7);
                var before = root.copy();
                player.setItemSlot(EquipmentSlot.LEGS, root);
                observed = player;
                faultRoot = root;
                replacement = root.copy();
                receipt = 0;
                fault = mode;
                try {
                    CommonHooks.onArmorHurt(player.damageSources().generic(), new EquipmentSlot[]{EquipmentSlot.LEGS}, 2, player);
                    require(fault.isEmpty() && receipt == (mode.equals("short") ? 199 : 200), "Fault did not use real extraction: " + mode);
                    int energy = mode.equals("reenter") ? 800 : mode.equals("wallet") ? 77 : 1000;
                    int wear = List.of("short", "staged", "binding").contains(mode) ? 2 : 0;
                    if (mode.equals("component")) {
                        before.set(DataComponents.CUSTOM_NAME, Component.literal("Independent edit"));
                    }
                    state(mode + '/' + inverse, root, before, energy, wear);
                    if (mode.equals("replace")) {
                        require(player.getItemBySlot(EquipmentSlot.LEGS) == replacement, "Independent replacement overwritten");
                        state("replacement " + inverse, replacement, before, 1000, 0);
                    }
                } finally {
                    fault = "";
                    observed = null;
                    binding.set(null, original);
                }
            }
        }
    }

    /** Called only after the real LiveHandler has staged extraction in the production transaction. */
    public static void extracted(int amount) {
        if (fault.isEmpty() || amount <= 0) {
            return;
        }
        String mode = fault;
        fault = "";
        receipt = amount;
        switch (mode) {
            case "short", "staged" -> { /* the probe changes the real amount/resource */ }
            case "reenter" -> CommonHooks.onArmorHurt(observed.damageSources().generic(), new EquipmentSlot[]{EquipmentSlot.LEGS}, 2, observed);
            case "replace" -> observed.setItemSlot(EquipmentSlot.LEGS, replacement);
            case "wallet" -> faultRoot.set(JustDireDataComponents.FORGE_ENERGY, 77);
            case "component" -> faultRoot.set(DataComponents.CUSTOM_NAME, Component.literal("Independent edit"));
            case "binding" -> {
                try {
                    binding(EquipmentSlot.LEGS).set(null, null);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException(failure);
                }
            }
            default -> throw new AssertionError("Unknown extraction fault " + mode);
        }
    }

    private static void coexistence(MinecraftServer server) throws Exception {
        boolean boots = ModList.get().isLoaded("mismatchedboots");
        for (int legsCharge : List.of(1000, 199, 0)) {
            joint(server, "independent wallets " + legsCharge, legsCharge, 2000, null);
        }
        if (boots) {
            joint(server, "unfunded Boots only", 1000, 199, null);
        }
        for (var disabled : boots ? List.of(EquipmentSlot.LEGS, EquipmentSlot.FEET) : List.of(EquipmentSlot.LEGS)) {
            joint(server, "local storage refusal " + disabled, 1000, 2000, disabled);
        }
    }

    private static void joint(MinecraftServer server, String name, int legsCharge, int bootsCharge, EquipmentSlot disabled) throws Exception {
        var player = player(server);
        var legs = mixed("celestigem", false, legsCharge, true);
        boolean bootsInstalled = ModList.get().isLoaded("mismatchedboots");
        var boots = bootsInstalled ? boots(bootsCharge) : new ItemStack(Items.DIAMOND_BOOTS);
        Field field = disabled == null ? null : binding(disabled);
        Object original = field == null ? null : field.get(null);
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        player.doTick();
        var beforeLegs = legs.copy();
        var beforeBoots = boots.copy();
        try {
            if (field != null) {
                field.set(null, null);
            }
            require(player.hurtServer(player.level(), player.damageSources().mobAttack(new Zombie(EntityType.ZOMBIE, player.level())), 8),
                    "Joint real hit rejected");
        } finally {
            if (field != null) {
                field.set(null, original);
            }
        }
        boolean legsPay = disabled != EquipmentSlot.LEGS && legsCharge >= 200;
        boolean bootsPay = bootsInstalled && disabled != EquipmentSlot.FEET && bootsCharge >= 200;
        state(name + "/LEGS", legs, beforeLegs, legsPay ? legsCharge - 200 : legsCharge, legsPay ? 0 : 2);
        state(name + "/FEET", boots, beforeBoots, bootsInstalled ? bootsPay ? bootsCharge - 200 : bootsCharge : -1, bootsPay ? 0 : 2);
    }

    private static Field binding(EquipmentSlot slot) throws ReflectiveOperationException {
        String type = slot == EquipmentSlot.LEGS
                ? "games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireResources"
                : "games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireResourcesCompatibility";
        Field field = Class.forName(type).getDeclaredField("api");
        field.setAccessible(true);
        return field;
    }

    private static ItemStack boots(int charge) throws ReflectiveOperationException {
        var nativeBoots = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:eclipsealloy_boots")));
        var pair = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory")
                .getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
        var root = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
        root.set(JustDireDataComponents.FORGE_ENERGY, charge);
        return root;
    }

    private static ItemStack mixed(String tier, boolean two, int charge, boolean inverse) {
        var pair = MixedLeggingsFactory.createPair(nativeLeggings(tier, 0),
                two ? nativeLeggings("eclipsealloy", 0) : new ItemStack(Items.DIAMOND_LEGGINGS));
        var root = inverse ? pair.inverse() : pair.primary();
        root.set(JustDireDataComponents.FORGE_ENERGY, charge);
        return root;
    }

    private static ItemStack nativeLeggings(String tier, int charge) {
        var root = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:" + tier + "_leggings")));
        root.set(JustDireDataComponents.FORGE_ENERGY, charge);
        return root;
    }

    private static ServerPlayer player(MinecraftServer server) {
        var player = RepairFixtures.player(server);
        player.connection.markClientLoaded();
        player.setNoGravity(true);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static Path report(String name) {
        return Path.of(System.getProperty("leggings.test.report"), name);
    }
}
