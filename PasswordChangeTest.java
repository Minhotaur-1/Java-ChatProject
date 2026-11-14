
package ChatProject;

import java.util.Scanner;

public class PasswordChangeTest {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);
        UserDAO dao = new UserDAO();

        System.out.println("=== 회원 비밀번호 변경 ===");

        // 회원 아이디 입력
        System.out.print("회원 아이디 입력: ");
        String id = sc.nextLine();

        // 현재 비밀번호 확인
        System.out.print("현재 비밀번호 입력: ");
        String currentPassword = sc.nextLine();

        // 새 비밀번호 입력
        System.out.print("새 비밀번호 입력: ");
        String newPassword = sc.nextLine();

        // 새 비밀번호 재확인
        System.out.print("새 비밀번호 확인: ");
        String confirmPassword = sc.nextLine();

        if (!newPassword.equals(confirmPassword)) {
            System.out.println("새 비밀번호가 일치하지 않습니다.");
            sc.close();
            return;
        }

        // MySQL 데이터베이스에서 비밀번호 변경
        boolean result = dao.changePassword(
                id, currentPassword, newPassword);

        if (result) {
            System.out.println("비밀번호 변경 성공!");
        } else {
            System.out.println("비밀번호 변경 실패!");
        }

        sc.close();
    }
}
