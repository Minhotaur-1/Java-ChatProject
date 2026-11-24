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

    // 10주차: 방 입장 시 최근 30건의 채팅 기록을 전달
    public static void sendRecentHistory(ClientInfo client, int roomId) {
        if (!isRoomMember(roomId, client.id)) {
            client.send("ERROR|이전 대화를 조회할 권한이 없습니다.");
            return;
        }

        try {
            java.util.List<ChatMessageDAO.ChatRecord> rows =
                    new ChatMessageDAO().loadRecentMessages(roomId, 30);
            java.util.Map<Long, ReplyMessageDAO.ReplyInfo> references =
                    new ReplyMessageDAO().recentReplies(roomId, 30);

            // 전송 중 다른 서버 메시지가 섞이지 않도록 묶어서 출력
            synchronized (client.out) {
                client.out.println("HISTORY_BEGIN|" + roomId + "|" + rows.size());
                for (ChatMessageDAO.ChatRecord record : rows) {
                    ReplyMessageDAO.ReplyInfo reference = references.get(record.messageId);
                    if (reference == null) {
                        client.out.println("HISTORY|" + record.messageId + "|"
                                + record.roomId + "|" + record.senderId + "|"
                                + record.sentAt + "|" + record.content);
                    } else {
                        client.out.println("HISTORY_REPLY|" + record.messageId + "|"
                                + record.roomId + "|" + record.senderId + "|"
                                + record.sentAt + "|" + reference.originalMessageId + "|"
                                + reference.originalSender + "|" + reference.previewBase64()
                                + "|" + record.content);
                    }
                }
                client.out.println("HISTORY_END|" + roomId);
            }
        } catch (SQLException e) {
            client.send("ERROR|이전 채팅 기록을 불러오지 못했습니다.");
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

        // 서버에서 금칙어를 치환한 후 상대방에게 전송
        String filteredText;
        try {
            filteredText = ForbiddenWordFilter.filter(text);
        } catch (SQLException e) {
            sender.send("ERROR|금칙어 조회 오류로 귓속말을 보내지 못했습니다.");
            e.printStackTrace();
            return;
        }

        target.send("WHISPER_FROM|"
                + sender.id + "|" + filteredText);

        sender.send("WHISPER_SENT|"
                + targetId + "|" + filteredText);
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
            sendRecentHistory(target, roomId);

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

                // 11주차: 회원이 채팅방에서 보내는 파일의 바이너리 조각 중계
                // 디스크 저장 없이 같은 방에 접속한 상대 회원에게만 전송.
                if (message.startsWith("FILE_")) {
                    FileTransferRelay.handle(currentUser, message, onlineUsers);
                    continue;
                }

                // 11주차: 회원이 채팅방에서 보내는 파일의 바이너리 조각 중계
                // 디스크 저장 없이 같은 방에 접속한 상대 회원에게만 전송.
                if (message.startsWith("FILE_")) {
                    FileTransferRelay.handle(currentUser, message, onlineUsers);
                    continue;
                }

                // 2인 틱택토: 대전 신청/수락/거절/착수/종료
                if (message.startsWith("TTT_INVITE|")) {
                    TicTacToeService.invite(currentUser, message.substring(11).trim(), onlineUsers);
                    continue;
                }
                if (message.startsWith("TTT_ACCEPT|")) {
                    TicTacToeService.accept(currentUser, message.substring(11).trim(), onlineUsers);
                    continue;
                }
                if (message.startsWith("TTT_DECLINE|")) {
                    TicTacToeService.decline(currentUser, message.substring(12).trim(), onlineUsers);
                    continue;
                }
                if (message.startsWith("TTT_MOVE|")) {
                    String[] data = message.split("\\|", 3);
                    try {
                        if (data.length != 3) throw new NumberFormatException();
                        TicTacToeService.move(currentUser, Long.parseLong(data[1]),
                                Integer.parseInt(data[2]), onlineUsers);
                    } catch (NumberFormatException e) {
                        currentUser.send("TTT_ERROR|착수 정보가 올바르지 않습니다.");
                    }
                    continue;
                }
                if (message.startsWith("TTT_LEAVE|")) {
                    try {
                        TicTacToeService.leave(currentUser,
                                Long.parseLong(message.substring(10)), onlineUsers);
                    } catch (NumberFormatException ignored) { }
                    continue;
                }

                // 채팅방 목록
                if (message.equals("ROOMS")) {

                    sendRoomList(currentUser);
                }

                // 9주차: 1:1 채팅방 만들기
                else if (message.startsWith("CREATE_DIRECT|")) {
                    String targetId = message.substring("CREATE_DIRECT|".length()).trim();
                    if (!targetId.matches("[A-Za-z0-9_]{3,30}")) {
                        currentUser.send("ERROR|상대방 아이디를 확인해주세요.");
                        continue;
                    }

                    int newRoomId = new ChatRoomDAO()
                            .createDirectRoom(currentUser.id, targetId);
                    if (newRoomId == -1) {
                        currentUser.send("ERROR|1:1 방 생성 실패: 상대 회원을 확인해주세요.");
                    } else {
                        FileTransferRelay.onRoomChange(currentUser);
                        FileTransferRelay.onRoomChange(currentUser);
                        currentUser.roomId = newRoomId;
                        currentUser.send("CREATE_OK|" + newRoomId + "|DIRECT");
                        currentUser.send("JOIN_OK|" + newRoomId);
                        sendRecentHistory(currentUser, newRoomId);
                        sendRoomList(currentUser);
                    }
                }

                // 9주차: 그룹 채팅방 만들기. 형식: CREATE_GROUP|방이름|id1,id2
                else if (message.startsWith("CREATE_GROUP|")) {
                    String[] data = message.split("\\|", 3);
                    if (data.length != 3 || data[1].trim().isEmpty()
                            || data[1].trim().length() > 100
                            || data[2].trim().isEmpty()) {
                        currentUser.send("ERROR|그룹 방 형식: 방이름과 참여자 아이디를 확인해주세요.");
                        continue;
                    }

                    String[] memberIds = data[2].split(",", -1);
                    int newRoomId = new ChatRoomDAO().createGroupRoom(
                            currentUser.id, data[1].trim(), memberIds);
                    if (newRoomId == -1) {
                        currentUser.send("ERROR|그룹 방 생성 실패: 회원 ID와 중복을 확인해주세요.");
                    } else {
                        FileTransferRelay.onRoomChange(currentUser);
                        FileTransferRelay.onRoomChange(currentUser);
                        currentUser.roomId = newRoomId;
                        currentUser.send("CREATE_OK|" + newRoomId + "|GROUP");
                        currentUser.send("JOIN_OK|" + newRoomId);
                        sendRecentHistory(currentUser, newRoomId);
                        sendRoomList(currentUser);
                    }
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

                        if (currentUser.roomId != roomId) FileTransferRelay.onRoomChange(currentUser);
                        if (currentUser.roomId != roomId) FileTransferRelay.onRoomChange(currentUser);
                        currentUser.roomId = roomId;
                        currentUser.send("JOIN_OK|" + roomId);
                        sendRecentHistory(currentUser, roomId);

                    } catch (NumberFormatException e) {
                        currentUser.send(
                                "ERROR|채팅방 번호를 확인해주세요.");
                    }
                }

                // PNG/GIF 이모티콘: 서버가 허용한 ID만 DB에 저장합니다.
                // 이미지 파일은 두 클라이언트에 동일하게 포함되므로 파일 경로를 보내지 않습니다.
                else if (message.startsWith("EMOTICON|")) {
                    String emoticonId = message.substring("EMOTICON|".length()).trim();
                    int roomId = currentUser.roomId;
                    if (roomId == -1 || !isRoomMember(roomId, currentUser.id)) {
                        currentUser.send("ERROR|채팅방에 입장한 후 이모티콘을 보내주세요.");
                    } else if (!EmoticonCatalog.isAllowed(emoticonId)) {
                        currentUser.send("ERROR|지원하지 않는 이모티콘입니다.");
                    } else {
                        try {
                            String content = EmoticonCatalog.toMessage(emoticonId);
                            long messageId = new ChatMessageDAO()
                                    .saveMessage(roomId, currentUser.id, content);
                            String delivery = "CHAT_FROM|" + messageId + "|"
                                    + roomId + "|" + currentUser.id + "|" + content;
                            for (ClientInfo target : onlineUsers.values()) {
                                if (target.roomId == roomId
                                        && isRoomMember(roomId, target.id)) {
                                    target.send(delivery);
                                }
                            }
                        } catch (SQLException e) {
                            currentUser.send("ERROR|이모티콘 DB 저장에 실패했습니다.");
                            e.printStackTrace();
                        }
                    }
                }

                // 10주차: 일반 채팅을 DB에 저장한 뒤 같은 방에 전송
                else if (message.startsWith("CHAT|")) {
                    String content = message.substring(5).trim();
                    int roomId = currentUser.roomId;

                    if (roomId == -1 || !isRoomMember(roomId, currentUser.id)) {
                        currentUser.send("ERROR|먼저 참여 중인 채팅방에 입장해주세요.");
                    } else if (content.isEmpty() || content.length() > 1000) {
                        currentUser.send("ERROR|메시지는 1~1000자로 입력해주세요.");
                    } else {
                        try {
                            // 금칙어를 서버에서 먼저 치환하고, 치환된 내용만 DB에 저장
                            String filteredContent = ForbiddenWordFilter.filter(content);

                            // 실제 방 참여자의 아이디/닉네임과 멘션을 비교
                            java.util.Set<String> mentionedIds =
                                    MentionService.findMentionedUserIds(roomId, filteredContent);

                            // DB 저장 성공 시에만 채팅방에 메시지 전달
                            long messageId = new ChatMessageDAO()
                                    .saveMessage(roomId, currentUser.id, filteredContent);

                            String delivery = "CHAT_FROM|" + messageId + "|"
                                    + roomId + "|" + currentUser.id + "|" + filteredContent;

                            for (ClientInfo target : onlineUsers.values()) {
                                if (target.roomId == roomId
                                        && isRoomMember(roomId, target.id)) {
                                    target.send(delivery);
                                    // 같은 메시지를 받은 뒤 해당 수신자에게만 강조 알림
                                    if (!target.id.equals(currentUser.id)
                                            && mentionedIds.contains(target.id)) {
                                        target.send("MENTION_NOTICE|" + roomId + "|"
                                                + messageId + "|" + currentUser.id);
                                    }
                                }
                            }
                        } catch (SQLException e) {
                            currentUser.send("ERROR|금칙어 조회 또는 채팅 메시지 저장에 실패했습니다.");
                            e.printStackTrace();
                        }
                    }
                }

                // 새 기능: 선택한 원본 메시지에 답장 (서버에서 방 권한/원본 관계 검사)
                else if (message.startsWith("REPLY|")) {
                    String[] parts = message.split("\\|", 3);
                    int roomId = currentUser.roomId;
                    if (roomId == -1 || !isRoomMember(roomId, currentUser.id)) {
                        currentUser.send("ERROR|먼저 참여 중인 채팅방에 입장해주세요.");
                    } else if (parts.length != 3) {
                        currentUser.send("ERROR|답장 형식이 올바르지 않습니다.");
                    } else {
                        try {
                            long originalId = Long.parseLong(parts[1]);
                            String content = parts[2].trim();
                            if (content.isEmpty() || content.length() > 1000) {
                                currentUser.send("ERROR|답장은 1~1000자로 입력해주세요.");
                                continue;
                            }
                            String filtered = ForbiddenWordFilter.filter(content);
                            java.util.Set<String> mentionedIds =
                                    MentionService.findMentionedUserIds(roomId, filtered);
                            ReplyMessageDAO.SavedReply reply = new ReplyMessageDAO()
                                    .saveReply(roomId, currentUser.id, originalId, filtered);
                            String delivery = "REPLY_FROM|" + reply.newMessageId + "|"
                                    + roomId + "|" + currentUser.id + "|"
                                    + reply.reference.originalMessageId + "|"
                                    + reply.reference.originalSender + "|"
                                    + reply.reference.previewBase64() + "|" + filtered;
                            for (ClientInfo target : onlineUsers.values()) {
                                if (target.roomId == roomId && isRoomMember(roomId, target.id)) {
                                    target.send(delivery);
                                    if (!target.id.equals(currentUser.id)
                                            && mentionedIds.contains(target.id)) {
                                        target.send("MENTION_NOTICE|" + roomId + "|"
                                                + reply.newMessageId + "|" + currentUser.id);
                                    }
                                }
                            }
                        } catch (NumberFormatException e) {
                            currentUser.send("ERROR|답장할 메시지 번호가 올바르지 않습니다.");
                        } catch (SQLException e) {
                            currentUser.send("ERROR|답장 저장에 실패했습니다. SQL 컬럼과 서버 로그를 확인해주세요.");
                            e.printStackTrace();
                        }
                    }
                }

                // 10주차: 현재 입장한 채팅방에서 키워드 포함 메시지 검색
                else if (message.startsWith("SEARCH|")) {
                    String keyword = message.substring("SEARCH|".length()).trim();
                    int roomId = currentUser.roomId;

                    if (roomId == -1 || !isRoomMember(roomId, currentUser.id)) {
                        currentUser.send("ERROR|검색할 채팅방에 먼저 입장해주세요.");
                    } else if (keyword.isEmpty() || keyword.length() > 100) {
                        currentUser.send("ERROR|검색어는 1~100자로 입력해주세요.");
                    } else {
                        try {
                            java.util.List<ChatMessageDAO.ChatRecord> matches =
                                    new ChatMessageDAO().searchMessages(roomId, keyword, 30);
                            synchronized (currentUser.out) {
                                currentUser.out.println("SEARCH_BEGIN|" + roomId
                                        + "|" + matches.size());
                                for (ChatMessageDAO.ChatRecord record : matches) {
                                    currentUser.out.println("SEARCH_RESULT|"
                                            + record.messageId + "|" + record.roomId
                                            + "|" + record.senderId + "|"
                                            + record.sentAt + "|" + record.content);
                                }
                                currentUser.out.println("SEARCH_END|" + roomId);
                            }
                        } catch (SQLException e) {
                            currentUser.send("ERROR|채팅 검색에 실패했습니다.");
                            e.printStackTrace();
                        }
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

                else if (message.startsWith("IMAGE|")) {

                	ChatImageRelay.sendImage(
                			currentUser,
                			message.substring(6),
                			onlineUsers);
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

                // 송신자 연결 종료 시 상대방의 미완료 파일 임시저장을 폐기하도록 통지
                FileTransferRelay.onDisconnect(currentUser);
                // 송신자 연결 종료 시 상대방의 미완료 파일 임시저장을 폐기하도록 통지
                FileTransferRelay.onDisconnect(currentUser);
                // 틱택토 진행 중 연결 종료 시 상대방에게 알림
                TicTacToeService.onDisconnect(currentUser, onlineUsers);
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
