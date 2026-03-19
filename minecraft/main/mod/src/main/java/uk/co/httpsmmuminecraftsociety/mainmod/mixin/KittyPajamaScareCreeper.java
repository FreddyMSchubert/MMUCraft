package uk.co.httpsmmuminecraftsociety.mainmod.mixin;

import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.CharmsManager;
import uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.equippable.KittyPajamasCharm;

import java.util.List;
import java.util.Objects;

@Mixin(Creeper.class)
public abstract class KittyPajamaScareCreeper extends Monster
{
    protected KittyPajamaScareCreeper(EntityType<? extends @NotNull KittyPajamaScareCreeper> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(at = @At("TAIL"), method = "registerGoals")
    private void addGoals(CallbackInfo info) {
        Goal goal = new AvoidEntityGoal<>(this, Player.class, this::shouldFleeFromPlayer, 6.0F, 1.0D, 1.2D, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test);
        this.goalSelector.addGoal(3, goal);
    }

    @Unique
    private boolean shouldFleeFromPlayer(LivingEntity livingEntity)
    {
        if (!(livingEntity instanceof Player player)) return true;
        ItemStack stack = player.getItemBySlot(EquipmentSlot.LEGS);
        List<CharmFakeItem> items = CharmsManager.getAbilitiesFromItemStack(stack);
        for (CharmFakeItem item : items) {
            if (Objects.equals(item.getCharm().id(), new KittyPajamasCharm().id())) return true;
        }
        return false;
    }
}