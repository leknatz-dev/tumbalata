package ph.tumbalata.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;

public class Slipper {
    public Vector2 position;
    public Vector2 velocity;
    public float radius = 8f;

    public Slipper(float x, float y) {
        this.position = new Vector2(x, y);
        this.velocity = new Vector2(0, 0);
    }

    /** Backwards-compatible: built-in outer walls from (0,0) to (screenWidth, screenHeight). */
    public void update(float delta, float screenWidth, float screenHeight) {
        update(delta, 0f, 0f, screenWidth, screenHeight);
    }

    /** Updates the slipper; the built-in outer walls are the rectangle (minX,minY)-(maxX,maxY). */
    public void update(float delta, float minX, float minY, float maxX, float maxY) {
        if (velocity.len() > 0) {
            position.add(velocity.x * delta, velocity.y * delta);

            // Reduced friction multiplier (0.8f) for a faster, slicker glide
            float effectiveFriction = GameConstants.SLIPPER_FRICTION * 0.8f;
            velocity.scl(1f - Math.min(1f, effectiveFriction * delta));
            
            if (velocity.len() < 8f) {
                velocity.set(0, 0);
            }

            // Wall bounce collision with speed dampening
            if (position.x < minX + radius) { 
                position.x = minX + radius; 
                velocity.x = -velocity.x * GameConstants.BOUNCE_DAMPING; 
            } else if (position.x > maxX - radius) { 
                position.x = maxX - radius; 
                velocity.x = -velocity.x * GameConstants.BOUNCE_DAMPING; 
            }
            
            if (position.y < minY + radius) { 
                position.y = minY + radius; 
                velocity.y = -velocity.y * GameConstants.BOUNCE_DAMPING; 
            } else if (position.y > maxY - radius) { 
                position.y = maxY - radius; 
                velocity.y = -velocity.y * GameConstants.BOUNCE_DAMPING; 
            }
        }
    }

    public void render(ShapeRenderer shapeRenderer) {
        shapeRenderer.setColor(Color.BROWN);
        shapeRenderer.ellipse(position.x - radius, position.y - 4, radius * 2, 8);
    }

    public void reset(float x, float y) {
        position.set(x, y);
        velocity.set(0, 0);
    }
}