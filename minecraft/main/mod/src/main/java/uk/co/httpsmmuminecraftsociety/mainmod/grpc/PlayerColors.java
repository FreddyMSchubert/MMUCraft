package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import net.minecraft.world.scores.TeamColor;

import java.util.UUID;

final class PlayerColors {
    private PlayerColors() {}

    static int parse(String color) {
        if (!color.matches("^#[0-9A-Fa-f]{6}$")) return 0xE6E6E6;
        return withMinimumLightness(Integer.parseInt(color.substring(1), 16));
    }

    static int defaultColor(UUID playerId) {
        int rgb = playerId.hashCode() & 0xFFFFFF;
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        int maximum = Math.max(red, Math.max(green, blue));
        if (maximum == 0) return 0xE6E6E6;
        // Match the website's HSV brightness of 0.9 while retaining hue and saturation.
        red = (int) Math.round(red / (double) maximum * 229.5);
        green = (int) Math.round(green / (double) maximum * 229.5);
        blue = (int) Math.round(blue / (double) maximum * 229.5);
        return withMinimumLightness(red << 16 | green << 8 | blue);
    }

    static int withMinimumLightness(int rgb) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        double lightness = (Math.max(red, Math.max(green, blue)) + Math.min(red, Math.min(green, blue))) / 510.0;
        if (lightness >= 0.6) return rgb;
        double whiteBlend = (0.6 - lightness) / (1 - lightness);
        red = (int) Math.round(red + (255 - red) * whiteBlend);
        green = (int) Math.round(green + (255 - green) * whiteBlend);
        blue = (int) Math.round(blue + (255 - blue) * whiteBlend);
        return red << 16 | green << 8 | blue;
    }

    static TeamColor closestTeamColor(int rgb) {
        Oklab source = oklab(rgb);
        TeamColor closest = TeamColor.WHITE;
        double shortestDistance = Double.POSITIVE_INFINITY;
        for (TeamColor candidate : TeamColor.VALUES) {
            Oklab target = oklab(candidate.rgb());
            double lightness = source.lightness - target.lightness;
            double a = source.a - target.a;
            double b = source.b - target.b;
            double chroma = source.chroma() - target.chroma();
            double hueDistance = Math.max(0, a * a + b * b - chroma * chroma);
            // Preserve hue more strongly than saturation when approximating with 16 colors.
            double distance = lightness * lightness + 0.5 * chroma * chroma + 2 * hueDistance;
            if (target.chroma() < 0.0001) {
                // A small, capped bias against losing visible color. Near-neutral choices
                // (OKLab chroma <= 0.02) receive no bias and can still match grey or black.
                distance += Math.min(0.025, Math.max(0, source.chroma() - 0.02) * 0.4);
            }
            if (distance < shortestDistance) {
                closest = candidate;
                shortestDistance = distance;
            }
        }
        return closest;
    }

    record Oklab(double lightness, double a, double b) {
        double chroma() {
            return Math.hypot(a, b);
        }
    }

    // Bjorn Ottosson's linear-sRGB conversion: https://bottosson.github.io/posts/oklab/
    static Oklab oklab(int rgb) {
        double red = linearChannel(rgb >> 16 & 0xFF);
        double green = linearChannel(rgb >> 8 & 0xFF);
        double blue = linearChannel(rgb & 0xFF);
        double l = Math.cbrt(0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue);
        double m = Math.cbrt(0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue);
        double s = Math.cbrt(0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue);
        return new Oklab(
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        );
    }

    private static double linearChannel(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
