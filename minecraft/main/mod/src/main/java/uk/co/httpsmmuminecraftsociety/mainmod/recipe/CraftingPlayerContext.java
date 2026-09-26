package uk.co.httpsmmuminecraftsociety.mainmod.recipe;

import net.minecraft.world.entity.player.Player;
import uk.co.httpsmmuminecraftsociety.mainmod.claims.ClaimsManager;

import java.util.ArrayDeque;

public final class CraftingPlayerContext {
    private static final ThreadLocal<ArrayDeque<Player>> PLAYERS = ThreadLocal.withInitial(ArrayDeque::new);

    private CraftingPlayerContext() {}

    public static void begin(Player player) {
        PLAYERS.get().push(player);
    }

    public static void end() {
        ArrayDeque<Player> players = PLAYERS.get();
        if (!players.isEmpty()) players.pop();
        if (players.isEmpty()) PLAYERS.remove();
    }

    public static boolean isCommitteeCrafting() {
        Player player = PLAYERS.get().peek();
        return player != null && ClaimsManager.isCommittee(player.getUUID());
    }
}
