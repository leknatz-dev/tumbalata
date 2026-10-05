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

    // One live background shared by the menu screens, so the characters keep walking across screen changes
    private MenuBackdrop backdrop;
    private boolean backdropTried = false;

    /** The shared live menu background, or null if it couldn't be built (screens then use their image background). */
    public MenuBackdrop getBackdrop() {
        if (backdrop == null && !backdropTried) {
            backdropTried = true;
            backdrop = MenuBackdrop.tryCreate();
        }
        return backdrop;
    }

    private void releaseBackdrop() {
        if (backdrop != null) {
            backdrop.dispose();
            backdrop = null;
        }
        backdropTried = false;
    }

    private static boolean isMenuScreen(Screen screen) {
        return screen instanceof MainMenuScreen
            || screen instanceof PlayerSelectScreen
            || screen instanceof CharacterSelectScreen;
    }

    // Sweep animation played over every screen change
    private ScreenTransition transition;

    @Override
    public void create() {
        transition = new ScreenTransition();
        // Size the launcher gave the window (also where "untouched" starts)
        autoW = Gdx.graphics.getWidth();
        autoH = Gdx.graphics.getHeight();
        setScreen(new MainMenuScreen(this));
    }

    // A screen change that was requested during this frame. It is carried out at the end of the frame, after the
    // current screen has drawn its last frame (which becomes the transition's snapshot).
    private Screen pendingScreen;
    private int pendingW, pendingH;

    /**
     * Switches screens with the can sweep. Requests made while a transition is already playing are ignored.
     * The window is resized to width x height ONLY if it is untouched (not fullscreen, not maximized/resized by hand),
     * and that happens when the sweep is finished.
     */
    public void changeScreen(Screen newScreen, int width, int height) {
        if (transition.isActive() || pendingScreen != null) return;
        pendingScreen = newScreen;
        pendingW = width;
        pendingH = height;
    }

    private void switchNow(Screen newScreen) {
        if (!isMenuScreen(newScreen)) {
            releaseBackdrop(); // not needed during the game or the victory screen
        }
        if (getScreen() != null) {
            getScreen().dispose();
        }
        setScreen(newScreen);
    }

    @Override
    public void render() {
        super.render(); // draws the current screen (and lets it read input, which may request a screen change)

        if (pendingScreen != null) {
            final int w = pendingW, h = pendingH;
            Screen next = pendingScreen;
            pendingScreen = null;

            transition.captureSnapshot();               // the old screen's last frame
            transition.begin(() -> applyWindowSize(w, h));
            switchNow(next);                            // the new screen is underneath; the snapshot covers it
            return;
        }

        transition.update(Gdx.graphics.getDeltaTime());
        transition.draw();
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
    public void dispose() {
        if (transition != null) transition.dispose();
        releaseBackdrop();
        super.dispose();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
    }
}