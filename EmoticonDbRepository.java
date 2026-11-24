package ChatProject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 11주차: PNG/GIF 이모티콘 원본 파일을 MySQL에 저장하고 이미지 바이트를 조회합니다.
 * 연결 정보는 기존 ChatProject 실행 설정(CHAT_DB_PASSWORD)을 그대로 사용합니다.
 */
public final class EmoticonDbRepository {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/chatdb";
    private static final String DB_USER = "root";

    private EmoticonDbRepository() { }

    private static Connection connection() throws SQLException {
        String password = System.getenv("CHAT_DB_PASSWORD");
        if (password == null || password.isEmpty()) {
            throw new SQLException("CHAT_DB_PASSWORD 환경변수가 설정되지 않았습니다.");
        }
        return DriverManager.getConnection(DB_URL, DB_USER, password);
    }

    /** 기존 데이터는 덮어쓰지 않고, 아직 없는 이미지 ID만 등록합니다. */
    public static int seedBundledEmoticons(String[] ids, String[] labels,
                                            String[] files) throws SQLException, IOException {
        if (ids.length != labels.length || ids.length != files.length) {
            throw new IllegalArgumentException("이모티콘 목록 길이가 일치하지 않습니다.");
        }
        String sql = "INSERT IGNORE INTO emoticon_assets "
                + "(emoticon_id, display_name, mime_type, image_data) VALUES (?, ?, ?, ?)";
        int inserted = 0;
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.length; i++) {
                String file = files[i];
                String mimeType = file.toLowerCase().endsWith(".gif")
                        ? "image/gif" : "image/png";
                String path = "/ChatProject/emoticons/" + file;
                byte[] bytes = readResource(path);
                if (bytes == null) {
                    throw new IOException("이모티콘 파일을 찾을 수 없습니다: " + path);
                }
                ps.setString(1, ids[i]);
                ps.setString(2, labels[i]);
                ps.setString(3, mimeType);
                ps.setBytes(4, bytes);
                inserted += ps.executeUpdate();
            }
        }
        return inserted;
    }

    public static byte[] loadImage(String emoticonId) throws SQLException {
        String sql = "SELECT image_data FROM emoticon_assets WHERE emoticon_id = ?";
        try (Connection conn = connection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, emoticonId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getBytes(1);
            }
        }
    }

    private static byte[] readResource(String path) throws IOException {
        try (InputStream in = EmoticonDbRepository.class.getResourceAsStream(path)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }
}
