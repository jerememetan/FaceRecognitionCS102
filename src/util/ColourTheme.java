package util;
import java.awt.*;

public class ColourTheme {
    public static final Color PRIMARY_COLOR = new Color(59, 130, 246);
    public static final Color SUCCESS_COLOR = new Color(46, 125, 50);
    public static final Color WARNING_COLOR = new Color(255, 152, 0);
    public static final Color DANGER = new Color(239, 68, 68);
    
    public static final Color BACKGROUND = new Color(248, 249, 250);
    public static final Color TEXT_PRIMARY = new Color(33, 37, 41);
    public static final Color TEXT_SECONDARY = new Color(108, 117, 125);
    public static final Color LINE_BORDER = new Color(226,232,240);
    public static final Color HEADER_PANEL_BACKGROUND = new Color(45, 55, 72);
    public static final Color FOREGROUND_COLOR = new Color(71, 85, 105);
    // Private constructor prevents instantiation
    private ColourTheme() {
        throw new AssertionError("Cannot instantiate utility class");
    }
}
