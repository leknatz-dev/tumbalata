package ph.tumbalata.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;

/**
 * Owns ALL window handling (resize + fullscreen) so every screen behaves the same.
 * Screens should switch with changeScreen(...) and call toggleFullscreen() on F11.
 */
public class TumbalataGame extends Game {
    private static final int DEFAULT_WINDOW_W = 700;
    private static final int DEFAULT_WINDOW_H = 500;

    // The windowed size WE last set. If the window is any other size, the player resized or maximized it
    // by hand, and we must not override that.
    private int autoW = DEFAULT_WINDOW_W;
    private int autoH = DEFAULT_WINDOW_H;

    @Override
    public void create() {
        // Size the launcher gave the window (also where "untouched" starts)
        autoW = Gdx.graphics.getWidth();
        autoH = Gdx.graphics.getHeight();
        setScreen(new MainMenuScreen(this));
    }

    /** Switches screens. The window is resized to width x height ONLY if it is untouched (not fullscreen, not maximized/resized by hand). */
    public void changeScreen(Screen newScreen, int width, int height) {
        applyWindowSize(width, height);
        if (getScreen() != null) {
            getScreen().dispose();
        }
        setScreen(newScreen);
    }

    private void applyWindowSize(int width, int height) {
        if (Gdx.graphics.isFullscreen()) return;

        boolean untouched = Gdx.graphics.getWidth() == autoW && Gdx.graphics.getHeight() == autoH;
        if (untouched) {
            Gdx.graphics.setWindowedMode(width, height);
            autoW = width;
            autoH = height;
        }
    }

    public void toggleFullscreen() {
        if (Gdx.graphics.isFullscreen()) {
            Gdx.graphics.setWindowedMode(autoW, autoH);
        } else {
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
    }
}