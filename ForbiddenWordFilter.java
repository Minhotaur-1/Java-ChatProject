package ChatProject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

public class ForbiddenWordFilter {

    private static final String URL = "jdbc:mysql://localhost:3306/chatdb";
    private static final String DB_USER = "root";

    /**
     * DB에 활성화된 금칙어를 불러와 대소문자 구별 없이 ***로 치환한다.
     * DB 오류가 발생하면 예외를 던져 원문이 전송되지 않도록 한다.
     */
    public static String filter(String message) throws SQLException {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String password = System.getenv("CHAT_DB_PASSWORD");
        if (password == null || password.isEmpty()) {
            throw new SQLException("CHAT_DB_PASSWORD 환경변수가 없습니다.");
        }

        String sql = "SELECT word FROM forbidden_words "
                + "WHERE is_active = 1 "
                + "ORDER BY CHAR_LENGTH(word) DESC, word";

        String result = message;

        try (Connection conn = DriverManager.getConnection(URL, DB_USER, password);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String bannedWord = rs.getString("word");
                if (bannedWord == null || bannedWord.trim().isEmpty()) {
                    continue;
                }

                Pattern pattern = Pattern.compile(
                        Pattern.quote(bannedWord),
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                result = pattern.matcher(result).replaceAll("***");
            }
        }
        return result;
    }
}
