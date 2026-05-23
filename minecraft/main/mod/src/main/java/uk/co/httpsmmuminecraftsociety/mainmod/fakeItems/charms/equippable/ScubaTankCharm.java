package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;

public class ScubaTankCharm implements Charm
{
    public static int adjustAirSupplyAfterVanillaDecrease(ServerPlayer player, int currentAir, int vanillaAir)
    {
        if (vanillaAir >= currentAir) return vanillaAir;
        if (!player.isUnderWater()) return vanillaAir;

        boolean movementInputHeld = isMovementInputHeld(player.getLastClientInput());
        if (movementInputHeld) return vanillaAir;

        int charmLevel = Math.min(Math.max(CharmsManager.getPlayerCharmLevel(player, ScubaTankCharm.class), 0), 5);
        if (charmLevel <= 0) return vanillaAir;
        if (charmLevel >= 5) return currentAir;

        float saveChance = charmLevel * 0.2F;
        return player.getRandom().nextFloat() < saveChance ? currentAir : vanillaAir;
    }

    private static boolean isMovementInputHeld(Input input)
    {
        return input.forward()
                || input.backward()
                || input.left()
                || input.right()
                || input.jump()
                || input.shift()
                || input.sprint();
    }
}
