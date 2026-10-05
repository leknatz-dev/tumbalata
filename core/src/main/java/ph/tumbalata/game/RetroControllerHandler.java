package ph.tumbalata.game;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerAdapter;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.math.Vector2;

public class RetroControllerHandler extends ControllerAdapter {

    private Controller activeController;
    private final Vector2 moveDirection = new Vector2(0, 0);

    // Track button states
    public boolean buttonA = false;
    public boolean buttonB = false;
    public boolean buttonSelect = false;
    public boolean buttonStart = false;

    public RetroControllerHandler() {
        // Register this class to listen for controller events
        Controllers.addListener(this);

        // Check if a controller is connected on startup
        if (Controllers.getControllers().size > 0) {
            activeController = Controllers.getControllers().first();
            System.out.println("Connected Controller: " + activeController.getName());
        }
    }

    @Override
    public void connected(Controller controller) {
        if (activeController == null) {
            activeController = controller;
            System.out.println("Controller Connected: " + controller.getName());
        }
    }

    @Override
    public void disconnected(Controller controller) {
        if (activeController == controller) {
            activeController = null;
            System.out.println("Controller Disconnected!");
        }
    }

    @Override
    public boolean buttonDown(Controller controller, int buttonCode) {
        if (controller != activeController) return false;

        // Print button code to console for easy debugging
        System.out.println("Pressed Button Index: " + buttonCode);

        switch (buttonCode) {
            case 0: // Typically A
                buttonA = true;
                break;
            case 1: // Typically B
                buttonB = true;
                break;
            case 8: // Typically Select
                buttonSelect = true;
                break;
            case 9: // Typically Start
                buttonStart = true;
                break;
        }
        return true;
    }

    @Override
    public boolean buttonUp(Controller controller, int buttonCode) {
        if (controller != activeController) return false;

        switch (buttonCode) {
            case 0:
                buttonA = false;
                break;
            case 1:
                buttonB = false;
                break;
            case 8:
                buttonSelect = false;
                break;
            case 9:
                buttonStart = false;
                break;
        }
        return true;
    }

    // Call this to get D-Pad movement vector
    public Vector2 getMoveDirection() {
        moveDirection.set(0, 0);

        if (activeController == null) return moveDirection;

        // Check libGDX D-Pad mappings
        if (activeController.getButton(activeController.getMapping().buttonDpadLeft))  moveDirection.x -= 1;
        if (activeController.getButton(activeController.getMapping().buttonDpadRight)) moveDirection.x += 1;
        if (activeController.getButton(activeController.getMapping().buttonDpadUp))    moveDirection.y += 1;
        if (activeController.getButton(activeController.getMapping().buttonDpadDown))  moveDirection.y -= 1;

        // Fallback check for axis-based D-Pads (USB retro pads usually use Axis 0 and 1)
        if (moveDirection.len2() == 0) {
            float axisX = activeController.getAxis(0);
            float axisY = activeController.getAxis(1);

            if (axisX < -0.5f) moveDirection.x = -1;
            else if (axisX > 0.5f) moveDirection.x = 1;

            if (axisY < -0.5f) moveDirection.y = 1;
            else if (axisY > 0.5f) moveDirection.y = -1;
        }

        return moveDirection.nor();
    }
}