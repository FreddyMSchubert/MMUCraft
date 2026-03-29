package uk.co.httpsmmuminecraftsociety.mainmod.fakeItems.charms;

import com.google.gson.JsonObject;

public record CharmUpgradeDefinition(
        String id,
        int count
) {
    public static CharmUpgradeDefinition of(JsonObject json, String filePath) {
        if (!json.has("id")) {
            throw new IllegalStateException(filePath + ": charm upgrade ingredient missing required field 'id'");
        }
        if (!json.has("count")) {
            throw new IllegalStateException(filePath + ": charm upgrade ingredient missing required field 'count'");
        }

        String id = json.get("id").getAsString();
        int count = json.get("count").getAsInt();

        if (id.isBlank()) {
            throw new IllegalStateException(filePath + ": charm upgrade ingredient id cannot be blank");
        }
        if (count < 1) {
            throw new IllegalStateException(filePath + ": charm upgrade ingredient count must be >= 1");
        }

        return new CharmUpgradeDefinition(id, count);
    }

    public boolean isVanillaItemId() {
        return id.contains(":");
    }
}
