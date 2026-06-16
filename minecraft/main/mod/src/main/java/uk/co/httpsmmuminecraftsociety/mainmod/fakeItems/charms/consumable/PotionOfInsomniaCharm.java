package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.ConsumableCallbacksCharm;

import java.util.ArrayList;
import java.util.List;

public class PotionOfInsomniaCharm implements Charm, ConsumableCallbacksCharm
{
    @Override
    public void onConsumeTick(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks, int charmLevel) {}

    List<String> names = List.of(
            "Diego",
            "Esmeralda",
            "Bartholomew",
            "Freddy",
            "oderzo",
            "Mia",
            "Hannah",
            "Cahlum",
            "Iman",
            "Adam",
            "Samuel",
            "Josh",
            "Ellie",
            "Alex",
            "Spencer",
            "Tosb",
            "Your mother the sleep demon",
            "Dobbo",
            "Necro",
            "Abigail",
            "Josh Wardle",
            "Ultimate",
            "KittyScan",
            "Kaif",
            "SirElixir",
            "raz"
    );

    @Override
    public void onConsumeFinished(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks, int charmLevel)
    {
        // 4 normal phantoms
        for (int i = 0; i < 4; i++)
        {
            Phantom phantom = new Phantom(EntityTypes.PHANTOM, level);

            double offsetX = (level.getRandom().nextDouble() - 0.5D) * 3.0D;
            double offsetZ = (level.getRandom().nextDouble() - 0.5D) * 3.0D;

            phantom.setPos(
                    player.getX() + offsetX,
                    player.getY() + 5,
                    player.getZ() + offsetZ
            );

            phantom.setTarget(player);
            phantom.setPersistenceRequired();

            phantom.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 1));

            // 1 in 5 chance to spawn invisible
            boolean isInvisible = Math.floor(Math.random() * 5) == 0;
            if (isInvisible) {
                phantom.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Integer.MAX_VALUE, 1));
            }

            level.addFreshEntity(phantom);
        }

        // Boss phantom
        Phantom boss = new Phantom(EntityTypes.PHANTOM, level);
        boss.setPos(
                player.getX(),
                player.getY() + 5,
                player.getZ()
        );

        boss.setTarget(player);
        boss.setPersistenceRequired();
        boss.setCustomName(Component.literal("Boss Phantom (" + names.get((int)Math.floor(Math.random() * names.size()))));
        boss.setCustomNameVisible(true);

        // Health
        var maxHealth = boss.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null)
        {
            maxHealth.setBaseValue(70.0D);
        }
        boss.setHealth(boss.getMaxHealth());

        // Damage
        var attackDamage = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null)
        {
            attackDamage.setBaseValue(12.0D);
        }

        // Speed
        var moveSpeed = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null)
        {
            moveSpeed.setBaseValue(0.5D);
        }

        var flyingSpeed = boss.getAttribute(Attributes.FLYING_SPEED);
        if (flyingSpeed != null)
        {
            flyingSpeed.setBaseValue(0.8D);
        }

        var followRange = boss.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null)
        {
            followRange.setBaseValue(64.0D);
        }

        // Bigger model/hitbox
        var scale = boss.getAttribute(Attributes.SCALE);
        if (scale != null)
        {
            scale.setBaseValue(2.5D);
        }

        boss.addEffect(new MobEffectInstance(MobEffects.STRENGTH, Integer.MAX_VALUE, 1));
        boss.addEffect(new MobEffectInstance(MobEffects.SPEED, Integer.MAX_VALUE, 1));
        boss.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 1));

        level.addFreshEntity(boss);
    }
}
