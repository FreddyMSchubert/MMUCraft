package uk.co.httpsmmuminecraftsociety.mainmod.modifiers.particleTrails;

import net.minecraft.core.particles.*;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum TrailParticle {
    WHITE(Items.DYE.white(), DyeColor.WHITE),
    ORANGE(Items.DYE.orange(), DyeColor.ORANGE),
    MAGENTA(Items.DYE.magenta(), DyeColor.MAGENTA),
    LIGHT_BLUE(Items.DYE.lightBlue(), DyeColor.LIGHT_BLUE),
    YELLOW(Items.DYE.yellow(), DyeColor.YELLOW),
    LIME(Items.DYE.lime(), DyeColor.LIME),
    PINK(Items.DYE.pink(), DyeColor.PINK),
    GRAY(Items.DYE.gray(), DyeColor.GRAY),
    LIGHT_GRAY(Items.DYE.lightGray(), DyeColor.LIGHT_GRAY),
    CYAN(Items.DYE.cyan(), DyeColor.CYAN),
    PURPLE(Items.DYE.purple(), DyeColor.PURPLE),
    BLUE(Items.DYE.blue(), DyeColor.BLUE),
    BROWN(Items.DYE.brown(), DyeColor.BROWN),
    GREEN(Items.DYE.green(), DyeColor.GREEN),
    RED(Items.DYE.red(), DyeColor.RED),
    BLACK(Items.DYE.black(), DyeColor.BLACK),
    ENCHANT(Items.ENCHANTING_TABLE, "Enchanting letters", ParticleTypes.ENCHANT),
    ENCHANTED_HIT(Items.LAPIS_LAZULI, "Enchanted sparks", ParticleTypes.ENCHANTED_HIT),
    END_ROD(Items.END_ROD, "End rod sparks", ParticleTypes.END_ROD),
    PORTAL(Items.OBSIDIAN, "Portal", ParticleTypes.PORTAL),
    REVERSE_PORTAL(Items.ENDER_PEARL, "Reverse portal", ParticleTypes.REVERSE_PORTAL),
    WITCH(Items.FERMENTED_SPIDER_EYE, "Witch magic", ParticleTypes.WITCH),
    FIREWORK(Items.FIREWORK_ROCKET, "Firework sparks", ParticleTypes.FIREWORK),
    TOTEM_OF_UNDYING(Items.TOTEM_OF_UNDYING, "Totem sparks", ParticleTypes.TOTEM_OF_UNDYING),
    CHERRY_LEAVES(Items.CHERRY_LEAVES, "Cherry leaves", ParticleTypes.CHERRY_LEAVES),
    PALE_OAK_LEAVES(Items.PALE_OAK_LEAVES, "Pale oak leaves", ParticleTypes.PALE_OAK_LEAVES),
    FLAME(Items.BLAZE_POWDER, "Flame", ParticleTypes.FLAME),
    SOUL_FIRE_FLAME(Items.SOUL_TORCH, "Soul flame", ParticleTypes.SOUL_FIRE_FLAME),
    COPPER_FIRE_FLAME(Items.COPPER_TORCH, "Copper flame", ParticleTypes.COPPER_FIRE_FLAME),
    LAVA(Items.MAGMA_CREAM, "Lava sparks", ParticleTypes.LAVA),
    HEART(Items.POPPY, "Hearts", ParticleTypes.HEART),
    SOUL(Items.SOUL_SAND, "Souls", ParticleTypes.SOUL),
    SCULK_CHARGE_POP(Items.SCULK, "Sculk sparks", ParticleTypes.SCULK_CHARGE_POP),
    EXPLOSION(Items.TNT, "Explosion", ParticleTypes.EXPLOSION),
    SONIC_BOOM(Items.ECHO_SHARD, "Sonic boom", ParticleTypes.SONIC_BOOM),
    HAPPY_VILLAGER(Items.EMERALD, "Happy villager", ParticleTypes.HAPPY_VILLAGER),
    ANGRY_VILLAGER(Items.ROTTEN_FLESH, "Angry villager", ParticleTypes.ANGRY_VILLAGER),
    EGG_CRACK(Items.TURTLE_EGG, "Egg sparks", ParticleTypes.EGG_CRACK),
    NOTE(Items.NOTE_BLOCK, "Music notes", ParticleTypes.NOTE),
    FIREFLY(Items.FIREFLY_BUSH, "Fireflies", ParticleTypes.FIREFLY),
    NAUTILUS(Items.NAUTILUS_SHELL, "Nautilus", ParticleTypes.NAUTILUS),
    INFESTED(Items.SPIDER_EYE, "Infested", ParticleTypes.INFESTED),
    SMALL_GUST(Items.BREEZE_ROD, "Small gust", ParticleTypes.SMALL_GUST),
    GUST(Items.WIND_CHARGE, "Gust", ParticleTypes.GUST),
    CAMPFIRE_COSY_SMOKE(Items.CAMPFIRE, "Campfire smoke", ParticleTypes.CAMPFIRE_COSY_SMOKE),
    CLOUD(Items.FEATHER, "Cloud", ParticleTypes.CLOUD),
    SMOKE(Items.CHARCOAL, "Smoke", ParticleTypes.SMOKE),
    POOF(Items.BONE, "Poof", ParticleTypes.POOF),
    SNEEZE(Items.BAMBOO, "Sneeze", ParticleTypes.SNEEZE),
    SPIT(Items.HAY_BLOCK, "Llama spit", ParticleTypes.SPIT),
    SQUID_INK(Items.INK_SAC, "Squid ink", ParticleTypes.SQUID_INK),
    GLOW_SQUID_INK(Items.GLOW_INK_SAC, "Glow squid ink", ParticleTypes.GLOW_SQUID_INK),
    GLOW(Items.GLOW_BERRIES, "Glow", ParticleTypes.GLOW),
    ELECTRIC_SPARK(Items.LIGHTNING_ROD.weathering().unaffected(), "Electric sparks", ParticleTypes.ELECTRIC_SPARK),
    WAX_ON(Items.HONEYCOMB, "Wax sparks", ParticleTypes.WAX_ON),
    SCRAPE(Items.COPPER_BLOCK.weathering().oxidized(), "Copper scrape", ParticleTypes.SCRAPE),
    CRIT(Items.FLINT, "Critical sparks", ParticleTypes.CRIT),
    DAMAGE_INDICATOR(Items.SWEET_BERRIES, "Damage hearts", ParticleTypes.DAMAGE_INDICATOR),
    SWEEP_ATTACK(Items.IRON_SWORD, "Sweeping arc", ParticleTypes.SWEEP_ATTACK),
    ITEM_SLIME(Items.SLIME_BALL, "Slime", ParticleTypes.ITEM_SLIME),
    ITEM_COBWEB(Items.COBWEB, "Cobweb", ParticleTypes.ITEM_COBWEB),
    ITEM_SNOWBALL(Items.SNOWBALL, "Snowball", ParticleTypes.ITEM_SNOWBALL),
    SNOWFLAKE(Items.SNOW_BLOCK, "Snowflakes", ParticleTypes.SNOWFLAKE),
    SPLASH(Items.PRISMARINE_SHARD, "Water splash", ParticleTypes.SPLASH),
    BUBBLE_POP(Items.PRISMARINE_CRYSTALS, "Bubble pop", ParticleTypes.BUBBLE_POP),
    FALLING_WATER(Items.WET_SPONGE, "Water drops", ParticleTypes.FALLING_WATER),
    FALLING_LAVA(Items.LAVA_BUCKET, "Lava drops", ParticleTypes.FALLING_LAVA),
    FALLING_HONEY(Items.HONEY_BOTTLE, "Honey drops", ParticleTypes.FALLING_HONEY),
    FALLING_NECTAR(Items.SUNFLOWER, "Nectar", ParticleTypes.FALLING_NECTAR),
    FALLING_OBSIDIAN_TEAR(Items.CRYING_OBSIDIAN, "Obsidian tears", ParticleTypes.FALLING_OBSIDIAN_TEAR),
    FALLING_SPORE_BLOSSOM(Items.SPORE_BLOSSOM, "Spore blossom", ParticleTypes.FALLING_SPORE_BLOSSOM),
    MYCELIUM(Items.MYCELIUM, "Mycelium spores", ParticleTypes.MYCELIUM),
    CRIMSON_SPORE(Items.CRIMSON_FUNGUS, "Crimson spores", ParticleTypes.CRIMSON_SPORE),
    WARPED_SPORE(Items.WARPED_FUNGUS, "Warped spores", ParticleTypes.WARPED_SPORE),
    ASH(Items.SOUL_SOIL, "Ash", ParticleTypes.ASH),
    WHITE_ASH(Items.BASALT, "White ash", ParticleTypes.WHITE_ASH),
    DUST_PLUME(Items.BRUSH, "Dust plume", ParticleTypes.DUST_PLUME),
    TRIAL_SPAWNER_DETECTED_PLAYER(Items.TRIAL_KEY, "Trial detection", ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER),
    TRIAL_SPAWNER_DETECTED_PLAYER_OMINOUS(Items.OMINOUS_TRIAL_KEY, "Ominous trial detection", ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER_OMINOUS),
    VAULT_CONNECTION(Items.DIAMOND, "Vault connection", ParticleTypes.VAULT_CONNECTION),
    OMINOUS_SPAWNING(Items.HEAVY_CORE, "Ominous spawning", ParticleTypes.OMINOUS_SPAWNING),
    RAID_OMEN(Items.OMINOUS_BOTTLE, "Raid omen", ParticleTypes.RAID_OMEN),
    TRIAL_OMEN(Items.CHISELED_TUFF_BRICKS, "Trial omen", ParticleTypes.TRIAL_OMEN),
    NOXIOUS_GAS(Items.POTENT_SULFUR, "Noxious gas", ParticleTypes.NOXIOUS_GAS),
    SULFUR_CUBE_GOO(Items.SULFUR_CUBE_BUCKET, "Sulfur goo", ParticleTypes.SULFUR_CUBE_GOO),
    REDSTONE(Items.REDSTONE, "Redstone dust", new DustParticleOptions(0xFF0000, 0.9f)),
    TINTED_LEAVES(Items.OAK_LEAVES, "Green leaves", ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF48B518)),
    DRAGON_BREATH(Items.DRAGON_BREATH, "Dragon breath", PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f)),
    EFFECT(Items.GLOWSTONE_DUST, "Potion swirls", SpellParticleOption.create(ParticleTypes.EFFECT, 0xAA55FF, 1.0f)),
    INSTANT_EFFECT(Items.GLISTERING_MELON_SLICE, "Instant effect", SpellParticleOption.create(ParticleTypes.INSTANT_EFFECT, 0xF82423, 1.0f)),
    DUST_COLOR_TRANSITION(Items.SCULK_SENSOR, "Sculk colour fade", DustColorTransitionOptions.SCULK_TO_REDSTONE),
    SCULK_CHARGE(Items.SCULK_VEIN, "Sculk charge", new SculkChargeParticleOptions(0.0f)),
    SHRIEK(Items.SCULK_SHRIEKER, "Shriek", new ShriekParticleOption(0)),
    BLOCK(Items.AMETHYST_BLOCK, "Amethyst fragments", new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState())),
    FALLING_DUST(Items.SAND, "Sand dust", new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState())),
    BLOCK_CRUMBLE(Items.CREAKING_HEART, "Creaking fragments", new BlockParticleOption(ParticleTypes.BLOCK_CRUMBLE, Blocks.CREAKING_HEART.defaultBlockState())),
    ITEM(Items.COOKIE, "Cookie crumbs", new ItemParticleOption(ParticleTypes.ITEM, Items.COOKIE));

    private static final Map<Item, TrailParticle> BY_ITEM = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(particle -> particle.ingredient, Function.identity()));

    public final Item ingredient;
    public final String label;
    public final ParticleOptions options;

    TrailParticle(Item ingredient, DyeColor dye) {
        this(ingredient, title(dye.getName()) + " dust", new DustParticleOptions(boostColor(dye.getTextureDiffuseColor()), 0.9f));
    }

    TrailParticle(Item ingredient, String label, ParticleOptions options) {
        this.ingredient = ingredient;
        this.label = label;
        this.options = options;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isBasicDust() {
        return options.getType() == ParticleTypes.DUST;
    }

    public static TrailParticle fromItem(ItemStack stack) {
        return BY_ITEM.get(stack.getItem());
    }

    public static TrailParticle fromId(String id) {
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String title(String name) {
        String words = name.replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static int boostColor(int color) {
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        int average = (red + green + blue) / 3;
        return boostChannel(red, average) << 16 | boostChannel(green, average) << 8 | boostChannel(blue, average);
    }

    private static int boostChannel(int channel, int average) {
        return Math.clamp(Math.round((average + (channel - average) * 1.25f) * 1.35f), 0, 255);
    }
}
