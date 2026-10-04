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
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Victory screen (placeholder art): a podium where the highest score stands in the MIDDLE and is the biggest.
 * Order left to right: 2nd, 1st, 3rd, (4th).
 */
public class VictoryScreen implements Screen {
    // --- SCREEN SIZE (same as the other menus) ---
    private static final float MENU_WIDTH = 700f;
    private static final float MENU_HEIGHT = 500f;
    private static final int MENU_WINDOW_W = 700;
    private static final int MENU_WINDOW_H = 500;

    // --- ASSET FILES (all optional - placeholders are drawn if missing) ---
    private static final String BACKGROUND_FILE = "victory_background.png";
    private static final String FALLBACK_BACKGROUND_FILE = "menu_background.png";
    private static final String TITLE_FILE = "victory_title.png";

    // --- PODIUM LAYOUT (index = rank: 0 = 1st place) ---
    private static final float BASE_Y = 70f;                                          // ground line of the podium
    private static final float[] SLOT_CENTER_X = { 350f, 190f, 500f, 610f };          // 1st is in the exact middle
    private static final float[] BLOCK_W       = { 120f,  90f,  90f,  70f };          // 1st is the widest...
    private static final float[] BLOCK_H       = { 200f, 140f, 100f,  70f };          // ...and the tallest
    private static final float[] FIGURE_SCALE  = { 1.5f, 1.15f, 1.0f, 0.85f };        // ...with the biggest character
    private static final String[] RANK_LABELS  = { "1ST", "2ND", "3RD", "4TH" };

    // --- ANIMATION ---
    private static final float RISE_TIME = 0.6f;       // seconds for a podium block to rise
    private static final float RISE_STAGGER = 0.7f;    // delay between ranks (lowest rank rises first, winner last)
    private static final float POP_TIME = 0.3f;        // character "pop in" after its block has risen
    private static final float INPUT_DELAY = 1.0f;     // ignore keys at first so a button mash in the game can't skip this

    private static final float TITLE_CENTER_Y = 440f;
    private static final float TITLE_BOB_AMOUNT = 7f;
    private static final float TITLE_BOB_SPEED = 2.5f;

    private final TumbalataGame game;
    private final int playerCount;
    private final int[] scores;
    private final int[] order;        // order[rank] = player index (rank 0 = highest score)
    private final Color[] colors;     // placeholder color per player

    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();

    private Texture background;
    private Texture titleTexture;

    private float time = 0f;
    private boolean leaving = false;

    /**
     * @param scores         one score per player (index 0 = Player 1)
     * @param characterIndex the character Player 1 picked (used for its placeholder color)
     */
    public VictoryScreen(TumbalataGame game, int playerCount, int[] scores, int characterIndex) {
        this.game = game;
        this.playerCount = MathUtils.clamp(playerCount, 1, 4);
        this.scores = new int[this.playerCount];
        for (int i = 0; i < this.playerCount; i++) {
            this.scores[i] = (scores != null && i < scores.length) ? scores[i] : 0;
        }

        // Rank players by score, highest first (ties keep the lower player number first)
        order = new int[this.playerCount];
        for (int i = 0; i < this.playerCount; i++) order[i] = i;
        for (int i = 0; i < this.playerCount; i++) {
            for (int j = 0; j < this.playerCount - 1 - i; j++) {
                if (this.scores[order[j]] < this.scores[order[j + 1]]) {
                    int tmp = order[j];
                    order[j] = order[j + 1];
                    order[j + 1] = tmp;
                }
            }
        }

        // Player 1 uses the chosen character's color, the others take the remaining colors in order
        colors = new Color[this.playerCount];
        int pick = MathUtils.clamp(characterIndex, 0, Characters.COUNT - 1);
        colors[0] = Characters.CARD_COLORS[pick];
        int next = 0;
        for (int p = 1; p < this.playerCount; p++) {
            if (next == pick) next++;
            colors[p] = Characters.CARD_COLORS[next % Characters.COUNT];
            next++;
        }
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
        if (background == null) background = loadTexture(FALLBACK_BACKGROUND_FILE);
        titleTexture = loadTexture(TITLE_FILE);
    }

