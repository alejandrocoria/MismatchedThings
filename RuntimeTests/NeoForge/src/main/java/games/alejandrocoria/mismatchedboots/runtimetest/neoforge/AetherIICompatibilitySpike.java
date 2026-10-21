package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.menu.MismatchedWorkbenchMenu;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;

/** Opt-in native measurements and disposable candidates, not a production adapter. */
public final class AetherIICompatibilitySpike {
    private static boolean finished;
    private static boolean candidate;
    private static DataComponentType<TagKey<Item>> armorSet;
    private static int setExtensions;
    private static int sentryExtensions;

    private AetherIICompatibilitySpike() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer()));
    }

    public static boolean recognizesSet(ItemStack root, TagKey<Item> requested) {
        boolean found = candidate && MixedBootsSources.anySource(root, source -> source.get(armorSet) == requested);
        if (found) { setExtensions++; }
        return found;
    }

    public static boolean recognizesItem(ItemStack root, Holder<Item> item) {
        boolean found = candidate && MixedBootsSources.anySource(root, source -> source.is(item));
        if (found) { sentryExtensions++; }
        return found;
    }

    private static void run(MinecraftServer server) {
        if (finished) { return; }
        finished = true;
        var report = new StringBuilder("Aether II alpha.4.1; test-only native query candidates\n");
        try {
            armorSet = component("armor_set");
            var api = new NativeApi();
            var player = FakePlayerFactory.getMinecraft(server.overworld());
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(0, 80, 0); player.setNoGravity(true);
            sets(server, player, api, report);
            charms(server, player, api, report);
            sentry(server, player, api, report);
            require(setExtensions > 0 && sentryExtensions > 0, "Candidate hooks were not exercised");
            report.append("sets=passed; charms=passed; sentry=passed\n");
            NetworkAcceptanceFiles.write("aether-ii-spike-report.txt", report.toString());
            NetworkAcceptanceFiles.write("aether-ii-spike-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.write("aether-ii-spike-report.txt", report.toString());
            NetworkAcceptanceFiles.failure("aether-ii-spike", failure);
        } finally { candidate = false; server.halt(false); }
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
                clear(subject); gloves.clearContent(); candidate = false;
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
                    candidate = false; count(api, subject, tag, index == 0 ? 3 : 2);
                    candidate = true; count(api, subject, tag, 3);
                    require(api.ability(subject, tag), "Candidate failed threshold " + material);
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
                subject.setItemSlot(EquipmentSlot.FEET, root); candidate = false;
                count(api, subject, root.get(armorSet), 1);
                count(api, subject, root.get(armorSet) == zanite ? neptune : zanite, 0);
                candidate = true; count(api, subject, zanite, 1); count(api, subject, neptune, 1);
            }
            subject.setItemSlot(EquipmentSlot.HEAD, item("zanite_helmet"));
            subject.setItemSlot(EquipmentSlot.CHEST, item("zanite_chestplate"));
            subject.setItemSlot(EquipmentSlot.LEGS, item("neptune_leggings"));
            api.accessories(subject).setItem(api.handwearIndex, item("neptune_gloves"));
            count(api, subject, zanite, 3); count(api, subject, neptune, 3);
            clear(player);
            player.setItemSlot(EquipmentSlot.HEAD, item("zanite_helmet"));
            player.setItemSlot(EquipmentSlot.CHEST, item("zanite_chestplate"));
            var inverse = paidPair(player, item("zanite_boots"), new ItemStack(Items.DIAMOND_BOOTS)).get(1);
            String before = snapshot(server, inverse);
            player.setItemSlot(EquipmentSlot.FEET, inverse);
            var speedId = Identifier.parse("aether_ii:armor_set.ability.zanite.attack_speed");
            candidate = false; NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
            require(!player.getAttribute(Attributes.ATTACK_SPEED).hasModifier(speedId), "Baseline unexpectedly has set effect");
            candidate = true; NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
            close(player.getAttribute(Attributes.ATTACK_SPEED).getModifier(speedId).amount(), .15, "Native Zanite activation");
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
            close(player.getAttribute(Attributes.ATTACK_SPEED).getModifier(speedId).amount(), .15, "Repeated native activation");
            require(before.equals(snapshot(server, inverse)), "Native tick modified source/root");
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
            require(!player.getAttribute(Attributes.ATTACK_SPEED).hasModifier(speedId), "Native effect did not deactivate");
            // Wrong-slot and missing-data controls still flow through the original native query.
            clear(player); player.setItemSlot(EquipmentSlot.MAINHAND, inverse); count(api, player, zanite, 0);
            var missing = inverse.copy(); missing.remove(ModContent.mixedBootsDataType().orElseThrow());
            player.setItemSlot(EquipmentSlot.FEET, missing); count(api, player, zanite, 0);
            report.append("SETS six complete families: native 2/3 + gloves; owner/inverse; duplicate once; unequal sets both queries; ")
                    .append("candidate can complete two sets simultaneously; native Zanite tick activation/repeat/removal; immutable roots/history\n");
        } finally { clear(player); subject.discard(); candidate = false; }
    }

    private static void charms(MinecraftServer server, ServerPlayer player, NativeApi api, StringBuilder report) throws Exception {
        clear(player);
        ItemStack source = install(player, api, item("neptune_boots"), true);
        close(amount(source.getAttributeModifiers(), Attributes.MAX_HEALTH), 2, "Native Health charm");
        String before = snapshot(server, source);
        var vanilla = new ItemStack(Items.DIAMOND_BOOTS);
        var pair = paidPair(player, source, vanilla);
        report.append("HEALTH native IDs=").append(healthIds(source)).append("; owner effective IDs=")
                .append(healthIds(pair.get(0))).append('\n');
        measureHealth(player, pair.get(0), 1, 3, "current owner", report);
        measureHealth(player, pair.get(1), 1, 1, "current inverse", report);
        var two = paidPair(player, source, source);
        for (var root : two) { measureHealth(player, root, 2, 4, "current two charmed sources", report); }
        var emptyPair = paidPair(player, item("neptune_boots"), vanilla);
        measureHealth(player, emptyPair.get(0), 0, 0, "empty holders", report);
        ItemStack later = install(player, api, emptyPair.get(0), true);
        measureHealth(player, later, 0, 2, "current later root installation", report);
        install(player, api, emptyPair.get(1), false);

        var current = ComponentRulesLifecycle.serverRules();
        var rules = new LinkedHashMap<>(current.components().rules());
        rules.put(Identifier.parse("aether_ii:charms"), ComponentRule.of(ComponentPolicy.OMIT));
        var snapshotOnly = new ComponentRules(current.durability(), current.repairCost(), new ComponentRuleSet(rules));
        var candidatePair = MixedBootsFactory.createPair(source, vanilla, AttributeRulesLifecycle.serverRules(), snapshotOnly);
        for (var root : List.of(candidatePair.primary(), candidatePair.inverse())) {
            require(!root.has(component("charms")), "Snapshot candidate retained live root holders");
            measureHealth(player, root, 1, 1, "snapshot-only candidate", report);
            install(player, api, root, false);
        }
        var candidateTwo = MixedBootsFactory.createPair(source, source, AttributeRulesLifecycle.serverRules(), snapshotOnly);
        measureHealth(player, candidateTwo.primary(), 2, 2, "snapshot-only two sources", report);
        measureHealth(player, candidateTwo.inverse(), 2, 2, "snapshot-only two sources inverse", report);
        require(before.equals(snapshot(server, source)), "Composition or Forge mutated original charmed source");
        report.append("CHARMS native server menu cursor/slot/apply/locked/take paths conserve one purchased charm; inverse admission rejected; ")
                .append("root edits do not rebuild baked history. Snapshot-only OMIT is supplied per creation, not a production default\n");
    }

    private static ItemStack install(ServerPlayer player, NativeApi api, ItemStack input, boolean accepted) throws Exception {
        var server = player.level().getServer();
        String history = history(server, input);
        String inputBefore = snapshot(server, input);
        AbstractContainerMenu menu = api.forge(player);
        menu.setCarried(input.copy()); menu.clicked(0, 0, ContainerInput.PICKUP, player);
        require(menu.getCarried().isEmpty(), "Forge input placement failed");
        var charm = item("charm_of_health_1");
        require(menu.getSlot(3).mayPlace(charm) == accepted, "Wrong native charm admission");
        menu.setCarried(charm); menu.clicked(3, 0, ContainerInput.PICKUP, player);
        if (!accepted) {
            require(menu.getCarried().getCount() == 1 && menu.getSlot(3).getItem().isEmpty(), "Rejected charm was consumed");
            require(inputBefore.equals(snapshot(server, menu.getSlot(0).getItem())), "Rejected installation changed input");
            return input;
        }
        require(menu.getCarried().isEmpty(), "Charm did not leave paid cursor");
        require(api.apply(menu, player), "Native Apply rejected holders");
        require(!menu.getSlot(3).mayPickup(player), "Installed charm did not lock");
        require(api.apply(menu, player), "Repeated native Apply failed");
        menu.clicked(0, 0, ContainerInput.PICKUP, player);
        ItemStack result = menu.getCarried().copy();
        require(!result.isEmpty() && menu.getSlot(0).getItem().isEmpty() && menu.getSlot(3).getItem().isEmpty(),
                "Native take retained paid item/charm display");
        require(api.installed(result) == 1, "Native installation duplicated or lost charm");
        require(history.equals(history(server, result)) && inputBefore.equals(snapshot(server, input)),
                "Native installation mutated original input or frozen source history");
        return result;
    }

    private static void measureHealth(ServerPlayer player, ItemStack root, double baked, double effective,
                                      String label, StringBuilder report) throws Exception {
        String before = snapshot(player.level().getServer(), root);
        close(amount(root.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY), Attributes.MAX_HEALTH), baked, label + " baked");
        close(amount(root.getAttributeModifiers(), Attributes.MAX_HEALTH), effective, label + " effective");
        Method update = LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates"); update.setAccessible(true);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); update.invoke(player);
        double base = player.getAttributeValue(Attributes.MAX_HEALTH);
        player.setItemSlot(EquipmentSlot.FEET, root); update.invoke(player);
        close(player.getAttributeValue(Attributes.MAX_HEALTH) - base, effective, label + " equipped");
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); update.invoke(player);
        require(before.equals(snapshot(player.level().getServer(), root)), "Attribute/equipment query mutated root/history");
        report.append("HEALTH ").append(label).append(": baked=").append(baked)
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
            for (boolean enabled : List.of(false, true)) {
                candidate = enabled;
                fall(player, source, 7.99, true); fall(player, source, 8, false);
                fall(player, root, 7.99, enabled); fall(player, root, 8, false);
                fall(player, new ItemStack(Items.DIAMOND_BOOTS), 7.99, false);
            }
            require(before.equals(snapshot(server, root)), "Fall modified root/history");
        }
        candidate = true;
        var duplicate = MixedBootsFactory.createPair(source, source).primary();
        player.setItemSlot(EquipmentSlot.FEET, duplicate); count(api, player, source.get(armorSet), 1);
        close(amount(duplicate.getAttributeModifiers(), Attributes.FALL_DAMAGE_MULTIPLIER), -.75, "Two Sentry numeric");
        fall(player, duplicate, 7.99, true);
        clear(player); player.setItemSlot(EquipmentSlot.MAINHAND, pair.get(0));
        fall(player, ItemStack.EMPTY, 7.99, false);
        report.append("SENTRY native/owner/inverse/duplicate event dispatch at 7.99 and 8; candidate restores only short-fall predicate; ")
                .append("stored average -.375 is unchanged; no equipment/source mutation\n");
    }

    private static void fall(ServerPlayer player, ItemStack boots, double distance, boolean suppressed) {
        player.setItemSlot(EquipmentSlot.FEET, boots); player.fallDistance = distance;
        var event = new LivingFallEvent(player, distance, 1);
        NeoForge.EVENT_BUS.post(event);
        close(event.getDistance(), suppressed ? 0 : distance, "Sentry short-fall boundary, candidate=" + candidate);
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
        require(api.count(entity, tag) == expected, "Wrong set count " + tag + "; candidate=" + candidate + "; expected=" + expected);
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
    private static List<Identifier> healthIds(ItemStack stack) {
        return stack.getAttributeModifiers().modifiers().stream().filter(entry -> entry.attribute().equals(Attributes.MAX_HEALTH))
                .map(entry -> entry.modifier().id()).toList();
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
