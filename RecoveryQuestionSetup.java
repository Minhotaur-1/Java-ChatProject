
package ChatProject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Base64;
import java.util.Locale;
import java.util.Scanner;

import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class RecoveryQuestionSetup {

    private static final String URL =
            "jdbc:mysql://localhost:3306/chatdb";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD =
    System.getenv("CHAT_DB_PASSWORD");
           

    // 보안질문 답변을 해시로 변환
    public static String hashAnswer(String answer) throws Exception {

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        int iterations = 120000;

        String normalized =
                answer.trim().toLowerCase(Locale.ROOT);

        PBEKeySpec spec = new PBEKeySpec(
                normalized.toCharArray(), salt, iterations, 256);

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance(
                            "PBKDF2WithHmacSHA256");

            byte[] hash = factory.generateSecret(spec).getEncoded();

            return "pbkdf2$" + iterations + "$"
                    + Base64.getEncoder().encodeToString(salt)
                    + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } finally {
            spec.clearPassword();
        }
    }

    // 회원 보안질문 등록
    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.println("===== 보안질문 등록 =====");

        System.out.print("회원 아이디: ");
        String id = sc.nextLine();

        System.out.print("현재 회원 비밀번호: ");
        String password = sc.nextLine();

        System.out.print("보안질문 입력: ");
        String question = sc.nextLine();

        System.out.print("보안질문 답변: ");
        String answer = sc.nextLine();

        if (question.trim().isEmpty()
                || question.length() > 100
                || answer.trim().length() < 4) {

            System.out.println("질문은 1~100자, 답변은 4자 이상 입력해주세요.");
            sc.close();
            return;
        }

        String sql = "UPDATE users SET "
                + "recovery_question = ?, "
                + "recovery_answer_hash = ? "
                + "WHERE id = ? AND password = ?";

        try (
            Connection conn = DriverManager.getConnection(
                    URL, DB_USER, DB_PASSWORD);
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {

            String hashedAnswer = hashAnswer(answer);

            pstmt.setString(1, question);
            pstmt.setString(2, hashedAnswer);
            pstmt.setString(3, id);
            pstmt.setString(4, password);

            int result = pstmt.executeUpdate();

            if (result > 0) {
                System.out.println("보안질문 등록 성공!");
                System.out.println("답변은 해시로 저장되었습니다.");
            } else {
                System.out.println(
                        "회원 아이디 또는 비밀번호가 올바르지 않습니다.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        sc.close();
    }
}
