package games.alejandrocoria.mismatchedleggings.test;

import com.google.gson.JsonParser;
import games.alejandrocoria.mismatchedleggings.test.mixin.ArmorWearInvoker;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.WorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifiers;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantments;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real current Boots through the existing packaged sibling harness; no compile-time Boots dependency. */
final class ForbiddenBootsChecks {
    static final String FLAGS = "mismatchedboots:forbidden_modifiers";
    private static final Identifier BOOTS = Identifier.parse("mismatchedboots:mixed_boots");
    private static final String ADAPTER = "games.alejandrocoria.mismatchedboots.compatibility.forbiddenarcanus.ForbiddenBootsModifiers";
    private static final ResourceKey<Registry<Object>> MODIFIERS = ResourceKey.createRegistryKey(
            Identifier.parse("forbidden_arcanus:item_modifier"));

    private ForbiddenBootsChecks() {}

    static boolean enabled() {
        return Boolean.getBoolean("leggings.test.forbiddenBoots");
    }

    static void server(MinecraftServer server) throws Exception {
        if (!enabled()) { return; }
        matrix(server);
        smithing(server);
        tagReloads(server);
        workstations(server);
        justDire(server);
        legacy(server);
        deaths(server);
        Files.writeString(ForbiddenArcanusChecks.report("boots-server"),
                "Boots union/orientations, native wear/death/respawn, paid root upgrades, duplicate/stale refusal,\n"
                        + "published raw legacy roots, atomic failure, explicit empty state and codecs passed\n");
    }

    private static void matrix(MinecraftServer server) throws Exception {
        for (String first : List.of("none", "eternal", "soulbound")) {
            for (String second : List.of("none", "eternal", "soulbound")) {
                var left = source(server, first, true);
                var right = source(server, second, false);
                var beforeLeft = left.copy();
                var beforeRight = right.copy();
                var expected = new ForbiddenModifiers(first.equals("eternal") || second.equals("eternal"),
                        first.equals("soulbound") || second.equals("soulbound"));
                var player = ForbiddenModifierChecks.actor(server);
                try {
                    for (var root : pair(server, left, right)) {
                        verify(root, expected);
                        ForbiddenModifierChecks.codecs(server, root);
                        var history = history(root);
                        var result = recompose(server, root, history.getFirst(), history.getLast());
                        verify(result, expected);
                        var normalized = root.copy();
                        normalized.set(DataComponents.REPAIRABLE, result.get(DataComponents.REPAIRABLE));
                        require(ItemStack.matches(normalized, result), "Boots recomposition changed live components");
                        player.setItemSlot(EquipmentSlot.FEET, root.copy());
                        var worn = player.getItemBySlot(EquipmentSlot.FEET);
                        int damage = worn.getDamageValue();
                        ((ArmorWearInvoker) player).leggings$hurtArmor(player.damageSources().generic(), 8);
                        require(worn.getDamageValue() == (expected.eternal() ? 0 : damage + 2), "Boots native armor wear differs");
                    }
                } finally {
                    ForbiddenModifierChecks.close(player);
                }
                require(ItemStack.matches(left, beforeLeft) && ItemStack.matches(right, beforeRight), "Boots union mutated sources");
            }
        }
        var forged = new ItemStack(Items.DIAMOND_BOOTS);
        forged.set(type(), new ForbiddenModifiers(true, true));
        for (var root : pair(server, forged, new ItemStack(Items.IRON_BOOTS))) {
            require(!root.has(type()), "Source-carried root flags granted Boots authority");
        }
    }

