package com.example.amplifierpro.manager;

import android.graphics.Color;

public class ThemePalette {
    public final String name;
    public final int backgroundColor;
    public final int accentColor;
    public final int trackColor;
    public final int vuBaseColor;
    public final int vuInactiveColor;
    public final int textPrimaryColor;
    public final int textSecondaryColor;

    public ThemePalette(String name, int backgroundColor, int accentColor, int trackColor,
                        int vuBaseColor, int vuInactiveColor, int textPrimaryColor, int textSecondaryColor) {
        this.name = name;
        this.backgroundColor = backgroundColor;
        this.accentColor = accentColor;
        this.trackColor = trackColor;
        this.vuBaseColor = vuBaseColor;
        this.vuInactiveColor = vuInactiveColor;
        this.textPrimaryColor = textPrimaryColor;
        this.textSecondaryColor = textSecondaryColor;
    }

    public static final ThemePalette[] ALL_THEMES = new ThemePalette[] {
        new ThemePalette(
            "Sony Walkman",
            Color.parseColor("#000000"),
            Color.parseColor("#FF6600"),
            Color.parseColor("#383838"),
            Color.WHITE,
            Color.parseColor("#262626"),
            Color.WHITE,
            Color.parseColor("#B0B0B0")
        ),
        new ThemePalette(
            "Nokia XpressMusic Red",
            Color.parseColor("#0C0C0E"),
            Color.parseColor("#FF1744"),
            Color.parseColor("#1B1C20"),
            Color.WHITE,
            Color.parseColor("#222328"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#7A7E85")
        ),
        new ThemePalette(
            "Nokia XpressMusic Blue",
            Color.parseColor("#0B0E14"),
            Color.parseColor("#0088FF"),
            Color.parseColor("#161B26"),
            Color.WHITE,
            Color.parseColor("#1A202E"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#6F7D95")
        ),
        new ThemePalette(
            "Apple iPod",
            Color.parseColor("#14161A"),
            Color.parseColor("#3875D7"),
            Color.parseColor("#444B57"),
            Color.WHITE,
            Color.parseColor("#252A34"),
            Color.WHITE,
            Color.parseColor("#A0B2C6")
        ),
        new ThemePalette(
            "Microsoft Zune",
            Color.parseColor("#1B1B1B"),
            Color.parseColor("#FF3366"),
            Color.parseColor("#3E343A"),
            Color.WHITE,
            Color.parseColor("#2C2428"),
            Color.WHITE,
            Color.parseColor("#C8B4BC")
        ),
        new ThemePalette(
            "Winamp Classic",
            Color.parseColor("#1E222A"),
            Color.parseColor("#00FF00"),
            Color.parseColor("#363D4C"),
            Color.parseColor("#90FF90"),
            Color.parseColor("#1B3020"),
            Color.parseColor("#00FF00"),
            Color.parseColor("#70C870")
        ),
        new ThemePalette(
            "Windows Media Player",
            Color.parseColor("#0A1222"),
            Color.parseColor("#0078D7"),
            Color.parseColor("#203554"),
            Color.WHITE,
            Color.parseColor("#18273E"),
            Color.WHITE,
            Color.parseColor("#7EA4D6")
        ),
        new ThemePalette(
            "Poweramp",
            Color.parseColor("#121212"),
            Color.parseColor("#FF5500"),
            Color.parseColor("#353535"),
            Color.WHITE,
            Color.parseColor("#242424"),
            Color.WHITE,
            Color.parseColor("#CCCCCC")
        ),
        new ThemePalette(
            "Spotify",
            Color.parseColor("#121212"),
            Color.parseColor("#1DB954"),
            Color.parseColor("#282828"),
            Color.WHITE,
            Color.parseColor("#222222"),
            Color.WHITE,
            Color.parseColor("#B3B3B3")
        )
    };
}
