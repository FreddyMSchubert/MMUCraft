package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.EquippedTickCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.TickCallbackCharm;
import uk.co.httpsmmuminecraftsociety.mainmod.utils.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CaveSpiderPajamasCharm implements Charm, EquippedTickCallbackCharm, TickCallbackCharm
{
    private static final double CEILING_PROBE = 0.5D;

    private static final float VANILLA_DEFAULT_PLAYER_GRAVITY = 0.08f;

    private static final Identifier ANTI_GRAV_ATTRIBUTE_ID = Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "cave_spider_pajamas_anti_grav_attribute");

    private static final Map<UUID, Integer> PLAYER_USE_TICKS = new HashMap<>();

    private static final double CEILING_PROBE_HEIGHT = 0.5D; // if within slab of a ceiling, apply effect
    private static final double CEILING_EDGE_INSET = 0.08D;

    private static boolean playerTouchingCeiling(ServerPlayer player)
    {
        return !player.level().noBlockCollision(player, player.getBoundingBox()
                .deflate(CEILING_EDGE_INSET, 0.0D, CEILING_EDGE_INSET)
                .setMinY(player.getBoundingBox().maxY)
                .setMaxY(player.getBoundingBox().maxY + CEILING_PROBE_HEIGHT));
    }

    private static int getMaxHoldTicksPerLevel(int level)
    {
        if (level <= 0) return 0;
        if (level >= 7) return Integer.MAX_VALUE;
        return (int) Math.pow(2, level) * 20; // 1 -> 2, 2 -> 4, 3 -> 8, 4 -> 16, 5 -> 32, 6 -> 64
    }

    @Override
    public void equippedTick(ItemStack stack, ServerPlayer player, ServerLevel level, int charmLevel)
    {
        if (!playerTouchingCeiling(player))
        {
            PLAYER_USE_TICKS.remove(player.getUUID());
            return;
        }
        if (!player.getLastClientInput().jump())
        {
            PLAYER_USE_TICKS.remove(player.getUUID());
            return;
        }

        int useTicks = PLAYER_USE_TICKS.getOrDefault(player.getUUID(), 0);
        if (useTicks >= getMaxHoldTicksPerLevel(charmLevel))
        {
            PLAYER_USE_TICKS.remove(player.getUUID());
            return;
        }
        PLAYER_USE_TICKS.put(player.getUUID(), useTicks + 1);

        player.addEffect(new MobEffectInstance(
                MobEffects.LEVITATION,
                1,
                1,
                false,
                false,
                false
        ));

        Utils.applyPlayerModifier(
                player,
                Attributes.GRAVITY,
                ANTI_GRAV_ATTRIBUTE_ID,
                -VANILLA_DEFAULT_PLAYER_GRAVITY,
                AttributeModifier.Operation.ADD_VALUE
        );
    }

    @Override
    public void onTick(ServerPlayer player, ServerLevel level)
    {
        Utils.removePlayerModifier(player, Attributes.GRAVITY, ANTI_GRAV_ATTRIBUTE_ID);
    }
}
