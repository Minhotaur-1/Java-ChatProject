package ChatProject;

import java.sql.Connection;
import java.sql.DriverManager;

public class test {

    public static void main(String[] args) {

        String url = "jdbc:mysql://localhost:3306/chatdb";
        String user = "root";
        String password = System.getenv("CHAT_DB_PASSWORD");

        try {
            Connection conn = DriverManager.getConnection(url, user, password);

            System.out.println("DB 연결 성공!");

            conn.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}