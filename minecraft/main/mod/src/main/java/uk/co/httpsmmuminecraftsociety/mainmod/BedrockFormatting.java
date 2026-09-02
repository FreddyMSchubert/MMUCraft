package uk.co.httpsmmuminecraftsociety.mainmod;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;

public final class BedrockFormatting {
    private BedrockFormatting() {}

    public static boolean containsCode(String text) {
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) == '&' && isCode(Character.toLowerCase(text.charAt(i + 1)))) return true;
        }
        return false;
    }

    public static MutableComponent parse(String text) {
        MutableComponent result = MutableComponent.create(PlainTextContents.EMPTY);
        Style style = Style.EMPTY;
        int start = 0;

        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) != '&') continue;

            Style next = apply(style, Character.toLowerCase(text.charAt(i + 1)));
            if (next == null) continue;

            append(result, text.substring(start, i), style);
            style = next;
            start = ++i + 1;
        }

        append(result, text.substring(start), style);
        return result;
    }

    private static void append(MutableComponent result, String text, Style style) {
        if (!text.isEmpty()) {
            result.append(MutableComponent.create(PlainTextContents.create(text)).setStyle(style));
        }
    }

    private static Style apply(Style style, char code) {
        return switch (code) {
            case '0' -> style.withColor(0x000000);
            case '1' -> style.withColor(0x0000AA);
            case '2' -> style.withColor(0x00AA00);
            case '3' -> style.withColor(0x00AAAA);
            case '4' -> style.withColor(0xAA0000);
            case '5' -> style.withColor(0xAA00AA);
            case '6' -> style.withColor(0xFFAA00);
            case '7' -> style.withColor(0xC6C6C6);
            case '8' -> style.withColor(0x555555);
            case '9' -> style.withColor(0x5555FF);
            case 'a' -> style.withColor(0x55FF55);
            case 'b' -> style.withColor(0x55FFFF);
            case 'c' -> style.withColor(0xFF5555);
            case 'd' -> style.withColor(0xFF55FF);
            case 'e' -> style.withColor(0xFFFF55);
            case 'f' -> style.withColor(0xFFFFFF);
            case 'g' -> style.withColor(0xDDD605);
            case 'h' -> style.withColor(0xE3D4D1);
            case 'i' -> style.withColor(0xCECACA);
            case 'j' -> style.withColor(0x443A3B);
            case 'k' -> style.withObfuscated(true);
            case 'l' -> style.withBold(true);
            case 'm' -> style.withColor(0x971607);
            case 'n' -> style.withColor(0xB4684D);
            case 'o' -> style.withItalic(true);
            case 'p' -> style.withColor(0xDEB12D);
            case 'q' -> style.withColor(0x119F36);
            case 'r' -> Style.EMPTY;
            case 's' -> style.withColor(0x2CBAA8);
            case 't' -> style.withColor(0x21497B);
            case 'u' -> style.withColor(0x9A5CC6);
            case 'v' -> style.withColor(0xEB7114);
            case 'w' -> style.withColor(0x8CB3FF);
            case 'x', 'y', 'z' -> style;
            default -> null;
        };
    }

    private static boolean isCode(char code) {
        return code >= '0' && code <= '9' || code >= 'a' && code <= 'z';
    }
}
