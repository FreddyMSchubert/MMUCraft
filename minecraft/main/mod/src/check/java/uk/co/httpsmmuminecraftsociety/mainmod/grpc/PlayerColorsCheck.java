package uk.co.httpsmmuminecraftsociety.mainmod.grpc;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

import java.util.Optional;
import java.util.UUID;

public final class PlayerColorsCheck {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        checkOklabConversion();
        checkMutedColors();
        checkNameFormatting();
        checkDefaultsAndLightness();
        System.out.println("Player color checks passed: muted name tags, RGB names, role suffixes, and join defaults.");
    }

    private static void checkMutedColors() {
        // Light blues must retain their hue, without forcing near-neutral choices into colors.
        for (int rgb : new int[]{0xADD8E6, 0x87CEFA, 0x80CAFF}) {
            int mapped = PlayerColors.closestTeamColor(rgb).rgb();
            assert (mapped >> 16 & 0xFF) != (mapped >> 8 & 0xFF) || (mapped >> 8 & 0xFF) != (mapped & 0xFF)
                    : "A colored name must not become grey: " + Integer.toHexString(rgb);
        }
        assert PlayerColors.closestTeamColor(0xFF0000) == TeamColor.RED;
        assert PlayerColors.closestTeamColor(0xFF69B4) == TeamColor.LIGHT_PURPLE
                : "Hot pink must not collapse into red";
        assert PlayerColors.closestTeamColor(0xAAB0B4) == TeamColor.GRAY
                : "A deliberately low-saturation color should still be grey";
        for (TeamColor color : TeamColor.VALUES) {
            assert PlayerColors.closestTeamColor(color.rgb()) == color : "Exact palette colors must be preserved";
        }
    }

    private static void checkOklabConversion() {
        PlayerColors.Oklab red = PlayerColors.oklab(0xFF0000);
        assert Math.abs(red.lightness() - 0.627955) < 0.000001;
        assert Math.abs(red.a() - 0.224863) < 0.000001;
        assert Math.abs(red.b() - 0.125846) < 0.000001;
        PlayerColors.Oklab white = PlayerColors.oklab(0xFFFFFF);
        assert Math.abs(white.lightness() - 1) < 0.000001;
        assert white.chroma() < 0.000001;
        assert PlayerColors.oklab(0).lightness() == 0;
    }

    private static void checkNameFormatting() {
        PlayerTeam team = new Scoreboard().addPlayerTeam("mmu_test");
        int rgb = 0xBBAACC;
        team.setDisplayName(Component.literal("Player").withColor(rgb));
        team.setColor(Optional.of(PlayerColors.closestTeamColor(rgb)));
        team.setPlayerSuffix(Component.literal(" [Member]").withStyle(ChatFormatting.GREEN));
        Component name = PlayerTeam.formatNameForTeam(team, Component.literal("Player").withColor(rgb));
        assert name.getString().equals("Player [Member]");
        name.visit((style, text) -> {
            if (text.equals("Player")) assert style.getColor().getValue() == rgb : "RGB must override the team palette";
            if (text.equals(" [Member]")) assert style.getColor().getValue() == TeamColor.GREEN.rgb()
                    : "The role suffix must retain its own color";
            return Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        assert team.pack().displayName().orElseThrow().getStyle().getColor().getValue() == rgb
                : "The exact color must survive scoreboard persistence for the next join";
    }

    private static void checkDefaultsAndLightness() {
        UUID playerId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        assert PlayerColors.defaultColor(playerId) == PlayerColors.parse("#56E6DD")
                : "Join defaults must match the website UUID color";
        assert PlayerColors.parse("#bbaacc") == 0xBBAACC;
        assert PlayerColors.parse("invalid") == 0xE6E6E6;
        for (int rgb : new int[]{0, 0x123456, 0xFF0000, 0xBBAACC, 0xFFFFFF}) {
            int lightened = PlayerColors.withMinimumLightness(rgb);
            assert PlayerColors.withMinimumLightness(lightened) == lightened : "Refreshes must not change the color";
        }
    }
}
