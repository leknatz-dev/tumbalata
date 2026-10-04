package ph.tumbalata.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class PlayerSelectScreen implements Screen {
    private static final float MENU_WIDTH = 700f;
    private static final float MENU_HEIGHT = 500f;

    private static final int MENU_WINDOW_W = 700;
    private static final int MENU_WINDOW_H = 500;
    private static final int GAME_WINDOW_W = 1280;
    private static final int GAME_WINDOW_H = 698;

    private static final String BACKGROUND_FILE = "menu_background.png";
    private static final String BUTTON_3P_FILE = "3p_button.png";
    private static final String BUTTON_4P_FILE = "4p_button.png";
    private static final String SLIPPER_FILE = "menu_slipper.png";
    private static final String HEADING_FILE = "menu_title.png";

    private static final float BUTTON_W = 144f;
    private static final float BUTTON_H = 100f;
    private static final float BUTTON_3P_X = 176f;
    private static final float BUTTON_3P_Y = 200f;
    private static final float BUTTON_4P_X = 380f;
    private static final float BUTTON_4P_Y = 200f;

    private static final float HEADING_CENTER_Y = 380f;
    private static final float HEADING_BOB_AMOUNT = 6f;
    private static final float HEADING_BOB_SPEED = 2.5f;

    private static final float SLIPPER_GAP = 10f;
    private static final float SLIPPER_BOB_AMOUNT = 5f;
    private static final float SLIPPER_BOB_SPEED = 6f;

    private final TumbalataGame game;

    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();

    private Texture background;
    private Texture button3pTexture;
    private Texture button4pTexture;
    private Texture slipperTexture;
    private Texture headingTexture;

    private final Rectangle[] buttonBounds = new Rectangle[2];
    private final int[] playerCounts = { 3, 4 };
    private int selected = 0;
    private float time = 0f;
    private boolean leaving = false;
    private final Vector2 mouse = new Vector2();
    private float lastMouseX = -1f, lastMouseY = -1f;

    public PlayerSelectScreen(TumbalataGame game) {
        this.game = game;
    }

    public PlayerSelectScreen(Game game) {
        this.game = (TumbalataGame) game;
    }

    @Override
    public void show() {
        leaving = false;

        camera = new OrthographicCamera();
        viewport = new FitViewport(MENU_WIDTH, MENU_HEIGHT, camera);
        viewport.apply(true);

        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();

        background = loadTexture(BACKGROUND_FILE);
        button3pTexture = loadTexture(BUTTON_3P_FILE);
        button4pTexture = loadTexture(BUTTON_4P_FILE);
        slipperTexture = loadTexture(SLIPPER_FILE);
        headingTexture = loadTexture(HEADING_FILE);

        buttonBounds[0] = new Rectangle(BUTTON_3P_X, BUTTON_3P_Y, BUTTON_W, BUTTON_H);
        buttonBounds[1] = new Rectangle(BUTTON_4P_X, BUTTON_4P_Y, BUTTON_W, BUTTON_H);
    }

    private Texture loadTexture(String file) {
        if (!Gdx.files.internal(file).exists()) {
            Gdx.app.error("PlayerSelect", "Missing asset: " + file + " (using placeholder)");
            return null;
        }
        Texture t = new Texture(Gdx.files.internal(file));
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }

    @Override
    public void render(float delta) {
        if (leaving) return;
        time += delta;
        handleInput();

        ScreenUtils.clear(0.1f, 0.1f, 0.12f, 1f);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);

        Rectangle sel = buttonBounds[selected];
        float bob = (MathUtils.sin(time * SLIPPER_BOB_SPEED) + 1f) / 2f * SLIPPER_BOB_AMOUNT;

        if (background == null || button3pTexture == null || button4pTexture == null || slipperTexture == null) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            if (background == null) {
                shapeRenderer.setColor(0.18f, 0.35f, 0.25f, 1f);
                shapeRenderer.rect(0, 0, MENU_WIDTH, MENU_HEIGHT);
            }
            shapeRenderer.setColor(Color.DARK_GRAY);
            if (button3pTexture == null) shapeRenderer.rect(buttonBounds[0].x, buttonBounds[0].y, BUTTON_W, BUTTON_H);
            if (button4pTexture == null) shapeRenderer.rect(buttonBounds[1].x, buttonBounds[1].y, BUTTON_W, BUTTON_H);
            if (slipperTexture == null) {
                shapeRenderer.setColor(Color.BROWN);
                shapeRenderer.ellipse(sel.x + (BUTTON_W - 40f) / 2f, sel.y - SLIPPER_GAP - 16f + bob, 40f, 16f);
            }
            shapeRenderer.end();
        }

        batch.begin();

        if (background != null) batch.draw(background, 0, 0, MENU_WIDTH, MENU_HEIGHT);

        float headingBob = MathUtils.sin(time * HEADING_BOB_SPEED) * HEADING_BOB_AMOUNT;
        if (headingTexture != null) {
            float hw = headingTexture.getWidth();
            float hh = headingTexture.getHeight();
            batch.draw(headingTexture, (MENU_WIDTH - hw) / 2f, HEADING_CENTER_Y - hh / 2f + headingBob, hw, hh);
        } else {
            font.getData().setScale(2f);
            layout.setText(font, "HOW MANY PLAYERS?");
            font.draw(batch, "HOW MANY PLAYERS?", (MENU_WIDTH - layout.width) / 2f, HEADING_CENTER_Y + layout.height / 2f + headingBob);
            font.getData().setScale(1f);
        }

        if (button3pTexture != null) batch.draw(button3pTexture, buttonBounds[0].x, buttonBounds[0].y, BUTTON_W, BUTTON_H);
        else drawLabel(0, "3P");

        if (button4pTexture != null) batch.draw(button4pTexture, buttonBounds[1].x, buttonBounds[1].y, BUTTON_W, BUTTON_H);
        else drawLabel(1, "4P");

        if (slipperTexture != null) {
            float sw = slipperTexture.getWidth();
            float sh = slipperTexture.getHeight();
            float sx = sel.x + (BUTTON_W - sw) / 2f;
            float sy = sel.y - SLIPPER_GAP - sh + bob;
            batch.draw(slipperTexture, sx, sy, sw, sh);
        }

        batch.end();
    }

    private void drawLabel(int index, String text) {
        Rectangle r = buttonBounds[index];
        layout.setText(font, text);
        font.draw(batch, text, r.x + (BUTTON_W - layout.width) / 2f, r.y + (BUTTON_H + layout.height) / 2f);
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F11)) {
            if (game != null) game.toggleFullscreen();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)) {
            selected = (selected + buttonBounds.length - 1) % buttonBounds.length;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)) {
            selected = (selected + 1) % buttonBounds.length;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            leaving = true;
            if (game != null) {
                game.changeScreen(new MainMenuScreen(game), MENU_WINDOW_W, MENU_WINDOW_H);
            }
            return;
        }

        mouse.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(mouse);
        boolean mouseMoved = Gdx.input.getX() != lastMouseX || Gdx.input.getY() != lastMouseY;
        lastMouseX = Gdx.input.getX();
        lastMouseY = Gdx.input.getY();

        int hovered = -1;
        for (int i = 0; i < buttonBounds.length; i++) {
            if (buttonBounds[i].contains(mouse.x, mouse.y)) hovered = i;
        }
        if (hovered != -1 && mouseMoved) selected = hovered;

        boolean confirm = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            || Gdx.input.isKeyJustPressed(Input.Keys.E);
        boolean clicked = Gdx.input.justTouched() && hovered != -1;
        if (clicked) selected = hovered;

        if (confirm || clicked) startGame(playerCounts[selected]);
    }

    private void startGame(int playerCount) {
        leaving = true;
        if (game != null) {
            game.changeScreen(new GameScreen(playerCount), GAME_WINDOW_W, GAME_WINDOW_H);
        }
    }

    @Override
    public void resize(int width, int height) {
        if (viewport != null) {
            viewport.update(width, height, true);
        }
    }

    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void hide() {}

    @Override
    public void dispose() {
        if (batch != null) { batch.dispose(); batch = null; }
        if (shapeRenderer != null) { shapeRenderer.dispose(); shapeRenderer = null; }
        if (font != null) { font.dispose(); font = null; }
        if (background != null) { background.dispose(); background = null; }
        if (button3pTexture != null) { button3pTexture.dispose(); button3pTexture = null; }
        if (button4pTexture != null) { button4pTexture.dispose(); button4pTexture = null; }
        if (slipperTexture != null) { slipperTexture.dispose(); slipperTexture = null; }
        if (headingTexture != null) { headingTexture.dispose(); headingTexture = null; }
    }
}