    /** Loads a texture, or returns null (silently - these are optional) if the file isn't there. */
    private Texture loadTexture(String file) {
        if (!Gdx.files.internal(file).exists()) return null;
        Texture t = new Texture(Gdx.files.internal(file));
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }

    private float riseDelay(int rank) {
        return (playerCount - 1 - rank) * RISE_STAGGER;      // lowest rank first, winner last
    }

    private float riseProgress(int rank) {
        float p = MathUtils.clamp((time - riseDelay(rank)) / RISE_TIME, 0f, 1f);
        return 1f - (1f - p) * (1f - p) * (1f - p);          // ease-out
    }

    private float popProgress(int rank) {
        return MathUtils.clamp((time - riseDelay(rank) - RISE_TIME) / POP_TIME, 0f, 1f);
    }

    private float revealEndTime() {
        return riseDelay(0) + RISE_TIME + POP_TIME;
    }

    private Color blockColor(int rank) {
        switch (rank) {
            case 0:  return new Color(0.95f, 0.78f, 0.20f, 1f); // gold
            case 1:  return new Color(0.75f, 0.77f, 0.80f, 1f); // silver
            case 2:  return new Color(0.80f, 0.50f, 0.25f, 1f); // bronze
            default: return new Color(0.40f, 0.42f, 0.46f, 1f); // dark gray
        }
    }

    @Override
    public void render(float delta) {
        if (leaving) return;
        time += delta;
        handleInput();
        if (leaving) return; // handleInput may have switched screens and disposed this one

        ScreenUtils.clear(0.10f, 0.12f, 0.20f, 1f);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);

        // 1. Background image (if any)
        if (background != null) {
            batch.begin();
            batch.draw(background, 0, 0, MENU_WIDTH, MENU_HEIGHT);
            batch.end();
        }

        // 2. Podium blocks, shadows and placeholder characters
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        if (background == null) { // simple placeholder floor
            shapeRenderer.setColor(0.16f, 0.18f, 0.28f, 1f);
            shapeRenderer.rect(0, 0, MENU_WIDTH, BASE_Y);
        }

