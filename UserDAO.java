
package ChatProject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserDAO {

    private String url = "jdbc:mysql://localhost:3306/chatdb";
    private String user = "root";
    private String password = System.getenv("CHAT_DB_PASSWORD");

    // 아이디 중복 확인
    public boolean isDuplicateId(String id) {

        String sql = "SELECT id FROM users WHERE id = ?";

        try (
            Connection conn = DriverManager.getConnection(
                    url, user, password);
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    // 프로필 사진 경로 DB 저장
    public boolean updateProfileImage(String id, String imagePath) {

        String sql = "UPDATE users SET profile_image = ? WHERE id = ?";

        try (
            Connection conn = DriverManager.getConnection(
                    url, user, password);
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, imagePath);
            pstmt.setString(2, id);

            int result = pstmt.executeUpdate();

            if (result > 0) {
                System.out.println("프로필 사진 DB 저장 완료!");
                return true;
            } else {
                System.out.println("해당 회원을 찾을 수 없습니다.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    // 회원 주소 정보 수정
    public boolean updateAddress(String id, String zipcode,
            String address, String detailAddress) {

        String sql = "UPDATE users SET zipcode = ?, "
                + "address = ?, detail_address = ? WHERE id = ?";

        try (
            Connection conn = DriverManager.getConnection(
                    url, user, password);
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, zipcode);
            pstmt.setString(2, address);
            pstmt.setString(3, detailAddress);
            pstmt.setString(4, id);

            int result = pstmt.executeUpdate();

            if (result > 0) {
                System.out.println("주소 정보 DB 저장 완료!");
                return true;
            } else {
                System.out.println("해당 회원을 찾을 수 없습니다.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    // 닉네임 중복 확인
    public boolean isDuplicateNickname(String id, String nickname) {

        String sql = "SELECT id FROM users "
                + "WHERE nickname = ? AND id <> ?";

        try (
            Connection conn = DriverManager.getConnection(
                    url, user, password);
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, nickname);
            pstmt.setString(2, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }

    // 회원 닉네임 변경
    public boolean updateNickname(String id, String newNickname) {

        newNickname = newNickname.trim();

        if (newNickname.isEmpty() || newNickname.length() > 30) {
            System.out.println("닉네임은 1~30자로 입력해주세요.");
            return false;
        }

        if (isDuplicateNickname(id, newNickname)) {
            System.out.println("이미 사용 중인 닉네임입니다.");
            return false;
        }

        String sql = "UPDATE users SET nickname = ? WHERE id = ?";

        try (
            Connection conn = DriverManager.getConnection(
                    url, user, password);
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, newNickname);
            pstmt.setString(2, id);

            int result = pstmt.executeUpdate();

            if (result > 0) {
                System.out.println("닉네임 변경 완료!");
                return true;
            } else {
                System.out.println("회원이 없거나 변경된 내용이 없습니다.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    // 회원 비밀번호 변경
    public boolean changePassword(String id,
            String currentPassword, String newPassword) {

        if (newPassword.length() < 8 || newPassword.length() > 100) {
            System.out.println("새 비밀번호는 8~100자로 입력해주세요.");
            return false;
        }

        if (currentPassword.equals(newPassword)) {
            System.out.println("기존 비밀번호와 다른 비밀번호를 입력해주세요.");
            return false;
        }

        String sql = "UPDATE users SET password = ? "
                + "WHERE id = ? AND password = ?";

        try (
            Connection conn = DriverManager.getConnection(
                    url, user, password);
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, newPassword);
            pstmt.setString(2, id);
            pstmt.setString(3, currentPassword);

            int result = pstmt.executeUpdate();

            if (result > 0) {
                System.out.println("비밀번호 변경 완료!");
                return true;
            } else {
                System.out.println("회원 정보 또는 현재 비밀번호가 올바르지 않습니다.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

}
