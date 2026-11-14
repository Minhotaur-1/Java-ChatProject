
package ChatProject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatLobbyServer {

    private static final int PORT = 5000;

    private static final String URL =
            "jdbc:mysql://localhost:3306/chatdb";

    private static final String DB_USER = "root";

    private static final String DB_PASSWORD =
    		System.getenv("CHAT_DB_PASSWORD");

    // 접속자 정보
    static class ClientInfo {
        String id;
        PrintWriter out;
        volatile int roomId = -1;

        ClientInfo(String id, PrintWriter out) {
            this.id = id;
            this.out = out;
        }

        void send(String message) {
            synchronized (out) {
                out.println(message);
            }
        }
    }

    // 초대 정보
    static class Invitation {
        String inviter;
        int roomId;

        Invitation(String inviter, int roomId) {
            this.inviter = inviter;
            this.roomId = roomId;
        }
    }

    private static final ConcurrentHashMap<String, ClientInfo>
            onlineUsers = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, Invitation>
            invitations = new ConcurrentHashMap<>();

    // 접속자 전체 알림
    public static void broadcast(String message) {
        for (ClientInfo client : onlineUsers.values()) {
            client.send(message);
        }
    }

    // 회원 존재 확인
    public static boolean userExists(String id) {

        String sql = "SELECT id FROM users WHERE id = ?";

        try (
            Connection conn = DriverManager.getConnection(
                    URL, DB_USER, DB_PASSWORD);
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setString(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 채팅방 참여 여부 확인
    public static boolean isRoomMember(int roomId, String id) {

        String sql = "SELECT 1 FROM room_members "
                + "WHERE room_id = ? AND user_id = ?";

        try (
            Connection conn = DriverManager.getConnection(
                    URL, DB_USER, DB_PASSWORD);
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setInt(1, roomId);
            pstmt.setString(2, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 그룹 채팅방 확인
    public static boolean isGroupRoom(int roomId) {

        String sql = "SELECT 1 FROM chat_rooms "
                + "WHERE room_id = ? AND room_type = 'GROUP'";

        try (
            Connection conn = DriverManager.getConnection(
                    URL, DB_USER, DB_PASSWORD);
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {
            pstmt.setInt(1, roomId);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 접속자 목록
    public static void showOnlineUsers() {

        System.out.println();
        System.out.println("===== 현재 접속자 목록 =====");

        for (String id : onlineUsers.keySet()) {
            System.out.println("- " + id);
        }

        System.out.println("총 접속 인원: "
                + onlineUsers.size() + "명");
    }

    // 채팅방 목록 전송
    public static void sendRoomList(ClientInfo client) {

        String sql =
                "SELECT r.room_id, r.room_name, "
                + "r.room_type, "
                + "COUNT(m.user_id) AS member_count "
                + "FROM chat_rooms r "
                + "LEFT JOIN room_members m "
                + "ON r.room_id = m.room_id "
                + "GROUP BY r.room_id, r.room_name, r.room_type "
                + "ORDER BY r.room_id";

        try (
            Connection conn = DriverManager.getConnection(
                    URL, DB_USER, DB_PASSWORD);
            PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()
        ) {

            synchronized (client.out) {

                client.out.println("ROOMS_BEGIN");

                while (rs.next()) {

                    String name = rs.getString("room_name")
                            .replace("|", " ")
                            .replace("\n", " ")
                            .replace("\r", " ");

                    client.out.println("ROOM|"
                            + rs.getInt("room_id") + "|"
                            + name + "|"
                            + rs.getString("room_type") + "|"
                            + rs.getInt("member_count"));
                }

                client.out.println("ROOMS_END");
            }

        } catch (Exception e) {
            client.send("ROOMS_ERROR");
            e.printStackTrace();
        }
    }

    // 채팅방 변경 확인
    public static String getRoomVersion() {

        String sql =
                "SELECT "
                + "(SELECT COUNT(*) FROM chat_rooms) AS rooms, "
                + "(SELECT COALESCE(MAX(room_id), 0) "
                + "FROM chat_rooms) AS max_id, "
                + "(SELECT COUNT(*) FROM room_members) AS members";

        try (
            Connection conn = DriverManager.getConnection(
                    URL, DB_USER, DB_PASSWORD);
            PreparedStatement pstmt = conn.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery()
        ) {

            if (rs.next()) {
                return rs.getInt("rooms") + ":"
                        + rs.getInt("max_id") + ":"
                        + rs.getInt("members");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    // 채팅방 자동 갱신
    public static void watchRoomChanges() {

        String previous = getRoomVersion();

        while (!Thread.currentThread().isInterrupted()) {

            try {
                Thread.sleep(2000);

                String current = getRoomVersion();

                if (current == null) {
                    continue;
                }

                if (previous != null
                        && !previous.equals(current)) {

                    System.out.println(
                            "[알림] 채팅방 목록 변경 감지!");

                    for (ClientInfo client : onlineUsers.values()) {
                        sendRoomList(client);
                    }
                }

                previous = current;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // 귓속말
    public static void sendWhisper(
            ClientInfo sender, String targetId, String text) {

        if (sender.roomId == -1) {
            sender.send("ERROR|먼저 채팅방에 입장해주세요.");
            return;
        }

        ClientInfo target = onlineUsers.get(targetId);

        if (targetId.equals(sender.id)
                || target == null
                || target.roomId != sender.roomId) {

            sender.send(
                    "ERROR|상대방이 같은 채팅방에 접속해 있지 않습니다.");
            return;
        }

        if (text.trim().isEmpty() || text.length() > 500) {
            sender.send("ERROR|메시지는 1~500자로 입력해주세요.");
            return;
        }

        target.send("WHISPER_FROM|"
                + sender.id + "|" + text);

        sender.send("WHISPER_SENT|"
                + targetId + "|" + text);
    }

    // 초대 가능한 사용자 목록
    public static void sendInviteList(ClientInfo sender) {

        if (sender.roomId == -1
                || !isGroupRoom(sender.roomId)
                || !isRoomMember(sender.roomId, sender.id)) {

            sender.send("ERROR|그룹 채팅방에 먼저 입장해주세요.");
            return;
        }

        synchronized (sender.out) {

            sender.out.println("INVITE_LIST_BEGIN");

            for (ClientInfo target : onlineUsers.values()) {

                if (target.id.equals(sender.id)) {
                    continue;
                }

                // 로비에 있는 회원만 초대 가능
                if (target.roomId != -1) {
                    continue;
                }

                if (!isRoomMember(sender.roomId, target.id)) {
                    sender.out.println("INVITE_USER|" + target.id);
                }
            }

            sender.out.println("INVITE_LIST_END");
        }
    }

    // 초대 전송
    public static void inviteUser(
            ClientInfo sender, String targetId) {

        if (sender.roomId == -1
                || !isGroupRoom(sender.roomId)
                || !isRoomMember(sender.roomId, sender.id)) {

            sender.send("ERROR|그룹 채팅방에 입장해주세요.");
            return;
        }

        if (sender.id.equals(targetId)) {
            sender.send("ERROR|자기 자신은 초대할 수 없습니다.");
            return;
        }

        ClientInfo target = onlineUsers.get(targetId);

        if (target == null || target.roomId != -1) {
            sender.send(
                    "ERROR|상대방이 로비에 접속해 있지 않습니다.");
            return;
        }

        if (isRoomMember(sender.roomId, targetId)) {
            sender.send(
                    "ERROR|이미 해당 채팅방에 참여한 회원입니다.");
            return;
        }

        Invitation invitation =
                new Invitation(sender.id, sender.roomId);

        if (invitations.putIfAbsent(targetId, invitation) != null) {
            sender.send("ERROR|상대방에게 대기 중인 초대가 있습니다.");
            return;
        }

        target.send("INVITE_RECEIVED|"
                + sender.roomId + "|" + sender.id);

        sender.send("INVITE_SENT|" + targetId);

        System.out.println("[초대 전송] "
                + sender.id + " -> " + targetId);
    }

    // 초대 수락
    public static void acceptInvite(
            ClientInfo target, int roomId) {

        Invitation invitation = invitations.get(target.id);

        if (invitation == null || invitation.roomId != roomId) {
            target.send("ERROR|유효한 초대가 없습니다.");
            return;
        }

        ClientInfo inviter = onlineUsers.get(invitation.inviter);

        if (inviter == null || inviter.roomId != roomId
                || target.roomId != -1
                || !isGroupRoom(roomId)
                || !isRoomMember(roomId, invitation.inviter)) {

            invitations.remove(target.id, invitation);
            target.send("ERROR|초대가 더 이상 유효하지 않습니다.");
            return;
        }

        if (!invitations.remove(target.id, invitation)) {
            target.send("ERROR|이미 처리된 초대입니다.");
            return;
        }

        String sql = "INSERT INTO room_members "
                + "(room_id, user_id) VALUES (?, ?)";

        try (
            Connection conn = DriverManager.getConnection(
                    URL, DB_USER, DB_PASSWORD);
            PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {

            pstmt.setInt(1, roomId);
            pstmt.setString(2, target.id);

            pstmt.executeUpdate();

            target.roomId = roomId;

            target.send("INVITE_ACCEPTED|" + roomId);

            inviter.send("INVITE_RESULT|"
                    + target.id + "|ACCEPTED");

            System.out.println("[초대 수락] "
                    + target.id + " -> " + roomId + "번 방");

        } catch (Exception e) {
            target.send("ERROR|채팅방 참여 등록에 실패했습니다.");
            e.printStackTrace();
        }
    }

    // 초대 거절
    public static void declineInvite(
            ClientInfo target, int roomId) {

        Invitation invitation = invitations.get(target.id);

        if (invitation == null || invitation.roomId != roomId) {
            target.send("ERROR|유효한 초대가 없습니다.");
            return;
        }

        if (!invitations.remove(target.id, invitation)) {
            target.send("ERROR|이미 처리된 초대입니다.");
            return;
        }

        target.send("INVITE_DECLINED|" + roomId);

        ClientInfo inviter = onlineUsers.get(invitation.inviter);

        if (inviter != null) {
            inviter.send("INVITE_RESULT|"
                    + target.id + "|DECLINED");
        }
    }

    // 클라이언트 처리
    public static void handleClient(Socket socket) {

        ClientInfo currentUser = null;

        try (
            Socket clientSocket = socket;
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream(),
                            StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(
                            socket.getOutputStream(),
                            StandardCharsets.UTF_8), true)
        ) {

            out.println("채팅 로비에 연결되었습니다.");

            String message;

            while ((message = in.readLine()) != null) {

                // 회원 등록
                if (message.startsWith("LOGIN|")) {

                    String id = message.substring(6).trim();

                    if (currentUser != null) {
                        out.println("이미 로그인한 연결입니다.");
                        continue;
                    }

                    if (id.isEmpty() || !userExists(id)) {
                        out.println("존재하지 않는 회원입니다.");
                        continue;
                    }

                    ClientInfo user = new ClientInfo(id, out);

                    if (onlineUsers.putIfAbsent(id, user) != null) {
                        out.println("이미 접속 중인 아이디입니다.");
                        continue;
                    }

                    currentUser = user;

                    user.send("LOGIN_OK");

                    System.out.println("회원 접속: " + id);
                    showOnlineUsers();
                    broadcast("ONLINE_UPDATE");

                    continue;
                }

                if (currentUser == null) {
                    out.println("ERROR|먼저 로그인해주세요.");
                    continue;
                }

                // 채팅방 목록
                if (message.equals("ROOMS")) {

                    sendRoomList(currentUser);
                }

                // 채팅방 입장
                else if (message.startsWith("JOIN|")) {

                    try {
                        int roomId = Integer.parseInt(
                                message.substring(5).trim());

                        if (!isRoomMember(roomId, currentUser.id)) {
                            currentUser.send(
                                    "ERROR|참여할 수 없는 채팅방입니다.");
                            continue;
                        }

                        currentUser.roomId = roomId;
                        currentUser.send("JOIN_OK|" + roomId);

                    } catch (NumberFormatException e) {
                        currentUser.send(
                                "ERROR|채팅방 번호를 확인해주세요.");
                    }
                }

                // 귓속말
                else if (message.startsWith("WHISPER|")) {

                    String[] parts = message.split("\\|", 3);

                    if (parts.length == 3) {
                        sendWhisper(
                                currentUser, parts[1], parts[2]);
                    } else {
                        currentUser.send("ERROR|귓속말 형식 오류");
                    }
                }

                // 초대 가능 회원 목록
                else if (message.equals("INVITE_LIST")) {

                    sendInviteList(currentUser);
                }

                // 초대 전송
                else if (message.startsWith("INVITE|")) {

                    inviteUser(
                            currentUser, message.substring(7).trim());
                }

                // 초대 수락
                else if (message.startsWith("INVITE_ACCEPT|")) {

                    try {
                        int roomId = Integer.parseInt(
                                message.substring(14).trim());

                        acceptInvite(currentUser, roomId);

                    } catch (NumberFormatException e) {
                        currentUser.send(
                                "ERROR|초대 방 번호 오류");
                    }
                }

                // 초대 거절
                else if (message.startsWith("INVITE_DECLINE|")) {

                    try {
                        int roomId = Integer.parseInt(
                                message.substring(15).trim());

                        declineInvite(currentUser, roomId);

                    } catch (NumberFormatException e) {
                        currentUser.send(
                                "ERROR|초대 방 번호 오류");
                    }
                }

                else {
                    currentUser.send("ERROR|알 수 없는 요청입니다.");
                }
            }

        } catch (Exception e) {
            System.out.println("클라이언트 연결 종료");

        } finally {

            if (currentUser != null) {

                invitations.remove(currentUser.id);
                onlineUsers.remove(currentUser.id, currentUser);

                System.out.println(
                        "회원 접속 종료: " + currentUser.id);

                showOnlineUsers();
                broadcast("ONLINE_UPDATE");
            }
        }
    }

    public static void main(String[] args) {

        System.out.println("===== 채팅 로비 서버 =====");

        try (ServerSocket server = new ServerSocket(
                PORT, 50, InetAddress.getLoopbackAddress())) {

            System.out.println("서버 실행 성공!");
            System.out.println("포트 번호: " + PORT);

            Thread watcher = new Thread(
                    ChatLobbyServer::watchRoomChanges);

            watcher.setDaemon(true);
            watcher.start();

            System.out.println("채팅방 자동 갱신 기능 시작!");
            System.out.println("귓속말 기능 준비 완료!");
            System.out.println("초대 기능 준비 완료!");
            System.out.println("클라이언트 접속 대기 중...");

            while (true) {

                Socket socket = server.accept();

                new Thread(() -> handleClient(socket)).start();
            }

        } catch (Exception e) {
            System.out.println("서버 실행 실패!");
            e.printStackTrace();
        }
    }
}
