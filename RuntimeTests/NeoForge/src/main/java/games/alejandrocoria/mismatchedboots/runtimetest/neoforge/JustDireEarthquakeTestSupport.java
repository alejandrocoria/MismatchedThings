package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireEarthquakeCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/** Native controls/construction only; mixed action and ticking always call production. */
final class JustDireEarthquakeTestSupport {
    private static Method action, name, ticks, active, ownTick;
    private static Constructor<?> timer, binding;

    private JustDireEarthquakeTestSupport() {}

    static void bind() throws ReflectiveOperationException {
        var ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
        var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
        var record = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown");
        action = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.AbilityMethods")
                .getMethod("earthquake", Level.class, net.minecraft.world.entity.player.Player.class, ItemStack.class);
        timer = record.getConstructor(String.class, int.class, boolean.class);
        name = record.getMethod("abilityName"); ticks = record.getMethod("cooldownTicks"); active = record.getMethod("isactive");
        binding = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                .getConstructor(String.class, int.class, boolean.class, boolean.class);
        ownTick = JustDireEarthquakeCompatibility.class.getDeclaredMethod("tickEquipped", ServerPlayer.class); ownTick.setAccessible(true);
        var params = tool.getMethod("getAbilityParams", ability).invoke(original(0).getItem(), ability.getField("EARTHQUAKE").get(null));
        require(params.getClass().getField("activeCooldown").getInt(params) == 200
                && params.getClass().getField("cooldown").getInt(params) == 800, "Native clock parameters changed");
    }

    static ItemStack original(int amount) {
        var root = NeoForgeJustDireResourcesTest.boots("eclipsealloy", amount);
        JustDireAttributeTestSupport.set(root, "earthquake_upgrade_installed", true); return root;
    }

    static ItemStack mixed(int amount, boolean inverse, boolean two) throws ReflectiveOperationException {
        var pair = MixedBootsFactory.createPair(original(0), two ? original(0) : new ItemStack(Items.DIAMOND_BOOTS));
        var root = inverse ? pair.inverse() : pair.primary();
        try (var transaction = Transaction.openRoot()) {
            require(JustDireResourceTestSupport.handler(root).insert(amount, transaction) == amount, "Fixture recharge failed"); transaction.commit();
        }
        configure(root, 72); return root;
    }

    static void configure(ItemStack root, int key) throws ReflectiveOperationException {
        JustDireAttributeTestSupport.set(root, "earthquake_bindingmode", 2);
        JustDireAttributeTestSupport.set(root, "ability_bindings", List.of(binding("earthquake", key)));
    }

    static Object binding(String ability, int key) throws ReflectiveOperationException { return binding.newInstance(ability, key, false, true); }
    static Object timer(String ability, int count, boolean isActive) throws ReflectiveOperationException { return timer.newInstance(ability, count, isActive); }
    static Clock clock(ItemStack root, String ability) throws ReflectiveOperationException {
        for (Object record : root.getOrDefault(timers(), List.of())) {
            if (name.invoke(record).equals(ability)) { return new Clock((int) ticks.invoke(record), (boolean) active.invoke(record)); }
        }
        return null;
    }
    static void nativeUse(ServerPlayer player, ItemStack root) throws ReflectiveOperationException {
        require(!(boolean) action.invoke(null, player.level(), player, root), "Native false return changed");
    }
    static void tick(ServerPlayer player) throws ReflectiveOperationException { ownTick.invoke(null, player); }
    static int charge(ItemStack root) { return root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0); }
    static DataComponentType<List<Object>> timers() { return JustDireAttributeTestSupport.component("ability_cooldowns"); }
    private static void require(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
    record Clock(int ticks, boolean active) {}
}
