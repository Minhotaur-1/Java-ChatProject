
package ChatProject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Scanner;

public class PasswordRecoveryTest {

    private static final String URL =
            "jdbc:mysql://localhost:3306/chatdb";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD =
    System.getenv("CHAT_DB_PASSWORD");
            

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.println("===== 비밀번호 찾기 =====");

        System.out.print("회원 아이디 입력: ");
        String id = sc.nextLine();

        String sql = "SELECT recovery_question, "
                + "recovery_answer_hash FROM users WHERE id = ?";

        try (
            Connection conn = DriverManager.getConnection(
                    URL, DB_USER, DB_PASSWORD);
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, id);

            String question;
            String savedHash;

            try (ResultSet rs = pstmt.executeQuery()) {

                if (!rs.next()) {
                    System.out.println("회원 정보를 찾을 수 없습니다.");
                    return;
                }

                question = rs.getString("recovery_question");
                savedHash = rs.getString("recovery_answer_hash");
            }

            if (question == null || savedHash == null) {
                System.out.println("등록된 보안질문이 없습니다.");
                return;
            }

            System.out.println("보안질문: " + question);
            System.out.print("보안질문 답변: ");
            String answer = sc.nextLine();

            // 보안질문 답변 확인
            if (!FindIdTest.verifyAnswer(answer, savedHash)) {
                System.out.println("보안질문 답변이 틀렸습니다.");
                System.out.println("비밀번호 재설정 실패!");
                return;
            }

            System.out.println("본인 확인 성공!");

            System.out.print("새 비밀번호 입력: ");
            String newPassword = sc.nextLine();

            System.out.print("새 비밀번호 확인: ");
            String confirmPassword = sc.nextLine();

            if (!newPassword.equals(confirmPassword)) {
                System.out.println("새 비밀번호가 일치하지 않습니다.");
                return;
            }

            if (newPassword.length() < 8
                    || newPassword.length() > 100) {
                System.out.println(
                        "비밀번호는 8~100자로 입력해주세요.");
                return;
            }

            // MySQL 비밀번호 변경
            String updateSql = "UPDATE users SET password = ? "
                    + "WHERE id = ? AND recovery_answer_hash = ?";

            try (PreparedStatement update =
                    conn.prepareStatement(updateSql)) {

                update.setString(1, newPassword);
                update.setString(2, id);
                update.setString(3, savedHash);

                int result = update.executeUpdate();

                if (result > 0) {
                    System.out.println("비밀번호 재설정 완료!");
                    System.out.println("새 비밀번호로 로그인할 수 있습니다.");
                } else {
                    System.out.println("비밀번호 재설정 실패!");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            sc.close();
        }
    }
}
	