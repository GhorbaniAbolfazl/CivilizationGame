package civilization.view.input;

import civilization.view.render.CameraState;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.util.Objects;

/** Keyboard bindings that mutate only presentation-layer camera state. */
public final class KeyBindingInstaller {
    private static final double PAN_PIXELS = 48.0;

    private KeyBindingInstaller() {
    }

    public static void installCameraPan(
            JComponent component, CameraState camera, Runnable repaintRequest) {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(repaintRequest, "repaintRequest");
        bind(component, "LEFT", "pan-left", camera, repaintRequest, -PAN_PIXELS, 0);
        bind(component, "A", "pan-left-a", camera, repaintRequest, -PAN_PIXELS, 0);
        bind(component, "RIGHT", "pan-right", camera, repaintRequest, PAN_PIXELS, 0);
        bind(component, "D", "pan-right-d", camera, repaintRequest, PAN_PIXELS, 0);
        bind(component, "UP", "pan-up", camera, repaintRequest, 0, -PAN_PIXELS);
        bind(component, "W", "pan-up-w", camera, repaintRequest, 0, -PAN_PIXELS);
        bind(component, "DOWN", "pan-down", camera, repaintRequest, 0, PAN_PIXELS);
        bind(component, "S", "pan-down-s", camera, repaintRequest, 0, PAN_PIXELS);
    }

    private static void bind(
            JComponent component,
            String key,
            String actionName,
            CameraState camera,
            Runnable repaintRequest,
            double deltaX,
            double deltaY) {
        component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(key), actionName);
        component.getActionMap().put(actionName,
                new PanAction(camera, repaintRequest, deltaX, deltaY));
    }

    private static final class PanAction extends AbstractAction {
        private static final long serialVersionUID = 1L;
        private final CameraState camera;
        private final Runnable repaintRequest;
        private final double deltaX;
        private final double deltaY;

        private PanAction(
                CameraState camera,
                Runnable repaintRequest,
                double deltaX,
                double deltaY) {
            this.camera = camera;
            this.repaintRequest = repaintRequest;
            this.deltaX = deltaX;
            this.deltaY = deltaY;
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            camera.panScreenPixels(deltaX, deltaY);
            repaintRequest.run();
        }
    }
}
