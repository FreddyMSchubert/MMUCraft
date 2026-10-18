package uk.co.httpsmmuminecraftsociety.mainmod.utils;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityTypes;

public class TeleportCompanionCheck {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        assert TeleportPotionUtils.canAutoTeleportPet(EntityTypes.WOLF);
        assert TeleportPotionUtils.canAutoTeleportPet(EntityTypes.CAT);
        assert TeleportPotionUtils.canAutoTeleportPet(EntityTypes.PARROT);
        assert !TeleportPotionUtils.canAutoTeleportPet(EntityTypes.NAUTILUS);
        assert !TeleportPotionUtils.canAutoTeleportPet(EntityTypes.ZOMBIE_NAUTILUS);
    }
}
