package uk.co.httpsmmuminecraftsociety.mainmod.fishing;

import com.google.gson.JsonElement;

public final class FishTextureAngle {
    private FishTextureAngle() {
    }

    public static float parse(JsonElement angle) {
        if (angle.isJsonPrimitive() && angle.getAsJsonPrimitive().isNumber()) {
            return validate(angle.getAsFloat());
        }
        return parse(angle.getAsString());
    }

    public static float parse(String angle) {
        return switch (angle) {
            case "T" -> 0.0F;
            case "TR" -> 45.0F;
            case "R" -> 90.0F;
            case "BR" -> 135.0F;
            case "B" -> 180.0F;
            case "BL" -> 225.0F;
            case "L" -> 270.0F;
            case "TL" -> 315.0F;
            default -> throw new IllegalArgumentException("Unknown fish angle: " + angle);
        };
    }

    private static float validate(float degrees) {
        if (degrees >= 0.0F && degrees < 360.0F) return degrees;
        throw new IllegalArgumentException("Fish angle degrees must be at least 0 and less than 360");
    }
}
