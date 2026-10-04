package ph.tumbalata.game;

import com.badlogic.gdx.graphics.Color;

/** The 4 playable characters (placeholders for now). Shared by CharacterSelectScreen and GameScreen. */
public final class Characters {
    private Characters() {}

    public static final int COUNT = 4;

    public static final String[] NAMES = { "CHAR 1", "CHAR 2", "CHAR 3", "CHAR 4" };

    // Optional portraits for the select screen. If a file is missing, a colored placeholder card is drawn.
    public static final String[] PORTRAIT_FILES = { "char1.png", "char2.png", "char3.png", "char4.png" };

    // Placeholder card colors on the select screen
    public static final Color[] CARD_COLORS = {
        new Color(0.80f, 0.25f, 0.25f, 1f),
        new Color(0.25f, 0.40f, 0.85f, 1f),
        new Color(0.25f, 0.70f, 0.35f, 1f),
        new Color(0.90f, 0.75f, 0.20f, 1f)
    };

    // In-game sprite tints (placeholder until each character has its own sprite sheet)
    public static final Color[] TINTS = {
        new Color(1.00f, 0.60f, 0.60f, 1f),
        new Color(0.60f, 0.70f, 1.00f, 1f),
        new Color(0.60f, 1.00f, 0.65f, 1f),
        new Color(1.00f, 0.95f, 0.55f, 1f)
    };
}