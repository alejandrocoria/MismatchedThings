package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.serialization.JsonOps;
import com.stal111.forbidden_arcanus.common.item.modifier.BuiltInItemModifiers;
import com.stal111.forbidden_arcanus.common.item.modifier.ItemModifier;
import com.stal111.forbidden_arcanus.common.item.modifier.ModifierHelper;
import com.stal111.forbidden_arcanus.core.init.ModDataComponents;
import com.stal111.forbidden_arcanus.core.init.other.ModAttachmentTypes;
import com.stal111.forbidden_arcanus.core.registry.FARegistries;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsRecomposer;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Opt-in observation/prototype, never part of an armor product or its ordinary test source set. */
public final class ForbiddenModifierSpike {
    private static final StringBuilder REPORT = new StringBuilder();

    private ForbiddenModifierSpike() {}

    public static void server(MinecraftServer server) throws Exception {
        try {
            inheritance(server);
            workstations(server);
            candidates(server);
            deaths(server);
        } finally {
            Files.writeString(report("server"), REPORT);
        }
    }

    private static void inheritance(MinecraftServer server) {
        REPORT.append("CURRENT INHERITANCE (shipped defaults)\n");
        for (String first : List.of("none", "eternal", "soulbound")) {
            for (String second : List.of("none", "eternal", "soulbound")) {
                var left = source(server.registryAccess(), first, true);
                var right = source(server.registryAccess(), second, false);
                var beforeLeft = left.copy();
                var beforeRight = right.copy();
                var pair = MixedLeggingsFactory.createPair(left, right, CompositionRules.defaults());
                for (var root : List.of(pair.primary(), pair.inverse())) {
                    boolean primary = root == pair.primary();
                    require(MixedLeggingsStructure.validData(root).isPresent(), "Factory produced malformed modifier root");
                    require(modifier(root).equals(primary ? first : second), "Unknown component stopped following owner policy");
                    roundTrip(server, root);
                    var player = actor(server);
                    player.setItemSlot(EquipmentSlot.LEGS, root);
                    int damage = root.getDamageValue();
                    root.hurtAndBreak(1, player, EquipmentSlot.LEGS);
                    REPORT.append(first).append('/').append(second).append(primary ? " primary: " : " inverse: ")
                            .append(state(root)).append(" wearDelta=").append(root.getDamageValue() - damage).append('\n');
                    ((EmbeddedChannel) player.connection.getConnection().channel()).finishAndReleaseAll();
                }
                require(ItemStack.matches(left, beforeLeft) && ItemStack.matches(right, beforeRight), "Composition/wear mutated sources");
            }
        }
    }

    private static void workstations(MinecraftServer server) {
        REPORT.append("\nCURRENT NATIVE MENUS (paid real result-slot take)\n");
        for (String kind : List.of("eternal", "soulbound")) {
            for (boolean named : List.of(false, true)) {
                var root = pair(source(server.registryAccess(), "none", true), source(server.registryAccess(), "none", false));
                if (named) {
                    root.set(DataComponents.CUSTOM_NAME, Component.literal("Custom mixed name"));
                }
                var input = new SmithingRecipeInput(template(), root.copy(), addition(kind));
                boolean direct = server.overworld().recipeAccess().getRecipeFor(RecipeType.SMITHING, input, server.overworld()).isPresent();
                var attempt = attempt(server, root, template(), addition(kind));
                var result = attempt.taken();
                REPORT.append(kind).append(" named=").append(named).append(" direct=").append(direct)
                        .append(" preview: ").append(state(attempt.preview()))
                        .append(" takeAccepted=").append(!result.isEmpty()).append('\n');
                if (result.isEmpty()) {
                    continue;
                }
                roundTrip(server, result);
                if (MixedLeggingsStructure.validData(result).isPresent()) {
                    var trimmed = paid(server, result, new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE), new ItemStack(Items.DIAMOND));
                    REPORT.append("  subsequent trim => ").append(state(trimmed)).append('\n');
                    roundTrip(server, trimmed);
                }
            }
        }

