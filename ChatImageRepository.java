package ChatProject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * 11주차 2단계: 같은 채팅방에서 전송된 이미지 데이터를 chat_images에 보관하고 조회합니다.
 * PNG/JPEG/GIF만 갤러리에 자동 저장하며 나머지 파일은 기존 파일 전송으로만 처리합니다.
 */
public final class ChatImageRepository {
    /** 이미지 갤러리 저장 한도. 일반 파일 전송 한도는 별도로 20MB 그대로 유지. */
    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final String URL = "jdbc:mysql://localhost:3306/chatdb";
    private static final String USER = "root";

    private ChatImageRepository() { }

    private static Connection open() throws SQLException {
        String password = System.getenv("CHAT_DB_PASSWORD");
        if (password == null || password.isEmpty()) {
            throw new SQLException("CHAT_DB_PASSWORD 환경변수가 없습니다.");
        }
        return DriverManager.getConnection(URL, USER, password);
    }

    /** 파일명을 보고 이미지 저장 후보인지 결정합니다. (내용 판별은 별도) */
    public static boolean isCandidate(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".gif");
    }

    /** 확장자와 실제 이미지 형식을 검사합니다. 정상적인 이미지 외에는 null을 반환합니다. */
    public static String verifiedMime(String filename, byte[] bytes) {
        if (!isCandidate(filename) || bytes == null || bytes.length < 8
                || bytes.length > MAX_IMAGE_BYTES) return null;

        String name = filename.toLowerCase(java.util.Locale.ROOT);
        String mime = null;
        if (bytes.length >= 8 && (bytes[0] & 255) == 0x89
                && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && (bytes[4] & 255) == 13 && (bytes[5] & 255) == 10
                && (bytes[6] & 255) == 26 && (bytes[7] & 255) == 10
                && name.endsWith(".png")) {
            mime = "image/png";
        } else if (bytes.length >= 3 && (bytes[0] & 255) == 0xFF
                && (bytes[1] & 255) == 0xD8 && (bytes[2] & 255) == 0xFF
                && (name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
            mime = "image/jpeg";
        } else if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I'
                && bytes[2] == 'F' && bytes[3] == '8'
                && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a'
                && name.endsWith(".gif")) {
            mime = "image/gif";
        }
        if (mime == null) return null;

        // 확장자/헤더 위조 및 과도한 이미지 해상도 방어.
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) return null;
            java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 8192 || height > 8192
                        || (long) width * height > 24_000_000L) return null;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            return null;
        }
        return mime;
    }

    // 채팅 기록에 이미지 내용 자체(바이너리)를 넣지 않고 이미지 번호만 저장합니다.
    // 클라이언트에는 이 특수 마커가 이미지 미리보기로 표시됩니다.
    private static final String IMAGE_PREFIX = "[[CHATPROJECT_IMAGE:";
    private static final String IMAGE_SUFFIX = "]]";

    public static String imageMessage(long imageId) {
        if (imageId < 1) throw new IllegalArgumentException("이미지 번호가 올바르지 않습니다.");
        return IMAGE_PREFIX + imageId + IMAGE_SUFFIX;
    }

    /** 일반 텍스트에는 반응하지 않고 완전히 일치하는 이미지 마커만 인식합니다. */
    public static long imageIdFromMessage(String content) {
        if (content == null || !content.startsWith(IMAGE_PREFIX)
                || !content.endsWith(IMAGE_SUFFIX)) return -1;
        String digits = content.substring(IMAGE_PREFIX.length(),
                content.length() - IMAGE_SUFFIX.length());
        if (!digits.matches("[1-9][0-9]{0,18}")) return -1;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException ex) { return -1; }
    }

    /** 트랜잭션으로 이미지 원본과 그 이미지를 가리키는 채팅 한 건을 함께 저장합니다. */
    public static final class SavedImageMessage {
        public final long imageId;
        public final long messageId;
        SavedImageMessage(long imageId, long messageId) {
            this.imageId = imageId;
            this.messageId = messageId;
        }
    }

    public static SavedImageMessage saveImageWithChatMessage(int roomId, String senderId,
            String name, String mime, byte[] data) throws SQLException {
        if (roomId <= 0 || senderId == null || name == null || mime == null
                || !mime.equals(verifiedMime(name, data))) {
            throw new IllegalArgumentException("이미지 저장 정보가 올바르지 않습니다.");
        }

        String insertImage = "INSERT INTO chat_images "
                + "(room_id, sender_id, original_name, mime_type, image_data) "
                + "SELECT ?, ?, ?, ?, ? FROM DUAL WHERE EXISTS "
                + "(SELECT 1 FROM room_members WHERE room_id = ? AND user_id = ?)";
        String insertMessage = "INSERT INTO chat_message "
                + "(room_id, sender_id, content) VALUES (?, ?, ?)";
        try (Connection conn = open()) {
            conn.setAutoCommit(false);
            try {
                long imageId;
                try (PreparedStatement ps = conn.prepareStatement(
                        insertImage, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, roomId);
                    ps.setString(2, senderId);
                    ps.setString(3, name);
                    ps.setString(4, mime);
                    ps.setBytes(5, data);
                    ps.setInt(6, roomId);
                    ps.setString(7, senderId);
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("이미지를 보낸 사람이 이 채팅방의 회원이 아닙니다.");
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("이미지 번호 생성에 실패했습니다.");
                        imageId = keys.getLong(1);
                    }
                }
                long messageId;
                try (PreparedStatement ps = conn.prepareStatement(
                        insertMessage, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, roomId);
                    ps.setString(2, senderId);
                    ps.setString(3, imageMessage(imageId));
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("이미지 채팅 기록 생성에 실패했습니다.");
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("채팅 기록 번호 생성에 실패했습니다.");
                        messageId = keys.getLong(1);
                    }
                }
                conn.commit();
                return new SavedImageMessage(imageId, messageId);
            } catch (SQLException | RuntimeException ex) {
                try { conn.rollback(); } catch (SQLException rollback) {
                    ex.addSuppressed(rollback);
                }
                throw ex;
            }
        }
    }

    /** 서버에서 전송 완료 및 참여자 확인 후 호출. 반환값은 생성된 이미지 번호입니다. */
    public static long saveImage(int roomId, String senderId, String name,
                                 String mime, byte[] data) throws SQLException {
        if (roomId <= 0 || senderId == null || name == null
                || !mime.equals(verifiedMime(name, data))) {
            throw new IllegalArgumentException("갤러리 저장용 이미지 형식이 올바르지 않습니다.");
        }
        String sql = "INSERT INTO chat_images "
                + "(room_id, sender_id, original_name, mime_type, image_data) "
                + "SELECT ?, ?, ?, ?, ? FROM DUAL WHERE EXISTS "
                + "(SELECT 1 FROM room_members WHERE room_id = ? AND user_id = ?)";
        try (Connection conn = open();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, roomId);
            ps.setString(2, senderId);
            ps.setString(3, name);
            ps.setString(4, mime);
            ps.setBytes(5, data);
            ps.setInt(6, roomId);
            ps.setString(7, senderId);
            if (ps.executeUpdate() != 1) throw new SQLException("채팅방 참여자가 아니어서 저장할 수 없습니다.");
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("이미지 저장 번호를 받을 수 없습니다.");
    }

    public static final class ImageInfo {
        public final long id;
        public final String name;
        public final String sender;
        public final String mime;
        public final long bytes;
        public final Timestamp uploadedAt;

        ImageInfo(long id, String name, String sender, String mime,
                  long bytes, Timestamp uploadedAt) {
            this.id = id;
            this.name = name;
            this.sender = sender;
            this.mime = mime;
            this.bytes = bytes;
            this.uploadedAt = uploadedAt;
        }
    }

    /** 해당 방에 가입한 사용자만 이미지 목록 조회. 이미지 바이너리는 이 단계에서 읽지 않음. */
    public static List<ImageInfo> listImages(int roomId, String viewerId) throws SQLException {
        String sql = "SELECT i.image_id, i.original_name, i.sender_id, i.mime_type, "
                + "OCTET_LENGTH(i.image_data) AS byte_count, i.uploaded_at "
                + "FROM chat_images i WHERE i.room_id = ? AND EXISTS "
                + "(SELECT 1 FROM room_members m WHERE m.room_id = i.room_id AND m.user_id = ?) "
                + "ORDER BY i.image_id DESC LIMIT 48";
        List<ImageInfo> result = new ArrayList<>();
        try (Connection conn = open(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            ps.setString(2, viewerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ImageInfo(rs.getLong(1), rs.getString(2),
                            rs.getString(3), rs.getString(4), rs.getLong(5),
                            rs.getTimestamp(6)));
                }
            }
        }
        return result;
    }

    /** 현재 채팅방 참여 여부를 SQL로 검증하면서 이미지 원본을 읽습니다. */
    public static byte[] loadImage(int roomId, String viewerId, long imageId)
            throws SQLException {
        String sql = "SELECT i.image_data FROM chat_images i WHERE i.image_id = ? "
                + "AND i.room_id = ? AND EXISTS (SELECT 1 FROM room_members m "
                + "WHERE m.room_id = i.room_id AND m.user_id = ?)";
        try (Connection conn = open(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, imageId);
            ps.setInt(2, roomId);
            ps.setString(3, viewerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBytes(1) : null;
            }
        }
    }
}
