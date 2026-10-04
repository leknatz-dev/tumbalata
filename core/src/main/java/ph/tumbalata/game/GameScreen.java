package ph.tumbalata.game;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapGroupLayer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Shape2D;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class GameScreen implements Screen {
    public static final float WORLD_WIDTH = 1280f;
    public static final float WORLD_HEIGHT = 704f;

    private static final float VIEW_X = 0f;
    private static final float VIEW_Y = -64f;
    private static final float VIEW_W = 1408f;
    private static final float VIEW_H = 768f;

    private static final boolean SHOW_THROW_LINE = true;

    private static final String MAP_FILE = "MAPCOLLISION.tmx";
    private static final String PLAYER_COLLISION_LAYER = "collision1";
    private static final String CAN_COLLISION_LAYER = "collision2";
    private static final String UPPER_RING_FILE = "upperring.png";

    private static final float RING_X = 0f;
    private static final float RING_Y = -64f;

    private static final float PLAYER_HITBOX_W = 14f;
    private static final float PLAYER_HITBOX_H = 10f;
    private static final float PLAYER_HITBOX_OFFSET_Y = 0f;

    private static final float CAN_MIN_THROW_DISTANCE = 40f;
    private static final float CAN_MAX_THROW_DISTANCE = 500f;
    private static final float CAN_POWER_CURVE = 1.5f;

    private static final float CAN_RELEASE_HEIGHT = 30f;
    private static final float CAN_HITBOX_W = 16f;
    private static final float CAN_HITBOX_H = 16f;
    private static final float CAN_HITBOX_OFFSET_X = 0f;
    private static final float CAN_HITBOX_OFFSET_Y = 0f;
    private static final float CAN_WALL_BOUNCE = 0.5f;
    private static final boolean CAN_BLOCKED_BY_PLAYER_WALLS = true;
    private static final float CAN_WALL_MAX_HEIGHT = 10000f;

    private static final float SLIPPER_HITBOX_W = 12f;
    private static final float SLIPPER_HITBOX_H = 12f;
    private static final float SLIPPER_WALL_BOUNCE = 0.6f;

    private static final float OUTER_MIN_X = VIEW_X - 256f;
    private static final float OUTER_MIN_Y = VIEW_Y - 256f;
    private static final float OUTER_MAX_X = VIEW_X + VIEW_W + 256f;
    private static final float OUTER_MAX_Y = VIEW_Y + VIEW_H;

    private Viewport viewport;
    private OrthographicCamera camera;

    private ShapeRenderer shapeRenderer;
    private SpriteBatch spriteBatch;
    private BitmapFont font;

    private TiledMap map;
    private OrthogonalTiledMapRenderer mapRenderer;
    private Texture upperRingTexture;

    private Texture playerSheet;
    private Texture playerSlipperSheet;
    private Texture playerCanSheet;
    private Texture canSheet;

    private final Array<Shape2D> playerWalls = new Array<>();
    private final Array<Shape2D> canWalls = new Array<>();
    private final Array<Shape2D> canBlockers = new Array<>();

    private final Rectangle hitboxRect = new Rectangle();
    private final float[] hitboxVerts = new float[8];
    private final Polygon hitboxPoly = new Polygon(new float[8]);

    private final Vector2 prevThrowerPos = new Vector2();
    private final Vector2 prevTayaPos = new Vector2();
    private boolean debugCollision = false;
    private int debugView = 0;
    private float ringX = RING_X;
    private float ringY = RING_Y;

    private int playerCount = 3;
    private int characterIndex = 0; // which placeholder character Player 1 picked

    // --- MATCH TIMER (5 seconds for testing the victory screen; change MATCH_TIME_SECONDS for a real match) ---
    private static final float MATCH_TIME_SECONDS = 5f;
    private static final int VICTORY_WINDOW_W = 700;
    private static final int VICTORY_WINDOW_H = 500;
    private float matchTimeLeft = MATCH_TIME_SECONDS;
    private boolean matchOver = false;

    // Real scoring isn't built yet. Final scores per player (index 0 = Player 1); fill this in once scoring exists.
    private final int[] scores = new int[4];
    // While true, random scores are used when the timer ends so the victory screen has something to show.
    // Set to false once real scoring is in.
    private boolean fakeScoresForTesting = true;

    private enum GamePhase {
        THROWER_ROAMING,
        THROWER_SELECTING_ANGLE,
        THROWER_SELECTING_POWER,
        SLIPPER_FLYING,
        CAN_HIT_SCRAMBLE,
        TAYA_WAITING_PICKUP,
        TAYA_SELECTING_ANGLE,
        TAYA_SELECTING_POWER,
        TAYA_CAN_FLYING,
        RETRIEVAL_PHASE
    }
    private GamePhase currentPhase = GamePhase.THROWER_ROAMING;

    private float throwLineX, screenWidth, screenHeight;
    private Vector2 canBasePosition;
    private Player thrower;
    private Player taya;
    private Can can;
    private Slipper slipper;

    private float angleTimer = 0f, currentAngle = 0f;
    private float powerTimer = 0f, currentPower = 0f;

    private Vector2 canLandingSpot = new Vector2();
    private boolean isImpactPending = false;
    private float impactDelayTimer = 0f;

    public GameScreen() {
        this(3, 0);
    }

    public GameScreen(int playerCount) {
        this(playerCount, 0);
    }

    public GameScreen(int playerCount, int characterIndex) {
        this.playerCount = playerCount;
        this.characterIndex = MathUtils.clamp(characterIndex, 0, Characters.COUNT - 1);
    }

    @Override
    public void show() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(VIEW_W, VIEW_H, camera);
        
        // Sync viewport and camera with current resolution state when screen becomes active
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        camera.position.set(VIEW_X + VIEW_W / 2f, VIEW_Y + VIEW_H / 2f, 0);
        camera.update();

        shapeRenderer = new ShapeRenderer();
        spriteBatch = new SpriteBatch();
        font = new BitmapFont();
        font.setColor(Color.WHITE);
        font.getData().setScale(1.2f);

        screenWidth = WORLD_WIDTH;
        screenHeight = WORLD_HEIGHT;
        throwLineX = screenWidth * 0.25f;
        canBasePosition = new Vector2(screenWidth * 0.8f, screenHeight * 0.5f);

        map = new TmxMapLoader().load(MAP_FILE);
        mapRenderer = new OrthogonalTiledMapRenderer(map, 1f, spriteBatch);

        loadCollisionLayer(PLAYER_COLLISION_LAYER, playerWalls);
        loadCollisionLayer(CAN_COLLISION_LAYER, canWalls);

        canBlockers.addAll(canWalls);
        if (CAN_BLOCKED_BY_PLAYER_WALLS) canBlockers.addAll(playerWalls);

        upperRingTexture = new Texture(Gdx.files.internal(UPPER_RING_FILE));
        upperRingTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        playerSheet = new Texture(Gdx.files.internal("16x16 Walk-Sheet.png"));
        playerSheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        playerSlipperSheet = new Texture(Gdx.files.internal("16x16 Walkwithslipper.png"));
        playerSlipperSheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        playerCanSheet = new Texture(Gdx.files.internal("Walkwithcan.png"));
        playerCanSheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        canSheet = new Texture(Gdx.files.internal("can_spin_sheet.png"));
        canSheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        can = new Can(canBasePosition.x, canBasePosition.y, canSheet, 4, 1, 0.08f);
        slipper = new Slipper(throwLineX - 50, canBasePosition.y);

        thrower = new Player(
            throwLineX - 50f,
            canBasePosition.y,
            GameConstants.PLAYER_SPEED,
            Input.Keys.W, Input.Keys.S, Input.Keys.A, Input.Keys.D,
            20f, throwLineX - 20f, 20f, screenHeight - 20f,
            playerSheet, playerSlipperSheet, playerCanSheet
        );
        thrower.hasSlipper = false;

        taya = new Player(
            canBasePosition.x + 80f,
            canBasePosition.y,
            GameConstants.TAYA_SPEED,
            Input.Keys.UP, Input.Keys.DOWN, Input.Keys.LEFT, Input.Keys.RIGHT,
            20f, screenWidth - 20f, 20f, screenHeight - 20f,
            playerSheet, playerSlipperSheet, playerCanSheet
        );

        // Player 1 (WASD) wears the chosen character's placeholder color
        thrower.tint.set(Characters.TINTS[characterIndex]);
    }

    private void loadCollisionLayer(String layerName, Array<Shape2D> out) {
        collectCollision(map.getLayers(), layerName, false, out);
    }

    private void collectCollision(MapLayers layers, String layerName, boolean insideMatch, Array<Shape2D> out) {
        for (MapLayer layer : layers) {
            boolean matches = insideMatch || layerName.equalsIgnoreCase(layer.getName());
            if (layer instanceof MapGroupLayer) {
                collectCollision(((MapGroupLayer) layer).getLayers(), layerName, matches, out);
            } else if (matches) {
                for (MapObject obj : layer.getObjects()) {
                    addCollisionObject(layerName, obj, out);
                }
            }
        }
    }

    private void addCollisionObject(String layerName, MapObject obj, Array<Shape2D> out) {
        if (obj instanceof RectangleMapObject) {
            Rectangle r = ((RectangleMapObject) obj).getRectangle();
            float rotation = obj.getProperties().get("rotation", 0f, Float.class);
            if (Math.abs(rotation) < 0.01f) {
                out.add(r);
            } else {
                float px = r.x, py = r.y + r.height;
                float rad = -rotation * MathUtils.degreesToRadians;
                float cos = MathUtils.cos(rad), sin = MathUtils.sin(rad);
                float[] local = { 0, -r.height, r.width, -r.height, r.width, 0, 0, 0 };
                float[] v = new float[8];
                for (int i = 0; i < 4; i++) {
                    float lx = local[i * 2], ly = local[i * 2 + 1];
                    v[i * 2] = px + lx * cos - ly * sin;
                    v[i * 2 + 1] = py + lx * sin + ly * cos;
                }
                out.add(new Polygon(v));
            }
        } else if (obj instanceof PolygonMapObject) {
            out.add(((PolygonMapObject) obj).getPolygon());
        }
    }

    private boolean isBlocked(Array<Shape2D> walls, float cx, float cy, float w, float h) {
        float left = cx - w / 2f;
        float bottom = cy - h / 2f;
        float right = left + w;
        float top = bottom + h;

        hitboxRect.set(left, bottom, w, h);
        boolean polyBuilt = false;

        for (int i = 0; i < walls.size; i++) {
            Shape2D wall = walls.get(i);
            if (wall instanceof Rectangle) {
                if (((Rectangle) wall).overlaps(hitboxRect)) return true;
            } else if (wall instanceof Polygon) {
                if (!polyBuilt) {
                    hitboxVerts[0] = left;  hitboxVerts[1] = bottom;
                    hitboxVerts[2] = right; hitboxVerts[3] = bottom;
                    hitboxVerts[4] = right; hitboxVerts[5] = top;
                    hitboxPoly.setVertices(hitboxVerts);
                    polyBuilt = true;
                }
                if (Intersector.overlapConvexPolygons(hitboxPoly, (Polygon) wall)) return true;
            }
        }
        return false;
    }

    private boolean isPlayerBlocked(float x, float y) {
        return isBlocked(playerWalls, x, y + PLAYER_HITBOX_OFFSET_Y, PLAYER_HITBOX_W, PLAYER_HITBOX_H);
    }

    private void savePreviousPositions() {
        prevThrowerPos.set(thrower.position);
        prevTayaPos.set(taya.position);
    }

    private void resolvePlayerCollision(Player p, Vector2 prev) {
        float nx = p.position.x;
        float ny = p.position.y;

        if (!isPlayerBlocked(nx, ny)) return;
        if (isPlayerBlocked(prev.x, prev.y)) return;

        if (!isPlayerBlocked(nx, prev.y)) {
            p.position.set(nx, prev.y);
        } else if (!isPlayerBlocked(prev.x, ny)) {
            p.position.set(prev.x, ny);
        } else {
            p.position.set(prev.x, prev.y);
        }
    }

    private void sweepAgainstWalls(Vector2 pos, Vector2 vel, float oldX, float oldY, Array<Shape2D> walls,
                                   float w, float h, float offX, float offY, float bounce) {
        float nx = pos.x, ny = pos.y;
        if (nx == oldX && ny == oldY) return;

        float startX = oldX, startY = oldY;
        if (isBlocked(walls, startX + offX, startY + offY, w, h)) {
            boolean found = false;
            for (float r = 1f; r <= 24f && !found; r += 1f) {
                for (int i = 0; i < 16; i++) {
                    float a = i * MathUtils.PI2 / 16f;
                    float cx = oldX + MathUtils.cos(a) * r;
                    float cy = oldY + MathUtils.sin(a) * r;
                    if (!isBlocked(walls, cx + offX, cy + offY, w, h)) {
                        startX = cx;
                        startY = cy;
                        found = true;
                        break;
                    }
                }
            }
            if (!found) return;
        }

        float dx = nx - startX, dy = ny - startY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist == 0f) {
            pos.set(startX, startY);
            return;
        }

        int steps = Math.max(1, MathUtils.ceil(dist / 4f));
        float lastX = startX, lastY = startY;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float cx = startX + dx * t;
            float cy = startY + dy * t;
            if (isBlocked(walls, cx + offX, cy + offY, w, h)) {
                boolean xBlocked = isBlocked(walls, cx + offX, lastY + offY, w, h);
                boolean yBlocked = isBlocked(walls, lastX + offX, cy + offY, w, h);
                if (!xBlocked && !yBlocked) {
                    xBlocked = true;
                    yBlocked = true;
                }
                pos.set(lastX, lastY);
                if (xBlocked) vel.x = -vel.x * bounce;
                if (yBlocked) vel.y = -vel.y * bounce;
                return;
            }
            lastX = cx;
            lastY = cy;
        }
        pos.set(nx, ny);
    }

    private void updateCan(float delta) {
        float oldX = can.position.x;
        float oldY = can.position.y;

        can.update(delta, OUTER_MIN_X, OUTER_MIN_Y, OUTER_MAX_X, OUTER_MAX_Y);

        if (taya.hasCan || can.zPosition > CAN_WALL_MAX_HEIGHT) return;

        sweepAgainstWalls(can.position, can.velocity, oldX, oldY, canBlockers,
            CAN_HITBOX_W, CAN_HITBOX_H, CAN_HITBOX_OFFSET_X, CAN_HITBOX_OFFSET_Y, CAN_WALL_BOUNCE);
    }

    private void updateSlipper(float delta) {
        float oldX = slipper.position.x;
        float oldY = slipper.position.y;

        slipper.update(delta, OUTER_MIN_X, OUTER_MIN_Y, OUTER_MAX_X, OUTER_MAX_Y);

        sweepAgainstWalls(slipper.position, slipper.velocity, oldX, oldY, playerWalls,
            SLIPPER_HITBOX_W, SLIPPER_HITBOX_H, 0f, 0f, SLIPPER_WALL_BOUNCE);
    }

    @Override
    public void render(float delta) {
        // Enforce viewport apply to prevent scaling loss across screen switches
        viewport.apply();

        if (matchOver) return;
        updateMatchTimer(delta);
        if (matchOver) return; // the timer just ended and the victory screen took over - stop drawing this screen

        savePreviousPositions();
        handleInput(delta);
        update(delta);

        ScreenUtils.clear(0f, 0f, 0f, 1f);

        camera.update();
        shapeRenderer.setProjectionMatrix(camera.combined);
        spriteBatch.setProjectionMatrix(camera.combined);

        mapRenderer.setView(camera);
        mapRenderer.render();

        // Blending on so the shadows are see-through
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.WHITE);
        if (SHOW_THROW_LINE) {
            shapeRenderer.rectLine(throwLineX, VIEW_Y, throwLineX, VIEW_Y + VIEW_H, 4);
        }

        shapeRenderer.setColor(Color.LIGHT_GRAY);
        shapeRenderer.circle(canBasePosition.x, canBasePosition.y, 16f);

        if (taya.hasCan) {
            can.position.set(taya.position.x, taya.position.y + 15);
        }

        can.renderShadow(shapeRenderer);
        thrower.renderShadow(shapeRenderer);
        taya.renderShadow(shapeRenderer);

        if (!thrower.hasSlipper) {
            slipper.render(shapeRenderer);
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        spriteBatch.begin();

        thrower.render(spriteBatch);
        taya.render(spriteBatch);

        if (taya.hasCan) {
            TextureRegion canFrame = can.getCurrentFrame();
            float canWidth = can.getWidth();
            float canHeight = can.getHeight();

            float canX = taya.position.x - (canWidth / 2f);
            float canY = taya.position.y + 18f;

            spriteBatch.draw(
                canFrame,
                canX, canY,
                canWidth / 2f, canHeight / 2f,
                canWidth, canHeight,
                1f, 1f,
                90f
            );
        } else {
            can.render(spriteBatch);
        }

        // Upper ring overlay, drawn after the players so they walk behind it
        spriteBatch.draw(upperRingTexture, ringX, ringY);

        if (!thrower.hasSlipper && Vector2.dst(thrower.position.x, thrower.position.y, slipper.position.x, slipper.position.y) < 45f) {
            font.draw(spriteBatch, "[E] Pick Up Slipper", slipper.position.x - 45f, slipper.position.y + 25f);
        }

        if ((currentPhase == GamePhase.TAYA_WAITING_PICKUP || currentPhase == GamePhase.CAN_HIT_SCRAMBLE) && !taya.hasCan) {
            if (Vector2.dst(taya.position.x, taya.position.y, can.position.x, can.position.y) < 35f) {
                font.draw(spriteBatch, "[E] Pick Up Can", can.position.x - 35f, can.position.y + 30f);
            }
        }

        if ((currentPhase == GamePhase.RETRIEVAL_PHASE || currentPhase == GamePhase.CAN_HIT_SCRAMBLE) && taya.hasCan) {
            if (Vector2.dst(taya.position.x, taya.position.y, canBasePosition.x, canBasePosition.y) < 35f) {
                font.draw(spriteBatch, "[E] Place Can at Base", canBasePosition.x - 45f, canBasePosition.y + 35f);
            }
        }

        // Timer HUD (top center)
        float timerLeft = Math.max(0f, matchTimeLeft);
        int totalSeconds = MathUtils.ceil(timerLeft);
        String timerText = (totalSeconds / 60) + ":" + String.format("%02d", totalSeconds % 60);
        font.getData().setScale(2.2f);
        font.setColor(timerLeft <= 10f ? Color.SCARLET : Color.WHITE);
        font.draw(spriteBatch, timerText, VIEW_X + VIEW_W / 2f - 100f, VIEW_Y + VIEW_H - 14f, 200f, Align.center, false);
        font.getData().setScale(1.2f);
        font.setColor(Color.WHITE);

        spriteBatch.end();

        renderUIOverlays();

        if (debugCollision) {
            renderDebugCollision();
        }
    }

    private void updateMatchTimer(float delta) {
        matchTimeLeft -= delta;
        if (matchTimeLeft <= 0f) {
            endMatch();
        }
    }

    private void endMatch() {
        matchOver = true;

        int[] finalScores = new int[playerCount];
        for (int i = 0; i < playerCount; i++) {
            if (fakeScoresForTesting) {
                finalScores[i] = MathUtils.random(0, 20);
            } else {
                finalScores[i] = (i < scores.length) ? scores[i] : 0;
            }
        }

        if (Gdx.app.getApplicationListener() instanceof TumbalataGame) {
            TumbalataGame tumbalata = (TumbalataGame) Gdx.app.getApplicationListener();
            tumbalata.changeScreen(new VictoryScreen(tumbalata, playerCount, finalScores, characterIndex),
                VICTORY_WINDOW_W, VICTORY_WINDOW_H);
        }
    }

    private void handleInput(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F11)) {
            if (Gdx.app.getApplicationListener() instanceof TumbalataGame) {
                ((TumbalataGame) Gdx.app.getApplicationListener()).toggleFullscreen();
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) debugCollision = !debugCollision;
        if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) debugView = (debugView + 1) % 3;

        if (debugCollision) {
            float step = (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) ? 10f : 1f;
            if (Gdx.input.isKeyJustPressed(Input.Keys.I)) ringY += step;
            if (Gdx.input.isKeyJustPressed(Input.Keys.K)) ringY -= step;
            if (Gdx.input.isKeyJustPressed(Input.Keys.J)) ringX -= step;
            if (Gdx.input.isKeyJustPressed(Input.Keys.L)) ringX += step;
        }

        if (currentPhase == GamePhase.THROWER_ROAMING || currentPhase == GamePhase.CAN_HIT_SCRAMBLE || currentPhase == GamePhase.RETRIEVAL_PHASE) {
            thrower.handleInput(delta);
            if (Gdx.input.isKeyJustPressed(Input.Keys.E) && !thrower.hasSlipper) {
                if (Vector2.dst(thrower.position.x, thrower.position.y, slipper.position.x, slipper.position.y) < 45f) {
                    thrower.hasSlipper = true;
                }
            }
        }

        taya.handleInput(delta);

        if (currentPhase == GamePhase.TAYA_WAITING_PICKUP || currentPhase == GamePhase.CAN_HIT_SCRAMBLE) {
            if (!taya.hasCan && Gdx.input.isKeyJustPressed(Input.Keys.E)) {
                if (Vector2.dst(taya.position.x, taya.position.y, can.position.x, can.position.y) < 35f) {
                    taya.hasCan = true;
                }
            }
        }

        if ((currentPhase == GamePhase.CAN_HIT_SCRAMBLE || currentPhase == GamePhase.RETRIEVAL_PHASE) && taya.hasCan) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
                if (Vector2.dst(taya.position.x, taya.position.y, canBasePosition.x, canBasePosition.y) < 35f) {
                    taya.hasCan = false;
                    can.reset(canBasePosition.x, canBasePosition.y);
                }
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (currentPhase == GamePhase.THROWER_ROAMING && thrower.hasSlipper) {
                currentPhase = GamePhase.THROWER_SELECTING_ANGLE;
                angleTimer = 0f;
            } else if (currentPhase == GamePhase.THROWER_SELECTING_ANGLE) {
                currentPhase = GamePhase.THROWER_SELECTING_POWER;
                powerTimer = 0f;
            } else if (currentPhase == GamePhase.THROWER_SELECTING_POWER) {
                launchSlipper();
                thrower.hasSlipper = false;
                currentPhase = GamePhase.SLIPPER_FLYING;
            } else if (currentPhase == GamePhase.TAYA_WAITING_PICKUP && taya.hasCan) {
                currentPhase = GamePhase.TAYA_SELECTING_ANGLE;
                angleTimer = 0f;
            } else if (currentPhase == GamePhase.TAYA_SELECTING_ANGLE) {
                currentPhase = GamePhase.TAYA_SELECTING_POWER;
                powerTimer = 0f;
            } else if (currentPhase == GamePhase.TAYA_SELECTING_POWER) {
                tayaThrowCan();
                currentPhase = GamePhase.TAYA_CAN_FLYING;
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) resetRound();
    }

    private void update(float delta) {
        thrower.update(delta);
        taya.update(delta);

        resolvePlayerCollision(thrower, prevThrowerPos);
        resolvePlayerCollision(taya, prevTayaPos);

        if (currentPhase == GamePhase.THROWER_SELECTING_ANGLE) {
            angleTimer += delta * 3.5f;
            currentAngle = MathUtils.sin(angleTimer) * 80f;
        } else if (currentPhase == GamePhase.TAYA_SELECTING_ANGLE) {
            angleTimer += delta * 4f;
            currentAngle = (angleTimer * 50f) % 360f;
        } else if (currentPhase == GamePhase.THROWER_SELECTING_POWER || currentPhase == GamePhase.TAYA_SELECTING_POWER) {
            powerTimer += delta * 4f;
            currentPower = ((MathUtils.sin(powerTimer) + 1f) / 2f) * 100f;
        } else if (currentPhase == GamePhase.SLIPPER_FLYING) {
            updateSlipper(delta);

            if (slipper.velocity.len() == 0 && !can.isHit) {
                currentPhase = GamePhase.TAYA_WAITING_PICKUP;
            }

            if (!can.isHit && Vector2.dst(slipper.position.x, slipper.position.y, can.position.x, can.position.y) < 25f) {
                triggerCanHit();
            }
        } else if (currentPhase == GamePhase.CAN_HIT_SCRAMBLE) {
            updateCan(delta);
            updateSlipper(delta);

            if (thrower.hasSlipper && thrower.position.x < throwLineX) {
                resetRound();
                return;
            }

            checkTaggingLogic();
        } else if (currentPhase == GamePhase.TAYA_CAN_FLYING) {
            updateCan(delta);

            if (isImpactPending) {
                impactDelayTimer += delta;
                updateSlipper(delta);
                if (impactDelayTimer >= 0.6f) {
                    isImpactPending = false;
                    impactDelayTimer = 0f;
                    swapRoles();
                }
                return;
            }

            if (can.zPosition <= 0 && can.zVelocity <= 0) {
                float distToSlipper = Vector2.dst(can.position.x, can.position.y, slipper.position.x, slipper.position.y);

                if (distToSlipper < 30f) {
                    triggerTayaCanHitSlipper();
                } else if (can.velocity.len() == 0) {
                    currentPhase = GamePhase.RETRIEVAL_PHASE;
                }
            }
        } else if (currentPhase == GamePhase.RETRIEVAL_PHASE) {
            updateCan(delta);

            if (thrower.hasSlipper && thrower.position.x < throwLineX) {
                resetRound();
                return;
            }

            checkTaggingLogic();
        }
    }

    private void checkTaggingLogic() {
        boolean isCanStandingAtBase = !can.isHit && Vector2.dst(can.position.x, can.position.y, canBasePosition.x, canBasePosition.y) < 10f;
        boolean isThrowerPastLine = thrower.position.x > throwLineX;

        if (isCanStandingAtBase && isThrowerPastLine) {
            if (Vector2.dst(taya.position.x, taya.position.y, thrower.position.x, thrower.position.y) < 30f) {
                swapRoles();
            }
        }
    }

    private void swapRoles() {
        Player temp = thrower;
        thrower = taya;
        taya = temp;

        resetRound();
    }

    private void launchSlipper() {
        slipper.position.set(thrower.position);
        float rad = currentAngle * MathUtils.degreesToRadians;
        float speed = currentPower * 18f;
        slipper.velocity.set(MathUtils.cos(rad) * speed, MathUtils.sin(rad) * speed);
    }

    private void triggerCanHit() {
        can.isHit = true;
        currentPhase = GamePhase.CAN_HIT_SCRAMBLE;
        taya.hasCan = false;

        float hitAngle = MathUtils.atan2(can.position.y - slipper.position.y, can.position.x - slipper.position.x);
        float slipperImpactSpeed = slipper.velocity.len();

        float speedMultiplier = slipperImpactSpeed * 0.85f;
        float targetVx = MathUtils.cos(hitAngle) * speedMultiplier;
        float targetVy = MathUtils.sin(hitAngle) * speedMultiplier;

        float equivalentPower = MathUtils.clamp((slipperImpactSpeed / 1200f) * 100f, 20f, 100f);

        can.toss(targetVx, targetVy, equivalentPower);
        slipper.velocity.scl(0.5f);
    }

    private void tayaThrowCan() {
        taya.hasCan = false;
        float rad = currentAngle * MathUtils.degreesToRadians;

        can.position.set(taya.position);
        can.zPosition = CAN_RELEASE_HEIGHT;
        can.zVelocity = 0f;
        can.velocity.set(0f, 0f);

        float powerRatio = MathUtils.clamp(currentPower / 100f, 0f, 1f);
        float throwDistance = CAN_MIN_THROW_DISTANCE
            + (CAN_MAX_THROW_DISTANCE - CAN_MIN_THROW_DISTANCE) * (float) Math.pow(powerRatio, CAN_POWER_CURVE);
        canLandingSpot.set(taya.position).add(MathUtils.cos(rad) * throwDistance, MathUtils.sin(rad) * throwDistance);

        can.tossTo(canLandingSpot.x, canLandingSpot.y, currentPower);
    }

    private void triggerTayaCanHitSlipper() {
        isImpactPending = true;
        impactDelayTimer = 0f;

        float hitAngle = MathUtils.atan2(slipper.position.y - can.position.y, slipper.position.x - can.position.x);
        float knockbackSpeed = 350f;
        slipper.velocity.set(MathUtils.cos(hitAngle) * knockbackSpeed, MathUtils.sin(hitAngle) * knockbackSpeed);

        can.velocity.scl(0.3f);
        can.zVelocity = 120f;
    }

    private void resetRound() {
        currentPhase = GamePhase.THROWER_ROAMING;

        thrower.setXBounds(20, throwLineX - 20);
        taya.setXBounds(20, screenWidth - 20);

        thrower.position.set(throwLineX - 50f, screenHeight * 0.5f);
        thrower.hasSlipper = false;

        taya.position.set(canBasePosition.x + 80f, canBasePosition.y);
        taya.hasCan = false;

        slipper.reset(thrower.position.x, thrower.position.y);
        can.reset(canBasePosition.x, canBasePosition.y);

        angleTimer = 0f;
        powerTimer = 0f;
        isImpactPending = false;
        impactDelayTimer = 0f;
    }

    private void renderUIOverlays() {
        if (currentPhase == GamePhase.THROWER_SELECTING_ANGLE || currentPhase == GamePhase.TAYA_SELECTING_ANGLE) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            Player activePlayer = (currentPhase == GamePhase.THROWER_SELECTING_ANGLE) ? thrower : taya;
            shapeRenderer.setColor(currentPhase == GamePhase.THROWER_SELECTING_ANGLE ? Color.BLUE : Color.RED);
            float rad = currentAngle * MathUtils.degreesToRadians;
            shapeRenderer.line(
                activePlayer.position.x,
                activePlayer.position.y,
                activePlayer.position.x + MathUtils.cos(rad) * 65f,
                activePlayer.position.y + MathUtils.sin(rad) * 65f
            );
            shapeRenderer.end();
        }

        if (currentPhase == GamePhase.THROWER_SELECTING_POWER || currentPhase == GamePhase.TAYA_SELECTING_POWER) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            Player activePlayer = (currentPhase == GamePhase.THROWER_SELECTING_POWER) ? thrower : taya;

            shapeRenderer.setColor(Color.LIGHT_GRAY);
            shapeRenderer.rect(activePlayer.position.x - 25, activePlayer.position.y + 30, 50, 10);

            shapeRenderer.setColor(currentPhase == GamePhase.THROWER_SELECTING_POWER ? Color.GREEN : Color.RED);
            shapeRenderer.rect(activePlayer.position.x - 25, activePlayer.position.y + 30, 50 * (currentPower / 100f), 10);
            shapeRenderer.end();
        }
    }

    private void renderDebugCollision() {
        boolean showPlayerWalls = debugView != 2;
        boolean showCanWalls = debugView != 1;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        if (showPlayerWalls) {
            shapeRenderer.setColor(1f, 0f, 0f, 0.35f);
            for (Shape2D wall : playerWalls) fillDebugShape(wall);
        }
        if (showCanWalls) {
            shapeRenderer.setColor(1f, 1f, 0f, 0.35f);
            for (Shape2D wall : canWalls) fillDebugShape(wall);
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.rect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT);
        shapeRenderer.setColor(Color.MAGENTA);
        shapeRenderer.rect(ringX, ringY,
            upperRingTexture.getWidth(),
            upperRingTexture.getHeight());

        if (showPlayerWalls) {
            shapeRenderer.setColor(Color.RED);
            for (Shape2D wall : playerWalls) drawDebugShape(wall);
        }
        if (showCanWalls) {
            shapeRenderer.setColor(Color.YELLOW);
            for (Shape2D wall : canWalls) drawDebugShape(wall);
        }

        shapeRenderer.setColor(Color.CYAN);
        shapeRenderer.rect(thrower.position.x - PLAYER_HITBOX_W / 2f,
            thrower.position.y + PLAYER_HITBOX_OFFSET_Y - PLAYER_HITBOX_H / 2f, PLAYER_HITBOX_W, PLAYER_HITBOX_H);
        shapeRenderer.rect(taya.position.x - PLAYER_HITBOX_W / 2f,
            taya.position.y + PLAYER_HITBOX_OFFSET_Y - PLAYER_HITBOX_H / 2f, PLAYER_HITBOX_W, PLAYER_HITBOX_H);

        shapeRenderer.setColor(Color.ORANGE);
        shapeRenderer.rect(can.position.x + CAN_HITBOX_OFFSET_X - CAN_HITBOX_W / 2f,
            can.position.y + CAN_HITBOX_OFFSET_Y - CAN_HITBOX_H / 2f, CAN_HITBOX_W, CAN_HITBOX_H);

        shapeRenderer.end();
    }

    private void fillDebugShape(Shape2D shape) {
        if (shape instanceof Rectangle) {
            Rectangle r = (Rectangle) shape;
            shapeRenderer.rect(r.x, r.y, r.width, r.height);
        } else if (shape instanceof Polygon) {
            float[] v = ((Polygon) shape).getTransformedVertices();
            for (int i = 2; i + 3 < v.length; i += 2) {
                shapeRenderer.triangle(v[0], v[1], v[i], v[i + 1], v[i + 2], v[i + 3]);
            }
        }
    }

    private void drawDebugShape(Shape2D shape) {
        if (shape instanceof Rectangle) {
            Rectangle r = (Rectangle) shape;
            shapeRenderer.rect(r.x, r.y, r.width, r.height);
        } else if (shape instanceof Polygon) {
            shapeRenderer.polygon(((Polygon) shape).getTransformedVertices());
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        camera.position.set(VIEW_X + VIEW_W / 2f, VIEW_Y + VIEW_H / 2f, 0);
        camera.update();
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
        if (mapRenderer != null) mapRenderer.dispose();
        if (map != null) map.dispose();
        if (upperRingTexture != null) upperRingTexture.dispose();
        if (font != null) font.dispose();
        if (playerSheet != null) playerSheet.dispose();
        if (playerSlipperSheet != null) playerSlipperSheet.dispose();
        if (playerCanSheet != null) playerCanSheet.dispose();
        if (canSheet != null) canSheet.dispose();
    }
}