        for (int rank = 0; rank < playerCount; rank++) {
            float cx = SLOT_CENTER_X[rank];
            float w = BLOCK_W[rank];
            float h = BLOCK_H[rank] * riseProgress(rank);

            Color bc = blockColor(rank);
            shapeRenderer.setColor(bc);
            shapeRenderer.rect(cx - w / 2f, BASE_Y, w, h);
            shapeRenderer.setColor(Math.min(1f, bc.r + 0.15f), Math.min(1f, bc.g + 0.15f), Math.min(1f, bc.b + 0.15f), 1f);
            shapeRenderer.rect(cx - w / 2f, BASE_Y + h - 6f, w, 6f);   // lighter top edge

            float pop = popProgress(rank);
            if (pop > 0f) {
                float s = FIGURE_SCALE[rank] * pop;
                float footY = BASE_Y + BLOCK_H[rank] + ((rank == 0) ? MathUtils.sin(time * 4f) * 3f + 3f : 0f);

                // shadow on the podium top
                shapeRenderer.setColor(0f, 0f, 0f, 0.30f);
                shapeRenderer.ellipse(cx - 18f * s, BASE_Y + BLOCK_H[rank] - 4f * s, 36f * s, 8f * s);

                // placeholder character: body + head in the player's color
                Color pc = colors[order[rank]];
                shapeRenderer.setColor(pc);
                shapeRenderer.rect(cx - 14f * s, footY, 28f * s, 38f * s);
                shapeRenderer.circle(cx, footY + 50f * s, 12f * s);
                shapeRenderer.setColor(0f, 0f, 0f, 0.25f);
                shapeRenderer.rect(cx - 14f * s, footY, 28f * s, 6f * s);   // darker "feet"

                if (rank == 0) { // little crown for the winner
                    shapeRenderer.setColor(1f, 0.85f, 0.2f, 1f);
                    float crownY = footY + 62f * s;
                    shapeRenderer.rect(cx - 11f * s, crownY, 22f * s, 6f * s);
                    shapeRenderer.triangle(cx - 11f * s, crownY + 6f * s, cx - 6f * s, crownY + 6f * s, cx - 11f * s, crownY + 15f * s);
                    shapeRenderer.triangle(cx - 4f * s, crownY + 6f * s, cx + 4f * s, crownY + 6f * s, cx, crownY + 16f * s);
                    shapeRenderer.triangle(cx + 6f * s, crownY + 6f * s, cx + 11f * s, crownY + 6f * s, cx + 11f * s, crownY + 15f * s);
                }
            }
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // 3. Text
        batch.begin();

        float titleBob = MathUtils.sin(time * TITLE_BOB_SPEED) * TITLE_BOB_AMOUNT;
        if (titleTexture != null) {
            float tw = titleTexture.getWidth();
            float th = titleTexture.getHeight();
            batch.draw(titleTexture, (MENU_WIDTH - tw) / 2f, TITLE_CENTER_Y - th / 2f + titleBob, tw, th);
        } else {
            drawCentered("VICTORY!", MENU_WIDTH / 2f, TITLE_CENTER_Y + 14f + titleBob, 3f, Color.GOLD);
        }

        for (int rank = 0; rank < playerCount; rank++) {
            float cx = SLOT_CENTER_X[rank];
            float riseP = riseProgress(rank);
            float blockTopY = BASE_Y + BLOCK_H[rank] * riseP;

            // rank label inside the block
            if (riseP > 0.6f) {
                drawCentered(RANK_LABELS[rank], cx, blockTopY - 14f, rank == 0 ? 1.8f : 1.3f, Color.DARK_GRAY);
            }

            // name and score above the character
            if (popProgress(rank) >= 1f) {
                float s = FIGURE_SCALE[rank];
                float aboveY = BASE_Y + BLOCK_H[rank] + 80f * s + (rank == 0 ? 22f : 8f);
                int player = order[rank];
                drawCentered("P" + (player + 1), cx, aboveY + 18f, rank == 0 ? 1.8f : 1.2f, Color.WHITE);
                drawCentered(scores[player] + " pts", cx, aboveY, rank == 0 ? 1.3f : 1f, Color.LIGHT_GRAY);
            }
        }

        if (time >= revealEndTime()) {
            drawCentered("PLAYER " + (order[0] + 1) + " WINS!", MENU_WIDTH / 2f, 395f, 1.8f, Color.WHITE);
            if (((int) (time * 2f)) % 2 == 0) { // blinking hint
                drawCentered("PRESS ENTER TO CONTINUE", MENU_WIDTH / 2f, 40f, 1.1f, Color.LIGHT_GRAY);
            }
        }

        batch.end();
    }

    private void drawCentered(String text, float cx, float y, float scale, Color color) {
        font.getData().setScale(scale);
        font.setColor(color);
        layout.setText(font, text);
        font.draw(batch, text, cx - layout.width / 2f, y);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F11)) game.toggleFullscreen();

        if (time < INPUT_DELAY) return;

        boolean confirm = Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
            || Gdx.input.isKeyJustPressed(Input.Keys.E)
            || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
            || Gdx.input.justTouched();
        if (confirm) {
            leaving = true;
            game.changeScreen(new MainMenuScreen(game), MENU_WINDOW_W, MENU_WINDOW_H);
        }
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
        if (titleTexture != null) { titleTexture.dispose(); titleTexture = null; }
    }
}