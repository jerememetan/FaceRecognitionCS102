package app.service;
import ConfigurationAndLogging.*;
import java.sql.*;

public class DBConnection {
<<<<<<< HEAD
    private static final String URL = "jdbc:postgresql://aws-1-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require&preferQueryMode=simple";
    private static final String USER = "postgres.ulskejtvwmkzlpnjayhe";
    private static final String PASSWORD = "Group8CS102FreeA+";
=======
    private static final String URL = AppConfig.getInstance().getDatabaseURL();
    private static final String USER = AppConfig.getInstance().getDatabaseUser();
    private static final String PASSWORD = AppConfig.getInstance().getDatabasePassword();
>>>>>>> fc8bcaecbf3719a57729c26409dc768ad15935e9

    public static Connection getConnection() throws SQLException{
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
