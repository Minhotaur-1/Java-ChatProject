package ChatProject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class IdCheckServer {

    public static void main(String[] args) {

        try {

            ServerSocket serverSocket = new ServerSocket(5000);

            System.out.println("서버 실행 중...");
            System.out.println("클라이언트 접속 대기 중...");

            Socket socket = serverSocket.accept();

            System.out.println("클라이언트가 접속했습니다.");

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(socket.getInputStream())
                    );

            PrintWriter writer =
                    new PrintWriter(socket.getOutputStream(), true);

            String id = reader.readLine();

            UserDAO dao = new UserDAO();

            boolean result = dao.isDuplicateId(id);

            writer.println(result);

            reader.close();
            writer.close();
            socket.close();
            serverSocket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}