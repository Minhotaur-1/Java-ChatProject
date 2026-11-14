
package ChatProject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;

public class GroupRoomTest {

    private static final String URL =
            "jdbc:mysql://localhost:3306/chatdb";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD =
    System.getenv("CHAT_DB_PASSWORD");
            

    // 멀티 채팅방 생성
    public static void createGroupRoom(
            String roomName, String creator,
            ArrayList<String> members) {

        if (roomName.trim().isEmpty()
                || roomName.length() > 100) {
            System.out.println("채팅방 이름을 확인해주세요.");
            return;
        }

        if (members.size() < 3) {
            System.out.println("멀티 채팅방은 3명 이상이어야 합니다.");
            return;
        }

        // 중복 회원 검사
        HashSet<String> uniqueMembers =
                new HashSet<>(members);

        if (uniqueMembers.size() != members.size()
                || !uniqueMembers.contains(creator)) {
            System.out.println(
                    "중복 회원이 있거나 생성자가 포함되지 않았습니다.");
            return;
        }

        String checkSql =
                "SELECT id FROM users WHERE id = ?";

        String roomSql =
                "INSERT INTO chat_rooms "
                + "(room_name, room_type, created_by) "
                + "VALUES (?, 'GROUP', ?)";

        String memberSql =
                "INSERT INTO room_members (room_id, user_id) "
                + "VALUES (?, ?)";

        try (Connection conn =
                DriverManager.getConnection(
                        URL, DB_USER, DB_PASSWORD)) {

            conn.setAutoCommit(false);

            try {
                // 회원 존재 여부 확인
                for (String member : members) {
                    try (PreparedStatement pstmt =
                            conn.prepareStatement(checkSql)) {

                        pstmt.setString(1, member);

                        try (ResultSet rs =
                                pstmt.executeQuery()) {

                            if (!rs.next()) {
                                System.out.println(
                                        "존재하지 않는 회원: " + member);
                                conn.rollback();
                                return;
                            }
                        }
                    }
                }

                int roomId;

                // 멀티 채팅방 생성
                try (PreparedStatement pstmt =
                        conn.prepareStatement(
                                roomSql,
                                Statement.RETURN_GENERATED_KEYS)) {

                    pstmt.setString(1, roomName);
                    pstmt.setString(2, creator);

                    pstmt.executeUpdate();

                    try (ResultSet rs =
                            pstmt.getGeneratedKeys()) {

                        if (!rs.next()) {
                            throw new Exception(
                                    "채팅방 번호 생성 실패");
                        }

                        roomId = rs.getInt(1);
                    }
                }

                // 참여 회원 등록
                try (PreparedStatement pstmt =
                        conn.prepareStatement(memberSql)) {

                    for (String member : members) {
                        pstmt.setInt(1, roomId);
                        pstmt.setString(2, member);
                        pstmt.executeUpdate();
                    }
                }

                conn.commit();

                System.out.println();
                System.out.println("멀티 채팅방 생성 성공!");
                System.out.println("채팅방 번호: " + roomId);
                System.out.println("채팅방 이름: " + roomName);
                System.out.println("채팅방 유형: GROUP");
                System.out.println("생성자: " + creator);

                System.out.println("===== 참여 회원 =====");

                for (String member : members) {
                    System.out.println("- " + member);
                }

            } catch (Exception e) {
                conn.rollback();
                System.out.println("채팅방 생성 실패!");
                e.printStackTrace();
            }

        } catch (Exception e) {
            System.out.println("DB 연결 오류!");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.println("===== 멀티 채팅방 생성 =====");

        System.out.print("채팅방 이름: ");
        String roomName = sc.nextLine();

        System.out.print("채팅방 생성자 아이디: ");
        String creator = sc.nextLine().trim();

        System.out.print("전체 참여 인원(3명 이상): ");

        int count;

        try {
            count = Integer.parseInt(sc.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("인원은 숫자로 입력해주세요.");
            sc.close();
            return;
        }

        if (count < 3) {
            System.out.println("최소 3명이 필요합니다.");
            sc.close();
            return;
        }

        ArrayList<String> members = new ArrayList<>();

        // 생성자도 채팅방에 참여
        members.add(creator);

        for (int i = 1; i < count; i++) {
            System.out.print("참여 회원 " + i + " 아이디: ");
            members.add(sc.nextLine().trim());
        }

        createGroupRoom(roomName, creator, members);

        sc.close();
    }
}
