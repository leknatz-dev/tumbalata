package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;

import java.nio.IntBuffer;

/**
 * Screen-change transition that works like a MASK WIPE.
 *
 * When a screen change starts, a snapshot of the old screen is taken and the NEW screen is switched in right away
 * (underneath). Your can sheet then sweeps across: the old screen (the snapshot) is only visible on the side the
 * can hasn't reached yet, and the new screen shows up behind it. So the screens are never swapped in one visible
 * jump, and slow loading (like the game map) happens while the snapshot is still covering everything.
 *
 * Flow (driven by TumbalataGame.render):
 *   1. the old screen draws its last frame
 *   2. captureSnapshot() + begin(...)  -> the game switches to the new screen
 *   3. every frame: the new screen draws, then update() + draw() paint the snapshot (masked) and the can on top
 */
public class ScreenTransition {
    // --- ASSET (rename to match your file in assets/) ---
    private static final String TRANSITION_FILE = "can_transition.png";   // 2112 x 540

    // --- HOW THE SHEET IS USED ---
    // FRAME_COUNT = 1 : the whole image is one long strip that slides across the screen.
    // FRAME_COUNT > 1 : the image is split into this many equal frames, side by side, and then
    //     MOVE_ACROSS = true  -> one frame at a time is drawn while it travels across the screen (cycling through the frames)
    //     MOVE_ACROSS = false -> the frames play in place over the whole window (the old screen disappears at the halfway point)
    private static final int FRAME_COUNT = 8;
    private static final boolean MOVE_ACROSS = true;
    private static final float FRAMES_PER_SECOND = 12f;   // only used when FRAME_COUNT > 1 and MOVE_ACROSS

    private static final float DURATION = 1.4f;           // total seconds for the whole sweep
    private static final boolean RIGHT_TO_LEFT = true;    // false = sweeps left to right
    private static final boolean FLIP_ART = false;        // mirror the picture (use this if the can faces the wrong way)

    // Where the old/new screen "seam" sits on the sheet, measured from the FRONT edge of the sheet
    // (0 = the very front, 0.5 = the middle, 1 = the back). Put it where your art is solid so the seam is hidden.
    private static final float SEAM_POSITION = 0.5f;

    // The art is scaled so its HEIGHT matches the window height. Your sheet is 540 tall = a 960x540 screen at 1:1.
    private static final float ART_HEIGHT = 540f;

    private final SpriteBatch batch = new SpriteBatch();
    private final OrthographicCamera cam = new OrthographicCamera();
    private final IntBuffer savedViewport = BufferUtils.newIntBuffer(16);

    private Texture texture;           // your sheet, or null (then a plain black wipe is used)
    private Texture blackPixel;        // fallback
    private TextureRegion[] frames;
    private float frameWidth;          // width of one frame after scaling to ART_HEIGHT

    private TextureRegion snapshot;    // the old screen's last frame
    private boolean active = false;
    private float time = 0f;
    private Runnable onFinished;

    public ScreenTransition() {
        if (Gdx.files.internal(TRANSITION_FILE).exists()) {
            texture = new Texture(Gdx.files.internal(TRANSITION_FILE));
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

            int count = Math.max(1, FRAME_COUNT);
            TextureRegion[][] split = TextureRegion.split(texture, texture.getWidth() / count, texture.getHeight());
            frames = new TextureRegion[count];
            for (int i = 0; i < count; i++) frames[i] = split[0][i];
            frameWidth = (texture.getWidth() / (float) count) * (ART_HEIGHT / texture.getHeight());
        } else {
            Gdx.app.error("Transition", "Missing asset: " + TRANSITION_FILE + " (using a plain black wipe)");
            Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pm.setColor(Color.BLACK);
            pm.fill();
            blackPixel = new Texture(pm);
            pm.dispose();
        }
    }

    public boolean isActive() {
        return active;
    }

    /** Copies what is on screen right now (the old screen's last frame). Call after the old screen has drawn. */
    public void captureSnapshot() {
        releaseSnapshot();
        snapshot = ScreenUtils.getFrameBufferTexture();
    }

