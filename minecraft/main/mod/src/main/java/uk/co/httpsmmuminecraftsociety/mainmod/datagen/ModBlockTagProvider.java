package uk.co.httpsmmuminecraftsociety.mainmod.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagProvider extends FabricTagsProvider.BlockTagsProvider
{
    public static final TagKey<Block> VEIN_MINEABLE_BLOCKS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "vein_minable_blocks"));
    public static final TagKey<Block> NON_RENAMEABLE_BLOCKS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "non_renameable_blocks"));

    public ModBlockTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registryLookupFuture)
    {
        super(output, registryLookupFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider)
    {
        builder(VEIN_MINEABLE_BLOCKS)
                .add(key(Blocks.COAL_ORE))
                .add(key(Blocks.DEEPSLATE_COAL_ORE))
                .add(key(Blocks.IRON_ORE))
                .add(key(Blocks.DEEPSLATE_IRON_ORE))
                .add(key(Blocks.COPPER_ORE))
                .add(key(Blocks.DEEPSLATE_COPPER_ORE))
                .add(key(Blocks.GOLD_ORE))
                .add(key(Blocks.DEEPSLATE_GOLD_ORE))
                .add(key(Blocks.REDSTONE_ORE))
                .add(key(Blocks.DEEPSLATE_REDSTONE_ORE))
                .add(key(Blocks.EMERALD_ORE))
                .add(key(Blocks.DEEPSLATE_EMERALD_ORE))
                .add(key(Blocks.LAPIS_ORE))
                .add(key(Blocks.DEEPSLATE_LAPIS_ORE))
                .add(key(Blocks.DIAMOND_ORE))
                .add(key(Blocks.DEEPSLATE_DIAMOND_ORE))
                .add(key(Blocks.NETHER_GOLD_ORE))
                .add(key(Blocks.GILDED_BLACKSTONE))
                .add(key(Blocks.NETHER_QUARTZ_ORE))
                .add(key(Blocks.ANCIENT_DEBRIS))
                .add(key(Blocks.TEST_BLOCK));
    }

    private static ResourceKey<Block> key(Block block)
    {
        return block.builtInRegistryHolder().key();
    }
}
