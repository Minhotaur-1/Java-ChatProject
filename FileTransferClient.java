package ChatProject;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Client-side file sender / receiver attached to the existing lobby socket. */
public final class FileTransferClient {
    private final PrintWriter out;
    private final AtomicReference<ChatRoomWindow> roomWindow;
    private final AtomicReference<ChatLobbyFrame> lobbyWindow;

    // Sending occurs on background workers. Receiving runs on socket reader thread.
    private final Map<String, Outgoing> outgoing = new ConcurrentHashMap<>();
    private final Map<String, Incoming> incoming = new HashMap<>();

    private static final class Outgoing {
        final String id;
        final Path source;
        final String name;
        final int room;
        final long length;
        final CountDownLatch ready = new CountDownLatch(1);
        final CountDownLatch completed = new CountDownLatch(1);
        volatile String error;
        volatile int recipients;
        volatile boolean success;

        Outgoing(String id, Path source, String name, int room, long length) {
            this.id = id;
            this.source = source;
            this.name = name;
            this.room = room;
            this.length = length;
        }
    }

    private static final class Incoming {
        final String id;
        final int room;
        final String sender;
        final String name;
        final long expected;
        final Path temp;
        final OutputStream output;
        long received = 0;

        Incoming(String id, int room, String sender, String name, long expected,
                 Path temp, OutputStream output) {
            this.id = id;
            this.room = room;
            this.sender = sender;
            this.name = name;
            this.expected = expected;
            this.temp = temp;
            this.output = output;
        }
    }

    public FileTransferClient(PrintWriter out,
            AtomicReference<ChatRoomWindow> roomWindow,
            AtomicReference<ChatLobbyFrame> lobbyWindow) {
        this.out = out;
        this.roomWindow = roomWindow;
        this.lobbyWindow = lobbyWindow;
    }

    private Component parentWindow() {
        ChatRoomWindow w = roomWindow.get();
        if (w != null && w.isDisplayable()) return w;
        return lobbyWindow.get();
    }

