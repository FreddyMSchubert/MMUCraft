package uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.equippable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.FakeItems.charms.def.EquippedTickCallbackCharm;

public class WallClimbingCharm implements Charm, EquippedTickCallbackCharm
{
    @Override
    public String id()
    {
        return "cosmetic-charm-wall-climb";
    }

    @Override
    public ItemStack equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level) {
        if (!player.horizontalCollision) return stack;

        double climb = 0.2D;

        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x * 0.96D, 0.0D, v.z * 0.96D);

        // Mojmaps name may vary slightly by exact build/version.
        // Use the player teleport packet path here.
        player.connection.teleport(
                player.getX(),
                player.getY() + climb,
                player.getZ(),
                player.getYRot(),
                player.getXRot()
        );

        player.resetFallDistance();
        return stack;
    }
}
