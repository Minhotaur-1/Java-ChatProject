package ChatProject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ChatLobbyClient {

    private static final String HOST = "localhost";
    private static final int PORT = 5000;

    public static void main(String[] args) {
        start(new Scanner(System.in));
    }

    // 9주차 통합: 시작 메뉴와 동일한 Scanner 사용
    public static void start(Scanner sc) {

        System.out.println("===== ChatProject 회원 로그인 =====");
        System.out.print("아이디: ");

        String id = sc.nextLine().trim();

        if (id.isEmpty()) {
            System.out.println("아이디를 입력해주세요.");
            return;
        }

        System.out.print("비밀번호: ");
        String password = sc.nextLine();

        // 9주차 통합: 회원 DB에서 실제 로그인 검증
        if (!ProfileManager.login(id, password)) {
            System.out.println("로그인 실패! 아이디/비밀번호 또는 DB 연결을 확인해주세요.");
            return;
        }
        System.out.println("회원 로그인 성공: " + id);
        startLoggedIn(id, sc);
    }

    /** 이미 MySQL 로그인을 통과한 GUI에서 호출: 비밀번호 재입력을 하지 않음. */
    public static void startLoggedIn(String id, Scanner sc) {
        if (id == null || id.trim().isEmpty()) {
            System.out.println("로그인 사용자 정보가 없습니다.");
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

            System.out.println("채팅 로비 입장 성공: " + id);

            // 9주차 통합: 로비 입장 직후 날씨 정보 출력
            System.out.println();
            System.out.println("===== 로비 현재 날씨 =====");
            try {
                WeatherAPITest.main(new String[0]);
            } catch (Exception weatherError) {
                System.out.println("날씨 정보를 불러오지 못했습니다: "
                        + weatherError.getMessage());
            }

            // 10주차: GUI 창 상태와 현재 입장한 채팅방을 관리
            AtomicReference<ChatRoomWindow> chatWindowRef = new AtomicReference<>();
            AtomicReference<TicTacToeOnline> gameRef = new AtomicReference<>();
            AtomicInteger activeRoomId = new AtomicInteger(-1);
            // 1차 GUI 2단계: 기존 소켓 연결을 로비 GUI와 공유한다.
            java.util.concurrent.atomic.AtomicReference<ChatLobbyFrame> lobbyRef =
                    new java.util.concurrent.atomic.AtomicReference<>();
            // 첨부파일 매니저는 기존 소켓/GUI 창을 함께 사용합니다.
            FileTransferClient fileTransfers =
                    new FileTransferClient(out, chatWindowRef, lobbyRef);
            javax.swing.SwingUtilities.invokeLater(() -> {
                ChatLobbyFrame lobby = new ChatLobbyFrame(id, request -> {
                    synchronized (out) {
                        out.println(request);
                    }
                });
                lobbyRef.set(lobby);
                out.println("ROOMS");
            });

            // 서버에서 JOIN_OK를 받은 후 해당 채팅방 창을 열거나 전환
            java.util.function.IntConsumer showChatWindow = roomNumber ->
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        ChatRoomWindow window = chatWindowRef.get();
                        if (window == null || !window.isDisplayable()) {
                            window = new ChatRoomWindow(id, roomNumber,
                                    text -> { synchronized (out) { out.println("CHAT|" + text); } },
                                    command -> { synchronized (out) { out.println(command); } },
                                    selectedRoom -> fileTransfers.chooseAndSend(selectedRoom));
                            chatWindowRef.set(window);
                        } else {
                            window.setRoom(roomNumber);
                        }
                        window.setVisible(true);
                        window.toFront();
                    });

            // 서버 메시지 수신 스레드
            Thread receiver = new Thread(() -> {

                try {
                    String message;
                    StringBuilder searchResults = new StringBuilder();
                    int searchCount = 0;
                    java.util.List<String> inviteCandidates = new java.util.ArrayList<>();

                    while ((message = in.readLine()) != null) {

                        // 파일 수신은 기존 채팅 수신 스레드에서 순서대로 처리
                        if (fileTransfers.handleServerLine(message)) continue;

                        // 2인 틱택토 서버 이벤트
                        if (message.startsWith("TTT_INVITE_FROM|")) {
                            String from = message.substring("TTT_INVITE_FROM|".length());
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                int choice = javax.swing.JOptionPane.showConfirmDialog(
                                        lobbyRef.get(), from + "님이 틱택토 대전을 신청했습니다. 수락할까요?",
                                        "틱택토 초대", javax.swing.JOptionPane.YES_NO_OPTION);
                                synchronized (out) {
                                    out.println((choice == javax.swing.JOptionPane.YES_OPTION
                                            ? "TTT_ACCEPT|" : "TTT_DECLINE|") + from);
                                }
                            });
                        }
                        else if (message.startsWith("TTT_INVITE_SENT|")) {
                            String target = message.substring("TTT_INVITE_SENT|".length());
                            System.out.println("[틱택토] " + target + "님에게 대전 신청 완료");
                            ChatLobbyFrame lobby = lobbyRef.get();
                            if (lobby != null) lobby.showStatus(target + "님의 수락을 기다리는 중...");
                        }
                        else if (message.startsWith("TTT_INVITE_DECLINED|")) {
                            String target = message.substring("TTT_INVITE_DECLINED|".length());
                            System.out.println("[틱택토] " + target + "님이 대전을 거절했습니다.");
                            javax.swing.SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(
                                    lobbyRef.get(), target + "님이 대전을 거절했습니다."));
                        }
                        else if (message.startsWith("TTT_INVITE_DECLINED_ACK|")) {
                            System.out.println("[틱택토] 대전을 거절했습니다.");
                        }
                        else if (message.startsWith("TTT_INVITE_CANCELLED|")) {
                            String who = message.substring("TTT_INVITE_CANCELLED|".length());
                            System.out.println("[틱택토] " + who + "님의 연결이 종료되어 초대가 취소됐습니다.");
                        }
                        else if (message.startsWith("TTT_START|")) {
                            String[] data = message.split("\\|", 4);
                            if (data.length == 4) {
                                javax.swing.SwingUtilities.invokeLater(() -> {
                                    TicTacToeOnline old = gameRef.getAndSet(null);
                                    if (old != null) old.dispose();
                                    TicTacToeOnline game = new TicTacToeOnline(data[1], data[2], data[3],
                                            command -> { synchronized (out) { out.println(command); } });
                                    gameRef.set(game);
                                });
                            }
                        }
                        else if (message.startsWith("TTT_STATE|")) {
                            String[] data = message.split("\\|", 5);
                            if (data.length == 5) {
                                javax.swing.SwingUtilities.invokeLater(() -> {
                                    TicTacToeOnline game = gameRef.get();
                                    if (game != null && game.isDisplayable()
                                            && data[1].equals(game.getGameId())) {
                                        game.applyState(data[2], data[3], data[4]);
                                    }
                                });
                            }
                        }
                        else if (message.startsWith("TTT_OPPONENT_LEFT|")) {
                            String gameId = message.substring("TTT_OPPONENT_LEFT|".length());
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                TicTacToeOnline game = gameRef.get();
                                if (game != null && gameId.equals(game.getGameId())) game.opponentLeft();
                            });
                        }
                        else if (message.startsWith("TTT_ERROR|")) {
                            String reason = message.substring("TTT_ERROR|".length());
                            System.out.println("[틱택토 오류] " + reason);
                            javax.swing.SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(
                                    lobbyRef.get(), reason, "틱택토 안내",
                                    javax.swing.JOptionPane.WARNING_MESSAGE));
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                TicTacToeOnline game = gameRef.get();
                                if (game != null) game.clearPending();
                            });
                        }

                        // 접속자 변경 알림
                        else if (message.equals("ONLINE_UPDATE")) {

                            System.out.println();
                            System.out.println(
                                    "[알림] 접속자 목록이 변경되었습니다.");
                        }

                        // 채팅방 목록
                        else if (message.equals("ROOMS_BEGIN")) {
                            ChatLobbyFrame lobby = lobbyRef.get();
                            if (lobby != null && lobby.isDisplayable()) lobby.beginRooms();

                            System.out.println();
                            System.out.println(
                                    "===== 채팅방 목록 =====");
                        }

                        else if (message.startsWith("ROOM|")) {

                            String[] data = message.split("\\|", 5);

                            if (data.length == 5) {
                                ChatLobbyFrame lobby = lobbyRef.get();
                                if (lobby != null && lobby.isDisplayable()) {
                                    lobby.appendRoom(data[1], data[2], data[3], data[4]);
                                }
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
                            ChatLobbyFrame lobby = lobbyRef.get();
                            if (lobby != null && lobby.isDisplayable()) lobby.endRooms();

                            System.out.println();
                            System.out.println(
                                    "채팅방 목록 조회 완료!");
                        }

                        else if (message.equals("ROOMS_ERROR")) {

                            System.out.println(
                                    "채팅방 목록 조회 실패!");
                        }

                        // 9주차: 새 채팅방 생성 결과
                        else if (message.startsWith("CREATE_OK|")) {
                            String[] parts = message.split("\\|", 3);
                            if (parts.length == 3) {
                                System.out.println("[방 생성 성공] " + parts[1]
                                        + "번 / " + parts[2]);
                                ChatLobbyFrame lobby = lobbyRef.get();
                                if (lobby != null && lobby.isDisplayable()) {
                                    lobby.showStatus(parts[1] + "번 채팅방 생성 성공!");
                                }
                            }
                        }

                        // 채팅방 입장
                        else if (message.startsWith("JOIN_OK|")) {

                            String roomId = message.substring(8);

                            System.out.println();
                            System.out.println(
                                    "[알림] " + roomId
                                    + "번 채팅방에 입장했습니다.");
                            try {
                                int joinedId = Integer.parseInt(roomId);
                                activeRoomId.set(joinedId);
                                ChatLobbyFrame lobby = lobbyRef.get();
                                if (lobby != null && lobby.isDisplayable()) lobby.enteredRoom(joinedId);
                                showChatWindow.accept(joinedId);
                            } catch (NumberFormatException ignored) {
                                System.out.println("채팅방 번호를 확인할 수 없습니다.");
                            }
                        }

                        // 10주차: 채팅방에 입장하면 이전 대화 최근 30건 표시
                        else if (message.startsWith("HISTORY_BEGIN|")) {
                            String[] data = message.split("\\|", 3);
                            if (data.length == 3) {
                                System.out.println();
                                System.out.println("===== " + data[1]
                                        + "번 방 이전 채팅 (" + data[2] + "건) =====");
                                try {
                                    int historyRoomId = Integer.parseInt(data[1]);
                                    javax.swing.SwingUtilities.invokeLater(() -> {
                                        ChatRoomWindow window = chatWindowRef.get();
                                        if (window != null && window.isDisplayable()) {
                                            window.appendSystem(historyRoomId,
                                                    "이전 채팅 " + data[2] + "건");
                                        }
                                    });
                                } catch (NumberFormatException ignored) { }
                            }
                        }

                        else if (message.startsWith("HISTORY|")) {
                            // HISTORY|메시지ID|방번호|보낸사람|시각|내용
                            String[] data = message.split("\\|", 6);
                            if (data.length == 6) {
                                System.out.println("[이전] [" + data[4] + "] "
                                        + data[3] + ": " + data[5]);
                                try {
                                    int historyRoomId = Integer.parseInt(data[2]);
                                    final String sender = data[3];
                                    final String time = data[4];
                                    final String body = data[5];
                                    javax.swing.SwingUtilities.invokeLater(() -> {
                                        ChatRoomWindow window = chatWindowRef.get();
                                        if (window != null && window.isDisplayable()) {
                                            window.appendHistory(historyRoomId,
                                                    Long.parseLong(data[1]), time, sender, body);
                                        }
                                    });
                                } catch (NumberFormatException ignored) { }
                            }
                        }

                        // 답장 기록: 서버가 원본 메시지 정보를 함께 보내므로 재접속 후에도 표시
                        else if (message.startsWith("HISTORY_REPLY|")) {
                            // HISTORY_REPLY|메시지ID|방|발신자|시각|원본ID|원본작성자|원본미리보기Base64|내용
                            String[] data = message.split("\\|", 9);
                            if (data.length == 9) {
                                try {
                                    long idValue = Long.parseLong(data[1]);
                                    int roomValue = Integer.parseInt(data[2]);
                                    long originalId = Long.parseLong(data[5]);
                                    String originalPreview = new String(
                                            java.util.Base64.getUrlDecoder().decode(data[7]),
                                            java.nio.charset.StandardCharsets.UTF_8);
                                    final String sender = data[3];
                                    final String time = data[4];
                                    final String originalSender = data[6];
                                    final String content = data[8];
                                    System.out.println("[이전 답장] " + sender + ": " + content);
                                    javax.swing.SwingUtilities.invokeLater(() -> {
                                        ChatRoomWindow window = chatWindowRef.get();
                                        if (window != null && window.isDisplayable()) {
                                            window.appendReplyHistory(roomValue, idValue, time,
                                                    sender, originalId, originalSender,
                                                    originalPreview, content);
                                        }
                                    });
                                } catch (IllegalArgumentException ignored) { }
                            }
                        }

                        else if (message.startsWith("HISTORY_END|")) {
                            System.out.println("===== 이전 대화 불러오기 완료 =====");
                            System.out.println();
                        }

                        // 10주차: 채팅 검색 결과 (현재 채팅방 최대 30건)
                        else if (message.startsWith("SEARCH_BEGIN|")) {
                            String[] data = message.split("\\|", 3);
                            searchResults.setLength(0);
                            searchCount = 0;
                            if (data.length == 3) {
                                searchResults.append("채팅방 ").append(data[1])
                                        .append("번 검색 결과: ").append(data[2])
                                        .append("건\n\n");
                                System.out.println("===== " + data[1]
                                        + "번 방 검색 결과 (" + data[2] + "건) =====");
                            }
                        }

                        else if (message.startsWith("SEARCH_RESULT|")) {
                            // SEARCH_RESULT|메시지ID|방번호|보낸사람|시각|내용
                            String[] data = message.split("\\|", 6);
                            if (data.length == 6) {
                                String line = "[#" + data[1] + "] [" + data[4]
                                        + "] " + data[3] + ": " + data[5];
                                searchResults.append(line).append("\n");
                                System.out.println(line);
                                searchCount++;
                            }
                        }

                        else if (message.startsWith("SEARCH_END|")) {
                            if (searchCount == 0) {
                                searchResults.append("일치하는 메시지가 없습니다.\n");
                                System.out.println("일치하는 메시지가 없습니다.");
                            }
                            System.out.println("===== 채팅 검색 완료 =====");
                            final String resultText = searchResults.toString();
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                javax.swing.JTextArea area =
                                        new javax.swing.JTextArea(resultText, 16, 56);
                                area.setEditable(false);
                                area.setLineWrap(true);
                                area.setWrapStyleWord(true);
                                area.setCaretPosition(0);
                                javax.swing.JScrollPane scroll =
                                        new javax.swing.JScrollPane(area);
                                javax.swing.JOptionPane.showMessageDialog(
                                        null, scroll, "채팅 검색 결과",
                                        javax.swing.JOptionPane.INFORMATION_MESSAGE);
                            });
                        }

                        // 10주차: DB에 저장된 일반 채팅 메시지 표시
                        else if (message.startsWith("CHAT_FROM|")) {
                            // CHAT_FROM|메시지번호|방번호|발신자|내용
                            String[] data = message.split("\\|", 5);
                            if (data.length == 5) {
                                System.out.println("[" + data[2] + "번 방] "
                                        + data[3] + ": " + data[4]);
                                try {
                                    int incomingRoom = Integer.parseInt(data[2]);
                                    final String sender = data[3];
                                    final String body = data[4];
                                    javax.swing.SwingUtilities.invokeLater(() -> {
                                        ChatRoomWindow window = chatWindowRef.get();
                                        if (window != null && window.isDisplayable()) {
                                            window.appendLive(incomingRoom,
                                                    Long.parseLong(data[1]), sender, body);
                                        }
                                    });
                                } catch (NumberFormatException ignored) { }
                            }
                        }

                        // 답장 실시간 수신: DB에 저장한 원본 ID를 그대로 전달한다.
                        else if (message.startsWith("REPLY_FROM|")) {
                            // REPLY_FROM|새메시지ID|방번호|발신자|원본ID|원본작성자|원본미리보기Base64|내용
                            String[] data = message.split("\\|", 8);
                            if (data.length == 8) {
                                try {
                                    long messageId = Long.parseLong(data[1]);
                                    int roomValue = Integer.parseInt(data[2]);
                                    long originalId = Long.parseLong(data[4]);
                                    String originalPreview = new String(
                                            java.util.Base64.getUrlDecoder().decode(data[6]),
                                            java.nio.charset.StandardCharsets.UTF_8);
                                    final String sender = data[3];
                                    final String originalSender = data[5];
                                    final String content = data[7];
                                    System.out.println("[답장] " + sender + ": " + content);
                                    javax.swing.SwingUtilities.invokeLater(() -> {
                                        ChatRoomWindow window = chatWindowRef.get();
                                        if (window != null && window.isDisplayable()) {
                                            window.appendReplyLive(roomValue, messageId, sender,
                                                    originalId, originalSender, originalPreview, content);
                                        }
                                    });
                                } catch (IllegalArgumentException ignored) { }
                            }
                        }

                        // @아이디 / @닉네임으로 언급된 사람에게만 도착하는 알림
                        else if (message.startsWith("MENTION_NOTICE|")) {
                            // MENTION_NOTICE|방번호|메시지번호|보낸사람
                            String[] data = message.split("\\|", 4);
                            if (data.length == 4) {
                                try {
                                    int mentionedRoom = Integer.parseInt(data[1]);
                                    long mentionedMessage = Long.parseLong(data[2]);
                                    String from = data[3];
                                    System.out.println("[멘션 알림] " + from
                                            + "님이 나를 언급했습니다. (" + mentionedRoom + "번 방)");
                                    javax.swing.SwingUtilities.invokeLater(() -> {
                                        ChatRoomWindow window = chatWindowRef.get();
                                        if (window != null && window.isDisplayable()) {
                                            window.highlightMention(mentionedRoom,
                                                    mentionedMessage, from);
                                        }
                                    });
                                } catch (NumberFormatException ignored) { }
                            }
                        }

                        // 귓속말 수신: 콘솔에만 남지 않도록 반드시 GUI에도 표시.
                        else if (message.startsWith("WHISPER_FROM|")) {
                            String[] data = message.split("\\|", 3);
                            if (data.length == 3) {
                                System.out.println("[귓속말] " + data[1] + ": " + data[2]);
                                final String sender = data[1];
                                final String body = data[2];
                                javax.swing.SwingUtilities.invokeLater(() -> {
                                    ChatRoomWindow window = chatWindowRef.get();
                                    if (window != null && window.isDisplayable()) {
                                        window.appendWhisperFrom(sender, body);
                                    } else {
                                        javax.swing.JOptionPane.showMessageDialog(
                                                lobbyRef.get(), sender + "님: " + body,
                                                "새 귓속말", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                                    }
                                });
                            }
                        }

                        // 귓속말은 서버가 처리하고 성공 응답을 보낸 경우에만 GUI에 표시.
                        else if (message.startsWith("WHISPER_SENT|")) {
                            String[] data = message.split("\\|", 3);
                            if (data.length == 3) {
                                System.out.println("[귓속말 전송 완료] " + data[1] + "에게: " + data[2]);
                                final String target = data[1];
                                final String body = data[2];
                                javax.swing.SwingUtilities.invokeLater(() -> {
                                    ChatRoomWindow window = chatWindowRef.get();
                                    if (window != null && window.isDisplayable()) {
                                        window.appendWhisperSent(target, body);
                                    } else {
                                        javax.swing.JOptionPane.showMessageDialog(lobbyRef.get(),
                                                target + "님에게 귓속말을 전송했습니다.",
                                                "귓속말 전송", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                                    }
                                });
                            }
                        }

                        // 초대 가능 회원 목록 시작
                        else if (message.equals("INVITE_LIST_BEGIN")) {
                            inviteCandidates.clear();
                            System.out.println();
                            System.out.println(
                                    "===== 초대 가능한 회원 =====");
                        }

                        // 초대 가능한 회원
                        else if (message.startsWith("INVITE_USER|")) {

                            String userId = message.substring(12);
                            inviteCandidates.add(userId);
                            System.out.println("- " + userId);
                        }

                        // 초대 가능 회원 목록 끝
                        else if (message.equals("INVITE_LIST_END")) {
                            ChatLobbyFrame lobby = lobbyRef.get();
                            if (lobby != null && lobby.isDisplayable()) {
                                lobby.showInviteCandidateList(new java.util.ArrayList<>(inviteCandidates));
                            }
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
                            ChatLobbyFrame lobby = lobbyRef.get();
                            if (lobby != null && lobby.isDisplayable()) {
                                lobby.showStatus(target + "님에게 채팅방 초대 전송 완료");
                            }
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
                                try {
                                    int room = Integer.parseInt(data[1]);
                                    ChatLobbyFrame lobby = lobbyRef.get();
                                    if (lobby != null && lobby.isDisplayable()) {
                                        lobby.receivedInvitation(room, data[2]);
                                    }
                                } catch (NumberFormatException ignored) { }
                            }
                        }

                        // 초대 수락 완료
                        else if (message.startsWith("INVITE_ACCEPTED|")) {

                            String roomId = message.substring(16);

                            System.out.println();
                            System.out.println(
                                    "[초대 수락 성공] "
                                    + roomId + "번 채팅방에 입장했습니다.");
                            try {
                                int joinedId = Integer.parseInt(roomId);
                                activeRoomId.set(joinedId);
                                ChatLobbyFrame lobby = lobbyRef.get();
                                if (lobby != null && lobby.isDisplayable()) lobby.enteredRoom(joinedId);
                                showChatWindow.accept(joinedId);
                            } catch (NumberFormatException ignored) { }
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

                     // 이미지 수신
                     else if (message.startsWith("IMAGE_FROM|")) {

                         String[] data = message.split("\\|", 3);

                         if (data.length == 3) {

                             try {
                                 if (data[2].length() > 2800000) {
                                     throw new Exception("이미지 크기 초과");
                                 }

                                 byte[] imageBytes =
                                         java.util.Base64.getDecoder()
                                                 .decode(data[2]);

                                 java.awt.image.BufferedImage image =
                                         javax.imageio.ImageIO.read(
                                                 new java.io.ByteArrayInputStream(
                                                         imageBytes));

                                 if (image == null
                                         || image.getWidth() > 2000
                                         || image.getHeight() > 2000) {
                                     throw new Exception("올바르지 않은 이미지");
                                 }

                                 System.out.println(
                                         "[이미지 수신] " + data[1]
                                         + "님이 그림을 보냈습니다.");

                                 javax.swing.SwingUtilities.invokeLater(() -> {

                                     javax.swing.JFrame frame =
                                             new javax.swing.JFrame(
                                                     data[1] + "님이 보낸 그림");

                                     frame.setSize(900, 550);
                                     frame.setLocationRelativeTo(null);

                                     frame.setDefaultCloseOperation(
                                             javax.swing.JFrame.DISPOSE_ON_CLOSE);

                                     frame.add(new javax.swing.JScrollPane(
                                             new javax.swing.JLabel(
                                                     new javax.swing.ImageIcon(image))));

                                     frame.setVisible(true);
                                 });

                             } catch (Exception ex) {
                                 System.out.println(
                                         "이미지 수신 실패: " + ex.getMessage());
                             }
                         }
                     }

                     // 이미지 전송 결과
                     else if (message.startsWith("IMAGE_SENT|")) {

                         System.out.println(
                                 "[이미지 전송 완료] 수신자 "
                                 + message.substring(11) + "명");
                     }

                        
                        // 오류 출력
                        else if (message.startsWith("ERROR|")) {
                            ChatLobbyFrame lobby = lobbyRef.get();
                            if (lobby != null && lobby.isDisplayable()) {
                                lobby.showServerError(message.substring(6));
                            }
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
                } finally {
                    fileTransfers.shutdown();
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
            System.out.println("chatui           : 현재 채팅방 GUI 창 열기");
            System.out.println("lobby            : 채팅 로비 GUI 다시 열기");
            System.out.println("/create direct 아이디 : 1:1 채팅방 만들기");
            System.out.println("/create group 방이름|id1,id2 : 그룹 방 만들기");
            System.out.println("profile          : 내 프로필 조회");
            System.out.println("settings         : 프로필 설정");
            System.out.println("일반 메시지      : 방에 들어간 뒤 문장만 입력하면 전송");
            System.out.println("search           : 채팅 검색창 열기");
            System.out.println("/search 단어     : 채팅방 메시지 검색");
            System.out.println("/w 아이디 내용   : 귓속말");
            System.out.println("/invite list     : 초대 가능 회원 조회");
            System.out.println("/invite 아이디   : 회원 초대");
            System.out.println("/accept 번호     : 초대 수락");
            System.out.println("/decline 번호    : 초대 거절");
            System.out.println("memo             : 메모장 실행");
            System.out.println("exit             : 종료");
            System.out.println("game             : 2인 틱택토 초대창");
            System.out.println("/game 아이디     : 2인 틱택토 대전 신청");
            System.out.println("calendar         : 일정 관리 실행");
            System.out.println("paint            : 그림판 실행");

            // 사용자 입력
            while (true) {

                String input = sc.nextLine().trim();

                if (input.equalsIgnoreCase("exit")) {
                    break;
                }

                else if (input.equalsIgnoreCase("rooms")) {
                    out.println("ROOMS");
                }

                else if (input.equalsIgnoreCase("lobby")) {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        ChatLobbyFrame existing = lobbyRef.get();
                        if (existing != null && existing.isDisplayable()) {
                            existing.setVisible(true);
                            existing.toFront();
                        } else {
                            ChatLobbyFrame fresh = new ChatLobbyFrame(id,
                                    request -> { synchronized (out) { out.println(request); } });
                            lobbyRef.set(fresh);
                            if (activeRoomId.get() >= 0) fresh.enteredRoom(activeRoomId.get());
                            out.println("ROOMS");
                        }
                    });
                }

                else if (input.equalsIgnoreCase("chatui")) {
                    if (activeRoomId.get() == -1) {
                        System.out.println("먼저 /join 번호로 채팅방에 입장해주세요.");
                    } else {
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            ChatRoomWindow window = chatWindowRef.get();
                            if (window != null && window.isDisplayable()) {
                                window.setVisible(true);
                                window.toFront();
                            } else {
                                showChatWindow.accept(activeRoomId.get());
                            }
                        });
                    }
                }

                else if (input.equalsIgnoreCase("memo")) {

                	System.out.println("메모장을 실행합니다.");

                	javax.swing.SwingUtilities.invokeLater(() -> {
                		new MemoPad();
    });
}

                else if (input.equalsIgnoreCase("game")) {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        String other = javax.swing.JOptionPane.showInputDialog(
                                lobbyRef.get(), "대전 상대 아이디를 입력하세요.", "2인 틱택토",
                                javax.swing.JOptionPane.QUESTION_MESSAGE);
                        if (other != null && other.trim().matches("[A-Za-z0-9_]{3,30}")) {
                            synchronized (out) { out.println("TTT_INVITE|" + other.trim()); }
                        }
                    });
                }
                else if (input.startsWith("/game ")) {
                    String other = input.substring(6).trim();
                    if (other.matches("[A-Za-z0-9_]{3,30}")) {
                        synchronized (out) { out.println("TTT_INVITE|" + other); }
                    } else {
                        System.out.println("사용법: /game 상대아이디");
                    }
                }

                else if (input.equalsIgnoreCase("calendar")) {

                	System.out.println("일정 관리 캘린더를 실행합니다.");

                	javax.swing.SwingUtilities.invokeLater(() -> {
                		new CalendarManager(id);
    });
}

                else if (input.equalsIgnoreCase("paint")) {

                	System.out.println("그림판을 실행합니다.");

                	javax.swing.SwingUtilities.invokeLater(() -> {

                		new PaintBoard(imageData -> {

                			// 그린 이미지를 채팅 서버로 전송
                			out.println("IMAGE|" + imageData);

                			System.out.println(
                					"[이미지] 서버에 전송을 요청했습니다.");
                		});
                	});
                }



                
                // 9주차: 프로필 조회 기능 유지
                else if (input.equalsIgnoreCase("profile")) {
                    ProfileManager.showProfile(id);
                }

                // 9주차: 회원 정보 변경 메뉴 연결 (기존 수동 추가 기능 보존)
                else if (input.equalsIgnoreCase("settings")) {
                    boolean passwordChanged = ProfileSettingsMenu.open(sc, id);
                    if (passwordChanged) {
                        break;
                    }
                }

                // 채팅 검색: 검색어 입력창 또는 콘솔 명령어 지원
                else if (input.equalsIgnoreCase("search")) {
                    String keyword = javax.swing.JOptionPane.showInputDialog(
                            null, "현재 채팅방에서 검색할 단어를 입력하세요.",
                            "채팅 검색", javax.swing.JOptionPane.QUESTION_MESSAGE);
                    if (keyword != null && !keyword.trim().isEmpty()) {
                        if (keyword.trim().length() <= 100) {
                            out.println("SEARCH|" + keyword.trim());
                        } else {
                            System.out.println("검색어는 100자 이하로 입력해주세요.");
                        }
                    }
                }
                else if (input.startsWith("/search ")) {
                    String keyword = input.substring("/search ".length()).trim();
                    if (keyword.isEmpty() || keyword.length() > 100) {
                        System.out.println("사용법: /search 검색어 (1~100자)");
                    } else {
                        out.println("SEARCH|" + keyword);
                    }
                }
                else if (input.equals("/search")) {
                    System.out.println("사용법: /search 검색어");
                }

                // 9주차: 1:1 채팅방 생성
                else if (input.startsWith("/create direct ")) {
                    String otherId = input.substring("/create direct ".length()).trim();
                    if (otherId.matches("[A-Za-z0-9_]{3,30}")) {
                        out.println("CREATE_DIRECT|" + otherId);
                    } else {
                        System.out.println("사용법: /create direct 상대아이디");
                    }
                }

                // 9주차: 그룹 채팅방 생성
                // 입력 예시: /create group 자바 프로젝트|test01,test02
                else if (input.startsWith("/create group ")) {
                    String detail = input.substring("/create group ".length());
                    String[] parts = detail.split("\\|", 2);
                    if (parts.length == 2
                            && !parts[0].trim().isEmpty()
                            && parts[0].trim().length() <= 100
                            && !parts[1].trim().isEmpty()) {
                        out.println("CREATE_GROUP|" + parts[0].trim()
                                + "|" + parts[1].trim());
                    } else {
                        System.out.println("사용법: /create group 방이름|id1,id2");
                    }
                }

                else if (input.startsWith("/create")) {
                    System.out.println("1:1방: /create direct 상대아이디");
                    System.out.println("그룹방: /create group 방이름|id1,id2");
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

                // 10주차: 슬래시로 시작하지 않는 문장은 일반 채팅으로 보냄
                else if (!input.startsWith("/")) {
                    if (input.isEmpty()) {
                        continue;
                    }
                    if (input.length() > 1000) {
                        System.out.println("메시지는 1000자 이하로 입력해주세요.");
                        continue;
                    }
                    out.println("CHAT|" + input);
                }

                else {
                    System.out.println("알 수 없는 명령어입니다.");
                }
            }

            javax.swing.SwingUtilities.invokeLater(() -> {
                ChatRoomWindow window = chatWindowRef.getAndSet(null);
                if (window != null) window.dispose();
                ChatLobbyFrame lobby = lobbyRef.getAndSet(null);
                if (lobby != null) lobby.dispose();
                TicTacToeOnline game = gameRef.getAndSet(null);
                if (game != null) game.dispose();
            });
            System.out.println("채팅 로비에서 나갑니다.");

        } catch (Exception e) {

            System.out.println("서버 접속 오류!");
            System.out.println(e.getMessage());
        }
    }
}