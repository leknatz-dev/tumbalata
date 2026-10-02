package ph.tumbalata.game;

import com.badlogic.gdx.Game;

public class Main extends Game {

    @Override
    public void create() {
        // Set the initial screen to your mockup arena game screen
        setScreen(new GameScreen());
    }
}
