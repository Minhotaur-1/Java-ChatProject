
package ChatProject;

import java.util.Scanner;

public class ProfileSettingsMenu {

    // true: 비밀번호 변경으로 재로그인 필요
    // false: 일반적인 메뉴 종료
    public static boolean open(Scanner sc, String id) {

        UserDAO dao = new UserDAO();

        while (true) {

            System.out.println();
            System.out.println("===== 내 프로필 설정 =====");
            System.out.println("1. 내 프로필 조회");
            System.out.println("2. 닉네임 변경");
            System.out.println("3. 프로필 사진 변경");
            System.out.println("4. 주소 변경");
            System.out.println("5. 비밀번호 변경");
            System.out.println("0. 채팅 로비로 돌아가기");
            System.out.print("메뉴 선택: ");

            String menu = sc.nextLine().trim();

            switch (menu) {

            case "1":
                ProfileManager.showProfile(id);
                break;

            case "2":
                System.out.print("새 닉네임: ");
                String nickname = sc.nextLine();

                if (dao.updateNickname(id, nickname)) {
                    ProfileManager.showProfile(id);
                }
                break;

            case "3":
                System.out.print("이미지 파일 경로: ");
                String path = sc.nextLine();

                String savedPath =
                        ProfileImageUtil.resizeImage(path);

                if (savedPath != null) {

                    if (dao.updateProfileImage(id, savedPath)) {
                        ProfileManager.showProfile(id);
                    }
                }
                break;

            case "4":
                System.out.print("우편번호(5자리): ");
                String zipcode = sc.nextLine().trim();

                if (!zipcode.matches("[0-9]{5}")) {
                    System.out.println(
                            "우편번호는 숫자 5자리여야 합니다.");
                    break;
                }

                System.out.print("기본주소: ");
                String address = sc.nextLine();

                System.out.print("상세주소: ");
                String detailAddress = sc.nextLine();

                if (address.trim().isEmpty()
                        || detailAddress.trim().isEmpty()) {

                    System.out.println(
                            "주소를 모두 입력해주세요.");
                    break;
                }

                if (dao.updateAddress(
                        id, zipcode, address, detailAddress)) {

                    ProfileManager.showProfile(id);
                }
                break;

            case "5":
                System.out.print("현재 비밀번호: ");
                String currentPassword = sc.nextLine();

                System.out.print("새 비밀번호: ");
                String newPassword = sc.nextLine();

                System.out.print("새 비밀번호 확인: ");
                String confirmPassword = sc.nextLine();

                if (!newPassword.equals(confirmPassword)) {

                    System.out.println(
                            "새 비밀번호가 일치하지 않습니다.");
                    break;
                }

                if (dao.changePassword(
                        id, currentPassword, newPassword)) {

                    System.out.println(
                            "비밀번호가 변경되었습니다.");
                    System.out.println(
                            "보안을 위해 다시 로그인해주세요.");

                    return true;
                }
                break;

            case "0":
                System.out.println(
                        "채팅 로비로 돌아갑니다.");
                return false;

            default:
                System.out.println(
                        "올바른 메뉴 번호를 입력해주세요.");
            }
        }
    }
}
