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

public class MainMenuScreen implements Screen {
    private static final float MENU_WIDTH = 700f;
    private static final float MENU_HEIGHT = 500f;

    private static final int MENU_WINDOW_W = 700;
    private static final int MENU_WINDOW_H = 500;

    private static final String BACKGROUND_FILE = "menu_background.png";
    private static final String START_BUTTON_FILE = "start_button.png";
    private static final String EXIT_BUTTON_FILE = "exit_button.png";
    private static final String SLIPPER_FILE = "menu_slipper.png";
    private static final String TITLE_FILE = "menu_title.png";

    private static final float TITLE_CENTER_Y = 350f;
    private static final float TITLE_SCALE = 1.8f;
    private static final float TITLE_BOB_AMOUNT = 8f;
    private static final float TITLE_BOB_SPEED = 2.5f;

    private static final float BUTTON_W = 100f;
    private static final float BUTTON_H = 50f;
    private static final float START_X = 400f;
    private static final float START_Y = 170f;
    private static final float EXIT_X = 400f;
    private static final float EXIT_Y = 100f;

    private static final float SLIPPER_GAP = 12f;
    private static final boolean FLIP_SLIPPER = false;
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
    private Texture startTexture;
    private Texture exitTexture;
    private Texture slipperTexture;
    private Texture titleTexture;

    private final Rectangle[] buttonBounds = new Rectangle[2];
    private final String[] buttonLabels = { "START", "EXIT" };
    private int selected = 0;
    private float time = 0f;
    private final Vector2 mouse = new Vector2();
    private float lastMouseX = -1f, lastMouseY = -1f;
    private boolean leaving = false;

    public MainMenuScreen(TumbalataGame game) {
        this.game = game;
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
    startTexture = loadTexture(START_BUTTON_FILE);
    exitTexture = loadTexture(EXIT_BUTTON_FILE);
    slipperTexture = loadTexture(SLIPPER_FILE);
    titleTexture = loadTexture(TITLE_FILE);

    buttonBounds[0] = new Rectangle(START_X, START_Y, BUTTON_W, BUTTON_H);
    buttonBounds[1] = new Rectangle(EXIT_X, EXIT_Y, BUTTON_W, BUTTON_H);
}
    private Texture loadTexture(String file) {
        if (!Gdx.files.internal(file).exists()) {
            Gdx.app.error("Menu", "Missing asset: " + file + " (using a placeholder)");
            return null;
        }
        Texture t = new Texture(Gdx.files.internal(file));
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }

    @Override
    public void render(float delta) {
        time += delta;
        handleInput();
        if (leaving) return;

        ScreenUtils.clear(0.1f, 0.1f, 0.12f, 1f);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);

        boolean needShapes = background == null || startTexture == null || exitTexture == null || slipperTexture == null;
        if (needShapes) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            if (background == null) {
                shapeRenderer.setColor(0.18f, 0.35f, 0.25f, 1f);
                shapeRenderer.rect(0, 0, MENU_WIDTH, MENU_HEIGHT);
            }
            if (startTexture == null) {
                shapeRenderer.setColor(Color.DARK_GRAY);
                shapeRenderer.rect(buttonBounds[0].x, buttonBounds[0].y, BUTTON_W, BUTTON_H);
            }
            if (exitTexture == null) {
                shapeRenderer.setColor(Color.DARK_GRAY);
                shapeRenderer.rect(buttonBounds[1].x, buttonBounds[1].y, BUTTON_W, BUTTON_H);
            }
            shapeRenderer.end();
        }

        batch.begin();

        if (background != null) batch.draw(background, 0, 0, MENU_WIDTH, MENU_HEIGHT);

        float titleBob = MathUtils.sin(time * TITLE_BOB_SPEED) * TITLE_BOB_AMOUNT;
        if (titleTexture != null) {
            float tw = titleTexture.getWidth() * TITLE_SCALE;
            float th = titleTexture.getHeight() * TITLE_SCALE;
            batch.draw(titleTexture, (MENU_WIDTH - tw) / 2f, TITLE_CENTER_Y - th / 2f + titleBob, tw, th);
        } else {
            font.getData().setScale(3f);
            layout.setText(font, "TUMBALATA");
            font.draw(batch, "TUMBALATA", (MENU_WIDTH - layout.width) / 2f, TITLE_CENTER_Y + layout.height / 2f + titleBob);
            font.getData().setScale(1f);
        }

        if (startTexture != null) batch.draw(startTexture, buttonBounds[0].x, buttonBounds[0].y, BUTTON_W, BUTTON_H);
        else drawLabel(0);
        if (exitTexture != null) batch.draw(exitTexture, buttonBounds[1].x, buttonBounds[1].y, BUTTON_W, BUTTON_H);
        else drawLabel(1);

        Rectangle b = buttonBounds[selected];
        float bob = (MathUtils.sin(time * SLIPPER_BOB_SPEED) + 1f) / 2f * SLIPPER_BOB_AMOUNT;
        if (slipperTexture != null) {
            float sw = slipperTexture.getWidth();
            float sh = slipperTexture.getHeight();
            float sx = b.x + BUTTON_W + SLIPPER_GAP - bob;
            float sy = b.y + (BUTTON_H - sh) / 2f;
            batch.draw(slipperTexture, sx, sy, sw, sh, 0, 0,
                slipperTexture.getWidth(), slipperTexture.getHeight(), FLIP_SLIPPER, false);
        }

        batch.end();

        if (slipperTexture == null) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(Color.BROWN);
            shapeRenderer.ellipse(b.x + BUTTON_W + SLIPPER_GAP - bob, b.y + (BUTTON_H - 16f) / 2f, 40f, 16f);
            shapeRenderer.end();
        }
    }

    private void drawLabel(int index) {
        Rectangle r = buttonBounds[index];
        layout.setText(font, buttonLabels[index]);
        font.draw(batch, buttonLabels[index], r.x + (BUTTON_W - layout.width) / 2f, r.y + (BUTTON_H + layout.height) / 2f);
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F11)) game.toggleFullscreen();

        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) {
            selected = (selected + buttonBounds.length - 1) % buttonBounds.length;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            selected = (selected + 1) % buttonBounds.length;
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

        if (confirm || clicked) activate(selected);
    }

    private void activate(int index) {
        if (index == 0) {
            leaving = true;
            game.changeScreen(new PlayerSelectScreen(game), MENU_WINDOW_W, MENU_WINDOW_H);
        } else {
            Gdx.app.exit();
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        if (batch != null) { batch.dispose(); batch = null; }
        if (shapeRenderer != null) { shapeRenderer.dispose(); shapeRenderer = null; }
        if (font != null) { font.dispose(); font = null; }
        if (background != null) { background.dispose(); background = null; }
        if (startTexture != null) { startTexture.dispose(); startTexture = null; }
        if (exitTexture != null) { exitTexture.dispose(); exitTexture = null; }
        if (slipperTexture != null) { slipperTexture.dispose(); slipperTexture = null; }
        if (titleTexture != null) { titleTexture.dispose(); titleTexture = null; }
    }
}