package dev.freddy.killshift;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public final class DropRewards {
    public static final String CLAIMED_TAG = "killshift_drops_claimed";
    private static final Map<UUID, List<ItemStack>> PENDING = new HashMap<>();
    private static final Map<Mob, DeathForm> DEATH_FORMS = new WeakHashMap<>();
    private static final Deque<Capture> ACTIVE = new ArrayDeque<>();

    private DropRewards() {
    }

    public static void begin(LivingEntity dead, DamageSource source) {
        if (!(dead instanceof Mob mob) || !MobRegistry.supports(mob.getType())
                || !(source.getEntity() instanceof ServerPlayer killer)) return;
        boolean reward = !mob.entityTags().contains(CLAIMED_TAG);
        mob.addTag(CLAIMED_TAG);
        DEATH_FORMS.put(mob, new DeathForm(MobRegistry.createForm(mob), ShapeView.snapshot(mob)));
        List<ItemStack> equipment = new ArrayList<>();
        if (reward) {
            for (EquipmentSlot slot : EquipmentSlot.VALUES) {
                ItemStack equipped = mob.getItemBySlot(slot);
                if (!equipped.isEmpty()) equipment.add(equipped.copy());
            }
        }
        ACTIVE.push(new Capture(mob, killer, reward, equipment));
    }

    public static void end(LivingEntity dead) {
        if (!ACTIVE.isEmpty() && ACTIVE.peek().mob == dead) ACTIVE.pop();
    }

    public static DeathForm takeForm(Mob mob) {
        return DEATH_FORMS.remove(mob);
    }

    public static boolean capture(Entity source, ItemStack stack) {
        Capture current = ACTIVE.peek();
        if (current == null || current.mob != source) return false;
        if (current.reward && !stack.isEmpty()) {
            give(current.killer, stack.copy());
        }
        return true;
    }

    public static boolean transferEquipment(LivingEntity source) {
        Capture current = ACTIVE.peek();
        if (current == null || current.mob != source) return false;
        if (current.reward) {
            for (ItemStack equipped : current.equipment) give(current.killer, equipped);
        }
        return true;
    }

    public static void awardExperience(LivingEntity source, ServerLevel level, Vec3 position, int amount) {
        Capture current = ACTIVE.peek();
        if (current == null || current.mob != source) {
            ExperienceOrb.award(level, position, amount);
        } else if (current.reward && amount > 0) {
            current.killer.giveExperiencePoints(amount);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            PENDING.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>()).add(stack);
        }
    }

    public static void flush(ServerPlayer player) {
        List<ItemStack> queue = PENDING.get(player.getUUID());
        if (queue == null) return;
        queue.removeIf(stack -> {
            player.getInventory().add(stack);
            return stack.isEmpty();
        });
        if (queue.isEmpty()) PENDING.remove(player.getUUID());
    }

    public static void readSaved(ServerPlayer player, ValueInput input) {
        input.read("killshift_pending_drops", ItemStack.CODEC.listOf()).ifPresent(stacks -> {
            if (!stacks.isEmpty()) PENDING.put(player.getUUID(), new ArrayList<>(stacks));
        });
    }

    public static void writeSaved(ServerPlayer player, ValueOutput output) {
        List<ItemStack> queue = PENDING.get(player.getUUID());
        if (queue != null && !queue.isEmpty()) {
            output.store("killshift_pending_drops", ItemStack.CODEC.listOf(), queue);
        }
    }

    private record Capture(Mob mob, ServerPlayer killer, boolean reward, List<ItemStack> equipment) {
    }

    public record DeathForm(MobForm form, CompoundTag appearance) {
    }
}
