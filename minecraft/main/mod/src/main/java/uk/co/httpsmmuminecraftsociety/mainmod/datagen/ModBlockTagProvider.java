package uk.co.httpsmmuminecraftsociety.mainmod.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagProvider extends FabricTagsProvider.BlockTagsProvider
{
    public static final TagKey<Block> VEIN_MINEABLE_BLOCKS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MainMod.MOD_ID, "vein_minable_blocks"));

    public ModBlockTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registryLookupFuture)
    {
        super(output, registryLookupFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider)
    {
        valueLookupBuilder(VEIN_MINEABLE_BLOCKS)
                .add(Blocks.COAL_ORE)
                .add(Blocks.DEEPSLATE_COAL_ORE)
                .add(Blocks.IRON_ORE)
                .add(Blocks.DEEPSLATE_IRON_ORE)
                .add(Blocks.COPPER_ORE)
                .add(Blocks.DEEPSLATE_COPPER_ORE)
                .add(Blocks.GOLD_ORE)
                .add(Blocks.DEEPSLATE_GOLD_ORE)
                .add(Blocks.REDSTONE_ORE)
                .add(Blocks.DEEPSLATE_REDSTONE_ORE)
                .add(Blocks.EMERALD_ORE)
                .add(Blocks.DEEPSLATE_EMERALD_ORE)
                .add(Blocks.LAPIS_ORE)
                .add(Blocks.DEEPSLATE_LAPIS_ORE)
                .add(Blocks.DIAMOND_ORE)
                .add(Blocks.DEEPSLATE_DIAMOND_ORE)
                .add(Blocks.NETHER_GOLD_ORE)
                .add(Blocks.GILDED_BLACKSTONE)
                .add(Blocks.NETHER_QUARTZ_ORE)
                .add(Blocks.ANCIENT_DEBRIS);
    }
}
