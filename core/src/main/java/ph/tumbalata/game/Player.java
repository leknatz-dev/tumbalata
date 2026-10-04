package ph.tumbalata.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;

public class Player {
    public Vector2 position;
    public Vector2 velocity;
    public float speed;
    public boolean hasCan = false;
    public boolean hasSlipper = false;

    private int upKey, downKey, leftKey, rightKey;
    private float minX, maxX, minY, maxY;
    private boolean moving = false;

    public float width = 48f;
    public float height = 48f;

    // Multiplies the sprite's colors (white = unchanged). Used for the placeholder character colors.
    public Color tint = new Color(Color.WHITE);

    // Ground shadow under the feet (tweak these to match the sprite)
    public float shadowWidth = 28f;
    public float shadowHeight = 10f;
    public float shadowOffsetY = -19f;   // from the player's position to the CENTER of the shadow
    public float shadowAlpha = 0.30f;

    private PlayerAnimation walkAnim;
    private PlayerAnimation walkWithSlipperAnim;
    private PlayerAnimation walkWithCanAnim;
    public boolean facingRight = true;
    public void updateFacingFromAngle(float angle) {
    // Standard normalized angle check: if pointing left-ish, flip sprite left
    float normalizedAngle = (angle % 360 + 360) % 360;
    this.facingRight = !(normalizedAngle > 90 && normalizedAngle < 270);
}

    public Player(float x, float y, float speed, int upKey, int downKey, int leftKey, int rightKey, 
                  float minX, float maxX, float minY, float maxY, 
                  Texture normalSheet, Texture slipperSheet, Texture canSheet) {
        this.position = new Vector2(x, y);
        this.velocity = new Vector2(0, 0);
        this.speed = speed;
        this.upKey = upKey;
        this.downKey = downKey;
        this.leftKey = leftKey;
        this.rightKey = rightKey;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;

        this.walkAnim = new PlayerAnimation(normalSheet, 0.12f);
        if (slipperSheet != null) {
            this.walkWithSlipperAnim = new PlayerAnimation(slipperSheet, 0.12f);
        }
        if (canSheet != null) {
            this.walkWithCanAnim = new PlayerAnimation(canSheet, 0.12f);
        }
    }

    public void setXBounds(float minX, float maxX) {
        this.minX = minX;
        this.maxX = maxX;
    }

    public void handleInput(float delta) {
        moving = false;
        velocity.set(0, 0);

        if (Gdx.input.isKeyPressed(upKey)) {
            velocity.y += 1;
            moving = true;
        }
        if (Gdx.input.isKeyPressed(downKey)) {
            velocity.y -= 1;
            moving = true;
        }
        if (Gdx.input.isKeyPressed(leftKey)) {
            velocity.x -= 1;
            moving = true;
        }
        if (Gdx.input.isKeyPressed(rightKey)) {
            velocity.x += 1;
            moving = true;
        }

        if (moving) {
            velocity.nor().scl(speed);
            position.add(velocity.x * delta, velocity.y * delta);
        }

        position.x = Math.max(minX, Math.min(maxX, position.x));
        position.y = Math.max(minY, Math.min(maxY, position.y));
    }

    public boolean isMoving() {
        return moving;
    }

    public void update(float delta) {
        if (hasCan && walkWithCanAnim != null) {
            walkWithCanAnim.update(delta, velocity);
        } else if (hasSlipper && walkWithSlipperAnim != null) {
            walkWithSlipperAnim.update(delta, velocity);
        } else {
            walkAnim.update(delta, velocity);
        }
    }

    public void render(SpriteBatch batch) {
        TextureRegion currentFrame;
        if (hasCan && walkWithCanAnim != null) {
            currentFrame = walkWithCanAnim.getCurrentFrame();
        } else if (hasSlipper && walkWithSlipperAnim != null) {
            currentFrame = walkWithSlipperAnim.getCurrentFrame();
        } else {
            currentFrame = walkAnim.getCurrentFrame();
        }

        float previousColor = batch.getPackedColor();
        batch.setColor(tint);
        batch.draw(
            currentFrame, 
            position.x - width / 2f, 
            position.y - height / 2f, 
            width, 
            height
        );
        batch.setPackedColor(previousColor);
    }

    /** Call inside a ShapeRenderer Filled block (with GL blending on), BEFORE the sprites are drawn. */
    public void renderShadow(ShapeRenderer shapeRenderer) {
        shapeRenderer.setColor(0f, 0f, 0f, shadowAlpha);
        shapeRenderer.ellipse(
            position.x - shadowWidth / 2f,
            position.y + shadowOffsetY - shadowHeight / 2f,
            shadowWidth,
            shadowHeight
        );
    }
}