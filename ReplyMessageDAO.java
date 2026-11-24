package ChatProject;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** 답장 기능: 원본 메시지 조회, 답장 저장, 이전 대화의 답장 관계 복원. */
public final class ReplyMessageDAO {
    private static final String URL = "jdbc:mysql://localhost:3306/chatdb";
    private static final String USER = "root";

    private static Connection connect() throws SQLException {
        String password = System.getenv("CHAT_DB_PASSWORD");
        if (password == null || password.isEmpty()) {
            throw new SQLException("CHAT_DB_PASSWORD 환경변수를 설정해주세요.");
        }
        return DriverManager.getConnection(URL, USER, password);
    }

    public static final class ReplyInfo {
        public final long originalMessageId;
        public final String originalSender;
        public final String originalPreview;

        public ReplyInfo(long originalMessageId, String originalSender, String originalPreview) {
            this.originalMessageId = originalMessageId;
            this.originalSender = originalSender;
            this.originalPreview = originalPreview;
        }

        public String previewBase64() {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    originalPreview.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static final class SavedReply {
        public final long newMessageId;
        public final ReplyInfo reference;

        SavedReply(long newMessageId, ReplyInfo reference) {
            this.newMessageId = newMessageId;
            this.reference = reference;
        }
    }

    private static String preview(String fullContent) {
        String singleLine = fullContent == null ? "" :
                fullContent.replace('\r', ' ').replace('\n', ' ').trim();
        return singleLine.length() > 100 ? singleLine.substring(0, 100) + "…" : singleLine;
    }

    /** 원본이 같은 채팅방에 있는지 확인하고, 같은 DB 연결로 답장을 저장한다. */
    public SavedReply saveReply(int roomId, String senderId, long replyToId, String filteredText)
            throws SQLException {
        if (replyToId <= 0 || filteredText == null || filteredText.trim().isEmpty()
                || filteredText.length() > 1000) {
            throw new SQLException("올바르지 않은 답장 요청입니다.");
        }
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try {
                ReplyInfo original;
                String find = "SELECT sender_id, content FROM chat_message "
                        + "WHERE message_id = ? AND room_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(find)) {
                    ps.setLong(1, replyToId);
                    ps.setInt(2, roomId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("현재 채팅방에 없는 메시지에는 답장할 수 없습니다.");
                        }
                        original = new ReplyInfo(replyToId,
                                rs.getString("sender_id"), preview(rs.getString("content")));
                    }
                }

                String insert = "INSERT INTO chat_message "
                        + "(room_id, sender_id, content, reply_to_message_id) VALUES (?, ?, ?, ?)";
                long newId;
                try (PreparedStatement ps = conn.prepareStatement(
                        insert, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, roomId);
                    ps.setString(2, senderId);
                    ps.setString(3, filteredText);
                    ps.setLong(4, replyToId);
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("답장 저장에 실패했습니다.");
                    }
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("답장 번호를 가져올 수 없습니다.");
                        newId = keys.getLong(1);
                    }
                }
                conn.commit();
                return new SavedReply(newId, original);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /** 최근 답장 메타데이터를 원본 메시지와 JOIN하여 조회한다. */
    public Map<Long, ReplyInfo> recentReplies(int roomId, int max) throws SQLException {
        Map<Long, ReplyInfo> replies = new HashMap<>();
        String sql = "SELECT m.message_id, m.reply_to_message_id, "
                + "p.sender_id AS original_sender, p.content AS original_content "
                + "FROM chat_message m JOIN chat_message p "
                + "ON p.message_id = m.reply_to_message_id AND p.room_id = m.room_id "
                + "WHERE m.room_id = ? ORDER BY m.message_id DESC LIMIT ?";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            ps.setInt(2, max);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    replies.put(rs.getLong("message_id"),
                            new ReplyInfo(rs.getLong("reply_to_message_id"),
                                    rs.getString("original_sender"),
                                    preview(rs.getString("original_content"))));
                }
            }
        }
        return replies;
    }
}
