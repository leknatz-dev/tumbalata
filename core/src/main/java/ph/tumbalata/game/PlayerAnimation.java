package ph.tumbalata.game;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

public class PlayerAnimation {
    public static final int FRAME_WIDTH = 16;
    public static final int FRAME_HEIGHT = 16;

    // 8-directional walking animations
    private Animation<TextureRegion> animDown;
    private Animation<TextureRegion> animDownRight;
    private Animation<TextureRegion> animDownLeft;
    private Animation<TextureRegion> animRight;
    private Animation<TextureRegion> animLeft;
    private Animation<TextureRegion> animUpRight;
    private Animation<TextureRegion> animUpLeft;
    private Animation<TextureRegion> animUp;

    private float stateTime = 0f;
    private int currentDirectionIndex = 0; // 0: S, 1: SE, 2: E, 3: NE, 4: N, 5: NW, 6: W, 7: SW

    public PlayerAnimation(Texture spriteSheet, float frameDuration) {
        TextureRegion[][] tmp = TextureRegion.split(
            spriteSheet, 
            spriteSheet.getWidth() / 4, 
            spriteSheet.getHeight() / 5
        );

        // Row 0: Down
        animDown = createAnim(tmp[0], false);
        
        // Row 1: Down-Right & Down-Left
        animDownRight = createAnim(tmp[1], false);
        animDownLeft = createAnim(tmp[1], true);

        // Row 2: Right & Left
        animRight = createAnim(tmp[2], false);
        animLeft = createAnim(tmp[2], true);

        // Row 3: Up-Right & Up-Left
        animUpRight = createAnim(tmp[3], false);
        animUpLeft = createAnim(tmp[3], true);

        // Row 4: Up
        animUp = createAnim(tmp[4], false);
    }

    private Animation<TextureRegion> createAnim(TextureRegion[] rowFrames, boolean flipX) {
        TextureRegion[] frames = new TextureRegion[4];
        for (int i = 0; i < 4; i++) {
            frames[i] = new TextureRegion(rowFrames[i]);
            frames[i].flip(flipX, false);
        }
        return new Animation<TextureRegion>(0.12f, frames);
    }

    public void update(float delta, Vector2 velocity) {
        if (velocity.len2() > 1f) {
            stateTime += delta;

            // Calculate movement angle (-180 to 180 degrees)
            float angle = velocity.angleDeg(); // 0 is Right, 90 is Up, 180 is Left, 270 is Down

            // Map angle to 8 directional sectors (45 degrees each)
            if (angle >= 22.5f && angle < 67.5f) {
                currentDirectionIndex = 3; // NE
            } else if (angle >= 67.5f && angle < 112.5f) {
                currentDirectionIndex = 4; // N
            } else if (angle >= 112.5f && angle < 157.5f) {
                currentDirectionIndex = 5; // NW
            } else if (angle >= 157.5f && angle < 202.5f) {
                currentDirectionIndex = 6; // W
            } else if (angle >= 202.5f && angle < 247.5f) {
                currentDirectionIndex = 7; // SW
            } else if (angle >= 247.5f && angle < 292.5f) {
                currentDirectionIndex = 0; // S
            } else if (angle >= 292.5f && angle < 337.5f) {
                currentDirectionIndex = 1; // SE
            } else {
                currentDirectionIndex = 2; // E
            }
        } else {
            // Idle state: reset animation frame to standing frame (frame 0)
            stateTime = 0f;
        }
    }

    public TextureRegion getCurrentFrame() {
        Animation<TextureRegion> activeAnim;
        switch (currentDirectionIndex) {
            case 1: activeAnim = animDownRight; break;
            case 2: activeAnim = animRight; break;
            case 3: activeAnim = animUpRight; break;
            case 4: activeAnim = animUp; break;
            case 5: activeAnim = animUpLeft; break;
            case 6: activeAnim = animLeft; break;
            case 7: activeAnim = animDownLeft; break;
            default: activeAnim = animDown; break;
        }

        return activeAnim.getKeyFrame(stateTime, true);
    }
}