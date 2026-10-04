package ph.tumbalata.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Screen;

public class TumbalataGame extends Game {

    @Override
    public void create() {
        setScreen(new PlayerSelectScreen(this));
    }

    public void changeScreen(Screen newScreen, int width, int height) {
        if (getScreen() != null) {
            getScreen().dispose();
        }
        if (!Gdx.graphics.isFullscreen()) {
            Gdx.graphics.setWindowedMode(width, height);
        }
        setScreen(newScreen);
    }

    public void toggleFullscreen() {
        if (Gdx.graphics.isFullscreen()) {
            Gdx.graphics.setWindowedMode(700, 500);
        } else {
            DisplayMode currentMode = Gdx.graphics.getDisplayMode();
            Gdx.graphics.setFullscreenMode(currentMode);
        }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
    }
}