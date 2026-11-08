package games.alejandrocoria.mismatchedleggings.test.undergarden;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;
import quek.undergarden.block.entity.InfuserBlockEntity;
import quek.undergarden.component.RogdoriumInfusion;
import quek.undergarden.component.UndergardenData;
import quek.undergarden.inventory.InfuserMenu;
import quek.undergarden.recipe.InfuserInput;
import quek.undergarden.recipe.InfusingRecipe;
import quek.undergarden.recipe.ItemInfusingRecipe;
import quek.undergarden.registry.UGAttachments;
import quek.undergarden.registry.UGDataComponents;
import quek.undergarden.registry.UGDataMaps;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Deliberately test-local: candidate composition does not alter either packaged factory. */
public final class InfusionSpike {
    public static final boolean CANDIDATE = Boolean.getBoolean("undergarden.spike.candidate");
    public static final List<String> PRODUCTS = switch (System.getProperty("undergarden.spike.graph", "both")) {
        case "boots" -> List.of("boots");
        case "leggings" -> List.of("leggings");
        default -> List.of("boots", "leggings");
    };
    private static InfusingRecipe armorRecipe;
    private static float unprotectedIncrement;
    private static boolean finished;
    private static final StringBuilder REPORT = new StringBuilder();
    public static final BlockPos CHEST = new BlockPos(0, 4, 0);

    private InfusionSpike() {}