    /** Starts the sweep. onFinished runs once when it ends (the game resizes the window there). */
    public void begin(Runnable onFinished) {
        this.onFinished = onFinished;
        this.active = true;
        this.time = 0f;
    }

    /** Advance the sweep. Call every frame. */
    public void update(float delta) {
        if (!active) return;

        time += Math.min(delta, 0.05f); // a slow frame (like loading the map) must not make the sweep jump
        if (time >= DURATION) {
            active = false;
            releaseSnapshot();
            Runnable done = onFinished;
            onFinished = null;
            if (done != null) done.run();
        }
    }

    /** Draw the sweep over everything. Call every frame AFTER the current screen has rendered. */
    public void draw() {
        if (!active || snapshot == null) return;

        float p = MathUtils.clamp(time / DURATION, 0f, 1f);
        int backW = Gdx.graphics.getBackBufferWidth();
        int backH = Gdx.graphics.getBackBufferHeight();
        float virtualW = ART_HEIGHT * backW / (float) backH;

        cam.setToOrtho(false, virtualW, ART_HEIGHT);
        cam.update();

        // The screens set a letterboxed GL viewport; draw over the WHOLE window, then put theirs back
        savedViewport.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, savedViewport);
        int vx = savedViewport.get(0), vy = savedViewport.get(1), vw = savedViewport.get(2), vh = savedViewport.get(3);
        Gdx.gl.glViewport(0, 0, backW, backH);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.setProjectionMatrix(cam.combined);
        batch.setColor(Color.WHITE);

        boolean playInPlace = texture != null && frames.length > 1 && !MOVE_ACROSS;

        if (playInPlace) {
            // frames play over the whole window; the old screen is shown until the halfway point
            batch.begin();
            if (p < 0.5f) batch.draw(snapshot, 0, 0, virtualW, ART_HEIGHT);
            int index = Math.min(frames.length - 1, (int) (p * frames.length));
            batch.draw(frames[index], 0, 0, virtualW, ART_HEIGHT);
            batch.end();
        } else {
            float stripW = (texture == null) ? virtualW * 1.5f : frameWidth;
            float x = RIGHT_TO_LEFT ? MathUtils.lerp(virtualW, -stripW, p) : MathUtils.lerp(-stripW, virtualW, p);

            // The seam travels with the sheet: the old screen stays only on the side the sheet hasn't reached yet
            float seamV = RIGHT_TO_LEFT ? x + SEAM_POSITION * stripW : x + stripW - SEAM_POSITION * stripW;
            int seamPx = (int) MathUtils.clamp(seamV / virtualW * backW, 0f, backW);
            // Moving right to left: the part the sheet hasn't reached (left of the seam) is still the OLD screen,
            // and the part it has already passed (right of the seam) is the NEW screen. Left to right is the mirror.
            int oldX = RIGHT_TO_LEFT ? 0 : seamPx;
            int oldW = RIGHT_TO_LEFT ? seamPx : backW - seamPx;

            if (oldW > 0) {
                Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
                Gdx.gl.glScissor(oldX, 0, oldW, backH);
                batch.begin();
                batch.draw(snapshot, 0, 0, virtualW, ART_HEIGHT);
                batch.end();
                Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            }

            // the sheet on top
            batch.begin();
            if (texture == null) {
                batch.draw(blackPixel, x, 0, stripW, ART_HEIGHT);
            } else {
                int index = (frames.length > 1) ? ((int) (time * FRAMES_PER_SECOND)) % frames.length : 0;
                if (FLIP_ART) {
                    batch.draw(frames[index], x + stripW, 0, -stripW, ART_HEIGHT);
                } else {
                    batch.draw(frames[index], x, 0, stripW, ART_HEIGHT);
                }
            }
            batch.end();
        }

        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glViewport(vx, vy, vw, vh);
    }

    private void releaseSnapshot() {
        if (snapshot != null) {
            snapshot.getTexture().dispose();
            snapshot = null;
        }
    }

    public void dispose() {
        releaseSnapshot();
        batch.dispose();
        if (texture != null) texture.dispose();
        if (blackPixel != null) blackPixel.dispose();
    }
}