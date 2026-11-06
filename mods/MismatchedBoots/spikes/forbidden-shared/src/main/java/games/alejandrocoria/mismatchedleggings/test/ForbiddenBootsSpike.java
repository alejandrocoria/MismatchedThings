package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifiers;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Generate real old-product saves; subsequent client JVM loads them through the disposable candidate. */
public final class ForbiddenBootsSpike {
    static final BlockPos STORAGE = new BlockPos(0, 65, 0);
    private static boolean finished;

    private ForbiddenBootsSpike() {}

    public static void register(IEventBus bus) {
        require(ForbiddenBootsCandidate.nativeApplications == 0, "Native modifier API ran during codec/bootstrap construction");
        require(ModList.get().getModContainerById("mismatchedboots").orElseThrow().getModInfo()
                .getVersion().toString().equals("1.0.0"), "Spike is not using published Boots 1.0.0");
        var components = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, "leggings_skeleton_test");
        components.register("forbidden_boots_state", () -> ForbiddenBootsCandidate.FLAGS);
        components.register(bus);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
        if (FMLEnvironment.getDist().isClient()) {
            bus.addListener(ForbiddenBootsSpikeClient::setup);
        }
    }

    private static void tick(MinecraftServer server) {
        if (!server.isDedicatedServer() || finished || server.getTickCount() < 20) { return; }
        finished = true;
        try {
            write(server);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        }
        server.halt(false);
    }

    private static void write(MinecraftServer server) throws Exception {
        require(ForbiddenBootsCandidate.adoptions == 0, "Old producer was normalized before fixture creation");
        var cases = new LinkedHashMap<String, ItemStack>();
        cases.put("plain", BootsCoexistence.mixed());
        for (var kinds : List.of(List.of("eternal", "none"), List.of("soulbound", "none"), List.of("eternal", "soulbound"))) {
            var first = source(server, kinds.getFirst(), true);
            var second = source(server, kinds.getLast(), false);
            var pair = BootsCoexistence.pair(first, second);
            cases.put(kinds + " primary", pair.getFirst());
            cases.put(kinds + " inverse", pair.getLast());
        }
        var historyOnly = cases.get("[eternal, none] primary").copy();
        require(historyOnly.isDamageableItem(), "Published producer did not reproduce finite Eternal projection");
        historyOnly.remove(ForbiddenBootsCandidate.nativeType());
        cases.put("history only", historyOnly);
        cases.put("unknown fiery", BootsCoexistence.pair(source(server, "fiery", true), new ItemStack(Items.IRON_BOOTS)).getFirst());
        var invalid = cases.get("[eternal, none] primary").copy();
        invalid.remove(DataComponents.EQUIPPABLE);
        cases.put("malformed equipment", invalid);
        var customItemName = cases.get("[eternal, none] primary").copy();
        customItemName.set(DataComponents.ITEM_NAME, Component.literal("Unrecognized structural override"));
        cases.put("unrecognized item name", customItemName);
        cases.put("bare", item("mismatchedboots:mixed_boots"));

        var player = ForbiddenModifierChecks.actor(server);
        var routing = new StringBuilder();
        try {
            for (String kind : List.of("eternal", "soulbound")) {
                var plain = BootsCoexistence.mixed();
                var template = item("forbidden_arcanus:apply_modifier_smithing_template");
                var addition = addition(kind);
                require(route(server, plain, template, addition).handled(), "Candidate did not reserve a supported FA request");
                var menu = SmithingChecks.ready(player, template, plain.copy(), addition);
                routing.append(kind).append(" old direct native root recipe=")
                        .append(server.overworld().recipeAccess().getRecipeFor(RecipeType.SMITHING,
                                new SmithingRecipeInput(template, plain, addition), server.overworld()).isPresent())
                        .append(", actual menu result=").append(!menu.getSlot(3).getItem().isEmpty()).append('\n');
                if (menu.getSlot(3).hasItem()) {
                    cases.put("old paid " + kind, take(player, menu));
                }
            }
            var jdt = BootsCoexistence.pair(item("justdirethings:eclipsealloy_boots"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
            jdt.set(component("justdirethings:forge_energy"), 2000);
            var upgrade = item("justdirethings:upgrade_stepheight");
            require(!route(server, jdt, ItemStack.EMPTY, upgrade).handled(), "FA route swallowed JDT upgrade");
            var menu = SmithingChecks.ready(player, ItemStack.EMPTY, jdt.copy(), upgrade);
            var paid = take(player, menu);
            require(Boolean.TRUE.equals(paid.get(component("justdirethings:stepheight_upgrade_installed")))
                            && Integer.valueOf(2000).equals(paid.get(component("justdirethings:forge_energy"))),
                    "Native old Boots JDT route lost upgrade/charge");
            ForbiddenBootsCandidate.apply(paid, ForbiddenBootsCandidate.modifier(server, "soulbound"));
            cases.put("paid JDT plus native Soulbound", paid);
            routing.append("JDT actual root upgrade paid once, FA candidate unhandled, charge preserved\n");
        } finally {
            ForbiddenModifierChecks.close(player);
        }
        server.overworld().getChunkAt(STORAGE);
        server.overworld().setBlock(STORAGE, Blocks.BARREL.defaultBlockState(), 3);
        var barrel = storage(server);
        var baseline = new CompoundTag();
        int slot = 0;
        for (var entry : cases.entrySet()) {
            var root = entry.getValue();
            if (ForbiddenBootsCandidate.valid(root)) {
                root.set(DataComponents.CUSTOM_NAME, Component.literal("Legacy live " + slot));
                root.set(DataComponents.REPAIR_COST, 5);
                var opaque = new CompoundTag();
                opaque.putLongArray("opaque", new long[]{Long.MAX_VALUE, Long.MIN_VALUE});
                root.set(DataComponents.CUSTOM_DATA, CustomData.of(opaque));
                if (root.isDamageableItem()) { root.setDamageValue(Math.min(17, root.getMaxDamage() - 1)); }
            }
            require(!root.has(ForbiddenBootsCandidate.FLAGS), "Producer created candidate flags");
            barrel.setItem(slot, root);
            baseline.putString("label" + slot, entry.getKey());
            baseline.put("root" + slot, RecoveryChecks.encode(root, server.registryAccess()));
            slot++;
        }
        baseline.putInt("count", slot);
        barrel.setChanged();
        NbtIo.writeCompressed(baseline, baseline());
        require(ForbiddenBootsCandidate.adoptions == 0, "Published producer was silently upgraded during saving");
        Files.writeString(report("server"), "Published Boots generated " + slot + " roots in a real barrel; no adoption\n" + routing);
        Files.writeString(ForbiddenArcanusChecks.report("server"), "Bounded old-Boots producer, not Aureal acceptance\n");
        server.saveAllChunks(false, true, true);
    }

    static void verifyLoaded(MinecraftServer server, boolean reloaded) throws Exception {
        var baseline = RecoveryChecks.read(baseline());
        var barrel = storage(server);
        var report = new StringBuilder();
        for (int slot = 0; slot < baseline.getIntOr("count", 0); slot++) {
            var original = unconverted(server, baseline.getCompoundOrEmpty("root" + slot));
            var expected = original.copy();
            String label = baseline.getStringOr("label" + slot, "");
            var state = ForbiddenBootsCandidate.state(ForbiddenBootsCandidate.nativeHolder(original));
            if (ForbiddenBootsCandidate.valid(original) && !label.equals("unrecognized item name") && !state.isEmpty()) {
                expected.set(ForbiddenBootsCandidate.FLAGS, state);
                expected.set(DataComponents.ITEM_NAME, Component.translatable("item.mismatchedboots.mixed_boots"));
                if (state.eternal()) {
                    expected.remove(DataComponents.MAX_DAMAGE);
                    expected.remove(DataComponents.DAMAGE);
                }
            }
            var actual = barrel.getItem(slot);
            require(ItemStack.matches(actual, expected), "Native barrel load differed in " + label);
            require(ItemStack.matches(actual, ForbiddenBootsCandidate.adopt(actual)), "Adoption is not idempotent");
            var encoded = RecoveryChecks.encode(actual, server.registryAccess());
            require(ItemStack.matches(actual, RecoveryChecks.decode(encoded, server.registryAccess())), "Copy/save/read changed adopted state");
            report.append(baseline.getStringOr("label" + slot, "")).append(": ")
                    .append(actual.getOrDefault(ForbiddenBootsCandidate.FLAGS, ForbiddenModifiers.EMPTY)).append('\n');
        }
        if (!reloaded) {
            controls(server, baseline);
            routing(server, barrel.getItem(1));
        }
        Files.writeString(report(reloaded ? "reloaded" : "loaded"), report.toString());
    }

    private static void controls(MinecraftServer server, CompoundTag baseline) throws Exception {
        var original = unconverted(server, baseline.getCompoundOrEmpty("root1"));
        var current = original.copy();
        current.set(ForbiddenBootsCandidate.FLAGS, ForbiddenModifiers.EMPTY);
        require(ItemStack.matches(current, ForbiddenBootsCandidate.adopt(current))
                        && ForbiddenModifiers.EMPTY.equals(RecoveryChecks.decode(RecoveryChecks.encode(current, server.registryAccess()),
                        server.registryAccess()).get(ForbiddenBootsCandidate.FLAGS)), "Explicit empty state was reseeded");
        var absent = baseline.getCompoundOrEmpty("root1").copy();
        absent.getCompoundOrEmpty("components").getCompoundOrEmpty("mismatchedboots:mixed_boots_data")
                .getCompoundOrEmpty("left").putString("id", "removed_spike:missing_boots");
        var suspended = RecoveryChecks.decode(absent, server.registryAccess());
        require(suspended.has(component("mismatchedboots:suspended_boots")) && !suspended.has(ForbiddenBootsCandidate.FLAGS)
                        && !suspended.has(ForbiddenBootsCandidate.nativeType()),
                "Absent-source shell acquired live flags");
        var multiple = original.copy();
        multiple.setCount(2);
        require(ItemStack.matches(multiple, ForbiddenBootsCandidate.adopt(multiple)), "Malformed count acquired authority");
        var quarantine = Class.forName("games.alejandrocoria.mismatchedboots.data.SuspendedBootsData")
                .getMethod("quarantined", CompoundTag.class, CompoundTag.class)
                .invoke(null, baseline.getCompoundOrEmpty("root1").getCompoundOrEmpty("components"), new CompoundTag());
        var quarantined = original.copy();
        quarantined.set(ForbiddenBootsSpike.<Object>component("mismatchedboots:suspended_boots"), quarantine);
        require(ItemStack.matches(quarantined, ForbiddenBootsCandidate.adopt(quarantined))
                        && !RecoveryChecks.decode(RecoveryChecks.encode(quarantined, server.registryAccess()), server.registryAccess())
                        .has(ForbiddenBootsCandidate.FLAGS), "Quarantined state acquired modifier flags");
    }

    private static void routing(MinecraftServer server, ItemStack adopted) throws Exception {
        var other = addition("soulbound");
        var template = item("forbidden_arcanus:apply_modifier_smithing_template");
        var match = route(server, adopted, template, other);
        require(match.handled() && match.recipe() != null, "Masking native scalar did not admit the missing effect");
        var duplicate = route(server, adopted, template, addition("eternal"));
        require(duplicate.handled() && duplicate.recipe() == null, "Duplicate fell through to source-wise/native smithing");
        var player = ForbiddenModifierChecks.actor(server);
        var evidence = new StringBuilder("FA classifier admits the missing effect and handles duplicate rejection; JDT materials remain unhandled\n");
        try {
            for (String kind : List.of("soulbound", "eternal")) {
                var jdtRoot = BootsCoexistence.pair(item("justdirethings:eclipsealloy_boots"), new ItemStack(Items.IRON_BOOTS)).getFirst();
                ForbiddenBootsCandidate.apply(jdtRoot, ForbiddenBootsCandidate.modifier(server, kind));
                jdtRoot = ForbiddenBootsCandidate.adopt(jdtRoot);
                var menu = SmithingChecks.ready(player, ItemStack.EMPTY, jdtRoot.copy(), item("justdirethings:upgrade_stepheight"));
                var output = take(player, menu);
                require(jdtRoot.get(ForbiddenBootsCandidate.FLAGS).equals(output.get(ForbiddenBootsCandidate.FLAGS)),
                        "Old JDT recomposition removed adopted own state");
                evidence.append(kind).append(" + paid old JDT upgrade: flags preserved; live finite durability=")
                        .append(output.isDamageableItem()).append('\n');
            }
        } finally { ForbiddenModifierChecks.close(player); }
        Files.writeString(report("routing"), evidence.toString());
    }

    record Route(boolean handled, RecipeHolder<SmithingRecipe> recipe) {}

    private static Route route(MinecraftServer server, ItemStack root, ItemStack template, ItemStack addition) throws Exception {
        var state = root.getOrDefault(ForbiddenBootsCandidate.FLAGS, ForbiddenBootsCandidate.state(ForbiddenBootsCandidate.nativeHolder(root)));
        boolean handled = false;
        for (var recipe : server.getRecipeManager().recipeMap().byType(RecipeType.SMITHING)) {
            var nativeRecipe = recipe.value();
            if (!nativeRecipe.getClass().getName().equals("com.stal111.forbidden_arcanus.common.item.crafting.ApplyModifierRecipe")
                    || !Ingredient.testOptionalIngredient(nativeRecipe.templateIngredient(), template)
                    || !Ingredient.testOptionalIngredient(nativeRecipe.additionIngredient(), addition)) { continue; }
            var field = nativeRecipe.getClass().getDeclaredField("modifier");
            field.setAccessible(true);
            var added = ForbiddenBootsCandidate.state((Holder<?>) field.get(nativeRecipe));
            if (added.isEmpty()) { continue; }
            handled = true;
            if (state.union(added).equals(state)) { continue; }
            for (var source : ForbiddenBootsCandidate.sources(root)) {
                var admission = new ItemStack(source.getItem());
                List.copyOf(admission.getComponents().keySet()).forEach(admission::remove);
                admission.applyComponents(root.getComponents());
                admission.remove(ForbiddenBootsCandidate.nativeType());
                if (nativeRecipe.matches(new SmithingRecipeInput(template, admission, addition), server.overworld())) {
                    return new Route(true, recipe);
                }
            }
        }
        return new Route(handled, null);
    }

    static ItemStack unconverted(MinecraftServer server, CompoundTag raw) {
        ForbiddenBootsCandidate.BYPASS.set(true);
        try { return RecoveryChecks.decode(raw, server.registryAccess()); }
        finally { ForbiddenBootsCandidate.BYPASS.remove(); }
    }

    private static ItemStack source(MinecraftServer server, String kind, boolean diamond) {
        var source = new ItemStack(diamond ? Items.DIAMOND_BOOTS : Items.IRON_BOOTS);
        if (!kind.equals("none")) { ForbiddenBootsCandidate.apply(source, ForbiddenBootsCandidate.modifier(server, kind)); }
        return source;
    }

    static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow());
    }

    private static ItemStack addition(String kind) {
        return item("forbidden_arcanus:" + (kind.equals("eternal") ? "eternal_stella" : "soul_binding_crystal"));
    }

    private static ItemStack take(ServerPlayer player, net.minecraft.world.inventory.SmithingMenu menu) {
        var result = menu.getSlot(3).getItem().copy();
        require(!result.isEmpty(), "Native old-Boots smithing did not expose a paid result");
        var recipe = ((ResultContainer) menu.getSlot(3).container).getRecipeUsed();
        int credits = SmithingCredits.count(player, recipe.id().identifier());
        require(ItemStack.matches(result, menu.getSlot(3).safeTake(1, 1, player))
                        && menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty()
                        && SmithingCredits.count(player, recipe.id().identifier()) == credits + 1,
                "Native payment/recipe credit changed");
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> component(String id) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(id));
    }

    static BarrelBlockEntity storage(MinecraftServer server) {
        server.overworld().getChunkAt(STORAGE);
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(STORAGE);
        require(barrel != null, "Native old-world barrel was not loaded");
        return barrel;
    }

    static Path report(String phase) {
        return Path.of(System.getProperty("leggings.test.report"), "forbidden-boots-" + phase + ".txt");
    }

    private static Path baseline() {
        return Path.of(System.getProperty("leggings.test.report"), "forbidden-boots-baseline.nbt");
    }
}