    public static void register(IEventBus bus) {
        require(ModList.get().isLoaded("mismatchedboots") == PRODUCTS.contains("boots"), "Boots graph");
        require(ModList.get().isLoaded("mismatchedleggings") == PRODUCTS.contains("leggings"), "Leggings graph");
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
        if (FMLEnvironment.getDist().isClient()) {
            bus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> InfusionClient.register());
        }
    }

    public static ItemStack item(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(name)));
    }

    public static Path report(String name) {
        return Path.of(System.getProperty("leggings.test.report"), name);
    }

    public static int amount(ItemStack stack) {
        return stack.getOrDefault(UGDataComponents.ROGDORIUM_INFUSION, RogdoriumInfusion.DEFAULT).infusionAmount();
    }

    public static void charge(ItemStack stack, int amount) {
        stack.set(UGDataComponents.ROGDORIUM_INFUSION, new RogdoriumInfusion(amount, 3600));
    }

    public static List<ItemStack> pair(String product, ItemStack left, ItemStack right) throws Exception {
        String owner = product.equals("boots") ? "mismatchedboots.composition.MixedBootsFactory" : "mismatchedleggings.composition.MixedLeggingsFactory";
        Object pair = Class.forName("games.alejandrocoria." + owner).getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, left, right);
        var roots = List.of((ItemStack) pair.getClass().getMethod("primary").invoke(pair), (ItemStack) pair.getClass().getMethod("inverse").invoke(pair));
        if (CANDIDATE && (left.has(UGDataComponents.ROGDORIUM_INFUSION) || right.has(UGDataComponents.ROGDORIUM_INFUSION))) {
            int reserve = (int) (((long) amount(left) + amount(right)) / 2);
            int capacity = Math.max(left.getOrDefault(UGDataComponents.ROGDORIUM_INFUSION, RogdoriumInfusion.DEFAULT).infusionMax(),
                    right.getOrDefault(UGDataComponents.ROGDORIUM_INFUSION, RogdoriumInfusion.DEFAULT).infusionMax());
            roots.forEach(root -> root.set(UGDataComponents.ROGDORIUM_INFUSION, new RogdoriumInfusion(reserve, capacity)));
        }
        return roots;
    }

    public static boolean valid(ItemStack stack) {
        try {
            String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
            if (namespace.equals("mismatchedleggings") && PRODUCTS.contains("leggings")) {
                return ((java.util.Optional<?>) Class.forName("games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure")
                        .getMethod("validData", ItemStack.class).invoke(null, stack)).isPresent();
            }
            if (namespace.equals("mismatchedboots") && PRODUCTS.contains("boots")) {
                return (boolean) Class.forName("games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources")
                        .getMethod("anySource", ItemStack.class, java.util.function.Predicate.class)
                        .invoke(null, stack, (java.util.function.Predicate<ItemStack>) source -> true);
            }
            return false;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    public static boolean admit(InfusingRecipe recipe, ItemStack stack) {
        // Spike identifies the real loaded armor recipe. Production needs a reload-safe, narrowly checked operand.
        return recipe == armorRecipe && recipe instanceof ItemInfusingRecipe && valid(stack);
    }

    public static boolean admitMenu(ItemStack stack) {
        return armorRecipe != null && valid(stack);
    }

    private static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer() || server.getTickCount() < 20) { return; }
        finished = true;
        try {
            armorRecipe = (InfusingRecipe) server.getRecipeManager().getRecipes().stream()
                    .filter(holder -> holder.id().identifier().equals(Identifier.parse("undergarden:armor_infusing")))
                    .findFirst().orElseThrow().value();
            var level = server.overworld();
            var biome = level.registryAccess().lookupOrThrow(Registries.BIOME)
                    .getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse("undergarden:depths")));
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    level.getChunk(x, z).fillBiomesFromNoise((qx, qy, qz, sampler) -> biome,
                            level.getChunkSource().randomState().sampler());
                }
            }
            unprotectedIncrement = biome.getData(UGDataMaps.BIOME_LETHALITY).lethality() / .18F;
            require(unprotectedIncrement > 0, "Native lethal biome control");
            var saved = new ArrayList<ItemStack>();
            REPORT.append("candidate=").append(CANDIDATE).append(" products=").append(PRODUCTS).append('\n');
            REPORT.append("Native ARMOR inventory queries: ");
            for (var slot : net.minecraft.world.entity.EquipmentSlotGroup.ARMOR) {
                REPORT.append(slot).append('=').append(slot.getIndex(36)).append(' ');
            }
            REPORT.append('\n');
            for (String product : PRODUCTS) {
                verifyProduct(level, product, saved);
            }
            if (PRODUCTS.size() == 2) {
                var wearer = player(level);
                var feet = saved.getFirst().copy();
                var legs = saved.get(2).copy();
                charge(feet, 5);
                charge(legs, 5);
                wearer.setItemSlot(EquipmentSlot.FEET, feet);
                wearer.setItemSlot(EquipmentSlot.LEGS, legs);
                float infection = infection(wearer);
                int feetSpent = 5 - amount(feet);
                int legsSpent = 5 - amount(legs);
                require(feetSpent > 0 && legsSpent > 0 && Math.abs(infection - unprotectedIncrement / (1 + feetSpent + legsSpent)) < .0001,
                        "Native sibling independent spending");
                REPORT.append("Both roots: native spent FEET=").append(feetSpent).append(" LEGS=").append(legsSpent)
                        .append("; increment=").append(infection).append('\n');
            }
            level.setBlockAndUpdate(CHEST, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            var chest = (ChestBlockEntity) level.getBlockEntity(CHEST);
            for (int index = 0; index < saved.size(); index++) { chest.setItem(index, saved.get(index)); }
            server.saveEverything(true, true, true);
            Files.writeString(report("server.txt"), REPORT);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    private static void verifyProduct(ServerLevel level, String product, List<ItemStack> saved) throws Exception {
        var source = item("undergarden:froststeel_" + product);
        var other = item("minecraft:diamond_" + product);
        charge(source, 1201);
        var roots = pair(product, source, other);
        require(amount(roots.getFirst()) == (CANDIDATE ? 600 : 1201)
                && amount(roots.getLast()) == (CANDIDATE ? 600 : 0), "Creation distribution " + product);
        var duplicate = pair(product, source, source);
        require(duplicate.stream().allMatch(root -> amount(root) == 1201), "Two equal sources " + product);
        var differing = other.copy();
        charge(differing, 300);
        var unequal = pair(product, source, differing);
        require(amount(unequal.getFirst()) == (CANDIDATE ? 750 : 1201)
                && amount(unequal.getLast()) == (CANDIDATE ? 750 : 300), "Two unequal sources " + product);

        var nativeInput = item("minecraft:iron_" + product);
        var loaded = machine(level, nativeInput, 2);
        require(amount(loaded.output) == 1200 && loaded.fuelRemaining == 0 && loaded.inputRemaining == 0, "Paid vanilla native infusion");
        var infusedVanilla = pair(product, loaded.output, other);
        require(amount(infusedVanilla.getFirst()) == (CANDIDATE ? 600 : 1200)
                && amount(infusedVanilla.getLast()) == (CANDIDATE ? 600 : 0), "Infused vanilla armor composition");
        charge(nativeInput, 3500);
        var topup = machine(level, nativeInput, 6);
        require(amount(topup.output) == 3600 && topup.fuelRemaining == 0, "Native partial top-up payment");
        var root = roots.getFirst();
        var unchanged = root.copy();
        var mixedLoad = machine(level, root, 2);
        require(CANDIDATE ? amount(mixedLoad.output) == 1800 && mixedLoad.fuelRemaining == 0
                        : mixedLoad.output.isEmpty() && mixedLoad.fuelRemaining == 2 && mixedLoad.inputRemaining == 1,
                "Mixed machine admission/payment " + product);
        if (CANDIDATE) {
            require(valid(mixedLoad.output) && history(root, product).equals(history(mixedLoad.output, product)),
                    "Native machine failed to preserve mixed structure/history");
        }
        require(ItemStack.matches(root, unchanged), "Machine mutated input fixture");
        var bare = item("mismatched" + product + ":mixed_" + product);
        require(machine(level, bare, 1).output.isEmpty(), "Bare item admitted");
        require(!armorRecipe.matches(new InfuserInput(root, InfusingRecipe.SlotType.UTHERIUM), level), "Wrong fuel accepted");
        menu(level, new ItemStack(product.equals("boots") ? Items.IRON_BOOTS : Items.IRON_LEGGINGS), true);
        menu(level, root, CANDIDATE);
        menu(level, bare, false);

        var slot = product.equals("boots") ? EquipmentSlot.FEET : EquipmentSlot.LEGS;
        var wearer = player(level);
        var nativeProtection = loaded.output.copy();
        wearer.setItemSlot(slot, nativeProtection);
        float nativeIncrement = infection(wearer);
        int nativeSpent = 1200 - amount(nativeProtection);
        require(nativeSpent > 0 && Math.abs(nativeIncrement - unprotectedIncrement / (1 + nativeSpent)) < .0001,
                "Native infection control: increment=" + nativeIncrement + " reserve=" + amount(nativeProtection)
                        + " biome=" + level.getBiome(wearer.blockPosition()));
        var chargedRoot = root.copy();
        charge(chargedRoot, 1200);
        wearer.setItemSlot(slot, chargedRoot);
        require(Math.abs(infection(wearer) - nativeIncrement) < .0001 && amount(chargedRoot) == amount(nativeProtection),
                "Native/mixed positive reserve diverged");
        var spent = root.copy();
        var history = history(spent, product);
        charge(spent, 1);
        wearer.setItemSlot(slot, spent);
        require(Math.abs(infection(wearer) - unprotectedIncrement / 2) < .0001 && amount(spent) == 0, "Mixed last unit protection");
        require(Math.abs(infection(wearer) - unprotectedIncrement) < .0001 && amount(spent) == 0, "Mixed exhausted protection");
        require(history.equals(history(spent, product)), "Native consumption mutated immutable source history");
        charge(spent, 10);
        wearer.setItemSlot(slot, ItemStack.EMPTY);
        require(Math.abs(infection(wearer) - unprotectedIncrement) < .0001 && amount(spent) == 10, "Unequipped reservoir spent");
        wearer.setItemSlot(slot, spent);
        wearer.tickCount = 21;
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(wearer));
        require(amount(spent) == 10, "Consumed outside native 20-tick update");
        wearer.setData(UGAttachments.UNDERGARDEN_DATA, new UndergardenData(20, 0, true));
        wearer.tickCount = 20;
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(wearer));
        require(amount(spent) == 10, "Unexpected cure/spending at harmful threshold");
        roundtrip(level, spent);
        saved.add(spent.copy());
        saved.add(roots.getLast().copy());
        REPORT.append(product).append(": baseline/candidate creation; paid Infuser; partial topup=100 units costs six fuels; native infection increments=")
                .append(nativeIncrement).append(" protected vs ").append(unprotectedIncrement)
                .append(" bare; native spent per update=").append(nativeSpent)
                .append("; native menu quick-move, expiry/removal/timing/history and codec/stream passed\n");
        if (product.equals("leggings")) { ordinary(level); }
    }

    public static Object history(ItemStack root, String product) throws Exception {
        String content = product.equals("boots") ? "mismatchedboots.content.ModContent" : "mismatchedleggings.content.LeggingsContent";
        Object type = Class.forName("games.alejandrocoria." + content).getMethod(product.equals("boots") ? "mixedBootsDataType" : "mixedLeggingsDataType").invoke(null);
        if (type instanceof java.util.Optional<?> optional) { type = optional.orElseThrow(); }
        return root.copy().get((net.minecraft.core.component.DataComponentType<?>) type);
    }

    private static void roundtrip(ServerLevel level, ItemStack root) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        var encoded = ItemStack.CODEC.encodeStart(ops, root).getOrThrow();
        require(ItemStack.matches(root, ItemStack.CODEC.parse(ops, encoded).getOrThrow()), "Persistent stack codec");
        var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess(), ConnectionType.NEOFORGE);
        try {
            ItemStack.STREAM_CODEC.encode(bytes, root);
            require(ItemStack.matches(root, ItemStack.STREAM_CODEC.decode(bytes)), "Stream codec");
        } finally { bytes.release(); }
    }

    private static FakePlayer player(ServerLevel level) {
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "infusion_probe"));
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        player.setPos(.5, 4, .5);
        return player;
    }

    private static float infection(FakePlayer player) {
        player.setData(UGAttachments.UNDERGARDEN_DATA, new UndergardenData(0, 0, true));
        player.tickCount = 20;
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(player));
        return player.getData(UGAttachments.UNDERGARDEN_DATA).uthericInfection();
    }

    private static MachineResult machine(ServerLevel level, ItemStack input, int fuel) {
        var position = new BlockPos(2, 4, 0);
        var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse("undergarden:infuser"));
        level.removeBlock(position, false);
        level.setBlockAndUpdate(position, block.defaultBlockState());
        var machine = (InfuserBlockEntity) level.getBlockEntity(position);
        machine.setItem(2, item("undergarden:rogdorium").copyWithCount(fuel));
        machine.setItem(0, input.copy());
        for (int tick = 0; tick < 500 && machine.getItem(3).isEmpty(); tick++) {
            InfuserBlockEntity.serverTick(level, position, level.getBlockState(position), machine);
        }
        return new MachineResult(machine.getItem(3).copy(), machine.getItem(2).getCount(), machine.getItem(0).getCount());
    }

    private static void menu(ServerLevel level, ItemStack input, boolean accepted) {
        var position = new BlockPos(3, 4, 0);
        var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse("undergarden:infuser"));
        level.removeBlock(position, false);
        level.setBlockAndUpdate(position, block.defaultBlockState());
        var machine = (InfuserBlockEntity) level.getBlockEntity(position);
        var player = player(level);
        var menu = new InfuserMenu(0, player.getInventory(), machine, new net.minecraft.world.inventory.SimpleContainerData(2));
        require(menu.getSlot(0).mayPlace(input), "Native input slot unexpectedly rejects manual placement");
        player.getInventory().setItem(9, input.copy());
        menu.quickMoveStack(player, 4);
        require(accepted ? ItemStack.matches(machine.getItem(0), input) : machine.getItem(0).isEmpty(),
                "Native quick-move admission: " + input);
        if (accepted) {
            machine.setItem(2, item("undergarden:rogdorium"));
            for (int tick = 0; tick < 500 && machine.getItem(3).isEmpty(); tick++) {
                InfuserBlockEntity.serverTick(level, position, level.getBlockState(position), machine);
            }
            require(amount(machine.getItem(3)) == Math.min(3600, amount(input) + 600) && machine.getItem(2).isEmpty(),
                    "Paid native output after menu transfer");
            menu.quickMoveStack(player, 3);
            require(machine.getItem(3).isEmpty(), "Native output extraction failed");
        }
    }

    private static void ordinary(ServerLevel level) throws Exception {
        var player = player(level);
        for (String family : List.of("cloggrum", "froststeel", "utherium", "ancient")) {
            var source = item("undergarden:" + family + "_leggings");
            var other = new ItemStack(Items.DIAMOND_LEGGINGS);
            double nativeArmor = armor(player, source);
            double otherArmor = armor(player, other);
            for (var root : pair("leggings", source, other)) {
                require(Math.abs(armor(player, root) - (nativeArmor + otherArmor) / 2) < .0001, "Ordinary armor " + family);
                double base = player.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
                double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
                require(Math.abs(speed / base - (family.equals("froststeel") ? .975 : 1)) < .0001, "Froststeel equipped average " + family);
            }
        }
        REPORT.append("Four ordinary leggings: native/equipped armor and Froststeel averaged movement penalty passed\n");
    }

    private static double armor(FakePlayer player, ItemStack stack) {
        player.setItemSlot(EquipmentSlot.LEGS, stack);
        // Attribute assignment is driven by the real equipment update, not a detached modifier sum.
        ((games.alejandrocoria.mismatchedleggings.test.mixin.EquipmentUpdatesInvoker) player).leggings$detectEquipmentUpdates();
        return player.getAttributeValue(Attributes.ARMOR);
    }

    private record MachineResult(ItemStack output, int fuelRemaining, int inputRemaining) {}
}
