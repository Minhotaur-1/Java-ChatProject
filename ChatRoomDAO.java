
package ChatProject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Scanner;

public class ChatRoomDAO {

    private static final String URL =
            "jdbc:mysql://localhost:3306/chatdb";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD =
    System.getenv("CHAT_DB_PASSWORD");
            

    // 1:1 채팅방 생성
    public int createDirectRoom(String myId, String otherId) {

        if (myId.equals(otherId)) {
            System.out.println("자기 자신과 채팅방을 만들 수 없습니다.");
            return -1;
        }

        String checkSql =
                "SELECT COUNT(*) FROM users WHERE id IN (?, ?)";

        String roomSql =
                "INSERT INTO chat_rooms "
                + "(room_name, room_type, created_by) "
                + "VALUES (?, 'DIRECT', ?)";

        String memberSql =
                "INSERT INTO room_members (room_id, user_id) "
                + "VALUES (?, ?)";

        try (Connection conn = DriverManager.getConnection(
                URL, DB_USER, DB_PASSWORD)) {

            conn.setAutoCommit(false);

            try {
                // 회원이 실제로 존재하는지 확인
                try (PreparedStatement pstmt =
                        conn.prepareStatement(checkSql)) {

                    pstmt.setString(1, myId);
                    pstmt.setString(2, otherId);

                    try (ResultSet rs = pstmt.executeQuery()) {
                        rs.next();

                        if (rs.getInt(1) != 2) {
                            System.out.println(
                                    "존재하지 않는 회원이 있습니다.");
                            conn.rollback();
                            return -1;
                        }
                    }
                }

                int roomId;

                // 채팅방 생성
                try (PreparedStatement pstmt =
                        conn.prepareStatement(
                                roomSql,
                                Statement.RETURN_GENERATED_KEYS)) {

                    pstmt.setString(1,
                            "1:1 " + myId + " - " + otherId);
                    pstmt.setString(2, myId);

                    pstmt.executeUpdate();

                    try (ResultSet rs = pstmt.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new Exception(
                                    "채팅방 번호 생성 실패");
                        }
                        roomId = rs.getInt(1);
                    }
                }

                // 채팅방에 두 회원 등록
                try (PreparedStatement pstmt =
                        conn.prepareStatement(memberSql)) {

                    pstmt.setInt(1, roomId);
                    pstmt.setString(2, myId);
                    pstmt.executeUpdate();

                    pstmt.setInt(1, roomId);
                    pstmt.setString(2, otherId);
                    pstmt.executeUpdate();
                }

                conn.commit();

                System.out.println();
                System.out.println("1:1 채팅방 생성 성공!");
                System.out.println("채팅방 번호: " + roomId);
                System.out.println("참여 회원: "
                        + myId + ", " + otherId);

                return roomId;

            } catch (Exception e) {
                conn.rollback();
                System.out.println("채팅방 생성 실패!");
                e.printStackTrace();
            }

        } catch (Exception e) {
            System.out.println("DB 연결 오류!");
            e.printStackTrace();
        }

        return -1;
    }

    // 채팅방 생성 테스트
    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);
        ChatRoomDAO dao = new ChatRoomDAO();

        System.out.println("===== 1:1 채팅방 생성 =====");

        System.out.print("내 아이디: ");
        String myId = sc.nextLine().trim();

        System.out.print("상대방 아이디: ");
        String otherId = sc.nextLine().trim();

        dao.createDirectRoom(myId, otherId);

        sc.close();
    }
}
