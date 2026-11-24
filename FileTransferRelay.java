package ChatProject;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side file relay. All files are relayed as before.
 * PNG/JPEG/GIF images up to 5MB are additionally saved to MySQL chat_images.
 * All FILE_* commands are accepted only after an established chat login.
 */
public final class FileTransferRelay {
    public static final long MAX_BYTES = 20L * 1024L * 1024L;
    public static final int CHUNK_BYTES = 8 * 1024;
    private static final long MAX_DURATION_NANOS = 120L * 1_000_000_000L;
    private static final int MAX_NAME_LENGTH = 140;

    private static final class Transfer {
        final String id;
        final ChatLobbyServer.ClientInfo sender;
        final int room;
        final long size;
        final String name;
        final ByteArrayOutputStream galleryImage;
        final List<ChatLobbyServer.ClientInfo> recipients;
        final long startedAt = System.nanoTime();
        long bytes = 0;

        Transfer(String id, ChatLobbyServer.ClientInfo sender, int room,
                 long size, String name, List<ChatLobbyServer.ClientInfo> recipients) {
            this.id = id;
            this.sender = sender;
            this.room = room;
            this.size = size;
            this.name = name;
            this.galleryImage = size <= ChatImageRepository.MAX_IMAGE_BYTES
                    && ChatImageRepository.isCandidate(name)
                    ? new ByteArrayOutputStream((int) size) : null;
            this.recipients = recipients;
        }
    }

    // One in-progress outgoing transfer per logged-in user/socket.
    private static final ConcurrentHashMap<String, Transfer> active = new ConcurrentHashMap<>();

    private FileTransferRelay() { }

    public static void handle(ChatLobbyServer.ClientInfo user, String line,
            ConcurrentHashMap<String, ChatLobbyServer.ClientInfo> onlineUsers) {
        try {
            if (line.startsWith("FILE_BEGIN|")) {
                begin(user, line, onlineUsers);
            } else if (line.startsWith("FILE_CHUNK|")) {
                chunk(user, line);
            } else if (line.startsWith("FILE_END|")) {
                end(user, line, onlineUsers);
            } else if (line.startsWith("FILE_CANCEL|")) {
                cancel(user, line.substring("FILE_CANCEL|".length()), "전송이 취소됐습니다.");
            } else {
                user.send("FILE_ERROR||알 수 없는 파일 전송 명령입니다.");
            }
        } catch (RuntimeException ex) {
            cancel(user, "", "잘못된 파일 전송 요청입니다.");
        }
    }

