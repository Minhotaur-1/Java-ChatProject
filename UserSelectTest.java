package ChatProject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class UserSelectTest {

    public static void main(String[] args) {

        String url = "jdbc:mysql://localhost:3306/chatdb";
        String user = "root";
        String password = System.getenv("CHAT_DB_PASSWORD");

        try {
            Connection conn = DriverManager.getConnection(url, user, password);

            String sql = "SELECT * FROM users";

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            while (rs.next()) {

                String id = rs.getString("id");
                String pw = rs.getString("password");
                String nickname = rs.getString("nickname");
                String zipcode = rs.getString("zipcode");
                String address = rs.getString("address");

                System.out.println(
                    "ID: " + id
                    + " / PW: " + pw
                    + " / 닉네임: " + nickname
                    + " / 우편번호: " + zipcode
                    + " / 주소: " + address
                );
            }

            rs.close();
            stmt.close();
            conn.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}