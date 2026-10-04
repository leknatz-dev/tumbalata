package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
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

/** "Choose your character" screen: 4 placeholder cards, then starts the game. */
public class CharacterSelectScreen implements Screen {
    // --- SCREEN SIZE (same as the other menus) ---
    private static final float MENU_WIDTH = 700f;
    private static final float MENU_HEIGHT = 500f;
    private static final int MENU_WINDOW_W = 700;
    private static final int MENU_WINDOW_H = 500;
    private static final int GAME_WINDOW_W = 1280;
    private static final int GAME_WINDOW_H = 698;

    // --- ASSET FILES (optional - placeholders are drawn if missing) ---
    private static final String BACKGROUND_FILE = "menu_background.png";
    private static final String SLIPPER_FILE = "menu_slipper.png";
    private static final String HEADING_FILE = "select_character_title.png";

    // --- HEADING ---
    private static final float HEADING_CENTER_Y = 420f;
    private static final float HEADING_BOB_AMOUNT = 6f;
    private static final float HEADING_BOB_SPEED = 2.5f;

    // --- CARD LAYOUT: each card's BOTTOM-LEFT corner on the 700 x 500 screen (x right, y UP) ---
    private static final float CARD_W = 110f;
    private static final float CARD_H = 150f;
    private static final float CARD_GAP = 30f;
    private static final float CARDS_Y = 190f;
    private static final float CARDS_START_X = (MENU_WIDTH - (Characters.COUNT * CARD_W + (Characters.COUNT - 1) * CARD_GAP)) / 2f;

    // Slipper indicator sits under the selected card and bobs up and down
    private static final float SLIPPER_GAP = 10f;
    private static final float SLIPPER_BOB_AMOUNT = 5f;
    private static final float SLIPPER_BOB_SPEED = 6f;

    private final TumbalataGame game;
    private final int playerCount;

    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();

    private Texture background;
    private Texture slipperTexture;
    private Texture headingTexture;
    private final Texture[] portraits = new Texture[Characters.COUNT];

    private final Rectangle[] cardBounds = new Rectangle[Characters.COUNT];
    private int selected = 0;
    private float time = 0f;
    private boolean leaving = false;
    private final Vector2 mouse = new Vector2();
    private float lastMouseX = -1f, lastMouseY = -1f;

    public CharacterSelectScreen(TumbalataGame game, int playerCount) {
        this.game = game;
        this.playerCount = playerCount;
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
        slipperTexture = loadTexture(SLIPPER_FILE);
        headingTexture = loadTexture(HEADING_FILE);
        for (int i = 0; i < Characters.COUNT; i++) {
            portraits[i] = loadTexture(Characters.PORTRAIT_FILES[i]);
            cardBounds[i] = new Rectangle(CARDS_START_X + i * (CARD_W + CARD_GAP), CARDS_Y, CARD_W, CARD_H);
        }
    }

