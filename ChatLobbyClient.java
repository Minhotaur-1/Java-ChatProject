
package ChatProject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ChatLobbyClient {

    private static final String HOST = "localhost";
    private static final int PORT = 5000;

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.println("===== 채팅 로비 클라이언트 =====");
        System.out.print("사용자 아이디 입력: ");

        String id = sc.nextLine().trim();

        if (id.isEmpty()) {
            System.out.println("아이디를 입력해주세요.");
            return;
        }

        try (
            Socket socket = new Socket(HOST, PORT);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream(),
                            StandardCharsets.UTF_8));

            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(
                            socket.getOutputStream(),
                            StandardCharsets.UTF_8), true)
        ) {

            System.out.println("서버 연결 성공!");
            System.out.println("서버 메시지: " + in.readLine());

            // 서버에 사용자 등록
            out.println("LOGIN|" + id);

            String response = in.readLine();

            if (!"LOGIN_OK".equals(response)) {
                System.out.println("접속 실패: " + response);
                return;
            }

            System.out.println("로그인 등록 성공: " + id);

            // 서버 메시지 수신 스레드
            Thread receiver = new Thread(() -> {

                try {
                    String message;

                    while ((message = in.readLine()) != null) {

                        // 접속자 변경 알림
                        if (message.equals("ONLINE_UPDATE")) {

                            System.out.println();
                            System.out.println(
                                    "[알림] 접속자 목록이 변경되었습니다.");
                        }

                        // 채팅방 목록
                        else if (message.equals("ROOMS_BEGIN")) {

                            System.out.println();
                            System.out.println(
                                    "===== 채팅방 목록 =====");
                        }

                        else if (message.startsWith("ROOM|")) {

                            String[] data = message.split("\\|", 5);

                            if (data.length == 5) {
                                System.out.println();
                                System.out.println(
                                        "[" + data[1] + "번] "
                                        + data[2]);

                                System.out.println(
                                        "채팅 유형: " + data[3]);

                                System.out.println(
                                        "참여 인원: " + data[4] + "명");
                            }
                        }

                        else if (message.equals("ROOMS_END")) {

                            System.out.println();
                            System.out.println(
                                    "채팅방 목록 조회 완료!");
                        }

                        else if (message.equals("ROOMS_ERROR")) {

                            System.out.println(
                                    "채팅방 목록 조회 실패!");
                        }

                        // 채팅방 입장
                        else if (message.startsWith("JOIN_OK|")) {

                            String roomId = message.substring(8);

                            System.out.println();
                            System.out.println(
                                    "[알림] " + roomId
                                    + "번 채팅방에 입장했습니다.");
                        }

                        // 귓속말 수신
                        else if (message.startsWith("WHISPER_FROM|")) {

                            String[] data = message.split("\\|", 3);

                            if (data.length == 3) {
                                System.out.println();
                                System.out.println(
                                        "[귓속말] " + data[1]
                                        + ": " + data[2]);
                            }
                        }

                        // 귓속말 전송 완료
                        else if (message.startsWith("WHISPER_SENT|")) {

                            String[] data = message.split("\\|", 3);

                            if (data.length == 3) {
                                System.out.println(
                                        "[귓속말 전송 완료] "
                                        + data[1] + "에게: "
                                        + data[2]);
                            }
                        }

                        // 초대 가능 회원 목록 시작
                        else if (message.equals("INVITE_LIST_BEGIN")) {

                            System.out.println();
                            System.out.println(
                                    "===== 초대 가능한 회원 =====");
                        }

                        // 초대 가능한 회원
                        else if (message.startsWith("INVITE_USER|")) {

                            String userId = message.substring(12);
                            System.out.println("- " + userId);
                        }

                        // 초대 가능 회원 목록 끝
                        else if (message.equals("INVITE_LIST_END")) {

                            System.out.println(
                                    "===== 목록 조회 완료 =====");
                        }

                        // 초대 전송 완료
                        else if (message.startsWith("INVITE_SENT|")) {

                            String target = message.substring(12);

                            System.out.println();
                            System.out.println(
                                    "[초대] " + target
                                    + "님에게 초대를 보냈습니다.");
                        }

                        // 초대 수신
                        else if (message.startsWith("INVITE_RECEIVED|")) {

                            String[] data = message.split("\\|", 3);

                            if (data.length == 3) {

                                System.out.println();
                                System.out.println(
                                        "===== 채팅방 초대 도착 =====");

                                System.out.println(
                                        "초대한 회원: " + data[2]);

                                System.out.println(
                                        "채팅방 번호: " + data[1]);

                                System.out.println(
                                        "수락: /accept " + data[1]);

                                System.out.println(
                                        "거절: /decline " + data[1]);
                            }
                        }

                        // 초대 수락 완료
                        else if (message.startsWith("INVITE_ACCEPTED|")) {

                            String roomId = message.substring(16);

                            System.out.println();
                            System.out.println(
                                    "[초대 수락 성공] "
                                    + roomId + "번 채팅방에 입장했습니다.");
                        }

                        // 초대 거절 완료
                        else if (message.startsWith("INVITE_DECLINED|")) {

                            String roomId = message.substring(16);

                            System.out.println(
                                    "[초대 거절] "
                                    + roomId + "번 채팅방 초대를 거절했습니다.");
                        }

                        // 초대 결과 알림
                        else if (message.startsWith("INVITE_RESULT|")) {

                            String[] data = message.split("\\|", 3);

                            if (data.length == 3) {

                                if (data[2].equals("ACCEPTED")) {
                                    System.out.println(
                                            "[초대 수락] "
                                            + data[1]
                                            + "님이 초대를 수락했습니다.");
                                } else {
                                    System.out.println(
                                            "[초대 거절] "
                                            + data[1]
                                            + "님이 초대를 거절했습니다.");
                                }
                            }
                        }

                        // 오류 출력
                        else if (message.startsWith("ERROR|")) {

                            System.out.println(
                                    "[오류] " + message.substring(6));
                        }

                        else {
                            System.out.println("[서버] " + message);
                        }
                    }

                } catch (Exception e) {

                    if (!socket.isClosed()) {
                        System.out.println(
                                "서버 연결이 종료되었습니다.");
                    }
                }
            });

            receiver.setDaemon(true);
            receiver.start();

            // 로그인 후 채팅방 목록 요청
            out.println("ROOMS");

            System.out.println();
            System.out.println("===== 명령어 안내 =====");
            System.out.println("rooms            : 채팅방 목록 조회");
            System.out.println("/join 번호       : 채팅방 입장");
            System.out.println("/w 아이디 내용   : 귓속말");
            System.out.println("/invite list     : 초대 가능 회원 조회");
            System.out.println("/invite 아이디   : 회원 초대");
            System.out.println("/accept 번호     : 초대 수락");
            System.out.println("/decline 번호    : 초대 거절");
            System.out.println("memo             : 메모장 실행");
            System.out.println("exit             : 종료");

            // 사용자 입력
            while (true) {

                String input = sc.nextLine().trim();

                if (input.equalsIgnoreCase("exit")) {
                    break;
                }

                else if (input.equalsIgnoreCase("rooms")) {
                    out.println("ROOMS");
                }

                else if (input.equalsIgnoreCase("memo")) {

                	System.out.println("메모장을 실행합니다.");

                	javax.swing.SwingUtilities.invokeLater(() -> {
                		new MemoPad();
    });
}
                
                else if (input.startsWith("/join ")) {

                    String roomId = input.substring(6).trim();

                    if (roomId.matches("[0-9]+")) {
                        out.println("JOIN|" + roomId);
                    } else {
                        System.out.println(
                                "사용법: /join 채팅방번호");
                    }
                }

                else if (input.startsWith("/w ")) {

                    String[] parts = input.split("\\s+", 3);

                    if (parts.length == 3) {
                        out.println("WHISPER|"
                                + parts[1] + "|" + parts[2]);
                    } else {
                        System.out.println(
                                "사용법: /w 상대아이디 내용");
                    }
                }

                // 초대 가능 회원 목록
                else if (input.equalsIgnoreCase("/invite list")) {

                    out.println("INVITE_LIST");
                }

                // 회원 초대
                else if (input.startsWith("/invite ")) {

                    String targetId = input.substring(8).trim();

                    if (!targetId.isEmpty()) {
                        out.println("INVITE|" + targetId);
                    } else {
                        System.out.println(
                                "사용법: /invite 상대아이디");
                    }
                }

                // 초대 수락
                else if (input.startsWith("/accept ")) {

                    String roomId = input.substring(8).trim();

                    if (roomId.matches("[0-9]+")) {
                        out.println("INVITE_ACCEPT|" + roomId);
                    } else {
                        System.out.println(
                                "사용법: /accept 채팅방번호");
                    }
                }

                // 초대 거절
                else if (input.startsWith("/decline ")) {

                    String roomId = input.substring(9).trim();

                    if (roomId.matches("[0-9]+")) {
                        out.println("INVITE_DECLINE|" + roomId);
                    } else {
                        System.out.println(
                                "사용법: /decline 채팅방번호");
                    }
                }

                else {
                    System.out.println(
                            "알 수 없는 명령어입니다.");
                }
            }

            System.out.println("채팅 로비에서 나갑니다.");

        } catch (Exception e) {

            System.out.println("서버 접속 오류!");
            System.out.println(e.getMessage());
        }
    }
}