    private static void smithing(MinecraftServer server) throws Exception {
        var player = ForbiddenModifierChecks.actor(server);
        try {
            for (var plain : pair(server, source(server, "none", true), source(server, "none", false))) {
                for (String first : List.of("eternal", "soulbound")) {
                    for (boolean shift : List.of(false, true)) {
                        var before = plain.copy();
                        var once = ForbiddenModifierSmithingChecks.upgrade(player, plain, first, shift);
                        var both = ForbiddenModifierSmithingChecks.upgrade(player, once,
                                first.equals("eternal") ? "soulbound" : "eternal", !shift);
                        verify(both, new ForbiddenModifiers(true, true));
                        require(ItemStack.matches(before, plain), "Boots smithing changed the original root");
                        var beforeHistory = history(plain);
                        var afterHistory = history(both);
                        for (int side = 0; side < 2; side++) {
                            require(ItemStack.matches(beforeHistory.get(side), afterHistory.get(side)), "Boots smithing changed provenance");
                        }
                        rejected(player, both, "eternal");
                        rejected(player, both, "soulbound");
                    }
                }
            }
            var plain = pair(server, source(server, "none", true), source(server, "none", false)).getFirst();
            var recipes = List.copyOf(server.getRecipeManager().getRecipes());
            var original = recipes.stream().filter(recipe -> recipe.id().identifier()
                    .equals(Identifier.parse("forbidden_arcanus:apply_modifier/eternal"))).findFirst().orElseThrow();
            try {
                for (boolean shift : List.of(false, true)) {
                    var pending = ready(player, plain, "eternal");
                    WorkbenchChecks.applyRecipes(server, recipes.stream().filter(recipe -> recipe != original).toList());
                    stale(player, pending, shift);
                    WorkbenchChecks.applyRecipes(server, recipes);
                    pending = ready(player, plain, "eternal");
                    var changed = new ArrayList<RecipeHolder<?>>(recipes);
                    changed.remove(original);
                    changed.add(replaceHolder(original));
                    WorkbenchChecks.applyRecipes(server, changed);
                    stale(player, pending, shift);
                    WorkbenchChecks.applyRecipes(server, recipes);
                }
            } finally {
                WorkbenchChecks.applyRecipes(server, recipes);
            }
        } finally {
            ForbiddenModifierChecks.close(player);
        }
    }

    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> RecipeHolder<T> replaceHolder(RecipeHolder<T> original) {
        return new RecipeHolder<>(original.id(), original.value());
    }

