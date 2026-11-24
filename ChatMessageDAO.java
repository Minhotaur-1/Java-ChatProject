package ChatProject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** 10주차: 채팅 메시지 저장을 담당하는 JDBC 클래스 */
public class ChatMessageDAO {
    private static final String URL = "jdbc:mysql://localhost:3306/chatdb";
    private static final String DB_USER = "root";

    public long saveMessage(int roomId, String senderId, String content)
            throws SQLException {

        String password = System.getenv("CHAT_DB_PASSWORD");
        if (password == null || password.isEmpty()) {
            throw new SQLException("CHAT_DB_PASSWORD 환경변수를 설정해주세요.");
        }

        String sql = "INSERT INTO chat_message "
                + "(room_id, sender_id, content) VALUES (?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(
                URL, DB_USER, password);
             PreparedStatement pstmt = conn.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, roomId);
            pstmt.setString(2, senderId);
            pstmt.setString(3, content);
            int changed = pstmt.executeUpdate();
            if (changed != 1) {
                throw new SQLException("채팅 저장 결과가 올바르지 않습니다.");
            }

            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("생성된 메시지 ID를 확인할 수 없습니다.");
        }
    }
    // 이전 채팅 기록 한 건
    public static class ChatRecord {
        public final long messageId;
        public final int roomId;
        public final String senderId;
        public final String content;
        public final String sentAt;

        public ChatRecord(long messageId, int roomId,
                String senderId, String content, String sentAt) {
            this.messageId = messageId;
            this.roomId = roomId;
            this.senderId = senderId;
            this.content = content;
            this.sentAt = sentAt;
        }
    }

    // 특정 채팅방 최근 N개 메시지 조회 (최신 N개를 과거순으로 반환)
    public java.util.List<ChatRecord> loadRecentMessages(int roomId, int limit)
            throws SQLException {

        String password = System.getenv("CHAT_DB_PASSWORD");
        if (password == null || password.isEmpty()) {
            throw new SQLException("CHAT_DB_PASSWORD 환경변수를 설정해주세요.");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("조회 개수는 1~100 사이여야 합니다.");
        }

        String sql = "SELECT message_id, room_id, sender_id, content, sent_at "
                + "FROM chat_message WHERE room_id = ? "
                + "ORDER BY message_id DESC LIMIT ?";

        java.util.List<ChatRecord> result = new java.util.ArrayList<>();

        try (Connection conn = DriverManager.getConnection(
                URL, DB_USER, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, roomId);
            pstmt.setInt(2, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    java.sql.Timestamp time = rs.getTimestamp("sent_at");
                    String timeText = time == null ? "" :
                            time.toLocalDateTime().format(
                                    java.time.format.DateTimeFormatter.ofPattern(
                                            "yyyy-MM-dd HH:mm:ss"));
                    result.add(new ChatRecord(
                            rs.getLong("message_id"),
                            rs.getInt("room_id"),
                            rs.getString("sender_id"),
                            rs.getString("content"),
                            timeText));
                }
            }
        }

        // DB에서는 최신순으로 찾고, 화면에는 오래된 순서부터 표시
        java.util.Collections.reverse(result);
        return result;
    }

    // 10주차: 같은 채팅방의 대화 중 키워드가 들어간 최근 N개 메시지 검색
    public java.util.List<ChatRecord> searchMessages(
            int roomId, String keyword, int limit) throws SQLException {

        String password = System.getenv("CHAT_DB_PASSWORD");
        if (password == null || password.isEmpty()) {
            throw new SQLException("CHAT_DB_PASSWORD 환경변수를 설정해주세요.");
        }
        if (keyword == null || keyword.trim().isEmpty()
                || keyword.length() > 100 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("검색어 또는 검색 개수를 확인해주세요.");
        }

        // %, _, ! 문자는 SQL LIKE에서 특수한 의미가 있으므로 리터럴 검색으로 이스케이프
        String escapedKeyword = keyword.trim().replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");

        String sql = "SELECT message_id, room_id, sender_id, content, sent_at "
                + "FROM chat_message "
                + "WHERE room_id = ? AND content LIKE ? ESCAPE '!' "
                + "ORDER BY message_id DESC LIMIT ?";

        java.util.List<ChatRecord> results = new java.util.ArrayList<>();
        try (Connection conn = DriverManager.getConnection(
                    URL, DB_USER, password);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, roomId);
            stmt.setString(2, "%" + escapedKeyword + "%");
            stmt.setInt(3, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    java.sql.Timestamp timestamp = rs.getTimestamp("sent_at");
                    String timeText = timestamp == null ? "" :
                            timestamp.toLocalDateTime().format(
                                    java.time.format.DateTimeFormatter.ofPattern(
                                            "yyyy-MM-dd HH:mm:ss"));
                    results.add(new ChatRecord(
                            rs.getLong("message_id"),
                            rs.getInt("room_id"),
                            rs.getString("sender_id"),
                            rs.getString("content"),
                            timeText));
                }
            }
        }
        java.util.Collections.reverse(results);
        return results;
    }

}