    /** Loads a texture, or returns null (silently - these are optional) if the file isn't there. */
    private Texture loadTexture(String file) {
        if (!Gdx.files.internal(file).exists()) {
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
        if (leaving) return; // handleInput may have switched screens and disposed this one

        ScreenUtils.clear(0.1f, 0.1f, 0.12f, 1f);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);

        Rectangle sel = cardBounds[selected];
        float bob = (MathUtils.sin(time * SLIPPER_BOB_SPEED) + 1f) / 2f * SLIPPER_BOB_AMOUNT;

        // --- Shapes: placeholder background, cards, selection border, placeholder slipper ---
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        if (background == null) {
            shapeRenderer.setColor(0.18f, 0.35f, 0.25f, 1f);
            shapeRenderer.rect(0, 0, MENU_WIDTH, MENU_HEIGHT);
        }
        for (int i = 0; i < Characters.COUNT; i++) {
            Rectangle r = cardBounds[i];
            if (i == selected) { // white border behind the selected card
                shapeRenderer.setColor(Color.WHITE);
                shapeRenderer.rect(r.x - 5f, r.y - 5f, r.width + 10f, r.height + 10f);
            }
            if (portraits[i] == null) {
                Color c = Characters.CARD_COLORS[i];
                float dim = (i == selected) ? 1f : 0.65f; // unselected cards are a bit darker
                shapeRenderer.setColor(c.r * dim, c.g * dim, c.b * dim, 1f);
                shapeRenderer.rect(r.x, r.y, r.width, r.height);
                // simple placeholder "head" so the card doesn't look empty
                shapeRenderer.setColor(0f, 0f, 0f, 0.25f);
                shapeRenderer.circle(r.x + r.width / 2f, r.y + r.height * 0.62f, 24f);
                shapeRenderer.rect(r.x + r.width / 2f - 30f, r.y + 12f, 60f, 48f);
            }
        }
        if (slipperTexture == null) {
            shapeRenderer.setColor(Color.BROWN);
            shapeRenderer.ellipse(sel.x + (CARD_W - 40f) / 2f, sel.y - SLIPPER_GAP - 16f - 5f + bob, 40f, 16f);
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // --- Sprites and text ---
        batch.begin();
        if (background != null) batch.draw(background, 0, 0, MENU_WIDTH, MENU_HEIGHT);

        float headingBob = MathUtils.sin(time * HEADING_BOB_SPEED) * HEADING_BOB_AMOUNT;
        if (headingTexture != null) {
            float hw = headingTexture.getWidth();
            float hh = headingTexture.getHeight();
            batch.draw(headingTexture, (MENU_WIDTH - hw) / 2f, HEADING_CENTER_Y - hh / 2f + headingBob, hw, hh);
        } else {
            font.getData().setScale(2f);
            layout.setText(font, "CHOOSE YOUR CHARACTER");
            font.draw(batch, "CHOOSE YOUR CHARACTER", (MENU_WIDTH - layout.width) / 2f,
                HEADING_CENTER_Y + layout.height / 2f + headingBob);
            font.getData().setScale(1f);
        }

        for (int i = 0; i < Characters.COUNT; i++) {
            Rectangle r = cardBounds[i];
            if (portraits[i] != null) {
                batch.setColor(i == selected ? Color.WHITE : new Color(0.65f, 0.65f, 0.65f, 1f));
                batch.draw(portraits[i], r.x, r.y, r.width, r.height);
                batch.setColor(Color.WHITE);
            }
            layout.setText(font, Characters.NAMES[i]);
            font.draw(batch, Characters.NAMES[i], r.x + (r.width - layout.width) / 2f, r.y + 22f);
        }

        if (slipperTexture != null) {
            float sw = slipperTexture.getWidth();
            float sh = slipperTexture.getHeight();
            batch.draw(slipperTexture, sel.x + (CARD_W - sw) / 2f, sel.y - SLIPPER_GAP - 5f - sh + bob, sw, sh);
        }
        batch.end();
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F11)) game.toggleFullscreen();

        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)) {
            selected = (selected + cardBounds.length - 1) % cardBounds.length;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)) {
            selected = (selected + 1) % cardBounds.length;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            leaving = true;
            game.changeScreen(new PlayerSelectScreen(game), MENU_WINDOW_W, MENU_WINDOW_H);
            return;
        }

        mouse.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(mouse);
        boolean mouseMoved = Gdx.input.getX() != lastMouseX || Gdx.input.getY() != lastMouseY;
        lastMouseX = Gdx.input.getX();
        lastMouseY = Gdx.input.getY();

        int hovered = -1;
        for (int i = 0; i < cardBounds.length; i++) {
            if (cardBounds[i].contains(mouse.x, mouse.y)) hovered = i;
        }
        if (hovered != -1 && mouseMoved) selected = hovered;

        boolean confirm = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            || Gdx.input.isKeyJustPressed(Input.Keys.E);
        boolean clicked = Gdx.input.justTouched() && hovered != -1;
        if (clicked) selected = hovered;

        if (confirm || clicked) startGame();
    }

    private void startGame() {
        leaving = true;
        game.changeScreen(new GameScreen(playerCount, selected), GAME_WINDOW_W, GAME_WINDOW_H);
    }

    @Override
    public void resize(int width, int height) {
        if (viewport != null) viewport.update(width, height, true);
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
        if (slipperTexture != null) { slipperTexture.dispose(); slipperTexture = null; }
        if (headingTexture != null) { headingTexture.dispose(); headingTexture = null; }
        for (int i = 0; i < portraits.length; i++) {
            if (portraits[i] != null) { portraits[i].dispose(); portraits[i] = null; }
        }
    }
}