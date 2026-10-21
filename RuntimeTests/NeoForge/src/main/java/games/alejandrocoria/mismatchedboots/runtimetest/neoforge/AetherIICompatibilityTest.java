package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesConfig;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.menu.MismatchedWorkbenchMenu;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/** Native controls and independently scoped production charm/recognition acceptance. */
public final class AetherIICompatibilityTest {
    private static boolean finished;
    private static DataComponentType<TagKey<Item>> armorSet;
    private static final String VARIANT = System.getProperty("mismatchedboots.runtimetest.aetherRecognitionVariant", "live");
    private static final boolean SETS = !VARIANT.equals("setchanged");
    private static final boolean SENTRY = !VARIANT.equals("sentrychanged");

    private AetherIICompatibilityTest() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer()));
    }

    private static void run(MinecraftServer server) {
        if (finished) { return; }
        finished = true;
        boolean charmsOnly = Boolean.getBoolean("mismatchedboots.runtimetest.aetherIICharms");
        String prefix = charmsOnly ? "aether-ii-charms" : "aether-ii-recognition";
        var report = new StringBuilder(charmsOnly ? "Aether II alpha.4.1; production installed charm composition\n"
                : "Aether II alpha.4.1; production recognition; variant=" + VARIANT + "\n");
        try {
            armorSet = component("armor_set");
            var api = new NativeApi();
            var player = FakePlayerFactory.getMinecraft(server.overworld());
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(0, 80, 0); player.setNoGravity(true);
            if (charmsOnly) {
                charms(server, player, api, report);
                report.append("production charms=passed\n");
            } else {
                sets(server, player, api, report);
                sentry(server, player, api, report);
                damage(server, player, report);
                var charmed = install(player, api, item("neptune_boots"), "health", 0, true);
                for (var root : paidPair(player, charmed, new ItemStack(Items.DIAMOND_BOOTS))) {
                    require(!root.has(component("charms")), "Recognition refusal/peer restored live charm holders");
                    measure(player, root, Attributes.MAX_HEALTH, 1, "independent charm baseline", report);
                }
                if (!VARIANT.equals("live")) {
                    require(AetherRecognitionTestControl.setReads > 0 && AetherRecognitionTestControl.sentryReads > 0,
                            "Real peer result chains were not exercised");
                }
                report.append("production recognition=passed; sets=").append(SETS).append("; sentry=").append(SENTRY).append('\n');
            }
            NetworkAcceptanceFiles.write(prefix + "-report.txt", report.toString());
            NetworkAcceptanceFiles.write(prefix + "-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.write(prefix + "-report.txt", report.toString());
            NetworkAcceptanceFiles.failure(prefix, failure);
        } finally { server.halt(false); }
    }

    private static void sets(MinecraftServer server, ServerPlayer player, NativeApi api, StringBuilder report) throws Exception {
        // Native accessory lookup deliberately excludes FakePlayers. A real mob exercises gloves.
        var subject = EntityType.ZOMBIE.create(server.overworld(), EntitySpawnReason.COMMAND);
        require(subject != null, "Missing set subject");
        try {
            for (String material : List.of("beast_pelt", "burrukai_plate", "zanite", "arkenium", "gravitite", "neptune")) {
                var boots = item(material + "_boots");
                var tag = boots.get(armorSet);
                require(tag != null, "Missing native set " + material);
                var pair = paidPair(player, boots, new ItemStack(Items.DIAMOND_BOOTS));
                var duplicate = MixedBootsFactory.createPair(boots, boots).primary();
                var gloves = api.accessories(subject);
                clear(subject); gloves.clearContent();
                subject.setItemSlot(EquipmentSlot.HEAD, item(material + "_helmet"));
                subject.setItemSlot(EquipmentSlot.CHEST, item(material + "_chestplate"));
                count(api, subject, tag, 2);
                require(!api.ability(subject, tag), "Two-piece native threshold changed");
                gloves.setItem(api.handwearIndex, item(material + "_gloves"));
                count(api, subject, tag, 3);
                require(api.ability(subject, tag), "Gloves did not complete native threshold");
                gloves.clearContent();
                for (int index = 0; index < pair.size(); index++) {
                    var root = pair.get(index);
                    String before = snapshot(server, root);
                    subject.setItemSlot(EquipmentSlot.FEET, root);
                    count(api, subject, tag, SETS || index == 0 ? 3 : 2);
                    require(api.ability(subject, tag) == (SETS || index == 0), "Wrong production threshold " + material);
                    require(before.equals(snapshot(server, root)), "Set query mutated root/history");
                }
                subject.setItemSlot(EquipmentSlot.FEET, duplicate); count(api, subject, tag, 3);
                subject.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); count(api, subject, tag, 2);
                require(!api.ability(subject, tag), "Duplicate sources counted as two worn pieces");
            }
            clear(subject); api.accessories(subject).clearContent();
            var dual = MixedBootsFactory.createPair(item("zanite_boots"), item("neptune_boots"));
            var zanite = item("zanite_boots").get(armorSet);
            var neptune = item("neptune_boots").get(armorSet);
            for (var root : List.of(dual.primary(), dual.inverse())) {
                subject.setItemSlot(EquipmentSlot.FEET, root);
                count(api, subject, root.get(armorSet), 1);
                count(api, subject, root.get(armorSet) == zanite ? neptune : zanite, SETS ? 1 : 0);
            }
            subject.setItemSlot(EquipmentSlot.HEAD, item("zanite_helmet"));
            subject.setItemSlot(EquipmentSlot.CHEST, item("zanite_chestplate"));
            subject.setItemSlot(EquipmentSlot.LEGS, item("neptune_leggings"));
            api.accessories(subject).setItem(api.handwearIndex, item("neptune_gloves"));
            count(api, subject, zanite, SETS ? 3 : 2); count(api, subject, neptune, 3);
            clear(player);
            player.setItemSlot(EquipmentSlot.HEAD, item("zanite_helmet"));
            player.setItemSlot(EquipmentSlot.CHEST, item("zanite_chestplate"));
            var inverse = paidPair(player, item("zanite_boots"), new ItemStack(Items.DIAMOND_BOOTS)).get(1);
            String before = snapshot(server, inverse);
            player.setItemSlot(EquipmentSlot.FEET, inverse);
            var speedId = Identifier.parse("aether_ii:armor_set.ability.zanite.attack_speed");
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
            require(player.getAttribute(Attributes.ATTACK_SPEED).hasModifier(speedId) == SETS, "Wrong native Zanite activation/refusal");
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
            if (SETS) { close(player.getAttribute(Attributes.ATTACK_SPEED).getModifier(speedId).amount(), .15, "Repeated native activation"); }
            require(before.equals(snapshot(server, inverse)), "Native tick modified source/root");
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
            require(!player.getAttribute(Attributes.ATTACK_SPEED).hasModifier(speedId), "Native effect did not deactivate");
            // Wrong-slot and missing-data controls still flow through the original native query.
            clear(player); player.setItemSlot(EquipmentSlot.MAINHAND, inverse); count(api, player, zanite, 0);
            var missing = inverse.copy(); missing.remove(ModContent.mixedBootsDataType().orElseThrow());
            player.setItemSlot(EquipmentSlot.FEET, missing); count(api, player, zanite, 0);
            var countTwo = inverse.copy(); countTwo.setCount(2);
            player.setItemSlot(EquipmentSlot.FEET, countTwo); count(api, player, zanite, 0);
            var spoof = new ItemStack(Items.STICK);
            spoof.set(ModContent.mixedBootsDataType().orElseThrow(), ModContent.getMixedBootsData(inverse).orElseThrow());
            player.setItemSlot(EquipmentSlot.FEET, spoof); count(api, player, zanite, 0);
            clear(player); player.getInventory().setItem(0, inverse); count(api, player, zanite, 0);
            player.getInventory().setItem(0, ItemStack.EMPTY);
            report.append("SETS six complete families: native 2/3 + gloves; owner/inverse; duplicate once; unequal sets both queries; ")
                    .append("production two-set coexistence or independent refusal; native Zanite tick activation/repeat/removal; ")
                    .append("held/idle/missing-data/bad-count/spoof negatives; immutable roots/history\n");
        } finally { clear(player); subject.discard(); }
    }

    private static void charms(MinecraftServer server, ServerPlayer player, NativeApi api, StringBuilder report) throws Exception {
        clear(player);
        var charmsId = Identifier.parse("aether_ii:charms");
        require(ComponentRulesLifecycle.serverRules().components().ruleFor(charmsId).policy() == ComponentPolicy.OMIT,
                "Normal registered server rules did not select charm omission");
        Path configControl = Path.of(System.getProperty("mismatchedboots.runtimetest.markers")).resolve("component-config-control.toml");
        Files.writeString(configControl, "[components.\"minecraft:rarity\"]\npolicy=\"owner\"\n");
        var completed = new ComponentRulesConfig(configControl).load();
        require(completed.components().ruleFor(charmsId).policy() == ComponentPolicy.OMIT,
                "Existing config did not gain registered charm default");
        Files.writeString(configControl, "[components.\"aether_ii:charms\"]\npolicy=\"owner\"\n");
        require(new ComponentRulesConfig(configControl).load().components().ruleFor(charmsId).policy() == ComponentPolicy.OWNER,
                "Explicit registered charm override was replaced");
        var vanilla = new ItemStack(Items.DIAMOND_BOOTS);
        var nativeEmpty = item("neptune_boots");
        var emptyPair = paidPair(player, nativeEmpty, vanilla);
        for (var root : emptyPair) {
            require(!root.has(component("charms")), "Empty root holders survived default omission");
            measure(player, root, Attributes.MAX_HEALTH, 0, "empty holders", report);
            install(player, api, root, "health", 0, false);
        }
        for (var profile : List.of(new CharmProfile("health", Attributes.MAX_HEALTH, 2),
                new CharmProfile("defense", Attributes.ARMOR, 2),
                new CharmProfile("toughness", Attributes.ARMOR_TOUGHNESS, 1),
                new CharmProfile("resistance", Attributes.KNOCKBACK_RESISTANCE, .1),
                new CharmProfile("agility", Attributes.MOVEMENT_SPEED, .0025))) {
            ItemStack source = install(player, api, nativeEmpty, profile.name(), 0, true);
            String before = snapshot(server, source);
            double base = amount(nativeEmpty.getAttributeModifiers(), profile.attribute());
            measure(player, source, profile.attribute(), base + profile.bonus(), "native " + profile.name(), report);
            var pair = paidPair(player, source, vanilla);
            var two = paidPair(player, source, source);
            for (int index = 0; index < 2; index++) {
                ItemStack root = pair.get(index);
                double expected = amount(emptyPair.get(index).getAttributeModifiers(), profile.attribute()) + profile.bonus() / 2;
                require(!root.has(component("charms")), "Production root retained live holders");
                measure(player, root, profile.attribute(), expected, profile.name() + " mixed " + index, report);
                measure(player, two.get(index), profile.attribute(), base + profile.bonus(), profile.name() + " two sources " + index, report);
                var actualIds = root.getAttributeModifiers().modifiers().stream().map(entry -> entry.modifier().id()).toList();
                require(actualIds.stream().noneMatch(id -> id.getNamespace().equals("mismatchedboots") && id.getPath().contains("charm.")),
                        "Native event appended a live root charm modifier");
                install(player, api, root, profile.name(), 0, false);
                var data = ModContent.getMixedBootsData(root).orElseThrow();
                var left = data.left().source(); var right = data.right().source();
                left.set(DataComponents.CUSTOM_NAME, Component.literal("source recomposition control"));
                String original = snapshot(server, root);
                var recomposed = MixedBootsRecomposer.recompose(root, left, right);
                var repeated = MixedBootsRecomposer.recompose(recomposed, left, right);
                require(original.equals(snapshot(server, root)) && !repeated.has(component("charms")), "Recomposition mutated input or restored holders");
                require(ItemStack.matches(repeated, recomposed), "Recomposition accumulated state");
                var newData = ModContent.getMixedBootsData(repeated).orElseThrow();
                require(ItemStack.matches(left, newData.left().source()) && ItemStack.matches(right, newData.right().source()),
                        "Recomposition changed supplied source history");
                measure(player, repeated, profile.attribute(), expected, profile.name() + " recomposed " + index, report);
            }
            if (profile.name().equals("health")) {
                var doubleInstalled = install(player, api, source, "health", 1, true);
                measure(player, doubleInstalled, Attributes.MAX_HEALTH, 4, "native two Health holders", report);
                for (var root : paidPair(player, doubleInstalled, vanilla)) {
                    measure(player, root, Attributes.MAX_HEALTH, 2, "two Health holders mixed", report);
                }
            }
            require(before.equals(snapshot(server, source)), "Composition/Forge/attributes mutated original source");
        }
        for (var root : paidPair(player, item("sentry_boots"), vanilla)) {
            require(!root.has(component("charms")), "Sentry empty holders survived omission");
            install(player, api, root, "health", 0, false);
        }
        report.append("CHARMS five native armor charms; normal server defaults; paid Workbench inverse outputs; baked/effective/equipped values; ")
                .append("duplicate sources and two Health holders; empty Neptune/Sentry holders; native Forge paid apply/lock/take; ")
                .append("mixed installation rejected without consumption; repeat source-wise recomposition; immutable sources/root; ")
                .append("existing registered config completed and explicit owner override preserved\n");
    }

    private record CharmProfile(String name, Holder<Attribute> attribute, double bonus) {}

    private static ItemStack install(ServerPlayer player, NativeApi api, ItemStack input, String name, int holder, boolean accepted) throws Exception {
        var server = player.level().getServer();
        String history = history(server, input);
        String inputBefore = snapshot(server, input);
        AbstractContainerMenu menu = api.forge(player);
        menu.setCarried(input.copy()); menu.clicked(0, 0, ContainerInput.PICKUP, player);
        require(menu.getCarried().isEmpty(), "Forge input placement failed");
        var charm = item("charm_of_" + name + "_1");
        int slot = 3 + holder;
        require(menu.getSlot(slot).mayPlace(charm) == accepted, "Wrong native charm admission: " + name);
        int previous = accepted ? api.installed(input) : 0;
        menu.setCarried(charm); menu.clicked(slot, 0, ContainerInput.PICKUP, player);
        if (!accepted) {
            require(menu.getCarried().getCount() == 1 && ItemStack.matches(charm, menu.getCarried())
                    && menu.getSlot(slot).getItem().isEmpty(), "Rejected charm was consumed");
            require(inputBefore.equals(snapshot(server, menu.getSlot(0).getItem())), "Rejected installation changed input");
            return input;
        }
        require(menu.getCarried().isEmpty(), "Charm did not leave paid cursor");
        require(api.apply(menu, player), "Native Apply rejected holders");
        require(!menu.getSlot(slot).mayPickup(player), "Installed charm did not lock");
        require(api.apply(menu, player), "Repeated native Apply failed");
        menu.clicked(0, 0, ContainerInput.PICKUP, player);
        ItemStack result = menu.getCarried().copy();
        require(!result.isEmpty() && menu.getSlot(0).getItem().isEmpty() && menu.getSlot(slot).getItem().isEmpty(),
                "Native take retained paid item/charm display");
        require(api.installed(result) == previous + 1, "Native installation duplicated or lost charm");
        require(history.equals(history(server, result)) && inputBefore.equals(snapshot(server, input)),
                "Native installation mutated original input or frozen source history");
        return result;
    }

    private static void measure(ServerPlayer player, ItemStack root, Holder<Attribute> attribute, double expected,
                                String label, StringBuilder report) throws Exception {
        String before = snapshot(player.level().getServer(), root);
        double baked = amount(root.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY), attribute);
        double effective = amount(root.getAttributeModifiers(), attribute);
        // Native controls are event-backed, while mixed snapshots must already contain the whole result.
        if (ModContent.isMixedBoots(root)) { close(baked, expected, label + " baked"); }
        close(effective, expected, label + " effective");
        Method update = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates"); update.setAccessible(true);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); update.invoke(player);
        double base = player.getAttributeValue(attribute);
        player.setItemSlot(EquipmentSlot.FEET, root); update.invoke(player);
        close(player.getAttributeValue(attribute) - base, expected, label + " equipped");
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); update.invoke(player);
        require(before.equals(snapshot(player.level().getServer(), root)), "Attribute/equipment query mutated root/history");
        report.append("ATTRIBUTE ").append(label).append(": baked=").append(baked)
                .append(" effective/equipped=").append(effective).append('\n');
    }

    private static void sentry(MinecraftServer server, ServerPlayer player, NativeApi api, StringBuilder report) throws Exception {
        clear(player);
        var source = item("sentry_boots");
        var pair = paidPair(player, source, new ItemStack(Items.DIAMOND_BOOTS));
        close(amount(source.getAttributeModifiers(), Attributes.FALL_DAMAGE_MULTIPLIER), -.75, "Native Sentry numeric");
        for (var root : pair) {
            close(amount(root.getAttributeModifiers(), Attributes.FALL_DAMAGE_MULTIPLIER), -.375, "Mixed Sentry average");
            String before = snapshot(server, root);
            fall(player, source, 7.99, true); fall(player, source, 8, false);
            fall(player, root, 7.99, SENTRY); fall(player, root, 8, false);
            fall(player, new ItemStack(Items.DIAMOND_BOOTS), 7.99, false);
            require(before.equals(snapshot(server, root)), "Fall modified root/history");
        }
        var duplicate = MixedBootsFactory.createPair(source, source).primary();
        player.setItemSlot(EquipmentSlot.FEET, duplicate); count(api, player, source.get(armorSet), 1);
        close(amount(duplicate.getAttributeModifiers(), Attributes.FALL_DAMAGE_MULTIPLIER), -.75, "Two Sentry numeric");
        fall(player, duplicate, 7.99, SENTRY);
        var missing = pair.get(1).copy(); missing.remove(ModContent.mixedBootsDataType().orElseThrow());
        fall(player, missing, 7.99, false);
        var countTwo = pair.get(1).copy(); countTwo.setCount(2); fall(player, countTwo, 7.99, false);
        var spoof = new ItemStack(Items.STICK);
        spoof.set(ModContent.mixedBootsDataType().orElseThrow(), ModContent.getMixedBootsData(pair.get(1)).orElseThrow());
        fall(player, spoof, 7.99, false);
        clear(player); player.setItemSlot(EquipmentSlot.MAINHAND, pair.get(0));
        fall(player, ItemStack.EMPTY, 7.99, false);
        report.append("SENTRY native/owner/inverse/duplicate event dispatch at 7.99 and 8; production local short-fall predicate/refusal; ")
                .append("stored average -.375 is unchanged; no equipment/source mutation\n");
    }

    private static void fall(ServerPlayer player, ItemStack boots, double distance, boolean suppressed) {
        player.setItemSlot(EquipmentSlot.FEET, boots); player.fallDistance = distance;
        var event = new LivingFallEvent(player, distance, 1);
        NeoForge.EVENT_BUS.post(event);
        close(event.getDistance(), suppressed ? 0 : distance, "Sentry short-fall boundary, variant=" + VARIANT);
    }

    private static void damage(MinecraftServer server, ServerPlayer player, StringBuilder report) throws Exception {
        clear(player);
        var diamond = new ItemStack(Items.DIAMOND_BOOTS);
        close(actualFall(server, item("sentry_boots"), 7.99, false), 0, "Native Sentry real short fall");
        require(actualFall(server, item("sentry_boots"), 8, false) > 0, "Native Sentry acquired long-fall immunity");
        double ordinary = actualFall(server, diamond, 8, false);
        require(ordinary > 0, "Real fall control did not hurt");
        for (var root : paidPair(player, item("sentry_boots"), diamond)) {
            double shortFall = actualFall(server, root, 7.99, false);
            require(SENTRY ? shortFall == 0 : shortFall > 0, "Wrong real Sentry mixed fall/refusal");
            double longFall = actualFall(server, root, 8, false);
            require(longFall > 0 && longFall < ordinary, "Composed Sentry reduction was lost or became immunity");
        }
        close(actualFall(server, item("gravitite_boots"), 7.99, true), 0, "Native Gravitite real set fall");
        for (var root : paidPair(player, item("gravitite_boots"), diamond)) {
            double result = actualFall(server, root, 7.99, true);
            require(SETS || root.get(armorSet) != null ? result == 0 : result > 0,
                    "Native Gravitite fall consumer did not follow production set admission");
        }
        report.append("DAMAGE actual causeFallDamage/hurtServer: native/vanilla controls; both Sentry inverses at 7.99/8; ")
                .append("composed long-fall reduction; native Gravitite set-fall consumer and both inverses; immutable roots/history\n");
    }

    private static double actualFall(MinecraftServer server, ItemStack input, double distance, boolean gravitite) throws Exception {
        var subject = EntityType.ZOMBIE.create(server.overworld(), EntitySpawnReason.COMMAND);
        require(subject != null, "Could not create actual fall subject");
        try {
            subject.setNoAi(true);
            var boots = input.copy();
            String before = snapshot(server, boots);
            subject.setItemSlot(EquipmentSlot.FEET, boots);
            if (gravitite) {
                subject.setItemSlot(EquipmentSlot.HEAD, item("gravitite_helmet"));
                subject.setItemSlot(EquipmentSlot.CHEST, item("gravitite_chestplate"));
            }
            Method update = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates"); update.setAccessible(true); update.invoke(subject);
            double health = subject.getHealth(); subject.fallDistance = distance;
            subject.causeFallDamage(distance, 1, server.overworld().damageSources().fall());
            require(subject.getItemBySlot(EquipmentSlot.FEET) == boots && before.equals(snapshot(server, boots)),
                    "Actual fall substituted equipment or changed root/history");
            return health - subject.getHealth();
        } finally { subject.discard(); }
    }

    private static List<ItemStack> paidPair(ServerPlayer player, ItemStack first, ItemStack second) {
        var menu = new MismatchedWorkbenchMenu(1, player.getInventory());
        menu.getSlot(0).set(first.copy()); menu.getSlot(1).set(second.copy());
        var firstPreview = menu.getSlot(2).getItem().copy(); var secondPreview = menu.getSlot(3).getItem().copy();
        var primary = menu.getSlot(2).safeTake(1, 1, player);
        require(menu.isCommitted() && menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(), "Unpaid mixed outputs");
        var inverse = menu.getSlot(3).safeTake(1, 1, player);
        require(!primary.isEmpty() && !inverse.isEmpty() && ItemStack.matches(primary, firstPreview)
                && ItemStack.matches(inverse, secondPreview), "Workbench preview/take disagreement");
        return List.of(primary, inverse);
    }

    private static void clear(LivingEntity entity) {
        for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) { entity.setItemSlot(slot, ItemStack.EMPTY); }
    }
    private static void count(NativeApi api, LivingEntity entity, TagKey<Item> tag, int expected) throws Exception {
        require(api.count(entity, tag) == expected, "Wrong set count " + tag + "; variant=" + VARIANT + "; expected=" + expected);
    }
    private static double amount(ItemAttributeModifiers modifiers, Holder<Attribute> attribute) {
        return modifiers.modifiers().stream().filter(entry -> entry.attribute().equals(attribute) && entry.slot().test(EquipmentSlot.FEET))
                .mapToDouble(entry -> entry.modifier().amount()).sum();
    }
    private static ItemStack item(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("aether_ii", path)).orElseThrow());
    }
    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.fromNamespaceAndPath("aether_ii", path)).orElseThrow().value();
    }
    private static String snapshot(MinecraftServer server, ItemStack stack) {
        return ItemStack.CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), stack).getOrThrow().toString();
    }
    private static String history(MinecraftServer server, ItemStack stack) {
        return ModContent.getMixedBootsData(stack).map(data -> snapshot(server, data.left().source())
                + "|" + snapshot(server, data.right().source())).orElse("");
    }
    private static void close(double actual, double expected, String label) {
        require(Math.abs(actual - expected) < 1E-6, label + ": " + actual + " != " + expected);
    }
    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    /** Reflection keeps the optional alpha artifact off normal compilation/runtime dependencies. */
    private static final class NativeApi {
        private final Class<?> equipment = Class.forName("com.aetherteam.aetherii.item.equipment.EquipmentUtil");
        private final Class<?> forge = Class.forName("com.aetherteam.aetherii.inventory.menu.ArkeniumForgeMenu");
        private final Supplier<?> accessories = (Supplier<?>) Class.forName("com.aetherteam.aetherii.attachment.AetherIIDataAttachments").getField("ACCESSORIES").get(null);
        private final int handwearIndex;
        NativeApi() throws Exception {
            Class<?> slotType = Class.forName("com.aetherteam.aetherii.inventory.container.AccessoryContainer$SlotType");
            handwearIndex = ((int[]) slotType.getMethod("getIndex").invoke(slotType.getField("HANDWEAR").get(null)))[0];
        }
        int count(LivingEntity entity, TagKey<Item> tag) throws Exception {
            return (int) equipment.getMethod("getArmorCount", LivingEntity.class, TagKey.class).invoke(null, entity, tag);
        }
        boolean ability(LivingEntity entity, TagKey<Item> tag) throws Exception {
            return (boolean) equipment.getMethod("hasArmorAbility", LivingEntity.class, TagKey.class).invoke(null, entity, tag);
        }
        Container accessories(LivingEntity entity) throws Exception {
            return (Container) entity.getClass().getMethod("getData", Supplier.class).invoke(entity, accessories);
        }
        AbstractContainerMenu forge(ServerPlayer player) throws Exception {
            return (AbstractContainerMenu) forge.getConstructor(int.class, Inventory.class).newInstance(2, player.getInventory());
        }
        boolean apply(AbstractContainerMenu menu, Player player) throws Exception {
            return (boolean) forge.getMethod("slotCharms", Player.class).invoke(menu, player);
        }
        int installed(ItemStack stack) throws Exception {
            Object value = stack.get(component("charms"));
            var holders = (List<?>) value.getClass().getMethod("charmHolders").invoke(value);
            int count = 0;
            for (var holder : holders) {
                ItemStack installed = (ItemStack) holder.getClass().getMethod("getStack").invoke(holder);
                if (!installed.isEmpty()) { require(installed.getCount() == 1, "Invalid installed charm count"); count++; }
            }
            return count;
        }
    }
}
