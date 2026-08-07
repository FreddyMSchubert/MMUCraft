package uk.co.httpsmmuminecraftsociety.mainmod.dailies.tasks;

import com.google.gson.JsonObject;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import uk.co.httpsmmuminecraftsociety.mainmod.dailies.DailyTaskEvent;

public final class PlayNoteBlockTask extends CountedTask {
    private final String instrument;
    private final String instrumentName;

    public PlayNoteBlockTask(NoteBlockInstrument instrument, String instrumentName) {
        super("note_block:" + instrument.getSerializedName(), "🎵", 4, 10, 0.6D, "Played", "notes");
        this.instrument = instrument.getSerializedName();
        this.instrumentName = instrumentName;
    }

    @Override protected String name(int count) { return instrumentName + " Notes"; }
    @Override protected String description(int count) { return "Right-click a note block with the " + instrumentName.toLowerCase() + " instrument " + count + " times."; }
    @Override protected boolean matches(JsonObject task, DailyTaskEvent event) {
        return event.type() == DailyTaskEvent.Type.PLAY_NOTE_BLOCK && event.subject().equals(instrument);
    }
    @Override protected void addTaskData(JsonObject task, java.util.Random random) { task.addProperty("instrument", instrument); }
}
