package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
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
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Shape2D;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * Live background for the main menu: the real game map with the 4 characters wandering around on it.
 * Drawn in its own camera (map coordinates), so call render() first, then draw the menu UI on top.
 * Use MenuBackdrop.tryCreate(): it returns null if an asset is missing, and the menu falls back to its image background.
 */
public class MenuBackdrop {
    // --- ASSETS ---
    private static final String MAP_FILE = "MAPCOLLISION.tmx";
    private static final String COLLISION_LAYER = "collision1";          // characters can't walk through these
    private static final String WALK_SHEET_FILE = "16x16 Walk-Sheet.png"; // same walk sheet the game uses
    private static final String RING_FILE = "upperring.png";             // optional, drawn over the characters
    private static final float RING_X = 0f;
    private static final float RING_Y = -64f;

    // --- CAMERA: which part of the map the menu shows (map coordinates) ---
    private static final float SCREEN_W = 700f;
    private static final float SCREEN_H = 500f;
    private static final float CAMERA_X = 704f;      // center of the art
    private static final float CAMERA_Y = 320f;
    private static final float ZOOM = 1.5f;          // 1 = pixel for pixel, bigger = shows more of the map

    // --- CHARACTERS ---
    private static final float SPRITE_SIZE = 48f;
    private static final float SPEED_MIN = 60f;      // map pixels per second
    private static final float SPEED_MAX = 95f;
    private static final float IDLE_MIN = 0.4f;      // seconds a character stands still after reaching a spot
    private static final float IDLE_MAX = 1.8f;

    // Area the characters wander in (map coordinates). Spots inside walls are skipped automatically.
    private static final float WALK_MIN_X = 260f;
    private static final float WALK_MAX_X = 1130f;
    private static final float WALK_MIN_Y = 20f;
    private static final float WALK_MAX_Y = 420f;

    // Collision box around a character's feet (same idea as the game)
    private static final float HITBOX_W = 14f;
    private static final float HITBOX_H = 10f;

    // Shadow under each character
    private static final float SHADOW_W = 28f;
    private static final float SHADOW_H = 10f;
    private static final float SHADOW_OFFSET_Y = -19f;
    private static final float SHADOW_ALPHA = 0.30f;

    private final TiledMap map;
    private final OrthogonalTiledMapRenderer mapRenderer;
    private final OrthographicCamera mapCamera;
    private final Texture walkSheet;
    private final Texture ringTexture; // may be null

    private final Array<Shape2D> walls = new Array<>();
    private final Array<Walker> walkers = new Array<>();

    private final Rectangle hitboxRect = new Rectangle();
    private final float[] hitboxVerts = new float[8];
    private final Polygon hitboxPoly = new Polygon(new float[8]);

    /** Returns null (and logs) if the live background can't be built, so the menu can use a normal image instead. */
    public static MenuBackdrop tryCreate() {
        try {
            return new MenuBackdrop();
        } catch (Exception e) {
            Gdx.app.error("Backdrop", "Live background disabled: " + e);
            return null;
        }
    }

