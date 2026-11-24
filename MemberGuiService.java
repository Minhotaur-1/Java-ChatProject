package ChatProject;

import java.sql.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** 기존 chatdb 회원/보안질문 구조를 GUI에서 재사용하는 JDBC 서비스. */
public final class MemberGuiService {
    private static final String URL = "jdbc:mysql://localhost:3306/chatdb";
    private MemberGuiService() {}
    public static Connection connection() throws SQLException {
        String password = System.getenv("CHAT_DB_PASSWORD");
        if (password == null || password.isEmpty())
            throw new SQLException("Eclipse 환경변수 CHAT_DB_PASSWORD가 없습니다.");
        return DriverManager.getConnection(URL, "root", password);
    }
    private static boolean exists(Connection c, String sql, String v) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, v);
            try (ResultSet r = p.executeQuery()) { return r.next(); }
        }
    }
    public static boolean idExists(String id) throws SQLException {
        try (Connection c = connection()) { return exists(c,"SELECT 1 FROM users WHERE id=?",id); }
    }
    public static boolean nicknameExists(String nickname) throws SQLException {
        try (Connection c = connection()) { return exists(c,"SELECT 1 FROM users WHERE nickname=?",nickname); }
    }
    private static String hashAnswer(String answer) throws Exception {
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        int rounds=120000;
        PBEKeySpec spec=new PBEKeySpec(answer.trim().toLowerCase(Locale.ROOT).toCharArray(),salt,rounds,256);
        try {
            byte[] hash=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return "pbkdf2$"+rounds+"$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(hash);
        } finally { spec.clearPassword(); }
    }
    public static void register(String id,String pw,String nickname,String zip,String addr,String detail,String question,String answer) throws Exception {
        if(!id.matches("[A-Za-z0-9_]{3,30}")) throw new IllegalArgumentException("아이디: 영문·숫자·밑줄 3~30자");
        if(pw.length()<8 || pw.length()>100) throw new IllegalArgumentException("비밀번호: 8~100자");
        if(nickname.trim().isEmpty() || nickname.length()>30) throw new IllegalArgumentException("닉네임: 1~30자");
        if(!zip.matches("[0-9]{5}")) throw new IllegalArgumentException("우편번호: 숫자 5자리");
        if(addr.trim().isEmpty() || detail.trim().isEmpty() || addr.length()>255 || detail.length()>255)
            throw new IllegalArgumentException("주소와 상세주소를 255자 이내로 입력해주세요.");
        if(question.trim().isEmpty() || question.length()>100 || answer.trim().isEmpty())
            throw new IllegalArgumentException("보안질문(100자 이내)과 답변을 입력해주세요.");
        try(Connection c=connection()) {
            if(exists(c,"SELECT 1 FROM users WHERE id=?",id)) throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
            if(exists(c,"SELECT 1 FROM users WHERE nickname=?",nickname)) throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
            String sql="INSERT INTO users(id,password,nickname,zipcode,address,detail_address,recovery_question,recovery_answer_hash) VALUES(?,?,?,?,?,?,?,?)";
            try(PreparedStatement p=c.prepareStatement(sql)) {
                p.setString(1,id);p.setString(2,pw);p.setString(3,nickname);p.setString(4,zip);
                p.setString(5,addr);p.setString(6,detail);p.setString(7,question);p.setString(8,hashAnswer(answer));
                p.executeUpdate();
            }
        }
    }
    public static String findId(String nickname,String answer) throws Exception {
        try(Connection c=connection();PreparedStatement p=c.prepareStatement(
            "SELECT id,recovery_answer_hash FROM users WHERE nickname=?")) {
            p.setString(1,nickname);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) throw new IllegalArgumentException("일치하는 회원이 없습니다.");
                String id=r.getString(1), hash=r.getString(2);
                if(r.next()) throw new IllegalArgumentException("동일 닉네임 회원이 여러 명입니다.");
                if(!FindIdTest.verifyAnswer(answer,hash)) throw new IllegalArgumentException("보안질문 답변이 올바르지 않습니다.");
                return id;
            }
        }
    }
    public static String getQuestionByNickname(String nickname) throws SQLException {
        try(Connection c=connection();PreparedStatement p=c.prepareStatement("SELECT recovery_question FROM users WHERE nickname=?")) {
            p.setString(1,nickname);try(ResultSet r=p.executeQuery()) { return r.next()?r.getString(1):null; }
        }
    }
    public static String getQuestionById(String id) throws SQLException {
        try(Connection c=connection();PreparedStatement p=c.prepareStatement("SELECT recovery_question FROM users WHERE id=?")) {
            p.setString(1,id);try(ResultSet r=p.executeQuery()) { return r.next()?r.getString(1):null; }
        }
    }
    public static void resetPassword(String id,String answer,String newPassword) throws Exception {
        if(newPassword.length()<8||newPassword.length()>100) throw new IllegalArgumentException("비밀번호: 8~100자");
        try(Connection c=connection()) {
            String hash;
            try(PreparedStatement p=c.prepareStatement("SELECT recovery_answer_hash FROM users WHERE id=?")) {
                p.setString(1,id);try(ResultSet r=p.executeQuery()) {
                    if(!r.next()) throw new IllegalArgumentException("존재하지 않는 아이디입니다.");
                    hash=r.getString(1);
                }
            }
            if(!FindIdTest.verifyAnswer(answer,hash)) throw new IllegalArgumentException("보안질문 답변이 올바르지 않습니다.");
            try(PreparedStatement p=c.prepareStatement("UPDATE users SET password=? WHERE id=? AND recovery_answer_hash=?")) {
                p.setString(1,newPassword);p.setString(2,id);p.setString(3,hash);
                if(p.executeUpdate()!=1) throw new SQLException("비밀번호 변경에 실패했습니다.");
            }
        }
    }
}
