package ph.tumbalata.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;

public class Can {
    public Vector2 position;
    public Vector2 velocity;
    public float zPosition = 0f;
    public float zVelocity = 0f;
    public boolean isHit = false;

    public float width = 24f;
    public float height = 36f;

    // --- TWEAKABLE FLING & PHYSICS PARAMETERS ---
    private float gravity = -400f;         // Tweak gravity (more negative = falls faster)
    private float bounceFactor = 0.55f;    // Ground bounce elasticity (0 = no bounce, 1 = max bounce)
    private float friction = 0.95f;        // Ground slide friction
    private float wallBounceDamping = 0.75f; // Wall rebound speed retention (0.75 = retains 75% speed)
    private float tossPowerMultiplier = 5f; // Vertical jump scaling (lower = less air launch height)

    // Animation & Rotation variables
    private Animation<TextureRegion> spinAnimation;
    private float stateTime = 0f;
    private float rotationAngle = 0f;
    private TextureRegion frontFrame;

    public Can(float x, float y, Texture canSpriteSheet, int frameCols, int frameRows, float frameDuration) {
        this.position = new Vector2(x, y);
        this.velocity = new Vector2(0, 0);

        TextureRegion[][] tmp = TextureRegion.split(
            canSpriteSheet, 
            canSpriteSheet.getWidth() / frameCols, 
            canSpriteSheet.getHeight() / frameRows
        );

        TextureRegion[] spinFrames = new TextureRegion[frameCols * frameRows];
        int index = 0;
        for (int i = 0; i < frameRows; i++) {
            for (int j = 0; j < frameCols; j++) {
                spinFrames[index++] = tmp[i][j];
            }
        }

        spinAnimation = new Animation<TextureRegion>(frameDuration, spinFrames);
        spinAnimation.setPlayMode(Animation.PlayMode.LOOP);
        frontFrame = spinFrames[0];
    }

    // --- GETTERS FOR EXTERNAL RENDERING (GameScreen.java) ---
    public TextureRegion getCurrentFrame() {
        if (isHit && (velocity.len() > 0 || zPosition > 0)) {
            return spinAnimation.getKeyFrame(stateTime, true);
        }
        return frontFrame;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public void toss(float vx, float vy, float power) {
        this.velocity.set(vx, vy);
        // Tweak vertical popup height here
        this.zVelocity = 180f + (power * tossPowerMultiplier);
        this.isHit = true;
    }

    /** Backwards-compatible: built-in outer walls from (0,0) to (screenWidth, screenHeight). */
    public void update(float delta, float screenWidth, float screenHeight) {
        update(delta, 0f, 0f, screenWidth, screenHeight);
    }

    /** Updates physics; the built-in outer walls are the rectangle (minX,minY)-(maxX,maxY). */
    public void update(float delta, float minX, float minY, float maxX, float maxY) {
        if (!isHit && velocity.len() == 0 && zPosition == 0) {
            return;
        }

        // 1. Horizontal movement
        position.add(velocity.x * delta, velocity.y * delta);

        // 2. Strict Screen Wall Bounce Logic (4 Boundaries)
        float halfWidth = width / 2f;
        float halfHeight = height / 2f;

        // Left wall
        if (position.x <= minX + halfWidth) {
            position.x = minX + halfWidth;
            velocity.x = -velocity.x * wallBounceDamping;
        } 
        // Right wall
        else if (position.x >= maxX - halfWidth) {
            position.x = maxX - halfWidth;
            velocity.x = -velocity.x * wallBounceDamping;
        }

        // Bottom wall
        if (position.y <= minY + halfHeight) {
            position.y = minY + halfHeight;
            velocity.y = -velocity.y * wallBounceDamping;
        } 
        // Top wall
        else if (position.y >= maxY - halfHeight) {
            position.y = maxY - halfHeight;
            velocity.y = -velocity.y * wallBounceDamping;
        }

        // 3. Ground Friction
        velocity.scl(friction);
        if (velocity.len() < 5f) {
            velocity.set(0, 0);
        }

        // 4. Vertical Arc Physics (Height & Ground Bounce)
        if (zPosition > 0 || zVelocity > 0) {
            zVelocity += gravity * delta;
            zPosition += zVelocity * delta;

            if (zPosition <= 0) {
                zPosition = 0;
                if (Math.abs(zVelocity) > 40f) {
                    zVelocity = -zVelocity * bounceFactor;
                } else {
                    zVelocity = 0;
                }
            }
        }

        // 5. Spin Animation & Rotation Updates
        if (velocity.len() > 0 || zPosition > 0) {
            float speedMultiplier = Math.max(1.0f, velocity.len() / 150f);
            stateTime += delta * speedMultiplier;
            rotationAngle += delta * 600f * speedMultiplier;
            rotationAngle %= 360f;
        }
    }

    // Call inside shapeRenderer block in GameScreen.java
    public void renderShadow(ShapeRenderer shapeRenderer) {
        shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 0.35f);

        // Shadow shrinks as can reaches higher altitude
        float shadowScale = Math.max(0.4f, 1.0f - (zPosition / 250f));
        float shadowWidth = width * shadowScale;
        float shadowHeight = 8f * shadowScale;

        // Draw shadow flat on ground at position.x / position.y
        shapeRenderer.ellipse(
            position.x - shadowWidth / 2f, 
            position.y - shadowHeight / 2f, 
            shadowWidth, 
            shadowHeight
        );
    }

    // Call inside spriteBatch block in GameScreen.java
    public void render(SpriteBatch batch) {
        TextureRegion currentFrame = getCurrentFrame();
        float currentRotation = (isHit && (velocity.len() > 0 || zPosition > 0)) ? rotationAngle : 0f;

        // Sprite offset vertically by zPosition so it jumps off the shadow!
        batch.draw(
            currentFrame,
            position.x - width / 2f,
            position.y - height / 2f + zPosition,
            width / 2f,
            height / 2f,
            width,
            height,
            1.0f,
            1.0f,
            currentRotation
        );
    }

    public void reset(float x, float y) {
        position.set(x, y);
        velocity.set(0, 0);
        zPosition = 0f;
        zVelocity = 0f;
        isHit = false;
        stateTime = 0f;
        rotationAngle = 0f;
    }
}