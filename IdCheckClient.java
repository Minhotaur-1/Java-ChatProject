package ChatProject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class IdCheckClient {

    public static void main(String[] args) {

        try {

            Socket socket = new Socket("localhost", 5000);

            System.out.println("서버에 연결되었습니다.");

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(socket.getInputStream())
                    );

            PrintWriter writer =
                    new PrintWriter(socket.getOutputStream(), true);

            String id = "hello123";

            writer.println(id);

            String result = reader.readLine();

            if (Boolean.parseBoolean(result)) {
                System.out.println("이미 사용 중인 아이디입니다.");
            } else {
                System.out.println("사용 가능한 아이디입니다.");
            }

            reader.close();
            writer.close();
            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}