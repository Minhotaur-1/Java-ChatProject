
package ChatProject;

import java.util.Scanner;

public class NicknameUpdateTest {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);
        UserDAO dao = new UserDAO();

        System.out.println("=== 회원 닉네임 변경 ===");

        System.out.print("회원 아이디 입력: ");
        String id = sc.nextLine();

        System.out.print("변경할 닉네임 입력: ");
        String newNickname = sc.nextLine();

        boolean result = dao.updateNickname(id, newNickname);

        if (result) {
            System.out.println("닉네임 변경 성공!");
        } else {
            System.out.println("닉네임 변경 실패!");
        }

        sc.close();
    }
}
