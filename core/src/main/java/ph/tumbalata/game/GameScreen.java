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
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class GameScreen implements Screen {
    // Virtual resolution = the Tiled map size in pixels (40 x 22 tiles at 32x32)
    public static final float WORLD_WIDTH = 1280f;
    public static final float WORLD_HEIGHT = 704f;

    // Region of the world the camera shows. The court art (Image Layer) is 1408x768 but the TMX grid is only 1280x704,
    // so the art hangs 64px below the map (y -64..704) and 128px to the right (x 0..1408).
    private static final float VIEW_X = 0f;
    private static final float VIEW_Y = -64f;
    private static final float VIEW_W = 1408f;
    private static final float VIEW_H = 768f;

    // --- MAP / COLLISION SETTINGS ---
    // The white vertical line is the THROW LINE (gameplay), not part of the map. The thrower is kept behind it.
    private static final boolean SHOW_THROW_LINE = true;

    private static final String MAP_FILE = "MAPCOLLISION.tmx";
    private static final String PLAYER_COLLISION_LAYER = "collision1";
    private static final String CAN_COLLISION_LAYER = "collision2";
    private static final String UPPER_RING_FILE = "upperring.png";

    // Position where upperring.png's bottom-left corner is drawn (0,0 if it covers the whole map)
    private static final float RING_X = 0f;
    private static final float RING_Y = -64f;
    // Drawn size of the ring image. 0 = use the PNG's native size. Use 1280 x 704 to stretch a full-map overlay.
    private static final float RING_W = 0f;
    private static final float RING_H = 0f;

    // Player hitbox (small box around the feet). Tune with the F1 debug view.
    private static final float PLAYER_HITBOX_W = 14f;
    private static final float PLAYER_HITBOX_H = 10f;
    private static final float PLAYER_HITBOX_OFFSET_Y = 0f;

    // Can hitbox and wall behaviour
    private static final float CAN_HITBOX_W = 16f;
    private static final float CAN_HITBOX_H = 16f;
    // Shift the can hitbox relative to can.position (tune with F1 until the orange box sits on the can's base)
    private static final float CAN_HITBOX_OFFSET_X = 0f;
    private static final float CAN_HITBOX_OFFSET_Y = 0f;
    private static final float CAN_WALL_BOUNCE = 0.5f;      // 1 = perfect bounce, 0 = dead stop
    // true = the can is also stopped by collision1 (the walls around the playing area), so it can never land outside it
    private static final boolean CAN_BLOCKED_BY_PLAYER_WALLS = true;
    private static final float CAN_WALL_MAX_HEIGHT = 10000f; // can above this height passes over walls (huge = borders always block)

    // Slipper hitbox and wall behaviour (collides with collision1, like the players)
    private static final float SLIPPER_HITBOX_W = 12f;
    private static final float SLIPPER_HITBOX_H = 12f;
    private static final float SLIPPER_WALL_BOUNCE = 0.6f;

    // Safety-net limits for Can/Slipper's built-in walls. Kept well outside the art so collision2 / collision1 do the real bordering.
    private static final float OUTER_MIN_X = VIEW_X - 256f;
    private static final float OUTER_MIN_Y = VIEW_Y - 256f;
    private static final float OUTER_MAX_X = VIEW_X + VIEW_W + 256f;
    private static final float OUTER_MAX_Y = VIEW_Y + VIEW_H; // top edge of the art: nothing may leave through the top

    private Viewport viewport;
    private OrthographicCamera camera;

    private ShapeRenderer shapeRenderer;
    private SpriteBatch spriteBatch;
    private BitmapFont font; // For drawing UI prompts and notices

    private TiledMap map;
    private OrthogonalTiledMapRenderer mapRenderer;
    private Texture upperRingTexture;

    private Texture playerSheet;
    private Texture playerSlipperSheet;
    private Texture playerCanSheet;
    private Texture canSheet;

    // Collision shapes loaded from the TMX object layers (Rectangle or Polygon)
    private final Array<Shape2D> playerWalls = new Array<>();
    private final Array<Shape2D> canWalls = new Array<>();
    private final Array<Shape2D> canBlockers = new Array<>(); // everything that stops the can

    // Reusable hitbox helpers (avoid allocating every frame)
    private final Rectangle hitboxRect = new Rectangle();
    private final float[] hitboxVerts = new float[8];
    private final Polygon hitboxPoly = new Polygon(new float[8]);

    private final Vector2 prevThrowerPos = new Vector2();
    private final Vector2 prevTayaPos = new Vector2();
    private boolean debugCollision = false;
    private int debugView = 0; // 0 = both layers, 1 = only collision1, 2 = only collision2
    private float ringX = RING_X;
    private float ringY = RING_Y;

    private enum GamePhase {
        THROWER_ROAMING,
        THROWER_SELECTING_ANGLE,
        THROWER_SELECTING_POWER,
        SLIPPER_FLYING,
        CAN_HIT_SCRAMBLE,       // Can knocked down; Taya must upright can at base while Thrower retrieves slipper
        TAYA_WAITING_PICKUP,    // Can missed; Taya picks up can
        TAYA_SELECTING_ANGLE,   // Taya aims can at grounded slipper
        TAYA_SELECTING_POWER,   // Taya powers up can toss
        TAYA_CAN_FLYING,        // Can flying towards grounded slipper
        RETRIEVAL_PHASE         // Can missed slipper; Taya resets can, Thrower retrieves slipper safely
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

    // --- LANDING ARC & IMPACT DELAY TRACKING VARIABLES ---
    private Vector2 canLandingSpot = new Vector2();
    private boolean isImpactPending = false;
    private float impactDelayTimer = 0f;

    @Override
    public void show() {
        // Set up fixed aspect-ratio viewport & camera
        camera = new OrthographicCamera();
        viewport = new FitViewport(VIEW_W, VIEW_H, camera);
        viewport.apply();
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

        // Load the Tiled map (replaces the old mapcourt.png background)
        map = new TmxMapLoader().load(MAP_FILE);
        mapRenderer = new OrthogonalTiledMapRenderer(map, 1f, spriteBatch);

        // Diagnostics: real TMX size vs. the world size the camera shows, and window vs. screen size
        int mapTilesW = map.getProperties().get("width", Integer.class);
        int mapTilesH = map.getProperties().get("height", Integer.class);
        int tilePxW = map.getProperties().get("tilewidth", Integer.class);
        int tilePxH = map.getProperties().get("tileheight", Integer.class);
        Gdx.app.log("Map", "TMX is " + mapTilesW + "x" + mapTilesH + " tiles at " + tilePxW + "x" + tilePxH
            + " = " + (mapTilesW * tilePxW) + "x" + (mapTilesH * tilePxH) + " px (camera world is "
            + (int) WORLD_WIDTH + "x" + (int) WORLD_HEIGHT + ")");
        for (MapLayer l : map.getLayers()) {
            Gdx.app.log("Map", "layer '" + l.getName() + "' type=" + l.getClass().getSimpleName()
                + " visible=" + l.isVisible() + " offset=" + l.getOffsetX() + "," + l.getOffsetY());
        }
        Gdx.app.log("Map", "Window is " + Gdx.graphics.getWidth() + "x" + Gdx.graphics.getHeight()
            + ", screen is " + Gdx.graphics.getDisplayMode().width + "x" + Gdx.graphics.getDisplayMode().height);

        // Collision object layers: collision1 = players, collision2 = can
        loadCollisionLayer(PLAYER_COLLISION_LAYER, playerWalls);
        loadCollisionLayer(CAN_COLLISION_LAYER, canWalls);

        // The can is stopped by collision2, and (optionally) by collision1 so it always stays inside the playing area
        canBlockers.addAll(canWalls);
        if (CAN_BLOCKED_BY_PLAYER_WALLS) canBlockers.addAll(playerWalls);

        // Overlay drawn on top of the players so they walk "behind" the ring
        upperRingTexture = new Texture(Gdx.files.internal(UPPER_RING_FILE));
        upperRingTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        Gdx.app.log("Ring", UPPER_RING_FILE + " is " + upperRingTexture.getWidth() + "x" + upperRingTexture.getHeight()
            + " (map is " + (int) WORLD_WIDTH + "x" + (int) WORLD_HEIGHT + ")");

        // Load player textures
        playerSheet = new Texture(Gdx.files.internal("16x16 Walk-Sheet.png"));
        playerSheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        playerSlipperSheet = new Texture(Gdx.files.internal("16x16 Walkwithslipper.png"));
        playerSlipperSheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        playerCanSheet = new Texture(Gdx.files.internal("Walkwithcan.png"));
        playerCanSheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        // Can sheet loading with rotation frames (e.g., 4 columns, 1 row)
        canSheet = new Texture(Gdx.files.internal("can_spin_sheet.png"));
        canSheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        can = new Can(canBasePosition.x, canBasePosition.y, canSheet, 4, 1, 0.08f);
        slipper = new Slipper(throwLineX - 50, canBasePosition.y);

        // Initialize Players
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
    }

    // ------------------------------------------------------------------
    // COLLISION HELPERS
    // ------------------------------------------------------------------

    /** Loads every shape from all object layers named layerName (also inside group layers). */
    private void loadCollisionLayer(String layerName, Array<Shape2D> out) {
        int before = out.size;
        collectCollision(map.getLayers(), layerName, false, out);
        Gdx.app.log("Game", "Loaded " + (out.size - before) + " collision shapes for '" + layerName + "'");
        if (out.size > before) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int i = before; i < out.size; i++) {
                Shape2D sh = out.get(i);
                Rectangle b = (sh instanceof Rectangle) ? (Rectangle) sh : ((Polygon) sh).getBoundingRectangle();
                minX = Math.min(minX, b.x); minY = Math.min(minY, b.y);
                maxX = Math.max(maxX, b.x + b.width); maxY = Math.max(maxY, b.y + b.height);
            }
            Gdx.app.log("Game", "'" + layerName + "' bounds: x " + minX + " to " + maxX + ", y " + minY + " to " + maxY);
        }
        if (out.size == before) {
            Gdx.app.error("Game", "No usable objects found for layer '" + layerName + "' in " + MAP_FILE);
        }
    }

    private void collectCollision(MapLayers layers, String layerName, boolean insideMatch, Array<Shape2D> out) {
        for (MapLayer layer : layers) {
            boolean matches = insideMatch || layerName.equalsIgnoreCase(layer.getName());
            if (layer instanceof MapGroupLayer) {
                collectCollision(((MapGroupLayer) layer).getLayers(), layerName, matches, out);
            } else if (matches) {
                Gdx.app.log("Game", "Reading layer '" + layer.getName() + "' (" + layer.getObjects().getCount()
                    + " objects, visible=" + layer.isVisible() + ", offset=" + layer.getOffsetX() + "," + layer.getOffsetY() + ")");
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
                // Tiled rotates clockwise around the rectangle's top-left corner
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
        } else {
            Gdx.app.log("Game", "SKIPPED unsupported object '" + obj.getName() + "' ("
                + obj.getClass().getSimpleName() + ") in " + layerName + " - use rectangles or polygons");
        }
    }

    /** True if a box of size (w x h) centered at (cx, cy) overlaps any wall in the list. */
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
                    hitboxVerts[6] = left;  hitboxVerts[7] = top;
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

    private boolean isCanBlocked(float x, float y) {
        return isBlocked(canWalls, x + CAN_HITBOX_OFFSET_X, y + CAN_HITBOX_OFFSET_Y, CAN_HITBOX_W, CAN_HITBOX_H);
    }

    private void savePreviousPositions() {
        prevThrowerPos.set(thrower.position);
        prevTayaPos.set(taya.position);
    }

    /** Undo movement into a wall; tries X-only and Y-only so players slide along walls. */
    private void resolvePlayerCollision(Player p, Vector2 prev) {
        float nx = p.position.x;
        float ny = p.position.y;

        if (!isPlayerBlocked(nx, ny)) return;
        if (isPlayerBlocked(prev.x, prev.y)) return; // already inside a wall (e.g. spawn) - don't freeze them

        if (!isPlayerBlocked(nx, prev.y)) {
            p.position.set(nx, prev.y);       // slide horizontally
        } else if (!isPlayerBlocked(prev.x, ny)) {
            p.position.set(prev.x, ny);       // slide vertically
        } else {
            p.position.set(prev.x, prev.y);   // fully blocked
        }
    }

    /**
     * Moves along old -> current position in small steps, so fast objects can't tunnel through thin walls.
     * If the object starts the frame overlapping a wall (thrown while standing next to a border), it is first
     * nudged to the nearest free spot AROUND ITS START, then swept from there, so the wall still stops it.
     */
    private void sweepAgainstWalls(Vector2 pos, Vector2 vel, float oldX, float oldY, Array<Shape2D> walls,
                                   float w, float h, float offX, float offY, float bounce) {
        float nx = pos.x, ny = pos.y;
        if (nx == oldX && ny == oldY) return; // not moving

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
            if (!found) return; // buried deep inside a wall: let it move rather than freeze
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
                if (!xBlocked && !yBlocked) { // diagonal corner hit
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

    /** Updates the can, then bounces it off collision2 walls (the court border etc.). */
    private void updateCan(float delta) {
        float oldX = can.position.x;
        float oldY = can.position.y;

        can.update(delta, OUTER_MIN_X, OUTER_MIN_Y, OUTER_MAX_X, OUTER_MAX_Y);

        // Carried can follows Taya; a can above CAN_WALL_MAX_HEIGHT goes over the walls
        if (taya.hasCan || can.zPosition > CAN_WALL_MAX_HEIGHT) return;

        sweepAgainstWalls(can.position, can.velocity, oldX, oldY, canBlockers,
            CAN_HITBOX_W, CAN_HITBOX_H, CAN_HITBOX_OFFSET_X, CAN_HITBOX_OFFSET_Y, CAN_WALL_BOUNCE);
    }

    /** Updates the slipper, then bounces it off collision1 walls (same walls as the players). */
    private void updateSlipper(float delta) {
        float oldX = slipper.position.x;
        float oldY = slipper.position.y;

        slipper.update(delta, OUTER_MIN_X, OUTER_MIN_Y, OUTER_MAX_X, OUTER_MAX_Y);

        sweepAgainstWalls(slipper.position, slipper.velocity, oldX, oldY, playerWalls,
            SLIPPER_HITBOX_W, SLIPPER_HITBOX_H, 0f, 0f, SLIPPER_WALL_BOUNCE);
    }

    // ------------------------------------------------------------------
    // MAIN LOOP
    // ------------------------------------------------------------------

    @Override
    public void render(float delta) {
        savePreviousPositions();
        handleInput(delta);
        update(delta);

        ScreenUtils.clear(0f, 0f, 0f, 1);

        // Update matrices to use viewport camera projection
        camera.update();
        shapeRenderer.setProjectionMatrix(camera.combined);
        spriteBatch.setProjectionMatrix(camera.combined);

        // 1. Tiled map (background court layer)
        mapRenderer.setView(camera);
        mapRenderer.render();

        // 2. Shape rendering pass (Ground markers & shadows)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.WHITE);
        if (SHOW_THROW_LINE) {
            shapeRenderer.rectLine(throwLineX, VIEW_Y, throwLineX, VIEW_Y + VIEW_H, 4);
        }

        // Draw visual base marker for the Can
        shapeRenderer.setColor(Color.LIGHT_GRAY);
        shapeRenderer.circle(canBasePosition.x, canBasePosition.y, 16f);

        if (taya.hasCan) {
            can.position.set(taya.position.x, taya.position.y + 15);
        }

        // Render the Can's dynamic shadow on the ground
        can.renderShadow(shapeRenderer);

        // Draw slipper on ground when not being held
        if (!thrower.hasSlipper) {
            slipper.render(shapeRenderer);
        }
        shapeRenderer.end();

        // 3. Sprite & Text rendering pass (Entities & Prompts)
        spriteBatch.begin();

        thrower.render(spriteBatch);
        taya.render(spriteBatch);

        // Draw Can (Laying down when carried by Taya, upright otherwise)
        if (taya.hasCan) {
            TextureRegion canFrame = can.getCurrentFrame();
            float canWidth = can.getWidth();
            float canHeight = can.getHeight();

            float canX = taya.position.x - (canWidth / 2f);
            float canY = taya.position.y + 18f;

            spriteBatch.draw(
                canFrame,
                canX, canY,
                canWidth / 2f, canHeight / 2f, // Origin for 90 degree pivot
                canWidth, canHeight,
                1f, 1f,
                90f // Lays the can horizontally across Taya's hands
            );
        } else {
            can.render(spriteBatch);
        }

        // Upper ring layer: drawn AFTER players and the can so they appear behind it
        if (RING_W > 0f && RING_H > 0f) {
            spriteBatch.draw(upperRingTexture, ringX, ringY, RING_W, RING_H);
        } else {
            spriteBatch.draw(upperRingTexture, ringX, ringY);
        }

        // Interaction Prompts (drawn above the ring so they stay readable)
        if (!thrower.hasSlipper && Vector2.dst(thrower.position.x, thrower.position.y, slipper.position.x, slipper.position.y) < 45f) {
            font.draw(spriteBatch, "[E] Pick Up Slipper", slipper.position.x - 45f, slipper.position.y + 25f);
        }

        // Taya picking up knocked-down/thrown can
        if ((currentPhase == GamePhase.TAYA_WAITING_PICKUP || currentPhase == GamePhase.CAN_HIT_SCRAMBLE) && !taya.hasCan) {
            if (Vector2.dst(taya.position.x, taya.position.y, can.position.x, can.position.y) < 35f) {
                font.draw(spriteBatch, "[E] Pick Up Can", can.position.x - 35f, can.position.y + 30f);
            }
        }

        // Taya placing can at base
        if ((currentPhase == GamePhase.RETRIEVAL_PHASE || currentPhase == GamePhase.CAN_HIT_SCRAMBLE) && taya.hasCan) {
            if (Vector2.dst(taya.position.x, taya.position.y, canBasePosition.x, canBasePosition.y) < 35f) {
                font.draw(spriteBatch, "[E] Place Can at Base", canBasePosition.x - 45f, canBasePosition.y + 35f);
            }
        }

        spriteBatch.end();

        // 4. UI overlays (aiming line & power bar)
        renderUIOverlays();

        // 5. Optional collision debug view (toggle with F1)
        if (debugCollision) {
            renderDebugCollision();
        }
    }

    private void handleInput(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) debugCollision = !debugCollision;
        if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) debugView = (debugView + 1) % 3;

        // Debug only: nudge the ring with I/K/J/L (hold SHIFT for 10px). Copy the logged values into RING_X / RING_Y.
        if (debugCollision) {
            float step = (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) ? 10f : 1f;
            boolean moved = false;
            if (Gdx.input.isKeyJustPressed(Input.Keys.I)) { ringY += step; moved = true; }
            if (Gdx.input.isKeyJustPressed(Input.Keys.K)) { ringY -= step; moved = true; }
            if (Gdx.input.isKeyJustPressed(Input.Keys.J)) { ringX -= step; moved = true; }
            if (Gdx.input.isKeyJustPressed(Input.Keys.L)) { ringX += step; moved = true; }
            if (moved) Gdx.app.log("Ring", "ring position: RING_X = " + ringX + "f, RING_Y = " + ringY + "f");
        }

        // Player Movement Controls
        if (currentPhase == GamePhase.THROWER_ROAMING) {
            thrower.handleInput(delta);
            if (Gdx.input.isKeyJustPressed(Input.Keys.E) && !thrower.hasSlipper) {
                if (Vector2.dst(thrower.position.x, thrower.position.y, slipper.position.x, slipper.position.y) < 45f) {
                    thrower.hasSlipper = true;
                }
            }
        } else if (currentPhase == GamePhase.CAN_HIT_SCRAMBLE || currentPhase == GamePhase.RETRIEVAL_PHASE) {
            thrower.handleInput(delta);
            if (Gdx.input.isKeyJustPressed(Input.Keys.E) && !thrower.hasSlipper) {
                if (Vector2.dst(thrower.position.x, thrower.position.y, slipper.position.x, slipper.position.y) < 45f) {
                    thrower.hasSlipper = true;
                }
            }
        }

        taya.handleInput(delta);

        // Taya Can Pickup & Reset Logic during Scramble & Miss Phases
        if (currentPhase == GamePhase.TAYA_WAITING_PICKUP || currentPhase == GamePhase.CAN_HIT_SCRAMBLE) {
            // 1. Pickup Can from the ground
            if (!taya.hasCan && Gdx.input.isKeyJustPressed(Input.Keys.E)) {
                if (Vector2.dst(taya.position.x, taya.position.y, can.position.x, can.position.y) < 35f) {
                    taya.hasCan = true;
                }
            }
        }

        // 2. Put down/reset Can at base spot
        if ((currentPhase == GamePhase.CAN_HIT_SCRAMBLE || currentPhase == GamePhase.RETRIEVAL_PHASE) && taya.hasCan) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
                if (Vector2.dst(taya.position.x, taya.position.y, canBasePosition.x, canBasePosition.y) < 35f) {
                    taya.hasCan = false;
                    can.reset(canBasePosition.x, canBasePosition.y);
                }
            }
        }

        // Spacebar Shooting Actions
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

        // Push players back out of collision1 walls (uses positions saved before input)
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

            // Slipper lands and comes to stop without hitting can
            if (slipper.velocity.len() == 0 && !can.isHit) {
                currentPhase = GamePhase.TAYA_WAITING_PICKUP;
            }

            // Slipper hits can
            if (!can.isHit && Vector2.dst(slipper.position.x, slipper.position.y, can.position.x, can.position.y) < 25f) {
                triggerCanHit();
            }
        } else if (currentPhase == GamePhase.CAN_HIT_SCRAMBLE) {
            updateCan(delta);
            updateSlipper(delta);

            // Check if Thrower retrieved slipper and returned safely behind throw line
            if (thrower.hasSlipper && thrower.position.x < throwLineX) {
                resetRound();
                return;
            }

            // Tag Logic: Taya can tag thrower past throw line only if can is reset at base
            checkTaggingLogic();
        } else if (currentPhase == GamePhase.TAYA_CAN_FLYING) {
            updateCan(delta);

            // Handle hit delay pause after landing directly on/near slipper
            if (isImpactPending) {
                impactDelayTimer += delta;
                updateSlipper(delta);
                if (impactDelayTimer >= 0.6f) { // Delay before swapping roles
                    isImpactPending = false;
                    impactDelayTimer = 0f;
                    Gdx.app.log("Game", "TAYA HIT THE SLIPPER ON LANDING! Role swap!");
                    swapRoles();
                }
                return;
            }

            // Check hit condition strictly when the can touches the ground (zPosition <= 0)
            if (can.zPosition <= 0 && can.zVelocity <= 0) {
                float distToSlipper = Vector2.dst(can.position.x, can.position.y, slipper.position.x, slipper.position.y);

                // Outcome A: Landed on or near slipper (within 30 pixels radius)
                if (distToSlipper < 30f) {
                    triggerTayaCanHitSlipper();
                }
                // Outcome B: Missed landing target and stopped rolling
                else if (can.velocity.len() == 0) {
                    currentPhase = GamePhase.RETRIEVAL_PHASE;
                }
            }
        } else if (currentPhase == GamePhase.RETRIEVAL_PHASE) {
            updateCan(delta);

            // Check if Thrower safely returned home with slipper
            if (thrower.hasSlipper && thrower.position.x < throwLineX) {
                resetRound();
                return;
            }

            // Tag Logic: Taya can tag thrower past throw line if can is set upright at base
            checkTaggingLogic();
        }
    }

    private void checkTaggingLogic() {
        boolean isCanStandingAtBase = !can.isHit && Vector2.dst(can.position.x, can.position.y, canBasePosition.x, canBasePosition.y) < 10f;
        boolean isThrowerPastLine = thrower.position.x > throwLineX;

        if (isCanStandingAtBase && isThrowerPastLine) {
            if (Vector2.dst(taya.position.x, taya.position.y, thrower.position.x, thrower.position.y) < 30f) {
                Gdx.app.log("Game", "TAGGED! Thrower becomes the new Taya!");
                swapRoles();
            }
        }
    }

    private void swapRoles() {
        Player temp = thrower;
        thrower = taya;
        taya = temp;

        // Reset round positions & update movement boundaries
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
        taya.hasCan = false; // Taya must physically pick up the knocked-down can

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

        // Calculate intended landing spot based on power & angle
        float throwDistance = 120f + (currentPower * 3.8f);
        Vector2 throwDir = new Vector2(MathUtils.cos(rad), MathUtils.sin(rad));
        canLandingSpot.set(taya.position).add(throwDir.x * throwDistance, throwDir.y * throwDistance);

        // Velocity required to deliver the can to target landing spot
        float flightTime = 0.85f;
        float targetVx = (canLandingSpot.x - can.position.x) / flightTime;
        float targetVy = (canLandingSpot.y - can.position.y) / flightTime;

        can.toss(targetVx, targetVy, currentPower);
    }

    private void triggerTayaCanHitSlipper() {
        isImpactPending = true;
        impactDelayTimer = 0f;

        // Knock back the slipper on hit
        float hitAngle = MathUtils.atan2(slipper.position.y - can.position.y, slipper.position.x - can.position.x);
        float knockbackSpeed = 350f;
        slipper.velocity.set(MathUtils.cos(hitAngle) * knockbackSpeed, MathUtils.sin(hitAngle) * knockbackSpeed);

        // Bounce can off slipper
        can.velocity.scl(0.3f);
        can.zVelocity = 120f;
    }

    private void resetRound() {
        currentPhase = GamePhase.THROWER_ROAMING;

        // 1. Update movement boundaries according to current roles
        // Active Thrower is restricted behind throw line
        thrower.setXBounds(20, throwLineX - 20);
        // Active Taya can roam the right side/full arena
        taya.setXBounds(20, screenWidth - 20);

        // 2. Set spawn positions AFTER updating bounds
        thrower.position.set(throwLineX - 50f, screenHeight * 0.5f);
        thrower.hasSlipper = false;

        taya.position.set(canBasePosition.x + 80f, canBasePosition.y);
        taya.hasCan = false;

        // 3. Reset game items
        slipper.reset(thrower.position.x, thrower.position.y);
        can.reset(canBasePosition.x, canBasePosition.y);

        // 4. Reset state flags
        angleTimer = 0f;
        powerTimer = 0f;
        isImpactPending = false;
        impactDelayTimer = 0f;
    }

    private void renderUIOverlays() {
        // Aiming Line for both Thrower and Taya
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

        // Clean Power Bar Overlay
        if (currentPhase == GamePhase.THROWER_SELECTING_POWER || currentPhase == GamePhase.TAYA_SELECTING_POWER) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            Player activePlayer = (currentPhase == GamePhase.THROWER_SELECTING_POWER) ? thrower : taya;

            // Draw background container
            shapeRenderer.setColor(Color.LIGHT_GRAY);
            shapeRenderer.rect(activePlayer.position.x - 25, activePlayer.position.y + 30, 50, 10);

            // Draw filled power level
            shapeRenderer.setColor(currentPhase == GamePhase.THROWER_SELECTING_POWER ? Color.GREEN : Color.RED);
            shapeRenderer.rect(activePlayer.position.x - 25, activePlayer.position.y + 30, 50 * (currentPower / 100f), 10);
            shapeRenderer.end();
        }
    }

    /**
     * F1 = toggle debug view, F2 = cycle both / only collision1 / only collision2.
     * Red = collision1 (players), yellow = collision2 (can), cyan = player hitboxes, orange = can hitbox.
     */
    private void renderDebugCollision() {
        boolean showPlayerWalls = debugView != 2;
        boolean showCanWalls = debugView != 1;

        // Translucent fills so overlapping layers can be told apart
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

        // White = TMX map bounds, magenta = upperring.png bounds
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.rect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT);
        shapeRenderer.setColor(Color.MAGENTA);
        shapeRenderer.rect(ringX, ringY,
            (RING_W > 0f) ? RING_W : upperRingTexture.getWidth(),
            (RING_H > 0f) ? RING_H : upperRingTexture.getHeight());

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
        viewport.update(width, height, false);
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