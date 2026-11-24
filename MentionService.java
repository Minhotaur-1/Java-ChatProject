package ChatProject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 같은 방의 회원 ID 또는 닉네임을 @ID / @닉네임으로 언급했는지 검사한다.
 * DB에서 확인한 실제 회원에게만 알림을 보낸다.
 */
public final class MentionService {
    private static final String URL = "jdbc:mysql://localhost:3306/chatdb";
    private static final String DB_USER = "root";

    private MentionService() { }

    public static Set<String> findMentionedUserIds(int roomId, String message)
            throws SQLException {
        Set<String> matched = new HashSet<>();
        if (message == null || message.indexOf('@') < 0) {
            return matched;
        }

        String password = System.getenv("CHAT_DB_PASSWORD");
        if (password == null || password.isEmpty()) {
            throw new SQLException("CHAT_DB_PASSWORD 환경변수가 없습니다.");
        }

        String sql = "SELECT u.id, u.nickname FROM users u "
                + "INNER JOIN room_members rm ON rm.user_id = u.id "
                + "WHERE rm.room_id = ?";

        try (Connection conn = DriverManager.getConnection(URL, DB_USER, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String nickname = rs.getString("nickname");
                    if (containsMention(message, id)
                            || containsMention(message, nickname)) {
                        matched.add(id);
                    }
                }
            }
        }
        return matched;
    }

    /** 단어의 일부에 잘못 반응하지 않도록 @ 앞뒤의 문자 경계를 확인한다. */
    public static boolean containsMention(String text, String alias) {
        if (text == null || alias == null || alias.trim().isEmpty()) {
            return false;
        }
        String pattern = "(?<![\\p{L}\\p{N}_])@"
                + Pattern.quote(alias)
                + "(?![\\p{L}\\p{N}_])";
        return Pattern.compile(pattern,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(text).find();
    }
}
