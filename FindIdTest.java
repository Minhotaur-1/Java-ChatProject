
package ChatProject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Scanner;
import java.util.Base64;
import java.util.Locale;
import java.security.MessageDigest;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class FindIdTest {

    private static final String URL =
            "jdbc:mysql://localhost:3306/chatdb";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD =
    System.getenv("CHAT_DB_PASSWORD");
            

    // 보안질문 답변이 일치하는지 확인
    public static boolean verifyAnswer(
            String answer, String storedHash) {

        if (storedHash == null) {
            return false;
        }

        try {
            String[] parts = storedHash.split("\\$");

            if (parts.length != 4 ||
                    !parts[0].equals("pbkdf2")) {
                return false;
            }

            int iterations = Integer.parseInt(parts[1]);

            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] originalHash =
                    Base64.getDecoder().decode(parts[3]);

            String normalized =
                    answer.trim().toLowerCase(Locale.ROOT);

            PBEKeySpec spec = new PBEKeySpec(
                    normalized.toCharArray(),
                    salt, iterations, originalHash.length * 8);

            byte[] newHash;

            try {
                SecretKeyFactory factory =
                        SecretKeyFactory.getInstance(
                                "PBKDF2WithHmacSHA256");

                newHash = factory.generateSecret(spec)
                        .getEncoded();
            } finally {
                spec.clearPassword();
            }

            return MessageDigest.isEqual(
                    originalHash, newHash);

        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.println("===== 아이디 찾기 =====");

        System.out.print("가입한 닉네임 입력: ");
        String nickname = sc.nextLine();

        String sql = "SELECT id, recovery_question, "
                + "recovery_answer_hash FROM users "
                + "WHERE nickname = ?";

        try (
            Connection conn = DriverManager.getConnection(
                    URL, DB_USER, DB_PASSWORD);
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, nickname);

            String id;
            String question;
            String savedHash;

            try (ResultSet rs = pstmt.executeQuery()) {

                if (!rs.next()) {
                    System.out.println("회원 정보를 찾을 수 없습니다.");
                    return;
                }

                id = rs.getString("id");
                question = rs.getString("recovery_question");
                savedHash = rs.getString("recovery_answer_hash");

                if (rs.next()) {
                    System.out.println(
                            "동일한 닉네임의 회원이 여러 명입니다.");
                    return;
                }
            }

            if (question == null || savedHash == null) {
                System.out.println("등록된 보안질문이 없습니다.");
                return;
            }

            System.out.println("보안질문: " + question);
            System.out.print("답변 입력: ");
            String answer = sc.nextLine();

            if (verifyAnswer(answer, savedHash)) {
                System.out.println();
                System.out.println("본인 확인 성공!");
                System.out.println("회원 아이디: " + id);
            } else {
                System.out.println("보안질문 답변이 틀렸습니다.");
                System.out.println("아이디 찾기 실패!");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
