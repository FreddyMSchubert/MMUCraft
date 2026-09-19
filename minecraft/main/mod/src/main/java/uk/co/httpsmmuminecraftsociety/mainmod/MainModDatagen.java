package uk.co.httpsmmuminecraftsociety.mainmod;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModBlockTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModItemTagProvider;
import uk.co.httpsmmuminecraftsociety.mainmod.datagen.ModMasteryAdvancementProvider;

public class MainModDatagen implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();

        pack.addProvider(ModItemTagProvider::new);
        pack.addProvider(ModBlockTagProvider::new);
        pack.addProvider(ModMasteryAdvancementProvider::new);
    }
}