        var root = pair(source(server.registryAccess(), "none", true), source(server.registryAccess(), "none", false));
        root.set(DataComponents.CUSTOM_NAME, Component.literal("Eternal then Soulbound"));
        var eternal = paid(server, root, template(), addition("eternal"));
        var second = paid(server, eternal, template(), addition("soulbound"));
        REPORT.append("root Eternal then source-wise Soulbound => ").append(state(second)).append('\n');
        roundTrip(server, second);
    }

    private static void candidates(MinecraftServer server) {
        REPORT.append("\nSPIKE-ONLY ROOT CANDIDATES (not production interception)\n");
        for (String kind : List.of("eternal", "soulbound")) {
            var root = pair(source(server.registryAccess(), "none", true), source(server.registryAccess(), "none", false));
            var before = root.copy();
            var candidate = applyRoot(server.registryAccess(), root, kind);
            require(ItemStack.matches(root, before), "Prototype modified caller");
            var data = MixedLeggingsStructure.validData(candidate).orElseThrow();
            require(data.equals(MixedLeggingsStructure.validData(root).orElseThrow()), "Root candidate rewrote source history");
            var recomposed = MixedLeggingsRecomposer.recompose(candidate, data.left().source(), data.right().source());
            REPORT.append(kind).append(" normalized root: ").append(state(candidate))
                    .append("; existing recomposition: ").append(state(recomposed)).append('\n');
            if (modifier(candidate).equals("eternal")) {
                // Feasibility only: reapply the native effect AFTER generic recomposition.
                recomposed = applyRoot(server.registryAccess(), recomposed, kind);
                require(!recomposed.isDamageableItem(), "Native Eternal could not be restored after recomposition");
            }
            roundTrip(server, recomposed);
            var input = new SmithingRecipeInput(template(), admissionCopy(root, data.left().source()), addition(kind));
            var recipe = server.overworld().recipeAccess().getRecipeFor(RecipeType.SMITHING, input, server.overworld()).orElseThrow();
            require(!recipe.value().assemble(input).isEmpty(), "Live-root admission copy could not reuse native recipe validation");
            var blocked = admissionCopy(candidate, data.left().source());
            require(!holder(server.registryAccess(), kind).value().isValidItem(blocked), "Admission copy ignored already installed root modifier");
            REPORT.append("  native recipe accepts live-state native-item admission copy; second application refused; ")
                    .append(state(recomposed)).append('\n');
        }
    }

    private static void deaths(MinecraftServer server) {
        REPORT.append("\nREAL DEATH / PLAYERLIST RESPAWN (native attachment/clone path)\n");
        server.overworld().setChunkForced(0, 0, true);
        var original = source(server.registryAccess(), "soulbound", true);
        var mixed = pair(original, source(server.registryAccess(), "none", false));
        var inverse = MixedLeggingsFactory.createPair(original, source(server.registryAccess(), "none", false), CompositionRules.defaults()).inverse();
        for (var root : List.of(original, mixed, inverse,
                applyRoot(server.registryAccess(), pair(new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.IRON_LEGGINGS)), "soulbound"))) {
            var player = actor(server);
            player.setPos(.5, 65, .5);
            player.setItemSlot(EquipmentSlot.LEGS, root.copy());
            player.getInventory().setItem(0, new ItemStack(Items.APPLE));
            boolean retained = modifier(root).equals("soulbound");
            require(player.hurtServer(server.overworld(), player.damageSources().fellOutOfWorld(), 10000), "Death control took no damage");
            require(player.isDeadOrDying(), "Player did not actually die");
            require(player.getData(ModAttachmentTypes.SOULBOUND_INVENTORY.get()).entries().isEmpty() != retained,
                    "Native death attachment did not select live root modifier");
            var respawned = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
            require(respawned != player && !respawned.isDeadOrDying(), "PlayerList did not create a living replacement player");
            require(retained ? ItemStack.matches(root, respawned.getItemBySlot(EquipmentSlot.LEGS))
                            : respawned.getItemBySlot(EquipmentSlot.LEGS).isEmpty(),
                    "Native Soulbound did not preserve exactly the expected root through actual respawn");
            require(!respawned.getInventory().contains(new ItemStack(Items.APPLE)), "Unmarked control was returned after death");
            REPORT.append("modifier=").append(modifier(root)).append(" mixed=").append(MixedLeggingsStructure.isMixedItem(root))
                    .append(" rootReturned=").append(retained).append(" ordinaryAppleReturned=false\n");
            server.getPlayerList().remove(respawned);
            ((EmbeddedChannel) respawned.connection.getConnection().channel()).finishAndReleaseAll();
        }
    }

    static ItemStack source(HolderLookup.Provider registries, String kind, boolean diamond) {
        var stack = new ItemStack(diamond ? Items.DIAMOND_LEGGINGS : Items.IRON_LEGGINGS);
        stack.setDamageValue(diamond ? 20 : 10);
        if (!kind.equals("none")) {
            ModifierHelper.setModifier(stack, holder(registries, kind));
        }
        return stack;
    }

    static ItemStack pair(ItemStack first, ItemStack second) {
        return MixedLeggingsFactory.createPair(first, second, CompositionRules.defaults()).primary();
    }

    static ItemStack applyRoot(HolderLookup.Provider registries, ItemStack root, String kind) {
        var result = root.copy();
        var structuralName = result.get(DataComponents.ITEM_NAME);
        ModifierHelper.setModifier(result, holder(registries, kind));
        result.set(DataComponents.ITEM_NAME, structuralName);
        require(MixedLeggingsStructure.validData(result).isPresent(), "Native effect plus structural name restoration is not a valid root");
        return result;
    }

    private static ItemStack admissionCopy(ItemStack root, ItemStack nativeSource) {
        var copy = new ItemStack(nativeSource.getItem());
        List.copyOf(copy.getComponents().keySet()).forEach(copy::remove);
        copy.applyComponents(root.getComponents());
        return copy;
    }

    private static Holder<ItemModifier> holder(HolderLookup.Provider registries, String kind) {
        return registries.lookupOrThrow(FARegistries.ITEM_MODIFIER)
                .getOrThrow(kind.equals("eternal") ? BuiltInItemModifiers.ETERNAL : BuiltInItemModifiers.SOULBOUND);
    }

    private static ItemStack paid(MinecraftServer server, ItemStack root, ItemStack template, ItemStack addition) {
        var attempt = attempt(server, root, template, addition);
        require(!attempt.taken().isEmpty(), "Expected payable result was refused: " + state(attempt.preview()));
        return attempt.taken();
    }

    private static Attempt attempt(MinecraftServer server, ItemStack root, ItemStack template, ItemStack addition) {
        var player = actor(server);
        var before = root.copy();
        var menu = SmithingChecks.ready(player, template.copyWithCount(2), root, addition.copyWithCount(2));
        int templatesBefore = menu.getSlot(0).getItem().getCount();
        int additionsBefore = menu.getSlot(2).getItem().getCount();
        var result = menu.getSlot(3).getItem().copy();
        require(!result.isEmpty(), "No real smithing output for " + state(root) + ", addition=" + addition);
        require(ItemStack.matches(before, root), "Preview mutated its input");
        var taken = menu.getSlot(3).safeTake(1, 1, player);
        REPORT.append("  take probe: preview=").append(state(result)).append("; taken=").append(state(taken))
                .append("; baseCount=").append(menu.getSlot(1).getItem().getCount())
                .append("; templateCount=").append(menu.getSlot(0).getItem().getCount())
                .append("; additionCount=").append(menu.getSlot(2).getItem().getCount()).append('\n');
        if (taken.isEmpty()) {
            require(ItemStack.matches(root, menu.getSlot(1).getItem()) && menu.getSlot(0).getItem().getCount() == templatesBefore
                            && menu.getSlot(2).getItem().getCount() == additionsBefore,
                    "Rejected result consumed an input");
        } else {
            require(ItemStack.matches(result, taken) && !menu.getSlot(1).hasItem()
                            && menu.getSlot(0).getItem().getCount() == templatesBefore - 1
                            && menu.getSlot(2).getItem().getCount() == additionsBefore - 1
                            && menu.getSlot(3).safeTake(1, 1, player).isEmpty(), "Preview/payment/repeated take differs");
        }
        ((EmbeddedChannel) player.connection.getConnection().channel()).finishAndReleaseAll();
        return new Attempt(result, taken);
    }

    private static ItemStack template() {
        return ForbiddenArcanusChecks.nativeItem("apply_modifier_smithing_template");
    }

    private static ItemStack addition(String kind) {
        return ForbiddenArcanusChecks.nativeItem(kind.equals("eternal") ? "eternal_stella" : "soul_binding_crystal");
    }

    private static ServerPlayer actor(MinecraftServer server) {
        var player = RepairFixtures.player(server);
        new EmbeddedChannel(player.connection.getConnection());
        player.connection.markClientLoaded();
        return player;
    }

    static String modifier(ItemStack stack) {
        var value = stack.get(ModDataComponents.ITEM_MODIFIER.get());
        return value == null ? "none" : value.unwrapKey().orElseThrow().identifier().getPath();
    }

    static String state(ItemStack stack) {
        var data = MixedLeggingsStructure.validData(stack).orElse(null);
        var suspended = LeggingsContent.suspendedLeggingsData(stack).orElse(null);
        return "modifier=" + modifier(stack) + " damage=" + stack.getDamageValue() + '/' + stack.getMaxDamage()
                + " damageable=" + stack.isDamageableItem() + " structure="
                + (MixedLeggingsStructure.isMixedItem(stack) ? MixedLeggingsStructure.classify(stack) : "native")
                + " quarantined=" + (suspended != null && suspended.quarantined())
                + " sources=" + (data == null ? "unavailable" : modifier(data.left().source()) + '/' + modifier(data.right().source()))
                + " name=" + stack.getHoverName().getString();
    }

    private static void roundTrip(MinecraftServer server, ItemStack stack) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var saved = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
        var loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
        boolean malformed = MixedLeggingsStructure.isMixedItem(stack) && MixedLeggingsStructure.validData(stack).isEmpty();
        if (malformed) {
            REPORT.append("  malformed persistent decode => ").append(state(loaded))
                    .append(" unchanged=").append(ItemStack.matches(stack, loaded)).append('\n');
        } else {
            require(ItemStack.matches(stack, loaded), "Native modifier lost persistent data");
        }
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, stack);
            var received = ItemStack.STREAM_CODEC.decode(buffer);
            if (malformed) {
                REPORT.append("  malformed network decode => ").append(state(received))
                        .append(" unchanged=").append(ItemStack.matches(stack, received)).append('\n');
            } else {
                require(ItemStack.matches(stack, received), "Native modifier lost network data");
            }
        } finally {
            buffer.release();
        }
    }

    static Path report(String phase) {
        return Path.of(System.getProperty("leggings.test.report"), "modifiers-" + phase + ".txt");
    }

    private record Attempt(ItemStack preview, ItemStack taken) {}
}
