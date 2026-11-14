
package ChatProject;

import java.util.Scanner;

public class AddressInputTest {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.println("=== 회원 주소 입력 ===");

        System.out.print("회원 아이디 입력: ");
        String id = sc.nextLine();

        System.out.print("우편번호 입력 (5자리): ");
        String zipcode = sc.nextLine();

        if (!zipcode.matches("[0-9]{5}")) {
            System.out.println("우편번호는 숫자 5자리로 입력해주세요.");
            sc.close();
            return;
        }

        System.out.print("기본주소 입력: ");
        String address = sc.nextLine();

        System.out.print("상세주소 입력: ");
        String detailAddress = sc.nextLine();

        if (address.trim().isEmpty()
                || detailAddress.trim().isEmpty()) {
            System.out.println("주소를 모두 입력해주세요.");
            sc.close();
            return;
        }

        // MySQL 데이터베이스에 주소 저장
        UserDAO dao = new UserDAO();

        boolean result = dao.updateAddress(
                id, zipcode, address, detailAddress);

        if (result) {
            System.out.println();
            System.out.println("=== 주소 등록 결과 ===");
            System.out.println("회원 아이디: " + id);
            System.out.println("우편번호: " + zipcode);
            System.out.println("기본주소: " + address);
            System.out.println("상세주소: " + detailAddress);
            System.out.println("주소 등록 성공!");
        } else {
            System.out.println("주소 등록 실패!");
        }

        sc.close();
    }
}
