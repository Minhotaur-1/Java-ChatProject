
package ChatProject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Scanner;

public class ProfileManager {

    private static final String URL =
            "jdbc:mysql://localhost:3306/chatdb";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD =
    System.getenv("CHAT_DB_PASSWORD");
            

    // 회원 로그인 확인
    public static boolean login(String id, String password) {

        String sql = "SELECT id FROM users "
                   + "WHERE id = ? AND password = ?";

        try (
            Connection conn = DriverManager.getConnection(
                    URL, DB_USER, DB_PASSWORD);
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, id);
            pstmt.setString(2, password);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    // 회원 프로필 조회
    public static void showProfile(String id) {

        String sql = "SELECT id, nickname, profile_image, "
                   + "zipcode, address, detail_address "
                   + "FROM users WHERE id = ?";

        try (
            Connection conn = DriverManager.getConnection(
                    URL, DB_USER, DB_PASSWORD);
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {

                if (rs.next()) {
                    System.out.println();
                    System.out.println("===== 내 프로필 =====");
                    System.out.println("아이디: " + rs.getString("id"));
                    System.out.println("닉네임: " + rs.getString("nickname"));
                    System.out.println("프로필 사진: "
                            + rs.getString("profile_image"));
                    System.out.println("우편번호: "
                            + rs.getString("zipcode"));
                    System.out.println("기본주소: "
                            + rs.getString("address"));
                    System.out.println("상세주소: "
                            + rs.getString("detail_address"));
                } else {
                    System.out.println("회원 정보를 찾을 수 없습니다.");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);
        UserDAO dao = new UserDAO();

        System.out.println("===== 회원 프로필 관리 =====");

        System.out.print("회원 아이디: ");
        String id = sc.nextLine();

        System.out.print("비밀번호: ");
        String password = sc.nextLine();

        // 로그인 검사
        if (!login(id, password)) {
            System.out.println("로그인 실패!");
            sc.close();
            return;
        }

        System.out.println("로그인 성공!");

        while (true) {

            System.out.println();
            System.out.println("===== 프로필 관리 메뉴 =====");
            System.out.println("1. 내 프로필 조회");
            System.out.println("2. 닉네임 변경");
            System.out.println("3. 프로필 사진 변경");
            System.out.println("4. 주소 변경");
            System.out.println("5. 비밀번호 변경");
            System.out.println("6. 현재 날씨 확인");
            System.out.println("0. 종료");

            System.out.print("메뉴 선택: ");
            String menu = sc.nextLine();

            switch (menu) {

            case "1":
                showProfile(id);
                break;

            case "2":
                System.out.print("새 닉네임: ");
                String nickname = sc.nextLine();

                if (dao.updateNickname(id, nickname)) {
                    showProfile(id);
                }
                break;

            case "3":
                System.out.print("이미지 파일 경로: ");
                String path = sc.nextLine();

                String savedPath =
                        ProfileImageUtil.resizeImage(path);

                if (savedPath != null) {
                    if (dao.updateProfileImage(id, savedPath)) {
                        showProfile(id);
                    }
                }
                break;

            case "4":
                System.out.print("우편번호(5자리): ");
                String zipcode = sc.nextLine();

                if (!zipcode.matches("[0-9]{5}")) {
                    System.out.println("우편번호는 숫자 5자리여야 합니다.");
                    break;
                }

                System.out.print("기본주소: ");
                String address = sc.nextLine();

                System.out.print("상세주소: ");
                String detailAddress = sc.nextLine();

                if (address.trim().isEmpty()
                        || detailAddress.trim().isEmpty()) {
                    System.out.println("주소를 모두 입력해주세요.");
                    break;
                }

                if (dao.updateAddress(
                        id, zipcode, address, detailAddress)) {
                    showProfile(id);
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
                    System.out.println("새 비밀번호가 일치하지 않습니다.");
                    break;
                }

                if (dao.changePassword(
                        id, currentPassword, newPassword)) {
                    System.out.println(
                            "비밀번호가 변경되어 프로그램을 종료합니다.");
                    sc.close();
                    return;
                }
                break;
                

            case "6":
            	System.out.println("현재 날씨를 조회합니다.");
            	WeatherAPITest.main(new String[0]);
            	break;


            case "0":
                System.out.println("프로그램을 종료합니다.");
                sc.close();
                return;

            default:
                System.out.println("올바른 메뉴 번호를 입력해주세요.");
            }
        }
    }
}