package uk.co.httpsmmuminecraftsociety.mainmod.mixin.dailies;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.KilledTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskManager;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTargetId;

@Mixin(KilledTrigger.class)
public abstract class DailyKilledMixin {
    @Inject(method = "trigger", at = @At("HEAD"))
    private void mainmod$recordKill(ServerPlayer player, Entity victim, DamageSource source, CallbackInfo ci) {
        if ((Object)this != CriteriaTriggers.PLAYER_KILLED_ENTITY) return;

        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
        DailyTaskManager.record(player, DailyTaskEvent.of(DailyTaskEvent.Type.KILL_ENTITY, entityId));

        if (source.getDirectEntity() != null && source.getDirectEntity().getType() == net.minecraft.world.entity.EntityTypes.FIREWORK_ROCKET) {
            DailyTaskManager.record(player, new DailyTaskEvent(
                    DailyTaskEvent.Type.KILL_ENTITY,
                    entityId,
                    DailyTargetId.of(Items.FIREWORK_ROCKET),
                    1
            ));
        }

        ItemStack weapon = source.getWeaponItem();
        if (weapon == null || weapon.isEmpty()) return;
        String itemId = BuiltInRegistries.ITEM.getKey(weapon.getItem()).toString();
        DailyTaskManager.record(player, new DailyTaskEvent(DailyTaskEvent.Type.KILL_ENTITY, entityId, itemId, 1));
        if (weapon.is(ItemTags.SPEARS)) recordTag(player, entityId, DailyTargetId.of(ItemTags.SPEARS));
        if (weapon.is(ItemTags.AXES)) recordTag(player, entityId, DailyTargetId.of(ItemTags.AXES));
        if (weapon.is(ItemTags.SWORDS)) recordTag(player, entityId, DailyTargetId.of(ItemTags.SWORDS));
        if (weapon.is(ItemTags.HOES)) recordTag(player, entityId, DailyTargetId.of(ItemTags.HOES));
        if (weapon.is(ItemTags.PICKAXES)) recordTag(player, entityId, DailyTargetId.of(ItemTags.PICKAXES));
    }

    private static void recordTag(ServerPlayer player, String entityId, String tag) {
        DailyTaskManager.record(player, new DailyTaskEvent(DailyTaskEvent.Type.KILL_ENTITY, entityId, tag, 1));
    }
}
