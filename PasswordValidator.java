package ChatProject;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class PasswordValidator {

    public static String validatePassword(String password) {

        if (password.length() < 8) {
            return "비밀번호는 8자 이상이어야 합니다.";
        }

        Pattern letterPattern = Pattern.compile("[A-Za-z]");
        Matcher letterMatcher = letterPattern.matcher(password);

        if (!letterMatcher.find()) {
            return "영문자를 포함해야 합니다.";
        }

        Pattern numberPattern = Pattern.compile("[0-9]");
        Matcher numberMatcher = numberPattern.matcher(password);

        if (!numberMatcher.find()) {
            return "숫자를 포함해야 합니다.";
        }

        Pattern specialPattern = Pattern.compile("[!@#$%^&*()]");
        Matcher specialMatcher = specialPattern.matcher(password);

        if (!specialMatcher.find()) {
            return "특수문자를 포함해야 합니다.";
        }

        return "사용 가능한 비밀번호입니다.";
    }
}