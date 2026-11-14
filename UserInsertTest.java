package ChatProject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class UserInsertTest {

    public static void main(String[] args) {

        String url = "jdbc:mysql://localhost:3306/chatdb";
        String user = "root";
        String password = System.getenv("CHAT_DB_PASSWORD");

        String sql = "INSERT INTO users "
                   + "(id, password, nickname, profile_image, zipcode, address) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";

        try {
            Connection conn = DriverManager.getConnection(url, user, password);

            PreparedStatement pstmt = conn.prepareStatement(sql);

            pstmt.setString(1, "java01");
            pstmt.setString(2, "1234");
            pstmt.setString(3, "자바유저");
            pstmt.setString(4, null);
            pstmt.setString(5, "14000");
            pstmt.setString(6, "경기도 안양시");

            int result = pstmt.executeUpdate();

            if (result > 0) {
                System.out.println("회원 등록 성공!");
            } else {
                System.out.println("회원 등록 실패!");
            }

            pstmt.close();
            conn.close();

        } catch (Exception e) {
            e.printStackTrace();
        }	
    }
}