    private static boolean validId(String value) {
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validName(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH
                || name.equals(".") || name.equals("..")) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || c == '|' || c == ':' || c == '<'
                    || c == '>' || c == '"' || c == '*' || c == '?'
                    || Character.isISOControl(c)) return false;
        }
        return true;
    }

    private static void begin(ChatLobbyServer.ClientInfo user, String line,
            ConcurrentHashMap<String, ChatLobbyServer.ClientInfo> onlineUsers) {
        String[] fields = line.split("\\|", 4);
        String id = fields.length > 1 ? fields[1] : "";
        if (fields.length != 4 || !validId(id)) {
            user.send("FILE_ERROR|" + id + "|파일 전송 정보가 올바르지 않습니다.");
            return;
        }
        if (active.containsKey(user.id)) {
            user.send("FILE_ERROR|" + id + "|이전 파일 전송이 끝날 때까지 기다려주세요.");
            return;
        }
        final String name;
        final long size;
        try {
            name = new String(Base64.getUrlDecoder().decode(fields[2]), StandardCharsets.UTF_8);
            size = Long.parseLong(fields[3]);
        } catch (Exception e) {
            user.send("FILE_ERROR|" + id + "|파일명 또는 크기가 잘못됐습니다.");
            return;
        }
        if (!validName(name) || size < 1 || size > MAX_BYTES) {
            user.send("FILE_ERROR|" + id + "|파일명 또는 크기(20MB 이하)를 확인해주세요.");
            return;
        }
        int room = user.roomId;
        if (room < 0 || !ChatLobbyServer.isRoomMember(room, user.id)) {
            user.send("FILE_ERROR|" + id + "|먼저 참여 중인 채팅방에 입장해주세요.");
            return;
        }
        List<ChatLobbyServer.ClientInfo> recipients = new ArrayList<>();
        for (ChatLobbyServer.ClientInfo other : onlineUsers.values()) {
            if (!other.id.equals(user.id) && other.roomId == room
                    && ChatLobbyServer.isRoomMember(room, other.id)) {
                recipients.add(other);
            }
        }
        if (recipients.isEmpty()) {
            user.send("FILE_ERROR|" + id + "|같은 방에 접속 중인 상대방이 없습니다.");
            return;
        }
        Transfer transfer = new Transfer(id, user, room, size, name, recipients);
        if (active.putIfAbsent(user.id, transfer) != null) {
            user.send("FILE_ERROR|" + id + "|다른 파일을 전송하고 있습니다.");
            return;
        }
        for (ChatLobbyServer.ClientInfo recipient : recipients) {
            recipient.send("FILE_OFFER|" + id + "|" + room + "|" + user.id
                    + "|" + fields[2] + "|" + size);
        }
        user.send("FILE_READY|" + id + "|" + recipients.size());
    }

    private static void chunk(ChatLobbyServer.ClientInfo user, String line) {
        String[] fields = line.split("\\|", 3);
        if (fields.length != 3) {
            cancel(user, "", "파일 조각 형식이 올바르지 않습니다.");
            return;
        }
        Transfer transfer = active.get(user.id);
        if (transfer == null || !transfer.id.equals(fields[1])) {
            user.send("FILE_ERROR|" + fields[1] + "|진행 중인 파일 전송이 없습니다.");
            return;
        }
        if (System.nanoTime() - transfer.startedAt > MAX_DURATION_NANOS) {
            cancel(user, transfer.id, "파일 전송 시간이 초과됐습니다.");
            return;
        }
        final int bytes;
        final byte[] decoded;
        try {
            if (fields[2].length() > ((CHUNK_BYTES + 2) / 3) * 4) {
                throw new IllegalArgumentException();
            }
            decoded = Base64.getDecoder().decode(fields[2]);
            bytes = decoded.length;
        } catch (IllegalArgumentException ex) {
            cancel(user, transfer.id, "파일 데이터가 손상됐습니다.");
            return;
        }
        if (bytes == 0 || bytes > CHUNK_BYTES || transfer.bytes + bytes > transfer.size) {
            cancel(user, transfer.id, "파일 데이터 크기가 올바르지 않습니다.");
            return;
        }
        transfer.bytes += bytes;
        // 메모리 보관은 확장자와 5MB 조건을 통과한 이미지 후보에 한정합니다.
        if (transfer.galleryImage != null) {
            transfer.galleryImage.write(decoded, 0, bytes);
        }
        for (ChatLobbyServer.ClientInfo recipient : transfer.recipients) {
            recipient.send(line);
        }
    }

    private static void end(ChatLobbyServer.ClientInfo user, String line,
            ConcurrentHashMap<String, ChatLobbyServer.ClientInfo> onlineUsers) {
        String id = line.substring("FILE_END|".length());
        Transfer transfer = active.get(user.id);
        if (transfer == null || !transfer.id.equals(id)) {
            user.send("FILE_ERROR|" + id + "|진행 중인 파일 전송이 없습니다.");
            return;
        }
        if (transfer.bytes != transfer.size) {
            cancel(user, id, "파일 크기가 일치하지 않아 전송을 취소했습니다.");
            return;
        }
        active.remove(user.id, transfer);
        // 이미지 원본+채팅 기록을 하나의 트랜잭션으로 저장합니다.
        // 파일 중계가 완료된 뒤 이미지를 가리키는 CHAT_FROM을 전송하므로
        // 재입장 때에도 기존 HISTORY 프로토콜로 이미지를 다시 표시할 수 있습니다.
        // DB 오류가 발생해도 기존 FILE_END/FILE_SENT는 정상 전송합니다.
        ChatImageRepository.SavedImageMessage saved = null;
        if (transfer.galleryImage != null) {
            byte[] imageBytes = transfer.galleryImage.toByteArray();
            String mime = ChatImageRepository.verifiedMime(transfer.name, imageBytes);
            if (mime != null) {
                try {
                    saved = ChatImageRepository.saveImageWithChatMessage(
                            transfer.room, user.id, transfer.name, mime, imageBytes);
                    System.out.println("[11주차 이미지 기록] #" + saved.imageId
                            + " / 채팅 #" + saved.messageId + " 저장: " + transfer.name);
                } catch (Exception e) {
                    System.err.println("[11주차 이미지 기록] 이미지 DB 저장 실패: " + e.getMessage());
                }
            }
        }
        for (ChatLobbyServer.ClientInfo recipient : transfer.recipients) {
            recipient.send("FILE_END|" + id);
        }
        user.send("FILE_SENT|" + id + "|" + transfer.recipients.size());
        if (saved != null) {
            String delivery = "CHAT_FROM|" + saved.messageId + "|" + transfer.room
                    + "|" + user.id + "|" + ChatImageRepository.imageMessage(saved.imageId);
            for (ChatLobbyServer.ClientInfo target : onlineUsers.values()) {
                if (target.roomId == transfer.room
                        && ChatLobbyServer.isRoomMember(transfer.room, target.id)) {
                    target.send(delivery);
                }
            }
        }
    }

    private static void cancel(ChatLobbyServer.ClientInfo user, String id, String reason) {
        Transfer old = active.get(user.id);
        if (old != null && (id.isEmpty() || old.id.equals(id))
                && active.remove(user.id, old)) {
            for (ChatLobbyServer.ClientInfo recipient : old.recipients) {
                recipient.send("FILE_ABORT|" + old.id + "|" + reason);
            }
            user.send("FILE_ERROR|" + old.id + "|" + reason);
        } else {
            user.send("FILE_ERROR|" + id + "|" + reason);
        }
    }

    /** Cancel the old-room upload on a new room join, rather than leaking data across rooms. */
    public static void onRoomChange(ChatLobbyServer.ClientInfo user) {
        Transfer old = active.get(user.id);
        if (old != null) cancel(user, old.id, "채팅방을 이동해 파일 전송이 취소됐습니다.");
    }

    /** Remove incomplete uploads if a sender logs out. */
    public static void onDisconnect(ChatLobbyServer.ClientInfo user) {
        Transfer old = active.remove(user.id);
        if (old != null) {
            for (ChatLobbyServer.ClientInfo recipient : old.recipients) {
                recipient.send("FILE_ABORT|" + old.id + "|보낸 사람이 연결을 종료했습니다.");
            }
        }
    }
}
