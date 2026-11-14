package ChatProject;

public class PasswordTest {

    public static void main(String[] args) {

    	String password = "abc123";

        String result =
                PasswordValidator.validatePassword(password);

        System.out.println(result);
    }
}