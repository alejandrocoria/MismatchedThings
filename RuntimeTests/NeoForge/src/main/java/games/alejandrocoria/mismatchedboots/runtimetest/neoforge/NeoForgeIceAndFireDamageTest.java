package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/** Opt-in, pinned real-mod gate. No synthetic event posting or replacement damage listener. */
final class NeoForgeIceAndFireDamageTest {
    private static LivingEntity subject;
    private static float damageBefore;
    private static float damageAfter;
    private static int preEvents;
    private static int damageCases;
    private static boolean finished;

    private NeoForgeIceAndFireDamageTest() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, NeoForgeIceAndFireDamageTest::beforeDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NeoForgeIceAndFireDamageTest::afterDamage);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void beforeDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() == subject) {
            damageBefore = event.getNewDamage();
            preEvents++;
        }
    }

    private static void afterDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() == subject) {
            damageAfter = event.getNewDamage();
        }
    }

    private static void tick(MinecraftServer server) {
        if (finished) {
            return;
        }
        finished = true;
        try {
            verifyMatrix(server.overworld());
            NetworkAcceptanceFiles.write("ice-and-fire-damage-report.txt",
                    "15 dragon boot variants; 3 native damage types; " + damageCases + " real hurtServer cases\n"
                            + "ordinary/mixed/inverse/two-source controls; additive other-slot coefficients; "
                            + "invalid/spoofed roots; non-dragon/Troll/Lightning-set negative scope; "
                            + "unchanged equipment identity and source snapshots\n");
            NetworkAcceptanceFiles.write("ice-and-fire-damage-success.txt", "neoforge\n");
        } catch (Throwable throwable) {
            NetworkAcceptanceFiles.failure("ice-and-fire-damage", throwable);
        } finally {
            subject = null;
            server.halt(false);
        }
    }

    private static void verifyMatrix(ServerLevel level) {
        List<Item> boots = new ArrayList<>();
        for (String color : List.of("amethyst", "black", "blue", "bronze", "copper", "electric",
                "gray", "green", "red", "sapphire", "silver", "white")) {
            boots.add(item("armor_" + color + "_boots"));
        }
        for (String element : List.of("fire", "ice", "lightning")) {
            boots.add(item("dragonsteel_" + element + "_boots"));
        }
        List<DamageSource> dragonDamage = List.of(damage(level, "dragon_fire"), damage(level, "dragon_ice"),
                damage(level, "dragon_lightning"));
        for (DamageSource source : dragonDamage) {
            for (Item boot : boots) {
                String name = BuiltInRegistries.ITEM.getKey(boot).toString();
                hurt(level, source, new ItemStack(boot), 0.9F, name + " ordinary");
                var pair = MixedBootsFactory.createPair(new ItemStack(boot), new ItemStack(Items.IRON_BOOTS));
                hurt(level, source, pair.primary(), 0.9F, name + " mixed left");
                hurt(level, source, pair.inverse(), 0.9F, name + " mixed right");
                var both = MixedBootsFactory.createPair(new ItemStack(boot), new ItemStack(boot));
                hurt(level, source, both.primary(), 0.9F, name + " two identical sources");
            }
            var scaleSteel = MixedBootsFactory.createPair(new ItemStack(boots.getFirst()),
                    new ItemStack(boots.getLast()));
            hurt(level, source, scaleSteel.primary(), 0.9F, "Scale/Steel, not double-counted");
            hurt(level, source, scaleSteel.inverse(), 0.9F, "Steel/Scale, not double-counted");
            var colors = MixedBootsFactory.createPair(new ItemStack(boots.get(1)), new ItemStack(boots.get(4)));
            hurt(level, source, colors.primary(), 0.9F, "two scale colors");
            hurt(level, source, colors.inverse(), 0.9F, "two scale colors inverse");
            var elements = MixedBootsFactory.createPair(new ItemStack(item("dragonsteel_fire_boots")),
                    new ItemStack(item("dragonsteel_ice_boots")));
            hurt(level, source, elements.primary(), 0.9F, "two steel elements");
            hurt(level, source, elements.inverse(), 0.9F, "two steel elements inverse");

            hurt(level, source, new ItemStack(Items.IRON_BOOTS), 1.0F, "ordinary non-dragon");
            hurt(level, source, mixed("sheep_boots"), 1.0F, "unrelated source");
            hurt(level, source, MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS),
                    new ItemStack(Items.GOLDEN_BOOTS)).primary(), 1.0F, "neither source qualifies");
            hurt(level, source, new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID)),
                    1.0F, "bare root");
            ItemStack badCount = mixed("armor_red_boots");
            badCount.setCount(2);
            hurt(level, source, badCount, 1.0F, "invalid root count");
            ItemStack spoof = new ItemStack(Items.IRON_BOOTS);
            spoof.set(ModContent.mixedBootsDataType().orElseThrow(),
                    ModContent.getMixedBootsData(mixed("armor_red_boots")).orElseThrow());
            hurt(level, source, spoof, 1.0F, "foreign item carrying mixed data");

            for (int mask = 0; mask < 8; mask++) {
                float coefficient = 0.9F;
                List<ItemStack> otherArmor = new ArrayList<>();
                for (int bit = 0; bit < 3; bit++) {
                    if ((mask & (1 << bit)) != 0) {
                        coefficient -= new float[]{0.1F, 0.3F, 0.2F}[bit];
                        otherArmor.add(new ItemStack(item(new String[]{"armor_blue_helmet",
                                "dragonsteel_fire_chestplate", "armor_green_leggings"}[bit])));
                    } else {
                        otherArmor.add(ItemStack.EMPTY);
                    }
                }
                hurt(level, source, mixed("dragonsteel_ice_boots"), coefficient,
                        "native additive armor mask=" + mask, otherArmor);
            }
            // Valid data in another slot must not make its native class predicate recognize a dragon item.
            hurt(level, source, new ItemStack(Items.IRON_BOOTS), 1.0F, "mixed item forced into HEAD",
                    List.of(mixed("armor_red_boots"), ItemStack.EMPTY, ItemStack.EMPTY));
        }
        hurt(level, level.damageSources().generic(), mixed("armor_red_boots"), 1.0F, "non-dragon damage");
        hurt(level, level.damageSources().lightningBolt(), mixed("dragonsteel_lightning_boots"), 1.0F,
                "Lightning source alone grants no set immunity");
        hurt(level, level.damageSources().lightningBolt(), mixed("dragonsteel_lightning_boots"), 1.0F,
                "Lightning full-set recognition remains outside this block",
                List.of(new ItemStack(item("dragonsteel_lightning_helmet")),
                        new ItemStack(item("dragonsteel_lightning_chestplate")),
                        new ItemStack(item("dragonsteel_lightning_leggings"))));
        DamageSource arrow = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(net.minecraft.world.damagesource.DamageTypes.ARROW));
        hurt(level, arrow, mixed("forest_troll_leather_boots"), 1.0F, "Troll remains outside this block");
        hurt(level, arrow, new ItemStack(item("forest_troll_leather_boots")), 0.9F,
                "ordinary Troll remains unchanged");
    }

    private static void hurt(ServerLevel level, DamageSource source, ItemStack feet, float coefficient,
                             String description) {
        hurt(level, source, feet, coefficient, description,
                List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
    }

    private static void hurt(ServerLevel level, DamageSource source, ItemStack feet, float coefficient,
                             String description, List<ItemStack> otherArmor) {
        var zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        require(zombie != null, "Could not create test entity");
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, otherArmor.get(0));
        zombie.setItemSlot(EquipmentSlot.CHEST, otherArmor.get(1));
        zombie.setItemSlot(EquipmentSlot.LEGS, otherArmor.get(2));
        zombie.setItemSlot(EquipmentSlot.FEET, feet);
        var data = ModContent.getMixedBootsData(feet).orElse(null);
        ItemStack leftBefore = data == null ? ItemStack.EMPTY : data.left().source();
        ItemStack rightBefore = data == null ? ItemStack.EMPTY : data.right().source();
        Item itemBefore = feet.getItem();
        float healthBefore = zombie.getHealth();
        subject = zombie;
        damageBefore = -1;
        damageAfter = -1;
        preEvents = 0;
        require(zombie.hurtServer(level, source, 10.0F), description + ": hurt rejected");
        require(preEvents == 1 && damageBefore > 0 && damageAfter > 0, description + ": native Pre event missing");
        float expected = damageBefore * coefficient;
        near(expected, damageAfter, description + ": native damage multiplier");
        near(damageAfter, healthBefore - zombie.getHealth(), description + ": actual health loss");
        require(zombie.getItemBySlot(EquipmentSlot.FEET) == feet, description + ": equipped root replaced");
        require(feet.getItem() == itemBefore, description + ": root identity changed");
        if (data != null) {
            var after = ModContent.getMixedBootsData(feet).orElseThrow();
            require(ItemStack.matches(leftBefore, after.left().source())
                    && ItemStack.matches(rightBefore, after.right().source()), description + ": snapshots mutated");
        }
        damageCases++;
        subject = null;
        zombie.discard();
    }

    private static ItemStack mixed(String name) {
        return MixedBootsFactory.createPair(new ItemStack(item(name)), new ItemStack(Items.IRON_BOOTS)).primary();
    }

    private static Item item(String name) {
        Identifier id = Identifier.fromNamespaceAndPath("iceandfire", name);
        return BuiltInRegistries.ITEM.get(id).orElseThrow(() -> new AssertionError("Missing real item " + id)).value();
    }

    private static DamageSource damage(ServerLevel level, String name) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(
                ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("iceandfire", name))));
    }

    private static void near(float expected, float actual, String description) {
        require(Math.abs(expected - actual) < 0.0001F, description + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
