package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.fakeItemDefs;

import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.JukeboxSong;
import uk.co.httpsmmuminecraftsociety.mainmod.MainMod;

public record DiscItemFeature(
        ResourceKey<JukeboxSong> songKey
) implements ItemFeature
{
    public static ItemFeature of(JsonObject json)
    {
        String songId = json.get("songId").getAsString();
        Identifier id = Identifier.parse(songId);
        return new DiscItemFeature(ResourceKey.create(Registries.JUKEBOX_SONG, id));
    }

    @Override
    public void apply(ItemStack stack)
    {
        stack.set(
                net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE,
                new JukeboxPlayable(resolveSongHolder())
        );
    }

    @Override
    public void validate()
    {
        resolveSongHolder();
    }

    private Holder<JukeboxSong> resolveSongHolder() {
        HolderLookup.Provider registries = MainMod.getRegistries();

        return registries
                .lookupOrThrow(Registries.JUKEBOX_SONG)
                .getOrThrow(songKey);
    }
}
