package repository;

import config.AppConfig;
import java.sql.*;

public class DBConnection {
    private static final String URL = AppConfig.getInstance().getDatabaseURL();
    private static final String USER = AppConfig.getInstance().getDatabaseUser();
    private static final String PASSWORD = AppConfig.getInstance().getDatabasePassword();

    public static Connection getConnection() throws SQLException{
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}







