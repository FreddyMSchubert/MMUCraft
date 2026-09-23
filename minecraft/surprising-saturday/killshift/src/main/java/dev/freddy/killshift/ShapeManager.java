package dev.freddy.killshift;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

public final class ShapeManager {
    private static final Map<UUID, ShapeState> SHAPES = new HashMap<>();
    private static final Identifier HEALTH = id("health");
    private static final Identifier ATTACK = id("attack");
    private static final Identifier ARMOR = id("armor");
    private static final Identifier SCALE = id("scale");
    private static final Identifier SPEED = id("speed");
    private static final Identifier JUMP = id("jump");
    private static final Identifier WATER = id("water");
    private static final Identifier FALL = id("fall");

    private ShapeManager() {
    }

    static void onDeath(LivingEntity dead, DamageSource source) {
        if (dead instanceof ServerPlayer player) {
            clear(player);
        }

        Entity attacker = source.getEntity();
        if (!(attacker instanceof ServerPlayer killer)) {
            return;
        }
        if (dead instanceof ServerPlayer) {
            clear(killer);
        } else if (dead instanceof Mob mob) {
            EventApi.completed(killer, mob);
            transform(killer, mob);
        }
    }

    static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
        return !(target instanceof Mob mob) || ownerOf(mob) == null;
    }

    static InteractionResult onAttack(
            net.minecraft.world.entity.player.Player attacker,
            net.minecraft.world.level.Level level,
            InteractionHand hand,
            Entity target,
            EntityHitResult hit
    ) {
        if (!(attacker instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }

        if (target instanceof Mob view) {
            ServerPlayer owner = ownerOf(view);
            if (owner != null) {
                if (owner != player) {
                    player.attack(owner);
                }
                return InteractionResult.SUCCESS_SERVER;
            }
        }

        ShapeState state = get(player);
        if (state != null && state.view != null) {
            state.view.swing(hand, SwingAnimation.DEFAULT, false);
        }
        return InteractionResult.PASS;
    }

    static void tick(MinecraftServer server) {
        SHAPES.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                return false;
            }
            ShapeView.remove(entry.getValue());
            return true;
        });

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ShapeState state = get(player);
            if (state != null) {
                ShapeRuntime.tick(player, state);
                ShapeView.tick(player, state);
            }
        }
    }

    static ShapeState get(ServerPlayer player) {
        return SHAPES.get(player.getUUID());
    }

    static boolean hasShape(ServerPlayer player) {
        return get(player) != null;
    }

    public static boolean isFriendly(Mob mob, ServerPlayer player) {
        ShapeState state = get(player);
        return state != null && MobRegistry.isFriendly(mob.getType(), state.form.type());
    }

    private static void transform(ServerPlayer player, Mob source) {
        clear(player);
        MobForm form = MobRegistry.createForm(source);
        ShapeState state = new ShapeState(form);
        SHAPES.put(player.getUUID(), state);

        applyAttributes(player, form);
        copyEquipment(player, source);
        ShapeView.create(player, state, source);
    }

    static void clear(ServerPlayer player) {
        ShapeState state = SHAPES.remove(player.getUUID());
        if (state == null) {
            return;
        }

        ShapeView.remove(state);
        ShapeRuntime.clear(player);
        removeAttributes(player);
        player.setInvisible(false);
        player.noPhysics = false;
        player.getAbilities().mayfly = player.isCreative() || player.isSpectator();
        player.getAbilities().flying = player.isSpectator();
        player.getAbilities().setFlyingSpeed(0.05F);
        player.onUpdateAbilities();
        player.setAirSupply(player.getMaxAirSupply());
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    private static ServerPlayer ownerOf(Mob view) {
        for (Map.Entry<UUID, ShapeState> entry : SHAPES.entrySet()) {
            if (entry.getValue().view == view && view.level().getServer() != null) {
                return view.level().getServer().getPlayerList().getPlayer(entry.getKey());
            }
        }
        return null;
    }

    private static void applyAttributes(ServerPlayer player, MobForm form) {
        add(player, Attributes.MAX_HEALTH, HEALTH,
                form.maxHealth() - player.getAttributeBaseValue(Attributes.MAX_HEALTH),
                AttributeModifier.Operation.ADD_VALUE);
        add(player, Attributes.ATTACK_DAMAGE, ATTACK,
                form.attackDamage() - player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE),
                AttributeModifier.Operation.ADD_VALUE);
        add(player, Attributes.ARMOR, ARMOR,
                form.armor() - player.getAttributeBaseValue(Attributes.ARMOR),
                AttributeModifier.Operation.ADD_VALUE);
        add(player, Attributes.SCALE, SCALE, form.scale() - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        add(player, Attributes.MOVEMENT_SPEED, SPEED, form.traits().speedMultiplier() - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        add(player, Attributes.JUMP_STRENGTH, JUMP, form.traits().jumpMultiplier() - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (form.traits().aquatic()) {
            add(player, Attributes.WATER_MOVEMENT_EFFICIENCY, WATER, 1.0,
                    AttributeModifier.Operation.ADD_VALUE);
        }
        if (form.traits().noFallDamage()) {
            add(player, Attributes.SAFE_FALL_DISTANCE, FALL, 1024.0,
                    AttributeModifier.Operation.ADD_VALUE);
        }
        player.setHealth(player.getMaxHealth());
    }

    private static void removeAttributes(ServerPlayer player) {
        remove(player, Attributes.MAX_HEALTH, HEALTH);
        remove(player, Attributes.ATTACK_DAMAGE, ATTACK);
        remove(player, Attributes.ARMOR, ARMOR);
        remove(player, Attributes.SCALE, SCALE);
        remove(player, Attributes.MOVEMENT_SPEED, SPEED);
        remove(player, Attributes.JUMP_STRENGTH, JUMP);
        remove(player, Attributes.WATER_MOVEMENT_EFFICIENCY, WATER);
        remove(player, Attributes.SAFE_FALL_DISTANCE, FALL);
    }

    private static void add(
            ServerPlayer player,
            Holder<Attribute> attribute,
            Identifier id,
            double amount,
            AttributeModifier.Operation operation
    ) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && amount != 0.0) {
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    private static void remove(ServerPlayer player, Holder<Attribute> attribute, Identifier id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    private static void copyEquipment(ServerPlayer player, Mob source) {
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack stack = source.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                player.getInventory().add(stack.copy());
            }
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Killshift.MOD_ID, path);
    }
}
