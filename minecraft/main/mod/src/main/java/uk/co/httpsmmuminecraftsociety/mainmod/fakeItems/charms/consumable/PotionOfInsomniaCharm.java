package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.consumable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.item.ItemStack;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.Charm;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.def.ConsumableCallbacksCharm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PotionOfInsomniaCharm implements Charm, ConsumableCallbacksCharm
{
    public static final String BOSS_TAG = "mainmod_insomnia_boss";
    public static final String SUMMONED_TAG = "mainmod_insomnia_summoned";
    private static final Map<UUID, BossFight> BOSS_FIGHTS = new HashMap<>();
    private static final double FIGHT_RADIUS = 32.0D;

    private record BossFight(Phantom phantom, ServerBossEvent bar) {}

    public static int minionCount(int players) {
        return 4 * Math.min(5, Math.max(1, players));
    }

    public static void onEntityLoad(Entity entity, ServerLevel level) {
        if (!(entity instanceof Phantom phantom) || !phantom.entityTags().contains(BOSS_TAG)) return;
        BossFight previous = BOSS_FIGHTS.get(phantom.getUUID());
        if (previous != null && previous.phantom() != phantom) {
            previous.bar().removeAllPlayers();
            BOSS_FIGHTS.remove(phantom.getUUID());
        }
        BOSS_FIGHTS.computeIfAbsent(phantom.getUUID(), ignored -> new BossFight(phantom,
                new ServerBossEvent(phantom.getUUID(), phantom.getDisplayName(),
                        BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS)));
    }

    public static void onEntityUnload(Entity entity, ServerLevel level) {
        BossFight fight = BOSS_FIGHTS.get(entity.getUUID());
        if (fight == null || fight.phantom() != entity) return;
        fight.bar().removeAllPlayers();
        BOSS_FIGHTS.remove(entity.getUUID());
    }

    public static void tickBossFights(MinecraftServer server) {
        BOSS_FIGHTS.values().removeIf(fight -> {
            Phantom boss = fight.phantom();
            ServerBossEvent bar = fight.bar();
            if (!boss.isAlive() || boss.isRemoved()) {
                bar.removeAllPlayers();
                return true;
            }

            ServerLevel level = (ServerLevel) boss.level();
            bar.setProgress(boss.getHealth() / boss.getMaxHealth());
            Set<ServerPlayer> nearbyPlayers = new HashSet<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                boolean nearby = player.level() == level && player.isAlive()
                        && !player.isSpectator() && player.distanceToSqr(boss) <= FIGHT_RADIUS * FIGHT_RADIUS;
                if (nearby) nearbyPlayers.add(player);

                if (nearby && !player.isCreative() && boss.tickCount % 160 == 0
                        && !level.canSeeSky(player.blockPosition().above())) {
                    player.hurtServer(level, level.damageSources().sonicBoom(boss), 4.0F);
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60));
                    player.sendOverlayMessage(Component.literal("The phantom's shriek pierces your cover!"));
                    level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1,
                            player.getZ(), 16, 0.5, 0.5, 0.5, 0.02);
                }
            }
            for (ServerPlayer viewer : List.copyOf(bar.getPlayers())) {
                if (!nearbyPlayers.contains(viewer)) bar.removePlayer(viewer);
            }
            for (ServerPlayer viewer : nearbyPlayers) {
                if (!bar.getPlayers().contains(viewer)) bar.addPlayer(viewer);
            }
            return false;
        });
    }

    public static void clearBossFights() {
        BOSS_FIGHTS.values().forEach(fight -> fight.bar().removeAllPlayers());
        BOSS_FIGHTS.clear();
    }

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
            "Adam",
            "Samuel",
            "Josh",
            "Ellie",
            "Alex",
            "Spencer",
            "Tosb",
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

    List<String> titles = List.of(
        "Sleep Demon",
        "Nightmare",
        "Sleepless",
        "Insomniac",
        "Dream Eater",
        "Dream Reaper",
        "Dreadwing",
        "Nightfall Stalker",
        "Moonlit Menace"
    );

    @Override
    public boolean onConsumeFinished(ItemStack stack, ServerPlayer player, ServerLevel level, int elapsedTicks, int charmLevel)
    {
        List<ServerPlayer> fighters = level.getEntitiesOfClass(ServerPlayer.class,
                player.getBoundingBox().inflate(FIGHT_RADIUS),
                candidate -> candidate.isAlive() && !candidate.isSpectator() && !candidate.isCreative()
                        && candidate.distanceToSqr(player) <= FIGHT_RADIUS * FIGHT_RADIUS);
        int count = minionCount(fighters.size());
        for (int i = 0; i < count; i++)
        {
            Phantom phantom = new Phantom(EntityTypes.PHANTOM, level);

            double offsetX = (level.getRandom().nextDouble() - 0.5D) * 3.0D;
            double offsetZ = (level.getRandom().nextDouble() - 0.5D) * 3.0D;

            phantom.setPos(
                    player.getX() + offsetX,
                    player.getY() + 5,
                    player.getZ() + offsetZ
            );

            phantom.setTarget(fighters.isEmpty() ? player : fighters.get(i % fighters.size()));
            phantom.setPersistenceRequired();
            phantom.addTag(SUMMONED_TAG);

            phantom.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 1));

            // 1 in 8 chance to spawn invisible
            boolean isInvisible = Math.floor(Math.random() * 8) == 0;
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
        boss.addTag(BOSS_TAG);
        boss.addTag(SUMMONED_TAG);
        boss.setCustomName(Component.literal(names.get((int)Math.floor(Math.random() * names.size())) + " the " + titles.get((int)Math.floor(Math.random() * titles.size())) + " (Boss Phantom)"));
        boss.setCustomNameVisible(true);

        // Health
        var maxHealth = boss.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null)
        {
            maxHealth.setBaseValue(100.0D);
        }
        boss.setHealth(boss.getMaxHealth());

        // Damage
        var attackDamage = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null)
        {
            attackDamage.setBaseValue(12.0D);
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

        boss.addEffect(new MobEffectInstance(MobEffects.STRENGTH, Integer.MAX_VALUE, 2));
        boss.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 1));

        if (level.addFreshEntity(boss)) onEntityLoad(boss, level);
        stack.consume(1, player);
        return true;
    }
}
