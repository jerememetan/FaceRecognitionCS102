package app.service;

import java.sql.*;

public class DBConnection {
    private static final String URL = "jdbc:postgresql://aws-1-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require&preferQueryMode=simple";
    private static final String USER = "postgres.ulskejtvwmkzlpnjayhe";
    private static final String PASSWORD = "Group8CS102FreeA+";

    public static Connection getConnection() throws SQLException{
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
