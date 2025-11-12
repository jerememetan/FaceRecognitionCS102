package util;
import java.awt.*;

public class ColourTheme {
    public static final Color PRIMARY_COLOR = new Color(59, 130, 246);
    public static final Color SUCCESS_COLOR = new Color(46, 125, 50);
    public static final Color WARNING_COLOR = new Color(255, 152, 0);
    public static final Color ERROR_COLOR = new Color(211, 47, 47);
    public static final Color DANGER = new Color(239, 68, 68);
    
    public static final Color BACKGROUND = new Color(248, 249, 250);
    public static final Color TEXT_PRIMARY = new Color(33, 37, 41);
    public static final Color TEXT_SECONDARY = new Color(108, 117, 125);
    
    // Private constructor prevents instantiation
    private ColourTheme() {
        throw new AssertionError("Cannot instantiate utility class");
    }
}
