package games.alejandrocoria.mismatchedboots.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.UUID;

final class GameTestPlayers {
    private GameTestPlayers() {
    }

    static ServerPlayer makeMockServerPlayer(GameTestHelper helper, GameType gameType) {
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-mock-player"),
                ClientInformation.createDefault()) {
            @Override
            public GameType gameMode() {
                return gameType;
            }
        };
        gameType.updatePlayerAbilities(player.getAbilities());
        return player;
    }
}
