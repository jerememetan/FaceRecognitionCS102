package gui;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.WeakHashMap;


/**
 * FullScreenUtil
 *
 * Small helper utility to enable/disable fullscreen behavior for Swing frames.
 * Two modes are supported:
 *  - MAXIMIZED: sets the frame extended state to MAXIMIZED_BOTH (windowed fullscreen)
 *  - EXCLUSIVE: attempts exclusive fullscreen using GraphicsDevice.setFullScreenWindow
 *
 * Usage:
 *   // maximize a frame when showing
 *   FullScreenUtil.enableFullScreen(frame, FullScreenUtil.Mode.MAXIMIZED);
 *
 *   // or for exclusive fullscreen
 *   FullScreenUtil.enableFullScreen(frame, FullScreenUtil.Mode.EXCLUSIVE);
 *
 *   // later restore
 *   FullScreenUtil.disableFullScreen(frame);
 */
public final class FullScreenUtil {

    public enum Mode { MAXIMIZED, EXCLUSIVE }

    // map to hold prior window state for restoration (uses weak keys to avoid leaks)
    private static final Map<Window, StoredState> PREV_STATE_MAP = new WeakHashMap<>();

    private static class StoredState {
        final boolean undecorated;
        final Rectangle bounds;
        final int extendedState;

        StoredState(boolean undecorated, Rectangle bounds, int extendedState) {
            this.undecorated = undecorated;
            this.bounds = bounds;
            this.extendedState = extendedState;
        }
    }

    /**
     * Lightweight overload to enable fullscreen for any Window (JDialog, Dialog, etc.).
     * If the window is a JFrame, delegate to the existing implementation.
     * For other Window types we set the bounds to the maximum available screen bounds.
     */
    public static void enableFullScreen(Window window, Mode mode) {
        if (window == null) return;
        if (window instanceof JFrame) {
            enableFullScreen((JFrame) window, mode);
            return;
        }

        // save previous bounds so we can restore later
        PREV_STATE_MAP.put(window, new StoredState(false, window.getBounds(), 0));

        // emulate maximized fullscreen by using the maximum available window bounds
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        window.setBounds(bounds);
        // caller is responsible for making the window visible
    }

    private FullScreenUtil() { /* utility */ }

    /**
     * Enable fullscreen on the provided frame using the requested mode.
     * This method stores previous state in client properties so disableFullScreen
     * can restore it.
     */
    public static void enableFullScreen(JFrame frame, Mode mode) {
        if (frame == null) return;

    // Save previous properties in the map so we can restore later
    PREV_STATE_MAP.put(frame, new StoredState(frame.isUndecorated(), frame.getBounds(), frame.getExtendedState()));

        if (mode == Mode.EXCLUSIVE) {
            // Exclusive fullscreen (may not be supported on all platforms)
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            boolean wasVisible = frame.isVisible();
            try {
                // Need to dispose before changing undecorated state
                if (wasVisible) frame.dispose();
                frame.setUndecorated(true);
                // show before requesting exclusive mode on some platforms
                    // caller is responsible for making the frame visible
                gd.setFullScreenWindow(frame);
            } catch (Exception ex) {
                // fallback to maximized if exclusive mode fails
                try {
                    if (wasVisible) frame.dispose();
                    frame.setUndecorated(false);
                    frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                        // caller is responsible for making the frame visible
                } catch (Exception ignore) {}
            }
        } else {
            // MAXIMIZED: windowed fullscreen (keeps OS taskbar depending on platform)
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                // caller is responsible for making the frame visible
        }
    }

    /**
     * Disable fullscreen and restore previous window state saved by enableFullScreen.
     */
    public static void disableFullScreen(JFrame frame) {
        if (frame == null) return;

        // If frame is in exclusive fullscreen, try to exit it first
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        Window fs = gd.getFullScreenWindow();
        if (fs == frame) {
            gd.setFullScreenWindow(null);
        }

        StoredState prev = PREV_STATE_MAP.remove(frame);
        if (prev == null) return; // nothing to restore

        boolean wasUndec = prev.undecorated;

        try {
            // If we changed undecorated status, we must dispose before restoring
            if (frame.isUndecorated() && !wasUndec) {
                boolean wasVisible = frame.isVisible();
                if (wasVisible) frame.dispose();
                frame.setUndecorated(wasUndec);
                if (prev.bounds != null) frame.setBounds(prev.bounds);
                frame.setExtendedState(prev.extendedState);
                if (wasVisible) frame.setVisible(true);
            } else {
                // restore bounds/state if present
                if (prev.bounds != null) frame.setBounds(prev.bounds);
                frame.setExtendedState(prev.extendedState);
            }
        } catch (Exception ignore) {
            // best-effort restore; ignore errors to avoid crashing UI
        }
    }

    /**
     * Lightweight overload to disable fullscreen for any Window.
     * Delegates to JFrame version when appropriate; otherwise restores saved bounds.
     */
    public static void disableFullScreen(Window window) {
        if (window == null) return;
        if (window instanceof JFrame) {
            disableFullScreen((JFrame) window);
            return;
        }

        StoredState prev = PREV_STATE_MAP.remove(window);
        if (prev == null) return;

        try {
            if (prev.bounds != null) window.setBounds(prev.bounds);
        } catch (Exception ignore) {
            // best-effort
        }
    }
}