    private static void tagReloads(MinecraftServer server) throws Exception {
        var tags = new LinkedHashMap<>(BuiltInRegistries.ITEM.getTags().collect(Collectors.toMap(
                net.minecraft.core.HolderSet.Named::key, tag -> tag.stream().toList())));
        var player = ForbiddenModifierChecks.actor(server);
        try {
            for (boolean shift : List.of(false, true)) {
                var plain = pair(server, source(server, "none", true), source(server, "none", false)).getFirst();
                var pending = ready(player, plain, "eternal");
                var forbidden = TagKey.<Item>create(Registries.ITEM, Identifier.parse("forbidden_arcanus:modifier/eternal_incompatible"));
                var changed = new LinkedHashMap<>(tags);
                changed.put(forbidden, List.of(Items.DIAMOND_BOOTS.builtInRegistryHolder(), Items.IRON_BOOTS.builtInRegistryHolder()));
                BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, changed)).apply();
                rejected(player, plain, "eternal");
                stale(player, pending, shift);
                changed.put(forbidden, List.of(BuiltInRegistries.ITEM.get(BOOTS).orElseThrow()));
                BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, changed)).apply();
                rejected(player, plain, "eternal");
                changed.put(forbidden, List.of(Items.DIAMOND_BOOTS.builtInRegistryHolder()));
                BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, changed)).apply();
                verify(ForbiddenModifierSmithingChecks.upgrade(player, plain, "eternal", shift), new ForbiddenModifiers(true, false));
                BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, tags)).apply();
            }
        } finally {
            BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, tags)).apply();
            ForbiddenModifierChecks.close(player);
        }
    }

    private static void workstations(MinecraftServer server) throws Exception {
        var player = ForbiddenModifierChecks.actor(server);
        try {
            for (boolean eternal : List.of(false, true)) {
                var root = pair(server, source(server, "none", true), source(server, "none", false)).getFirst();
                root.setDamageValue(37);
                root.set(DataComponents.REPAIR_COST, 3);
                root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 3);
                root = ForbiddenModifierSmithingChecks.upgrade(player, root, "soulbound", false);
                if (eternal) { root = ForbiddenModifierSmithingChecks.upgrade(player, root, "eternal", true); }
                var expected = state(root);
                var trim = SmithingChecks.ready(player, new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE),
                        root.copy(), new ItemStack(Items.DIAMOND));
                var trimmed = trim.getSlot(3).safeTake(1, 1, player);
                verify(trimmed, expected);
                require(history(trimmed).stream().allMatch(source -> source.has(DataComponents.TRIM)), "Boots trim was not source-wise");
                var anvil = AnvilRepairChecks.ready(player, trimmed.copy(), ItemStack.EMPTY);
                anvil.setItemName("Retained Boots modifiers");
                var renamed = anvil.getSlot(2).safeTake(1, 1, player);
                verify(renamed, expected);
                var grindstone = GrindstoneChecks.ready(player, renamed.copy(), ItemStack.EMPTY);
                var ground = grindstone.getSlot(2).safeTake(1, 1, player);
                verify(ground, expected);
                require(ground.getEnchantments().isEmpty() && ground.getHoverName().getString().equals("Retained Boots modifiers"),
                        "Boots workstation lost its live name or failed to remove enchantments");
                ForbiddenModifierChecks.codecs(server, ground);
            }
        } finally {
            ForbiddenModifierChecks.close(player);
        }
    }

    private static List<ItemStack> justDire(MinecraftServer server) throws Exception {
        var results = new ArrayList<ItemStack>();
        var player = ForbiddenModifierChecks.actor(server);
        try {
            for (var root : pair(server, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:eclipsealloy_boots"))),
                    new ItemStack(Items.DIAMOND_BOOTS))) {
                root.set(component("justdirethings:forge_energy"), 2000);
                root.set(DataComponents.CUSTOM_NAME, Component.literal("Configured powered Boots"));
                var opaque = new CompoundTag();
                opaque.putLongArray("paid", new long[]{9_007_199_254_740_993L, Long.MAX_VALUE});
                root.set(DataComponents.CUSTOM_DATA, CustomData.of(opaque));
                for (String kind : List.of("eternal", "soulbound", "both")) {
                    var modified = ForbiddenModifierSmithingChecks.upgrade(player, root, kind.equals("both") ? "eternal" : kind, false);
                    if (kind.equals("both")) { modified = ForbiddenModifierSmithingChecks.upgrade(player, modified, "soulbound", true); }
                    var expected = state(modified);
                    var menu = SmithingChecks.ready(player, ItemStack.EMPTY, modified.copy(),
                            new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:upgrade_stepheight"))));
                    var upgraded = menu.getSlot(3).safeTake(1, 1, player);
                    verify(upgraded, expected);
                    require(Boolean.TRUE.equals(upgraded.get(component("justdirethings:stepheight_upgrade_installed")))
                            && Integer.valueOf(2000).equals(upgraded.get(component("justdirethings:forge_energy")))
                            && root.get(DataComponents.CUSTOM_DATA).equals(upgraded.get(DataComponents.CUSTOM_DATA)),
                            "JDT recomposition lost FA effects, FE or opaque paid state");
                }
                var menu = SmithingChecks.ready(player, ItemStack.EMPTY, root.copy(),
                        new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:upgrade_stepheight"))));
                var powered = menu.getSlot(3).safeTake(1, 1, player);
                var both = ForbiddenModifierSmithingChecks.upgrade(player,
                        ForbiddenModifierSmithingChecks.upgrade(player, powered, "soulbound", false), "eternal", true);
                verify(both, new ForbiddenModifiers(true, true));
                require(Boolean.TRUE.equals(both.get(component("justdirethings:stepheight_upgrade_installed")))
                        && Integer.valueOf(2000).equals(both.get(component("justdirethings:forge_energy")))
                        && root.get(DataComponents.CUSTOM_DATA).equals(both.get(DataComponents.CUSTOM_DATA)),
                        "FA purchases lost configured JDT state");
                // JSON cannot retain an NBT long-array's tag kind. Persistent world NBT and native wire codecs must.
                var ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
                var decoded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, both).getOrThrow()).getOrThrow();
                require(ItemStack.matches(both, decoded), "JDT/FA native NBT lost typed paid state");
                var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
                try {
                    ItemStack.STREAM_CODEC.encode(buffer, both);
                    require(ItemStack.matches(both, ItemStack.STREAM_CODEC.decode(buffer)), "JDT/FA wire codec lost typed paid state");
                } finally {
                    buffer.release();
                }
                results.add(both);
            }
        } finally {
            ForbiddenModifierChecks.close(player);
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> component(String id) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(id));
    }

    private static SmithingMenu ready(ServerPlayer player, ItemStack root, String kind) {
        player.getInventory().clearContent();
        return SmithingChecks.ready(player, ForbiddenArcanusChecks.nativeItem("apply_modifier_smithing_template"), root.copy(),
                ForbiddenArcanusChecks.nativeItem(kind.equals("eternal") ? "eternal_stella" : "soul_binding_crystal"));
    }

    private static void rejected(ServerPlayer player, ItemStack root, String kind) {
        var menu = ready(player, root, kind);
        require(!menu.getSlot(3).hasItem(), "Duplicate Boots upgrade exposed a result");
        stale(player, menu, false);
        stale(player, menu, true);
    }

    private static void stale(ServerPlayer player, SmithingMenu menu, boolean shift) {
        var inputs = List.of(menu.getSlot(0).getItem().copy(), menu.getSlot(1).getItem().copy(), menu.getSlot(2).getItem().copy());
        require((shift ? menu.quickMoveStack(player, 3) : menu.getSlot(3).safeTake(1, 1, player)).isEmpty(),
                "Stale Boots modifier preview was taken");
        for (int slot = 0; slot < 3; slot++) {
            require(ItemStack.matches(inputs.get(slot), menu.getSlot(slot).getItem()), "Refused Boots upgrade consumed an input");
        }
    }

    private static void deaths(MinecraftServer server) throws Exception {
        server.overworld().setChunkForced(0, 0, true);
        for (var sample : samples(server)) {
            var root = sample.stack();
            var player = ForbiddenModifierChecks.actor(server);
            player.setItemSlot(EquipmentSlot.FEET, root.copy());
            player.getInventory().setItem(0, new ItemStack(Items.APPLE));
            require(player.hurtServer(server.overworld(), player.damageSources().fellOutOfWorld(), 10000)
                    && player.isDeadOrDying(), "Boots Soulbound actor did not die");
            var respawned = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
            require(state(root).soulbound() ? ItemStack.matches(root, respawned.getItemBySlot(EquipmentSlot.FEET))
                    : respawned.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "Native Boots Soulbound retention differs");
            require(!respawned.getInventory().contains(new ItemStack(Items.APPLE)), "Unmarked death control was retained");
            server.getPlayerList().remove(respawned);
            ForbiddenModifierChecks.close(respawned);
        }
    }

    static List<ForbiddenArcanusChecks.Sample> clientSamples(MinecraftServer server) {
        try {
            return samples(server);
        } catch (Exception failure) {
            throw new AssertionError("Could not build native Boots packet fixtures", failure);
        }
    }

    static List<ForbiddenArcanusChecks.Sample> samples(MinecraftServer server) throws Exception {
        if (!enabled()) { return List.of(); }
        var samples = new ArrayList<ForbiddenArcanusChecks.Sample>();
        for (var effects : List.of(List.of("none", "none"), List.of("eternal", "none"),
                List.of("soulbound", "none"), List.of("eternal", "soulbound"))) {
            for (var root : pair(server, source(server, effects.getFirst(), true), source(server, effects.getLast(), false))) {
                samples.add(new ForbiddenArcanusChecks.Sample("installed boots " + samples.size(), root, 0));
            }
        }
        var fixture = fixture();
        for (int slot = 0; slot < fixture.getIntOr("count", 0); slot++) {
            var root = decode(server, fixture.getCompoundOrEmpty("root" + slot));
            if (root.has(type())) {
                samples.add(new ForbiddenArcanusChecks.Sample("installed boots legacy " + slot, root, 0));
            }
        }
        for (var root : justDire(server)) {
            samples.add(new ForbiddenArcanusChecks.Sample("installed boots powered " + samples.size(), root, 0));
        }
        return samples;
    }

    private static void legacy(MinecraftServer server) throws Exception {
        var fixture = fixture();
        require(fixture.getIntOr("count", 0) == 15, "Published Boots fixture lost cases");
        int adopted = 0;
        for (int slot = 0; slot < 15; slot++) {
            var raw = fixture.getCompoundOrEmpty("root" + slot);
            var root = decode(server, raw);
            var encoded = RecoveryChecks.encode(root, server.registryAccess());
            if (!root.has(type())) {
                require(raw.equals(encoded), "Skipped legacy Boots changed: " + fixture.getStringOr("label" + slot, ""));
                continue;
            }
            adopted++;
            var state = state(root);
            verify(root, state);
            var before = raw.getCompoundOrEmpty("components").copy();
            var after = encoded.getCompoundOrEmpty("components").copy();
            require(!before.contains(FLAGS), "Legacy fixture was not written by the old product");
            for (var patch : List.of(before, after)) {
                patch.remove(FLAGS);
                patch.remove("minecraft:item_name");
                if (state.eternal()) {
                    patch.remove("minecraft:max_damage");
                    patch.remove("!minecraft:max_damage");
                    patch.remove("minecraft:damage");
                    patch.remove("!minecraft:damage");
                }
            }
            require(before.equals(after), "Legacy adoption lost unrelated live/source data");
            require(encoded.equals(RecoveryChecks.encode(decode(server, encoded), server.registryAccess())),
                    "Adopted Boots did not persist idempotently");
        }
        require(adopted == 7, "Published Boots adoption count differs from its seven supported live scalars: " + adopted);
        var raw = fixture.getCompoundOrEmpty("root1").copy();
        System.setProperty("leggings.test.failForbiddenAdoption", "true");
        try {
            var unchanged = decode(server, raw);
            require(raw.equals(RecoveryChecks.encode(unchanged, server.registryAccess())),
                    "Failed optional adoption lost the original successful item or installed a partial marker");
            var data = history(unchanged);
            boolean refused = false;
            try {
                Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer")
                        .getMethod("recompose", ItemStack.class, ItemStack.class, ItemStack.class, HolderLookup.Provider.class)
                        .invoke(null, unchanged, data.getFirst(), data.getLast(), server.registryAccess());
            } catch (InvocationTargetException failure) {
                refused = failure.getCause() instanceof IllegalArgumentException;
            }
            require(refused && raw.equals(RecoveryChecks.encode(unchanged, server.registryAccess())),
                    "Recomposition after failed adoption discarded an existing live modifier");
        } finally {
            System.clearProperty("leggings.test.failForbiddenAdoption");
        }
        System.setProperty("leggings.test.fatalForbiddenAdoption", "true");
        try {
            boolean propagated = false;
            try {
                decode(server, raw);
            } catch (AssertionError fatal) {
                propagated = fatal.getMessage().equals("Expected fatal native adoption failure");
            }
            require(propagated, "Fatal native failures were swallowed as optional adoption incompatibility");
        } finally {
            System.clearProperty("leggings.test.fatalForbiddenAdoption");
        }
        var empty = new CompoundTag();
        empty.putBoolean("eternal", false);
        empty.putBoolean("soulbound", false);
        raw.getCompoundOrEmpty("components").put(FLAGS, empty);
        var explicit = decode(server, raw);
        require(explicit.has(type()) && state(explicit).isEmpty() && explicit.has(DataComponents.MAX_DAMAGE),
                "Explicit empty authority was reseeded from a native scalar");
        var missing = fixture.getCompoundOrEmpty("root1").copy();
        missing.getCompoundOrEmpty("components").getCompoundOrEmpty("mismatchedboots:mixed_boots_data")
                .getCompoundOrEmpty("left").putString("id", "removed_forbidden:missing_boots");
        var suspended = decode(server, missing);
        require(suspended.has(component("mismatchedboots:suspended_boots")) && !suspended.has(type()),
                "A missing-source suspended root acquired modifier authority");
        var shell = new ItemStack(BuiltInRegistries.ITEM.getValue(BOOTS));
        var archive = Class.forName("games.alejandrocoria.mismatchedboots.data.SuspendedBootsData")
                .getMethod("quarantined", CompoundTag.class, CompoundTag.class)
                .invoke(null, fixture.getCompoundOrEmpty("root1").getCompoundOrEmpty("components"), new CompoundTag());
        shell.set(component("mismatchedboots:suspended_boots"), archive);
        var quarantined = decode(server, RecoveryChecks.encode(shell, server.registryAccess()));
        require(quarantined.has(component("mismatchedboots:suspended_boots")) && !quarantined.has(type()),
                "Quarantined root acquired modifier authority");
    }

    private static CompoundTag fixture() throws Exception {
        try (var stream = ForbiddenBootsChecks.class.getResourceAsStream("/forbidden-boots-1.0.0.json")) {
            require(stream != null, "Missing frozen published Boots fixture");
            var manifest = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            require(manifest.get("bootsSha256").getAsString().equals(
                    "21e2ca6062ba86f17391f09b94cadd690278d1fb52a15ea6fcc436479651bb88"), "Wrong legacy Boots producer");
            var bytes = Base64.getDecoder().decode(manifest.get("gzipNbtBase64").getAsString());
            require(java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
                    .equals(manifest.get("sha256").getAsString()), "Frozen legacy fixture hash differs");
            return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
        }
    }

    private static ItemStack decode(MinecraftServer server, CompoundTag raw) {
        return ItemStack.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, server.registryAccess()), raw).getOrThrow();
    }

    static List<ItemStack> pair(MinecraftServer server, ItemStack left, ItemStack right) throws Exception {
        var input = new WorkbenchInput(left, right);
        var recipe = server.overworld().recipeAccess().getRecipeFor(WorkbenchRecipe.TYPE, input, server.overworld()).orElseThrow();
        var result = recipe.value().assemblePair(input, server.registryAccess());
        require(result.isComplete(), "Boots workbench context produced an incomplete pair");
        return List.of(result.primary(), result.inverse());
    }

    private static ItemStack source(MinecraftServer server, String kind, boolean diamond) throws Exception {
        var source = new ItemStack(diamond ? Items.DIAMOND_BOOTS : Items.IRON_BOOTS);
        if (!kind.equals("none")) {
            var holder = server.registryAccess().lookupOrThrow(MODIFIERS)
                    .getOrThrow(ResourceKey.create(MODIFIERS, Identifier.fromNamespaceAndPath("forbidden_arcanus", kind)));
            Class.forName("com.stal111.forbidden_arcanus.common.item.modifier.ModifierHelper")
                    .getMethod("setModifier", ItemStack.class, Holder.class).invoke(null, source, holder);
        }
        return source;
    }

    @SuppressWarnings("unchecked")
    static DataComponentType<ForbiddenModifiers> type() {
        return (DataComponentType<ForbiddenModifiers>) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(FLAGS));
    }

    private static ForbiddenModifiers state(ItemStack root) {
        return root.getOrDefault(type(), ForbiddenModifiers.EMPTY);
    }

    private static void verify(ItemStack root, ForbiddenModifiers state) {
        require(root.is(BuiltInRegistries.ITEM.getValue(BOOTS)) && state(root).equals(state), "Boots root authority differs");
        String scalar = ForbiddenModifierChecks.nativeKind(root);
        require(scalar.equals(state.soulbound() ? "soulbound" : state.eternal() ? "eternal" : "none"), "Boots scalar projection differs");
        require(state.eternal() ? !root.has(DataComponents.MAX_DAMAGE) && !root.has(DataComponents.DAMAGE)
                : root.isDamageableItem(), "Boots Eternal/finite durability differs");
    }

    private static List<ItemStack> history(ItemStack root) throws Exception {
        var maybe = (java.util.Optional<?>) Class.forName(ADAPTER).getMethod("validData", ItemStack.class).invoke(null, root);
        var data = maybe.orElseThrow();
        var result = new ArrayList<ItemStack>();
        for (String side : List.of("left", "right")) {
            var half = data.getClass().getMethod(side).invoke(data);
            result.add((ItemStack) half.getClass().getMethod("source").invoke(half));
        }
        return result;
    }

    static String historyDifference(ItemStack before, ItemStack after) {
        if (!before.is(BuiltInRegistries.ITEM.getValue(BOOTS))) { return ""; }
        try {
            var first = history(before);
            var second = history(after);
            return " left=" + ForbiddenModifierChecks.changedComponents(first.getFirst(), second.getFirst())
                    + " right=" + ForbiddenModifierChecks.changedComponents(first.getLast(), second.getLast());
        } catch (Exception failure) {
            return " unavailable history: " + failure;
        }
    }

    private static ItemStack recompose(MinecraftServer server, ItemStack root, ItemStack left, ItemStack right) throws Exception {
        try {
            return (ItemStack) Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer")
                    .getMethod("recompose", ItemStack.class, ItemStack.class, ItemStack.class, HolderLookup.Provider.class)
                    .invoke(null, root, left, right, server.registryAccess());
        } catch (InvocationTargetException failure) {
            throw new AssertionError("Boots recomposition failed", failure.getCause());
        }
    }
}
