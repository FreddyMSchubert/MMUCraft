package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms.held;

import com.google.gson.JsonParseException;

public final class WisdomCharmCheck {
    private WisdomCharmCheck() {}

    public static void main(String[] args) {
        var quotes = WisdomCharm.parseZenQuotes("""
                [{"q":"Keep going.","a":"A Person","h":"<blockquote>Ignore this</blockquote>"},
                 {"q":"","a":"Nobody"}]
                """);
        assert quotes.size() == 1;
        assert quotes.getFirst().text().equals("Keep going.");
        assert quotes.getFirst().author().equals("A Person");

        try {
            WisdomCharm.parseZenQuotes("[{\"q\":\"Too many requests\"}]");
            throw new AssertionError("Invalid API response was accepted");
        } catch (JsonParseException expected) {
            // The free API may return an error object instead of quote data.
        }
    }
}
