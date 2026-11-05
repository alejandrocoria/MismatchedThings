package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Disposable characterization of native enchantment creation versus live-root preservation. */
public final class RubyFireProbe implements ModInitializer {
    private static final StringBuilder OBSERVATIONS = new StringBuilder();
    private static boolean finished;

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (finished) {
                return;
            }
            finished = true;
            var reports = Path.of(System.getProperty("leggings.test.rubyFireReport"));
            try {
                verify(server);
                Files.writeString(reports.resolve("observations.txt"), OBSERVATIONS);
                Files.writeString(reports.resolve("probe.ok"), "Native smithing/composition/anvil characterization completed\n");
            } catch (Throwable failure) {
                var trace = new StringWriter();
                failure.printStackTrace(new PrintWriter(trace));
                try {
                    Files.writeString(reports.resolve("observations.txt"), OBSERVATIONS);
                    Files.writeString(reports.resolve("probe.failure.txt"), trace.toString());
                } catch (Exception reportFailure) {
                    failure.addSuppressed(reportFailure);
                }
                failure.printStackTrace();
            } finally {
                server.halt(false);
            }
        });
    }

    private static void verify(MinecraftServer server) throws Exception {
        SkeletonChecks.verify();
        verifyProduct(server, "leggings", EquipmentSlot.LEGS, Items.IRON_LEGGINGS);
        verifyProduct(server, "boots", EquipmentSlot.FEET, Items.IRON_BOOTS);
    }

    private static void verifyProduct(MinecraftServer server, String product, EquipmentSlot slot,
                                      Item iron) throws Exception {
        OBSERVATIONS.append("Product: ").append(product).append('\n');
        var ruby = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse("betternether:ruby_fire")));
        var unbreaking = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
        var protection = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
        var nativeRuby = nativeStack(server, "nether_ruby_" + product);
        var nativeFire = nativeStack(server, "flaming_ruby_" + product);
        require(level(nativeRuby, ruby) == 0 && level(nativeFire, ruby) == 1, "Native setup control failed");
        OBSERVATIONS.append("Native setup: Nether Ruby=0, Fireruby=1\n");

        var nativeUpgrade = upgrade(server, nativeRuby);
        require(nativeUpgrade.is(nativeFire.getItem()) && level(nativeUpgrade, ruby) == 1,
                "Native smithing did not add Ruby Fire I");
        retaliation(server, nativeUpgrade, slot, true, "native upgraded Fireruby");
        OBSERVATIONS.append("Native smithing: Ruby Fire=1, paid once, retaliation works\n");

        var composed = pair(product, nativeFire, new ItemStack(iron));
        int orientation = 0;
        for (var root : composed) {
            require(level(root, ruby) == 1, "Initial composition lost Ruby Fire");
            retaliation(server, root.copy(), slot, true, "initial composition " + orientation);
            anvil(server, root, ruby);
            OBSERVATIONS.append("Initial composition ").append(orientation++)
                    .append(": root Ruby Fire=1, retaliation and book application work\n");
        }

        nativeRuby.enchant(protection, 4);
        var pair = pair(product, nativeRuby, new ItemStack(iron));
        orientation = 0;
        for (var root : pair) {
            // Deliberately remove an inherited enchantment from the live root; keep it in provenance.
            var liveEnchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            liveEnchantments.set(unbreaking, 3);
            root.set(DataComponents.ENCHANTMENTS, liveEnchantments.toImmutable());
            root.set(DataComponents.CUSTOM_NAME, Component.literal("Live Ruby upgrade target"));
            root.set(DataComponents.REPAIR_COST, 3);
            root.setDamageValue(17);
            var before = root.copy();
            var result = upgrade(server, root);
            var source = sources(product, result).stream().filter(stack -> stack.is(nativeFire.getItem())).findFirst().orElseThrow();
            require(source.is(nativeFire.getItem()) && level(source, ruby) == 1, "Transformed source lacks native Ruby Fire");
            require(level(result, unbreaking) == 3 && level(result, protection) == 0,
                    "Upgrade lost live enchantments or resurrected removed source enchantments");
            require(result.get(DataComponents.CUSTOM_NAME).equals(before.get(DataComponents.CUSTOM_NAME))
                            && result.getDamageValue() == 17 && result.get(DataComponents.REPAIR_COST).equals(3),
                    "Upgrade reset live root state");
            require(ItemStack.matches(root, before), "Upgrade mutated input provenance");
            int rootRuby = level(result, ruby);
            int expected = Integer.getInteger("leggings.test.rubyFireExpected", -1);
            require(expected < 0 || rootRuby == expected, "Upgrade did not grant expected root Ruby Fire level");
            retaliation(server, result.copy(), slot, rootRuby > 0, "upgraded mixed " + orientation);
            OBSERVATIONS.append("Mixed upgrade ").append(orientation++)
                    .append(": source Ruby Fire=1, root Ruby Fire=").append(rootRuby)
                    .append(", live state preserved, removed Protection not resurrected, retaliation=")
                    .append(rootRuby > 0).append('\n');
        }
        retaliation(server, new ItemStack(iron), slot, false, "vanilla negative");
    }

    private static List<ItemStack> pair(String product, ItemStack first, ItemStack second) throws Exception {
        if (product.equals("leggings")) {
            var pair = MixedLeggingsFactory.createPair(first, second);
            return List.of(pair.primary(), pair.inverse());
        }
        return BootsCoexistence.pair(first, second);
    }

    private static List<ItemStack> sources(String product, ItemStack root) throws Exception {
        if (product.equals("leggings")) {
            var data = MixedLeggingsStructure.validData(root).orElseThrow();
            return List.of(data.left().source(), data.right().source());
        }
        var content = Class.forName("games.alejandrocoria.mismatchedboots.content.ModContent");
        var optional = (Optional<?>) content.getMethod("getMixedBootsData", ItemStack.class).invoke(null, root);
        var data = optional.orElseThrow();
        var left = data.getClass().getMethod("left").invoke(data);
        var right = data.getClass().getMethod("right").invoke(data);
        return List.of((ItemStack) left.getClass().getMethod("source").invoke(left),
                (ItemStack) right.getClass().getMethod("source").invoke(right));
    }

    private static ItemStack nativeStack(MinecraftServer server, String name) throws Exception {
        var id = Identifier.fromNamespaceAndPath("betternether", name);
        var item = BuiltInRegistries.ITEM.getValue(id);
        require(BuiltInRegistries.ITEM.getKey(item).equals(id), "Missing native fixture: " + id);
        var stack = item.getDefaultInstance();
        // Invoke the real native setup with live registries, not a hand-authored enchantment substitute.
        item.getClass().getMethod("setupItemStack", ItemStack.class, HolderLookup.Provider.class)
                .invoke(item, stack, server.registryAccess());
        return stack;
    }

    private static ItemStack upgrade(MinecraftServer server, ItemStack input) {
        var player = RepairFixtures.player(server);
        var menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
        menu.getSlot(0).set(new ItemStack(BuiltInRegistries.ITEM.getValue(
                Identifier.parse("betternether:flaming_ruby_upgrade_smithing_template")), 2));
        menu.getSlot(1).set(input.copy());
        menu.getSlot(2).set(new ItemStack(BuiltInRegistries.ITEM.getValue(
                Identifier.parse("betternether:gloomsculk_crystal")), 2));
        menu.createResult();
        var preview = menu.getSlot(3).getItem().copy();
        require(!preview.isEmpty(), "Native recipe produced no smithing preview for " + input);
        var taken = menu.getSlot(3).safeTake(1, 1, player);
        require(!taken.isEmpty() && ItemStack.matches(preview, taken) && !menu.getSlot(1).hasItem()
                        && menu.getSlot(0).getItem().getCount() == 1 && menu.getSlot(2).getItem().getCount() == 1
                        && menu.getSlot(3).safeTake(1, 1, player).isEmpty(),
                "Actual result pickup/payment differs from the preview");
        return taken;
    }

    private static void anvil(MinecraftServer server, ItemStack enchanted, Holder<Enchantment> ruby) {
        var player = RepairFixtures.player(server);
        var root = enchanted.copy();
        root.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        var stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(ruby, 1);
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        var menu = new AnvilMenu(2, player.getInventory(), ContainerLevelAccess.NULL);
        menu.getSlot(0).set(root);
        menu.getSlot(1).set(book);
        menu.createResult();
        var preview = menu.getSlot(2).getItem().copy();
        require(!preview.isEmpty() && level(preview, ruby) == 1, "Source-aware anvil rejected Ruby Fire book");
        var taken = menu.getSlot(2).safeTake(1, 1, player);
        require(ItemStack.matches(preview, taken) && !menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem(),
                "Anvil book result was not payable");
    }

    private static void retaliation(MinecraftServer server, ItemStack stack, EquipmentSlot slot, boolean expected, String label) {
        ServerPlayer wearer = RepairFixtures.player(server);
        wearer.setItemSlot(slot, stack);
        var attacker = new Zombie(server.overworld());
        float health = attacker.getHealth();
        int damage = stack.getDamageValue();
        for (int attempt = 0; attempt < 64 && (!attacker.isOnFire() || stack.getDamageValue() == damage); attempt++) {
            EnchantmentHelper.doPostAttackEffects(server.overworld(), wearer, server.overworld().damageSources().mobAttack(attacker));
        }
        require(attacker.isOnFire() == expected, label + ": wrong native retaliation answer");
        require(expected ? attacker.getHealth() < health && stack.getDamageValue() > damage
                        : attacker.getHealth() == health && stack.getDamageValue() == damage,
                label + ": damage/durability effect differs from ignition");
        attacker.discard();
    }

    private static int level(ItemStack stack, Holder<Enchantment> enchantment) {
        return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(enchantment);
    }
}
