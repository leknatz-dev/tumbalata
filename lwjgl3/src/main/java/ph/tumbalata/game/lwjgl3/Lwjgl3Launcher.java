package ph.tumbalata.game.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import ph.tumbalata.game.TumbalataGame;

public class Lwjgl3Launcher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Tumbalata");
        config.setWindowedMode(700, 500);
        config.setResizable(true); // Ensures seamless viewport stretching in fullscreen/maximized
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4);

        new Lwjgl3Application(new TumbalataGame(), config);
    }
}