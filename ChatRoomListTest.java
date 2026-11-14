
package ChatProject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ChatRoomListTest {

    private static final String URL =
            "jdbc:mysql://localhost:3306/chatdb";

    private static final String DB_USER = "root";

    private static final String DB_PASSWORD =
    System.getenv("CHAT_DB_PASSWORD");
            

    // 채팅방 목록 조회
    public static void showRoomList() {

        String sql =
                "SELECT r.room_id, r.room_name, "
              + "r.room_type, r.created_by, "
              + "COUNT(m.user_id) AS member_count "
              + "FROM chat_rooms r "
              + "LEFT JOIN room_members m "
              + "ON r.room_id = m.room_id "
              + "GROUP BY r.room_id, r.room_name, "
              + "r.room_type, r.created_by "
              + "ORDER BY r.room_id";

        try (
            Connection conn = DriverManager.getConnection(
                    URL, DB_USER, DB_PASSWORD);

            PreparedStatement pstmt =
                    conn.prepareStatement(sql);

            ResultSet rs = pstmt.executeQuery()
        ) {

            System.out.println("===== 채팅방 목록 =====");

            boolean hasRoom = false;

            while (rs.next()) {

                hasRoom = true;

                int roomId = rs.getInt("room_id");
                String roomName = rs.getString("room_name");
                String roomType = rs.getString("room_type");
                String creator = rs.getString("created_by");
                int count = rs.getInt("member_count");

                System.out.println();
                System.out.println("[" + roomId + "번] "
                        + roomName);

                System.out.println("채팅 유형: " + roomType);
                System.out.println("생성자: " + creator);
                System.out.println("참여 인원: " + count + "명");
            }

            if (!hasRoom) {
                System.out.println("생성된 채팅방이 없습니다.");
            }

            System.out.println();
            System.out.println("채팅방 목록 조회 완료!");

        } catch (Exception e) {
            System.out.println("채팅방 목록 조회 실패!");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        showRoomList();

    }
}
