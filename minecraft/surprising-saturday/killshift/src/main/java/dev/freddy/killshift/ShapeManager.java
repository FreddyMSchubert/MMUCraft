package dev.freddy.killshift;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;

public final class ShapeManager {
    private static final Map<UUID, ShapeState> SHAPES = new HashMap<>();
    private static final Map<UUID, CompoundTag> PENDING = new HashMap<>();
    private static final Identifier FORM = id("form");
    private static final Identifier SCALE = id("scale");

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
        if (dead instanceof ServerPlayer player) {
            if (killer != player) transform(killer, player);
        } else if (dead instanceof Mob mob) {
            if (!MobRegistry.supports(mob.getType())) return;
            EventApi.completed(killer, mob);
            transform(killer, mob);
        }
    }

    static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
        return ownerOf(target) == null;
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

        if (target instanceof LivingEntity view) {
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

    public static void readSaved(ServerPlayer player, ValueInput input) {
        input.read("killshift_form", CompoundTag.CODEC)
                .ifPresent(data -> PENDING.put(player.getUUID(), data));
    }

    public static void writeSaved(ServerPlayer player, ValueOutput output) {
        ShapeState state = get(player);
        if (state != null) output.store("killshift_form", CompoundTag.CODEC, state.save());
    }

    static void playerJoined(ServerPlayer player) {
        ShapeState old = SHAPES.remove(player.getUUID());
        if (old != null) ShapeView.remove(old);
        CompoundTag saved = PENDING.remove(player.getUUID());
        ShapeState state = saved == null ? null : ShapeState.load(saved);
        if (state == null) return;
        SHAPES.put(player.getUUID(), state);
        applyAttributes(player, state.form);
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
        ShapeEffects.apply(player, state);
        if (!ShapeView.create(player, state)) clear(player);
    }

    public static boolean isFriendly(Mob mob, ServerPlayer player) {
        ShapeState state = get(player);
        return mob.entityTags().contains("killshift_ally_" + player.getUUID())
                || state != null && MobRegistry.isFriendly(mob.getType(), state.form.type());
    }

    public static boolean isGolemEnemy(ServerPlayer player) {
        ShapeState state = get(player);
        return state != null && state.form.type().getCategory() == MobCategory.MONSTER
                && state.form.type() != EntityTypes.CREEPER;
    }

    private static void transform(ServerPlayer player, LivingEntity source) {
        float health = player.getHealth();
        CompoundTag viewData = ShapeView.snapshot(source);
        clear(player);
        MobForm form = MobRegistry.createForm(source);
        ShapeState state = new ShapeState(form, viewData);
        SHAPES.put(player.getUUID(), state);

        applyAttributes(player, form);
        player.setHealth(Math.min(health, player.getMaxHealth()));
        if (source instanceof Mob mob) copyEquipment(player, mob);
        ShapeEffects.apply(player, state);
        if (!ShapeView.create(player, state)) clear(player);
    }

    static void clear(ServerPlayer player) {
        ShapeState state = SHAPES.remove(player.getUUID());
        if (state == null) {
            return;
        }

        ShapeView.remove(state);
        ShapeEffects.clear(player, state);
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

    private static ServerPlayer ownerOf(LivingEntity view) {
        for (Map.Entry<UUID, ShapeState> entry : SHAPES.entrySet()) {
            if (entry.getValue().view == view && view.level().getServer() != null) {
                return view.level().getServer().getPlayerList().getPlayer(entry.getKey());
            }
        }
        return null;
    }

    private static void applyAttributes(ServerPlayer player, MobForm form) {
        form.attributes().forEach((attribute, value) -> {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) add(player, attribute, FORM,
                    value - instance.getBaseValue(), AttributeModifier.Operation.ADD_VALUE);
        });
        add(player, Attributes.SCALE, SCALE, form.scale() - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static void removeAttributes(ServerPlayer player) {
        for (Holder<Attribute> attribute : MobRegistry.COPIED_ATTRIBUTES) remove(player, attribute, FORM);
        remove(player, Attributes.SCALE, SCALE);
    }

    private static void add(
            ServerPlayer player,
            Holder<Attribute> attribute,
            Identifier id,
            double amount,
            AttributeModifier.Operation operation
    ) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
            if (amount != 0.0) {
                instance.addTransientModifier(new AttributeModifier(id, amount, operation));
            }
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
