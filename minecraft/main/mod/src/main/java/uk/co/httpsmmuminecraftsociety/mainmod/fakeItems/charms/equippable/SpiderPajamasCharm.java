package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.EquippedTickCallbackCharm;

public class SpiderPajamasCharm implements Charm, EquippedTickCallbackCharm
{
    @Override
    public String id()
    {
        return "cosmetic-charm-spider-pajamas";
    }

    @Override
    public ItemStack equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level) {
        if (!player.horizontalCollision) return stack;

        Vec3 v = player.getDeltaMovement();

        double climbAccel = 0.08D;
        double maxClimbY = 0.22D;

        double newX = v.x * 0.96D;
        double newY = Math.min(maxClimbY, Math.max(v.y, 0.0D) + climbAccel);
        double newZ = v.z * 0.96D;

        player.setDeltaMovement(newX, newY, newZ);
        player.resetFallDistance();

        // sync with client
        player.setDeltaMovement(newX, newY, newZ);
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));

        player.fallDistance = 0.0F;
        return stack;
    }
}
