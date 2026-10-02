package ph.tumbalata.game;

public class GameConstants {
    public static final float WORLD_WIDTH = 1280f;
    public static final float WORLD_HEIGHT = 720f;
    
    public static final float PLAYER_SPEED = 200f;
    public static final float TAYA_SPEED = 190f;
    
    // Friction for concrete surface (higher resistance than ice)
    public static final float SLIPPER_FRICTION = 2.2f; 
    public static final float CAN_FRICTION = 1.8f;
    
    // Wall bounce speed dampening (50% velocity retained per bounce)
    public static final float BOUNCE_DAMPING = 0.5f; 
}