
package ChatProject;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Scanner;
import java.util.UUID;
import javax.imageio.ImageIO;

public class ProfileImageUtil {

    // 프로필 사진을 200x200 크기로 변경하고 저장
    public static String resizeImage(String imagePath) {
        try {
            File originalFile = new File(imagePath);
            BufferedImage original = ImageIO.read(originalFile);

            if (original == null) {
                System.out.println("지원하지 않는 이미지입니다.");
                return null;
            }

            Image resized = original.getScaledInstance(
                    200, 200, Image.SCALE_SMOOTH);

            BufferedImage newImage = new BufferedImage(
                    200, 200, BufferedImage.TYPE_INT_RGB);

            Graphics2D g = newImage.createGraphics();
            g.drawImage(resized, 0, 0, null);
            g.dispose();

            // 이미지 저장 폴더 생성
            File folder = new File("profile_images");

            if (!folder.exists()) {
                folder.mkdirs();
            }

            // 중복되지 않는 파일 이름 생성
            String fileName = UUID.randomUUID() + ".jpg";
            File saveFile = new File(folder, fileName);

            // 리사이징한 이미지 저장
            ImageIO.write(newImage, "jpg", saveFile);

            System.out.println("이미지 저장 완료!");
            System.out.println("저장 위치: "
                    + saveFile.getAbsolutePath());

            return saveFile.getPath();

        } catch (Exception e) {
            System.out.println("이미지 처리 오류: "
                    + e.getMessage());
            return null;
        }
    }

    // 프로필 사진 등록 테스트
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("=== 프로필 사진 등록 ===");

        // 회원 아이디 입력
        System.out.print("회원 아이디 입력: ");
        String id = sc.nextLine();

        // 이미지 경로 입력
        System.out.print("이미지 파일 경로 입력: ");
        String path = sc.nextLine();

        // 이미지 크기 변경 및 저장
        String savedPath = resizeImage(path);

        if (savedPath != null) {

            // MySQL 데이터베이스에 이미지 경로 저장
            UserDAO dao = new UserDAO();

            boolean result =
                    dao.updateProfileImage(id, savedPath);

            if (result) {
                System.out.println("프로필 사진 등록 성공!");
            } else {
                System.out.println("프로필 사진 등록 실패!");
            }

        } else {
            System.out.println("이미지 업로드에 실패했습니다.");
        }

        sc.close();
    }
}