    private void notifyUser(String text) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                parentWindow(), text, "파일 전송", JOptionPane.INFORMATION_MESSAGE));
    }

    private void appendSystem(int room, String text) {
        SwingUtilities.invokeLater(() -> {
            ChatRoomWindow w = roomWindow.get();
            if (w != null && w.isDisplayable()) w.appendSystem(room, text);
        });
    }

    private static boolean validName(String name) {
        if (name == null || name.isBlank() || name.length() > 140
                || name.equals(".") || name.equals("..")) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || c == '|' || c == ':' || c == '<'
                    || c == '>' || c == '"' || c == '*' || c == '?'
                    || Character.isISOControl(c)) return false;
        }
        return true;
    }

    /** Invoked on the Swing EDT by the chat room's '파일 보내기' button. */
    public void chooseAndSend(int roomId) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("전송할 파일 선택 (최대 20MB)");
        if (chooser.showOpenDialog(parentWindow()) != JFileChooser.APPROVE_OPTION) return;
        File selected = chooser.getSelectedFile();
        Path path = selected.toPath();
        try {
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                notifyUser("읽을 수 있는 일반 파일을 선택해주세요.");
                return;
            }
            long length = Files.size(path);
            String filename = selected.getName();
            if (!validName(filename) || length < 1 || length > FileTransferRelay.MAX_BYTES) {
                notifyUser("파일명과 파일 크기를 확인해주세요. (1바이트~20MB)");
                return;
            }
            // Do not read large files on the UI thread.
            Thread worker = new Thread(() -> upload(path, filename, length, roomId),
                    "ChatProject-FileUpload");
            worker.setDaemon(true);
            worker.start();
        } catch (IOException e) {
            notifyUser("파일을 읽을 수 없습니다: " + e.getMessage());
        }
    }

    private void sendLine(String line) throws IOException {
        synchronized (out) {
            out.println(line);
            if (out.checkError()) throw new IOException("서버 연결이 종료됐습니다.");
        }
    }

    private void upload(Path path, String name, long length, int room) {
        final String id = UUID.randomUUID().toString();
        Outgoing transfer = new Outgoing(id, path, name, room, length);
        outgoing.put(id, transfer);
        String filename = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(name.getBytes(StandardCharsets.UTF_8));
        try {
            sendLine("FILE_BEGIN|" + id + "|" + filename + "|" + length);
            if (!transfer.ready.await(12, TimeUnit.SECONDS)) {
                throw new IOException("파일 전송 승인을 받지 못했습니다.");
            }
            if (transfer.error != null) throw new IOException(transfer.error);
            byte[] bytes = new byte[FileTransferRelay.CHUNK_BYTES];
            long sent = 0;
            try (java.io.InputStream input = Files.newInputStream(path)) {
                while (sent < length) {
                    if (transfer.error != null) throw new IOException(transfer.error);
                    int count = input.read(bytes, 0, (int) Math.min(bytes.length, length - sent));
                    if (count < 0) throw new IOException("파일을 읽는 도중 크기가 변경됐습니다.");
                    if (count == 0) continue;
                    String payload = Base64.getEncoder()
                            .encodeToString(Arrays.copyOf(bytes, count));
                    sendLine("FILE_CHUNK|" + id + "|" + payload);
                    sent += count;
                }
            }
            sendLine("FILE_END|" + id);
            if (!transfer.completed.await(15, TimeUnit.SECONDS)) {
                throw new IOException("서버의 전송 완료 응답을 받지 못했습니다.");
            }
            if (transfer.error != null) throw new IOException(transfer.error);
            if (!transfer.success) throw new IOException("파일 전송 완료 여부를 확인하지 못했습니다.");
            appendSystem(room, "파일 전송 완료: " + name
                    + " (수신 대상 " + transfer.recipients + "명)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            notifyUser("파일 전송이 중단됐습니다.");
            try { sendLine("FILE_CANCEL|" + id); } catch (IOException ignored) { }
        } catch (Exception e) {
            notifyUser("파일 전송 실패: " + e.getMessage());
            if (transfer.error == null) {
                try { sendLine("FILE_CANCEL|" + id); } catch (IOException ignored) { }
            }
        } finally {
            outgoing.remove(id);
        }
    }

    /** Called by the existing socket receiving thread before other handlers. */
    public boolean handleServerLine(String line) {
        if (!line.startsWith("FILE_")) return false;
        try {
            if (line.startsWith("FILE_READY|")) {
                String[] fields = line.split("\\|", 3);
                if (fields.length == 3) {
                    Outgoing tx = outgoing.get(fields[1]);
                    if (tx != null) {
                        tx.recipients = Integer.parseInt(fields[2]);
                        tx.ready.countDown();
                    }
                }
            } else if (line.startsWith("FILE_SENT|")) {
                String[] fields = line.split("\\|", 3);
                if (fields.length == 3) {
                    Outgoing tx = outgoing.get(fields[1]);
                    if (tx != null) {
                        tx.recipients = Integer.parseInt(fields[2]);
                        tx.success = true;
                        tx.completed.countDown();
                    }
                }
            } else if (line.startsWith("FILE_ERROR|")) {
                String[] fields = line.split("\\|", 3);
                if (fields.length == 3) {
                    Outgoing tx = outgoing.get(fields[1]);
                    if (tx != null) {
                        tx.error = fields[2];
                        tx.ready.countDown();
                        tx.completed.countDown();
                    } else {
                        notifyUser(fields[2]);
                    }
                }
            } else if (line.startsWith("FILE_OFFER|")) {
                receiveOffer(line);
            } else if (line.startsWith("FILE_CHUNK|")) {
                receiveChunk(line);
            } else if (line.startsWith("FILE_END|")) {
                receiveEnd(line.substring("FILE_END|".length()));
            } else if (line.startsWith("FILE_ABORT|")) {
                String[] fields = line.split("\\|", 3);
                if (fields.length == 3) {
                    Incoming rx = incoming.remove(fields[1]);
                    if (rx != null) {
                        discard(rx);
                        appendSystem(rx.room, rx.sender + "님의 파일 전송이 취소됐습니다.");
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println("[파일 수신 오류] " + ex.getMessage());
            notifyUser("파일 데이터 처리 중 오류: " + ex.getMessage());
        }
        return true;
    }

    private static String decodeName(String encoded) {
        byte[] decoded = Base64.getUrlDecoder().decode(encoded);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private void receiveOffer(String line) throws IOException {
        String[] fields = line.split("\\|", 6);
        if (fields.length != 6) return;
        String id = fields[1];
        String name = decodeName(fields[4]);
        long length;
        try { length = Long.parseLong(fields[5]); }
        catch (NumberFormatException e) { return; }
        int room;
        try { room = Integer.parseInt(fields[2]); }
        catch (NumberFormatException e) { return; }
        if (!validName(name) || length < 1 || length > FileTransferRelay.MAX_BYTES
                || incoming.containsKey(id) || incoming.size() >= 4) return;

        // Temporary file contains received data until the user explicitly saves it.
        Path temp = Files.createTempFile("chatproject-file-", ".part");
        try {
            OutputStream output = Files.newOutputStream(temp);
            incoming.put(id, new Incoming(id, room, fields[3], name, length, temp, output));
            appendSystem(room, "파일 수신 중: " + fields[3] + " → " + name
                    + " (" + humanSize(length) + ")");
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    private void receiveChunk(String line) {
        String[] fields = line.split("\\|", 3);
        if (fields.length != 3) return;
        Incoming rx = incoming.get(fields[1]);
        if (rx == null) return;
        try {
            if (fields[2].length() > ((FileTransferRelay.CHUNK_BYTES + 2) / 3) * 4)
                throw new IOException("파일 조각이 너무 큽니다.");
            byte[] data = Base64.getDecoder().decode(fields[2]);
            if (data.length == 0 || data.length > FileTransferRelay.CHUNK_BYTES
                    || rx.received + data.length > rx.expected) {
                throw new IOException("파일 크기 검증에 실패했습니다.");
            }
            rx.output.write(data);
            rx.received += data.length;
        } catch (Exception e) {
            incoming.remove(fields[1]);
            discard(rx);
            notifyUser("파일 수신 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    private void receiveEnd(String id) {
        Incoming rx = incoming.remove(id);
        if (rx == null) return;
        try {
            rx.output.close();
            if (rx.received != rx.expected) {
                Files.deleteIfExists(rx.temp);
                notifyUser("파일이 일부만 도착해 저장할 수 없습니다: " + rx.name);
                return;
            }
            appendSystem(rx.room, "파일 수신 완료: " + rx.sender + " → " + rx.name);
            SwingUtilities.invokeLater(() -> promptSave(rx));
        } catch (IOException e) {
            discard(rx);
            notifyUser("파일 수신을 완료할 수 없습니다: " + e.getMessage());
        }
    }

    private void promptSave(Incoming rx) {
        try {
            int confirm = JOptionPane.showConfirmDialog(parentWindow(),
                    rx.sender + "님이 파일을 보냈습니다.\n" + rx.name + " (" + humanSize(rx.received)
                            + ")\n\n내 컴퓨터에 저장하시겠습니까?",
                    "파일 수신", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("받은 파일 저장 위치 선택");
            chooser.setSelectedFile(new File(rx.name));
            if (chooser.showSaveDialog(parentWindow()) != JFileChooser.APPROVE_OPTION) return;
            Path destination = chooser.getSelectedFile().toPath();
            if (Files.exists(destination)) {
                int overwrite = JOptionPane.showConfirmDialog(parentWindow(),
                        "같은 이름의 파일이 있습니다. 덮어쓰시겠습니까?",
                        "파일 덮어쓰기", JOptionPane.YES_NO_OPTION);
                if (overwrite != JOptionPane.YES_OPTION) return;
            }
            Files.copy(rx.temp, destination, StandardCopyOption.REPLACE_EXISTING);
            JOptionPane.showMessageDialog(parentWindow(), "파일 저장 완료!\n" + destination);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentWindow(),
                    "파일을 저장하지 못했습니다: " + e.getMessage(),
                    "파일 저장 오류", JOptionPane.WARNING_MESSAGE);
        } finally {
            try { Files.deleteIfExists(rx.temp); } catch (IOException ignored) { }
        }
    }

    private static String humanSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    private static void discard(Incoming rx) {
        try { rx.output.close(); } catch (IOException ignored) { }
        try { Files.deleteIfExists(rx.temp); } catch (IOException ignored) { }
    }

    /** Call when the client connection terminates. */
    public void shutdown() {
        for (Incoming rx : incoming.values()) discard(rx);
        incoming.clear();
        for (Outgoing tx : outgoing.values()) {
            tx.error = "서버 연결이 종료됐습니다.";
            tx.ready.countDown();
            tx.completed.countDown();
        }
    }
}