    private MenuBackdrop() {
        if (!Gdx.files.internal(MAP_FILE).exists()) throw new RuntimeException("missing " + MAP_FILE);
        if (!Gdx.files.internal(WALK_SHEET_FILE).exists()) throw new RuntimeException("missing " + WALK_SHEET_FILE);

        map = new TmxMapLoader().load(MAP_FILE);
        mapRenderer = new OrthogonalTiledMapRenderer(map, 1f);

        mapCamera = new OrthographicCamera();
        mapCamera.setToOrtho(false, SCREEN_W, SCREEN_H);
        mapCamera.position.set(CAMERA_X, CAMERA_Y, 0f);
        mapCamera.zoom = ZOOM;
        mapCamera.update();

        walkSheet = new Texture(Gdx.files.internal(WALK_SHEET_FILE));
        walkSheet.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        if (Gdx.files.internal(RING_FILE).exists()) {
            ringTexture = new Texture(Gdx.files.internal(RING_FILE));
            ringTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        } else {
            ringTexture = null;
        }

        collectWalls(map.getLayers(), false);
        for (int i = 0; i < Characters.COUNT; i++) {
            walkers.add(new Walker(Characters.TINTS[i]));
        }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /** Updates and draws the map + characters. Afterwards the caller must restore its own projection matrices. */
    public void render(float delta, ShapeRenderer shapes) {
        for (int i = 0; i < walkers.size; i++) walkers.get(i).update(delta);

        mapCamera.update();
        mapRenderer.setView(mapCamera);
        mapRenderer.render();

        // shadows
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(mapCamera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, SHADOW_ALPHA);
        for (int i = 0; i < walkers.size; i++) {
            Walker w = walkers.get(i);
            shapes.ellipse(w.pos.x - SHADOW_W / 2f, w.pos.y + SHADOW_OFFSET_Y - SHADOW_H / 2f, SHADOW_W, SHADOW_H);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // characters, back to front (higher on the map = further away = drawn first)
        walkers.sort((a, b) -> Float.compare(b.pos.y, a.pos.y));
        Batch batch = mapRenderer.getBatch();
        batch.setProjectionMatrix(mapCamera.combined);
        batch.begin();
        for (int i = 0; i < walkers.size; i++) {
            Walker w = walkers.get(i);
            batch.setColor(w.tint);
            batch.draw(w.anim.getCurrentFrame(), w.pos.x - SPRITE_SIZE / 2f, w.pos.y - SPRITE_SIZE / 2f, SPRITE_SIZE, SPRITE_SIZE);
        }
        batch.setColor(Color.WHITE);
        if (ringTexture != null) {
            batch.draw(ringTexture, RING_X, RING_Y); // characters walk behind the ring, like in the game
        }
        batch.end();
    }

    /**
     * Draws the live background, then a dark layer over it so the menu UI stays readable, then restores the
     * caller's projection. Call this first in a menu screen's render().
     */
    public void renderDimmed(float delta, ShapeRenderer shapes, Matrix4 uiProjection, float screenW, float screenH, float dim) {
        render(delta, shapes);

        shapes.setProjectionMatrix(uiProjection); // the backdrop used its own camera
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, dim);
        shapes.rect(0, 0, screenW, screenH);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    public void dispose() {
        mapRenderer.dispose();
        map.dispose();
        walkSheet.dispose();
        if (ringTexture != null) ringTexture.dispose();
    }

    // ------------------------------------------------------------------
    // Collision (same idea as GameScreen: rectangles and polygons from the TMX collision layer)
    // ------------------------------------------------------------------

    private void collectWalls(MapLayers layers, boolean insideMatch) {
        for (MapLayer layer : layers) {
            boolean matches = insideMatch || COLLISION_LAYER.equalsIgnoreCase(layer.getName());
            if (layer instanceof MapGroupLayer) {
                collectWalls(((MapGroupLayer) layer).getLayers(), matches);
            } else if (matches) {
                for (MapObject obj : layer.getObjects()) {
                    if (obj instanceof RectangleMapObject) {
                        walls.add(((RectangleMapObject) obj).getRectangle());
                    } else if (obj instanceof PolygonMapObject) {
                        walls.add(((PolygonMapObject) obj).getPolygon());
                    }
                }
            }
        }
    }

    private boolean isBlocked(float cx, float cy) {
        float left = cx - HITBOX_W / 2f;
        float bottom = cy - HITBOX_H / 2f;
        float right = left + HITBOX_W;
        float top = bottom + HITBOX_H;

        hitboxRect.set(left, bottom, HITBOX_W, HITBOX_H);
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

    // ------------------------------------------------------------------
    // One wandering character
    // ------------------------------------------------------------------

    private class Walker {
        final Vector2 pos = new Vector2();
        final Vector2 vel = new Vector2();
        final Vector2 target = new Vector2();
        final PlayerAnimation anim;
        final Color tint;
        final float speed;
        float idleTimer;

        Walker(Color tint) {
            this.tint = new Color(tint);
            this.anim = new PlayerAnimation(walkSheet, 0.12f);
            this.speed = MathUtils.random(SPEED_MIN, SPEED_MAX);

            // start on a free spot
            pos.set((WALK_MIN_X + WALK_MAX_X) / 2f, (WALK_MIN_Y + WALK_MAX_Y) / 2f);
            for (int tries = 0; tries < 200; tries++) {
                float x = MathUtils.random(WALK_MIN_X, WALK_MAX_X);
                float y = MathUtils.random(WALK_MIN_Y, WALK_MAX_Y);
                if (!isBlocked(x, y)) {
                    pos.set(x, y);
                    break;
                }
            }
            idleTimer = MathUtils.random(0f, IDLE_MAX); // so they don't all start moving at the same moment
            pickTarget();
        }

        void pickTarget() {
            target.set(pos);
            for (int tries = 0; tries < 30; tries++) {
                float x = MathUtils.random(WALK_MIN_X, WALK_MAX_X);
                float y = MathUtils.random(WALK_MIN_Y, WALK_MAX_Y);
                if (!isBlocked(x, y)) {
                    target.set(x, y);
                    return;
                }
            }
        }

        void update(float delta) {
            if (idleTimer > 0f) {
                idleTimer -= delta;
                vel.set(0f, 0f);
                anim.update(delta, vel);
                if (idleTimer <= 0f) pickTarget();
                return;
            }

            float dx = target.x - pos.x;
            float dy = target.y - pos.y;
            if (dx * dx + dy * dy < 36f) { // arrived
                idleTimer = MathUtils.random(IDLE_MIN, IDLE_MAX);
                vel.set(0f, 0f);
                anim.update(delta, vel);
                return;
            }

            vel.set(dx, dy).nor().scl(speed);
            float oldX = pos.x, oldY = pos.y;
            pos.add(vel.x * delta, vel.y * delta);

            if (isBlocked(pos.x, pos.y)) {
                if (!isBlocked(pos.x, oldY)) {
                    pos.y = oldY;                 // slide horizontally along the wall
                } else if (!isBlocked(oldX, pos.y)) {
                    pos.x = oldX;                 // slide vertically along the wall
                } else {
                    pos.set(oldX, oldY);          // stuck: stop and choose somewhere else
                    vel.set(0f, 0f);
                    idleTimer = 0.2f;
                }
            }
            anim.update(delta, vel);
        }
    }
}