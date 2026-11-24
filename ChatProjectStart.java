package ChatProject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Scanner;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** 9주차 통합 시작 메뉴: 회원가입 / 로그인 / 계정 찾기 / 채팅 로비 */
public class ChatProjectStart {

    private static final String URL = "jdbc:mysql://localhost:3306/chatdb";
    private static final String DB_USER = "root";

    public static void main(String[] args) {
        // System.in은 프로그램 전체에서 한 Scanner로 관리한다.
        Scanner sc = new Scanner(System.in);

        if (System.getenv("CHAT_DB_PASSWORD") == null) {
            System.out.println("[안내] Eclipse 실행 환경변수 CHAT_DB_PASSWORD를 설정하세요.");
            return;
        }

        while (true) {
            System.out.println();
            System.out.println("===== ChatProject =====");
            System.out.println("1. 로그인 및 채팅 로비 입장");
            System.out.println("2. 회원가입");
            System.out.println("3. 아이디 찾기");
            System.out.println("4. 비밀번호 찾기");
            System.out.println("0. 종료");
            System.out.print("메뉴 선택: ");

            String menu = sc.nextLine().trim();

            switch (menu) {
                case "1":
                    // ChatLobbyClient가 DB 로그인 검사 후 서버와 연결한다.
                    ChatLobbyClient.start(sc);
                    break;
                case "2":
                    register(sc);
                    break;
                case "3":
                    FindIdTest.run(sc);
                    break;
                case "4":
                    PasswordRecoveryTest.run(sc);
                    break;
                case "0":
                    System.out.println("프로그램을 종료합니다.");
                    return;
                default:
                    System.out.println("올바른 메뉴 번호를 입력해주세요.");
            }
        }
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                URL, DB_USER, System.getenv("CHAT_DB_PASSWORD"));
    }

    private static void register(Scanner sc) {
        System.out.println();
        System.out.println("===== 회원가입 =====");

        System.out.print("사용할 아이디 (영문/숫자/밑줄, 3~30자): ");
        String id = sc.nextLine().trim();
        if (!id.matches("[A-Za-z0-9_]{3,30}")) {
            System.out.println("아이디 형식이 올바르지 않습니다.");
            return;
        }

        // 데이터베이스에 문제가 있으면 중복 검사 실패를 '사용 가능'으로 처리하지 않는다.
        try (Connection conn = getConnection()) {
            if (exists(conn, "SELECT 1 FROM users WHERE id = ?", id)) {
                System.out.println("이미 사용 중인 아이디입니다.");
                return;
            }

            System.out.print("비밀번호 (8~100자): ");
            String password = sc.nextLine();
            System.out.print("비밀번호 확인: ");
            String confirm = sc.nextLine();

            if (password.length() < 8 || password.length() > 100
                    || !password.equals(confirm)) {
                System.out.println("비밀번호 길이 또는 일치 여부를 확인해주세요.");
                return;
            }

            System.out.print("닉네임 (1~30자): ");
            String nickname = sc.nextLine().trim();
            if (nickname.isEmpty() || nickname.length() > 30) {
                System.out.println("닉네임은 1~30자로 입력해주세요.");
                return;
            }
            if (exists(conn, "SELECT 1 FROM users WHERE nickname = ?", nickname)) {
                System.out.println("이미 사용 중인 닉네임입니다.");
                return;
            }

            System.out.print("우편번호 (숫자 5자리): ");
            String zipcode = sc.nextLine().trim();
            if (!zipcode.matches("[0-9]{5}")) {
                System.out.println("우편번호는 숫자 5자리여야 합니다.");
                return;
            }

            System.out.print("기본주소: ");
            String address = sc.nextLine().trim();
            System.out.print("상세주소: ");
            String detailAddress = sc.nextLine().trim();
            if (address.isEmpty() || detailAddress.isEmpty()
                    || address.length() > 255 || detailAddress.length() > 255) {
                System.out.println("주소를 입력해주세요 (각 255자 이하).");
                return;
            }

            // 기존 7주차 아이디/비밀번호 찾기와 호환되는 보안질문 등록
            System.out.print("보안질문 (예: 내가 좋아하는 색은?): ");
            String question = sc.nextLine().trim();
            System.out.print("보안질문 답변: ");
            String answer = sc.nextLine().trim();
            if (question.isEmpty() || question.length() > 100 || answer.isEmpty()) {
                System.out.println("보안질문(100자 이하)과 답변을 입력해주세요.");
                return;
            }

            System.out.print("프로필 이미지 파일 경로 (없으면 Enter): ");
            String path = sc.nextLine().trim();
            String imagePath = null;
            if (!path.isEmpty()) {
                imagePath = ProfileImageUtil.resizeImage(path);
                if (imagePath == null) {
                    System.out.println("이미지를 처리하지 못해 회원가입을 취소했습니다.");
                    return;
                }
            }

            String answerHash = hashRecoveryAnswer(answer);
            String sql = "INSERT INTO users "
                    + "(id, password, nickname, profile_image, zipcode, address, "
                    + "detail_address, recovery_question, recovery_answer_hash) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, id);
                // 현재 프로젝트의 ProfileManager.login()이 평문을 비교하므로
                // 9주차 호환성을 위해 기존 저장 방식 사용. 배포 전 변경 필요.
                pstmt.setString(2, password);
                pstmt.setString(3, nickname);
                pstmt.setString(4, imagePath);
                pstmt.setString(5, zipcode);
                pstmt.setString(6, address);
                pstmt.setString(7, detailAddress);
                pstmt.setString(8, question);
                pstmt.setString(9, answerHash);

                if (pstmt.executeUpdate() == 1) {
                    System.out.println("회원가입 성공! 1번 메뉴에서 로그인해주세요.");
                }
            }

        } catch (SQLException e) {
            System.out.println("회원가입 DB 오류: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("회원가입 처리 오류: " + e.getMessage());
        }
    }

    private static boolean exists(Connection conn, String sql, String value)
            throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, value);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // FindIdTest.verifyAnswer()와 동일한 포맷 및 정규화 규칙
    private static String hashRecoveryAnswer(String answer) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        int iterations = 120_000;
        String normalized = answer.trim().toLowerCase(Locale.ROOT);
        PBEKeySpec spec = new PBEKeySpec(
                normalized.toCharArray(), salt, iterations, 256);
        try {
            byte[] hash = SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return "pbkdf2$" + iterations + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } finally {
            spec.clearPassword();
        }
    }
}
