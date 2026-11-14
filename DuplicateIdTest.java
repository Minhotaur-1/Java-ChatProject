package ChatProject;

public class DuplicateIdTest {

    public static void main(String[] args) {

        UserDAO dao = new UserDAO();

        String id = "hello123";

        boolean result = dao.isDuplicateId(id);

        if (result) {
            System.out.println("이미 사용 중인 아이디입니다.");
        } else {
            System.out.println("사용 가능한 아이디입니다.");
        }
    }
}