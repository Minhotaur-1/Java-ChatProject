package ChatProject;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.Map;
import java.util.LinkedHashMap;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.StyledDocument;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

/**
 * 10주차: 기존 콘솔 채팅과 연결되는 Swing 채팅 화면.
 * 서버와 DB는 기존 구현을 사용하며, 사용자별/채팅방별 화면 설정은 로컬에 보관한다.
 */
public class ChatRoomWindow extends JFrame {

    private final String userId;
    private final Consumer<String> messageSender;
    // 같은 서버 소켓으로 귓속말, 채팅 검색 요청을 보내기 위한 전용 콜백.
    private final Consumer<String> commandSender;
    // GUI에서 로컬 파일 선택기를 열고 선택된 파일을 기존 소켓으로 전송
    private final java.util.function.IntConsumer fileSender;
    private final Preferences settings =
            Preferences.userRoot().node("ChatProject/roomAppearance");

    private int roomId;
    private final JTextPane chatArea = new JTextPane();
    private final JTextField inputField = new JTextField();
    private final JLabel roomTitle = new JLabel();
    private final JLabel mentionStatus = new JLabel("");
    // 메시지 번호 -> 채팅창에 출력한 글자 범위
    private final Map<Long, int[]> messageRanges = new LinkedHashMap<>();
    private final Map<Long, ReplyTarget> replyTargets = new LinkedHashMap<>();
    private Long lastClickedId = null;
    private Long replyingToId = null;
    private final JLabel replyHint = new JLabel("");
    private final JPanel replyBar = new JPanel(new BorderLayout(6, 0));

    private static final class ReplyTarget {
        final String sender;
        final String content;
        ReplyTarget(String sender, String content) {
            this.sender = sender;
            this.content = content;
        }
    }
    private final JComboBox<String> fontSelect = new JComboBox<>(
            new String[]{"맑은 고딕", "Dialog", "SansSerif", "Serif", "Monospaced"});
    private final JComboBox<Integer> fontSizeSelect = new JComboBox<>(
            new Integer[]{12, 14, 16, 18, 20, 24});
    private boolean loadingSetting = false;

    // 기존 생성자와의 호환성 유지. 신규 호출에서는 아래 4인자 생성자를 사용한다.
    public ChatRoomWindow(String userId, int roomId, Consumer<String> messageSender) {
        this(userId, roomId, messageSender, null);
    }

    public ChatRoomWindow(String userId, int roomId,
                          Consumer<String> messageSender,
                          Consumer<String> commandSender) {
        this(userId, roomId, messageSender, commandSender, null);
    }

    public ChatRoomWindow(String userId, int roomId,
                          Consumer<String> messageSender,
                          Consumer<String> commandSender,
                          java.util.function.IntConsumer fileSender) {
        this.userId = userId;
        this.messageSender = messageSender;
        this.commandSender = commandSender;
        this.fileSender = fileSender;

        setSize(720, 560);
        setMinimumSize(new Dimension(500, 390));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // 상단: 현재 방과 꾸미기 버튼
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        roomTitle.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        toolbar.add(roomTitle);
        mentionStatus.setForeground(new Color(180, 95, 0));
        mentionStatus.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        toolbar.add(mentionStatus);
        toolbar.add(Box.createHorizontalStrut(10));

        JButton bgButton = new JButton("배경색 변경");
        toolbar.add(bgButton);
        toolbar.add(new JLabel("글꼴"));
        toolbar.add(fontSelect);
        toolbar.add(new JLabel("크기"));
        toolbar.add(fontSizeSelect);
        add(toolbar, BorderLayout.NORTH);

        chatArea.setEditable(false);
        // JTextPane은 이미지 아이콘을 본문 안에 삽입할 수 있고 자동 줄바꿈됩니다.
        chatArea.setMargin(new Insets(14, 14, 14, 14));
        JScrollPane scroll = new JScrollPane(chatArea);
        add(scroll, BorderLayout.CENTER);

        JPanel composePanel = new JPanel(new BorderLayout(8, 0));
        composePanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        JButton sendButton = new JButton("전송");
        JButton replyButton = new JButton("답장");
        replyButton.setToolTipText("채팅 메시지를 클릭한 뒤 답장 버튼을 누르세요.");
        replyButton.setEnabled(commandSender != null);
        JButton whisperButton = new JButton("귓속말");
        whisperButton.setToolTipText("같은 채팅방에 접속 중인 회원에게만 메시지를 보냅니다.");
        JButton searchButton = new JButton("검색");
        searchButton.setToolTipText("현재 채팅방에 저장된 메시지를 검색합니다.");
        whisperButton.setEnabled(commandSender != null);
        searchButton.setEnabled(commandSender != null);
        JButton mentionButton = new JButton("@멘션");
        mentionButton.setToolTipText("상대방 아이디 또는 닉네임을 @뒤에 입력하세요.");
        JButton emojiButton = new JButton("☺ 이모티콘");
        emojiButton.setToolTipText("기존 Unicode 이모티콘을 입력창에 추가합니다.");
        JButton imageEmojiButton = new JButton("PNG/GIF");
        imageEmojiButton.setToolTipText("PNG/GIF 이미지 이모티콘을 바로 전송합니다.");
        imageEmojiButton.setEnabled(commandSender != null);
        JButton fileButton = new JButton("파일 보내기");
        fileButton.setToolTipText("같은 채팅방의 접속 중인 회원에게 파일을 전송합니다 (최대 20MB).");
        fileButton.setEnabled(fileSender != null);
        JButton galleryButton = new JButton("이미지 갤러리");
        galleryButton.setToolTipText("현재 채팅방에 저장된 PNG/JPG/GIF 이미지를 확인합니다.");
        JPanel actionButtons = new JPanel(new GridLayout(0, 4, 5, 4));
        actionButtons.add(replyButton);
        actionButtons.add(whisperButton);
        actionButtons.add(searchButton);
        actionButtons.add(mentionButton);
        actionButtons.add(emojiButton);
        actionButtons.add(imageEmojiButton);
        actionButtons.add(fileButton);
        actionButtons.add(galleryButton);
        // 버튼 수가 늘어나도 입력창이 좁아지지 않도록 도구 버튼을 별도 줄로 둔다.
        JPanel writeRow = new JPanel(new BorderLayout(8, 0));
        writeRow.add(inputField, BorderLayout.CENTER);
        writeRow.add(sendButton, BorderLayout.EAST);
        composePanel.add(writeRow, BorderLayout.CENTER);
        composePanel.add(actionButtons, BorderLayout.SOUTH);

        replyBar.setBorder(BorderFactory.createEmptyBorder(0, 3, 7, 3));
        replyHint.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        replyBar.add(replyHint, BorderLayout.CENTER);
        JButton cancelReply = new JButton("답장 취소 ×");
        cancelReply.addActionListener(e -> clearReplyTarget());
        replyBar.add(cancelReply, BorderLayout.EAST);
        replyBar.setVisible(false);
        composePanel.add(replyBar, BorderLayout.NORTH);
        add(composePanel, BorderLayout.SOUTH);

        chatArea.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int offset = chatArea.viewToModel2D(e.getPoint());
                lastClickedId = null;
                for (Map.Entry<Long, int[]> entry : messageRanges.entrySet()) {
                    int[] range = entry.getValue();
                    if (offset >= range[0] && offset <= range[1]) {
                        lastClickedId = entry.getKey();
                        break;
                    }
                }
            }
        });

        inputField.addActionListener(e -> sendMessage());
        sendButton.addActionListener(e -> sendMessage());
        replyButton.addActionListener(e -> selectReplyTarget());
        whisperButton.addActionListener(e -> sendWhisper());
        searchButton.addActionListener(e -> searchMessages());
        emojiButton.addActionListener(e -> openEmojiPicker(emojiButton));
        imageEmojiButton.addActionListener(e -> openImageEmoticonPicker());
        fileButton.addActionListener(e -> {
            if (fileSender != null) fileSender.accept(roomId);
        });
        galleryButton.addActionListener(e ->
                new ChatImageGalleryFrame(this, userId, roomId));
        mentionButton.addActionListener(e -> {
            inputField.replaceSelection("@");
            inputField.requestFocusInWindow();
        });

        bgButton.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(
                    this, "채팅방 배경색 선택", chatArea.getBackground());
            if (chosen != null) {
                applyBackground(chosen);
                settings.putInt(settingKey("bg"), chosen.getRGB());
            }
        });

        fontSelect.addActionListener(e -> updateFont());
        fontSizeSelect.addActionListener(e -> updateFont());

        setRoom(roomId);
        setVisible(true);
    }

    // 채팅방 변경 시: 기존 메시지 정리, 해당 방의 화면 설정 불러오기
    public void setRoom(int newRoomId) {
        this.roomId = newRoomId;
        setTitle("ChatProject - " + roomId + "번 채팅방");
        roomTitle.setText(roomId + "번 채팅방");
        chatArea.setText("");
        chatArea.getHighlighter().removeAllHighlights();
        messageRanges.clear();
        replyTargets.clear();
        lastClickedId = null;
        clearReplyTarget();
        mentionStatus.setText("");
        loadSettings();
    }

    public void appendSystem(int targetRoom, String text) {
        if (targetRoom == roomId) {
            appendText("── " + text + " ──");
        }
    }

    public void appendHistory(int targetRoom, String time, String sender, String content) {
        appendHistory(targetRoom, -1L, time, sender, content);
    }

    public void appendHistory(int targetRoom, long messageId, String time,
                              String sender, String content) {
        if (targetRoom == roomId) {
            rememberMessage(messageId,
                    appendChatContent("[" + time + "] " + sender + ": ", content),
                    sender, describeForReply(content));
        }
    }

    public void appendLive(int targetRoom, String sender, String content) {
        appendLive(targetRoom, -1L, sender, content);
    }

    public void appendLive(int targetRoom, long messageId,
                           String sender, String content) {
        if (targetRoom == roomId) {
            rememberMessage(messageId, appendChatContent(sender + ": ", content),
                    sender, describeForReply(content));
        }
    }

    /** 이전 대화를 불러올 때도 원본 메시지를 표시한다. */
    public void appendReplyHistory(int targetRoom, long newId, String time,
            String sender, long originalId, String originalSender,
            String originalPreview, String content) {
        if (targetRoom == roomId) {
            int[] range = appendChatContent("[" + time + "] ↳ " + originalSender
                    + " (#" + originalId + "): " + describeForReply(originalPreview)
                    + "\n" + sender + ": ", content);
            rememberMessage(newId, range, sender, describeForReply(content));
        }
    }

    /** 실시간 답장도 메시지 번호를 기억하므로 다시 답장할 수 있다. */
    public void appendReplyLive(int targetRoom, long newId, String sender,
            long originalId, String originalSender, String originalPreview, String content) {
        if (targetRoom == roomId) {
            int[] range = appendChatContent("↳ " + originalSender + " (#" + originalId
                    + "): " + describeForReply(originalPreview)
                    + "\n" + sender + ": ", content);
            rememberMessage(newId, range, sender, describeForReply(content));
        }
    }

    private void rememberMessage(long messageId, int[] range, String sender, String content) {
        if (messageId < 0) return;
        messageRanges.put(messageId, range);
        replyTargets.put(messageId, new ReplyTarget(sender, content));
        // 오래된 기록의 위치 정보는 제한해 메모리 증가를 방지한다.
        if (messageRanges.size() > 500) {
            Long first = messageRanges.keySet().iterator().next();
            messageRanges.remove(first);
            replyTargets.remove(first);
        }
    }

    /** 메시지 번호에 해당하는 한 줄만 받는 사람의 창에서 강조 */
    public void highlightMention(int targetRoom, long messageId, String sender) {
        if (targetRoom != roomId) return;
        mentionStatus.setText("  [멘션] " + sender + "님이 나를 멘션했어요!");
        int[] span = messageRanges.get(messageId);
        if (span == null) return;
        try {
            Color bg = chatArea.getBackground();
            int luminance = (bg.getRed() * 299 + bg.getGreen() * 587
                    + bg.getBlue() * 114) / 1000;
            // 어두운 배경에서는 흰 글자가 읽히도록 어두운 강조색 사용
            Color highlightColor = (luminance < 130)
                    ? new Color(116, 86, 29) : new Color(255, 230, 145);
            chatArea.getHighlighter().addHighlight(span[0], span[1],
                    new DefaultHighlighter.DefaultHighlightPainter(highlightColor));
            chatArea.setCaretPosition(span[1]);
        } catch (BadLocationException e) {
            System.out.println("멘션 강조 실패: " + e.getMessage());
        }
        java.awt.Toolkit.getDefaultToolkit().beep();
    }

    private int[] appendText(String text) {
        StyledDocument doc = chatArea.getStyledDocument();
        int start = doc.getLength();
        try {
            doc.insertString(doc.getLength(), text + "\n", null);
        } catch (BadLocationException e) {
            throw new IllegalStateException("채팅 내용을 표시할 수 없습니다.", e);
        }
        int end = doc.getLength() - 1;
        chatArea.setCaretPosition(doc.getLength());
        return new int[]{start, end};
    }

    private static String describeForReply(String content) {
        long imageId = ChatImageRepository.imageIdFromMessage(content);
        return imageId > 0 ? "[이미지 #" + imageId + "]" : EmoticonCatalog.describe(content);
    }

    /** 일반 메시지, PNG/GIF 이모티콘, DB 저장 이미지를 같은 JTextPane에 표시합니다. */
    private int[] appendChatContent(String prefix, String content) {
        long imageId = ChatImageRepository.imageIdFromMessage(content);
        if (imageId > 0) return appendDatabaseImage(prefix, imageId);
        String id = EmoticonCatalog.extractId(content);
        if (id == null) return appendText(prefix + content);

        StyledDocument doc = chatArea.getStyledDocument();
        int start = doc.getLength();
        try {
            doc.insertString(doc.getLength(), prefix, null);
            ImageIcon icon = EmoticonCatalog.icon(id, chatArea);
            if (icon != null) {
                chatArea.setCaretPosition(doc.getLength());
                chatArea.insertIcon(icon); // ImageIcon으로 GIF 프레임 애니메이션 유지
            } else {
                doc.insertString(doc.getLength(), EmoticonCatalog.describe(content), null);
            }
            doc.insertString(doc.getLength(), "\n", null);
        } catch (BadLocationException e) {
            throw new IllegalStateException("이모티콘 내용을 표시할 수 없습니다.", e);
        }
        int end = doc.getLength() - 1;
        chatArea.setCaretPosition(doc.getLength());
        return new int[]{start, end};
    }

    /** 저장 이미지는 백그라운드에서 불러와 미리보기로 렌더링합니다. */
    private int[] appendDatabaseImage(String prefix, long imageId) {
        final int imageRoom = roomId;
        final JButton preview = new JButton("이미지 #" + imageId + " 불러오는 중...");
        preview.setToolTipText("클릭하면 DB에 저장된 원본 이미지를 크게 볼 수 있습니다.");
        preview.setHorizontalTextPosition(SwingConstants.CENTER);
        preview.setVerticalTextPosition(SwingConstants.BOTTOM);
        preview.addActionListener(e -> showDatabaseImage(imageRoom, imageId));

        StyledDocument doc = chatArea.getStyledDocument();
        int start = doc.getLength();
        try {
            doc.insertString(doc.getLength(), prefix, null);
            chatArea.setCaretPosition(doc.getLength());
            chatArea.insertComponent(preview); // 비동기 이미지 로드에도 글자 위치 유지
            doc.insertString(doc.getLength(), "\n", null);
        } catch (BadLocationException ex) {
            throw new IllegalStateException("이미지 메시지를 표시할 수 없습니다.", ex);
        }
        int end = doc.getLength() - 1;
        chatArea.setCaretPosition(doc.getLength());

        new SwingWorker<ImageIcon, Void>() {
            @Override protected ImageIcon doInBackground() throws Exception {
                byte[] bytes = ChatImageRepository.loadImage(imageRoom, userId, imageId);
                return thumbnail(bytes);
            }
            @Override protected void done() {
                if (!isDisplayable() || roomId != imageRoom || !preview.isDisplayable()) return;
                try {
                    ImageIcon icon = get();
                    if (icon == null) {
                        preview.setText("이미지 #" + imageId + " (미리보기 불가)");
                    } else {
                        preview.setText("이미지 #" + imageId + " · 크게 보기");
                        preview.setIcon(icon);
                    }
                } catch (Exception ex) {
                    preview.setText("이미지 #" + imageId + " 불러오기 실패");
                    preview.setToolTipText("DB 연결과 이미지 조회 권한을 확인하세요.");
                }
            }
        }.execute();
        return new int[]{start, end};
    }

    /** 큰 이미지 및 원본 GIF 애니메이션 표시. DB 조회 중에도 채팅창이 멈추지 않습니다. */
    private void showDatabaseImage(int targetRoom, long imageId) {
        if (roomId != targetRoom) return;
        new SwingWorker<byte[], Void>() {
            @Override protected byte[] doInBackground() throws Exception {
                return ChatImageRepository.loadImage(targetRoom, userId, imageId);
            }
            @Override protected void done() {
                if (!isDisplayable() || roomId != targetRoom) return;
                try {
                    byte[] bytes = get();
                    if (bytes == null) throw new IllegalStateException("이미지를 찾을 수 없습니다.");
                    ImageIcon icon = new ImageIcon(bytes);
                    if (icon.getIconWidth() < 1 || icon.getIconHeight() < 1)
                        throw new IllegalArgumentException("올바른 이미지 데이터가 아닙니다.");
                    JLabel picture = new JLabel(icon);
                    picture.setHorizontalAlignment(SwingConstants.CENTER);
                    JScrollPane scroll = new JScrollPane(picture);
                    scroll.setPreferredSize(new Dimension(
                            Math.min(860, Math.max(360, icon.getIconWidth() + 24)),
                            Math.min(620, Math.max(280, icon.getIconHeight() + 24))));
                    JDialog dialog = new JDialog(ChatRoomWindow.this, "저장 이미지 #" + imageId, false);
                    dialog.add(scroll);
                    dialog.pack();
                    dialog.setLocationRelativeTo(ChatRoomWindow.this);
                    dialog.setVisible(true);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ChatRoomWindow.this,
                            "이미지를 불러올 수 없습니다.\n" + ex.getMessage(),
                            "이미지 조회 실패", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private static ImageIcon thumbnail(byte[] bytes) {
        if (bytes == null || bytes.length < 8
                || bytes.length > ChatImageRepository.MAX_IMAGE_BYTES) return null;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) return null;
            int w = image.getWidth();
            int h = image.getHeight();
            if (w <= 0 || h <= 0 || (long) w * h > 24_000_000L) return null;
            double ratio = Math.min(220.0 / w, 140.0 / h);
            ratio = Math.min(1.0, ratio);
            Image scaled = image.getScaledInstance(
                    Math.max(1, (int) Math.round(w * ratio)),
                    Math.max(1, (int) Math.round(h * ratio)), Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception ex) { return null; }
    }

    /** 제공되는 PNG/GIF 이모티콘 중 하나를 선택해 서버로 즉시 전송합니다. */
    private void openImageEmoticonPicker() {
        if (commandSender == null) return;
        JDialog dialog = new JDialog(this, "PNG/GIF 이모티콘", false);
        dialog.setLayout(new BorderLayout(6, 6));
        JLabel instruction = new JLabel("  이모티콘을 누르면 현재 채팅방에 바로 전송됩니다.");
        instruction.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        dialog.add(instruction, BorderLayout.NORTH);

        JPanel choices = new JPanel(new GridLayout(2, 3, 8, 8));
        choices.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        for (String id : EmoticonCatalog.ids()) {
            JButton button = new JButton(EmoticonCatalog.label(id));
            button.setVerticalTextPosition(SwingConstants.BOTTOM);
            button.setHorizontalTextPosition(SwingConstants.CENTER);
            button.setPreferredSize(new Dimension(150, 125));
            ImageIcon icon = EmoticonCatalog.icon(id, button);
            if (icon != null) button.setIcon(icon);
            button.addActionListener(e -> {
                commandSender.accept("EMOTICON|" + id);
                dialog.dispose();
            });
            choices.add(button);
        }
        dialog.add(choices, BorderLayout.CENTER);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void selectReplyTarget() {
        if (lastClickedId == null || !replyTargets.containsKey(lastClickedId)) {
            JOptionPane.showMessageDialog(this,
                    "채팅창에서 답장할 메시지 한 줄을 먼저 클릭해 주세요.");
            return;
        }
        replyingToId = lastClickedId;
        ReplyTarget target = replyTargets.get(replyingToId);
        String preview = target.content.replace('\n', ' ').replace('\r', ' ');
        if (preview.length() > 70) preview = preview.substring(0, 70) + "…";
        replyHint.setText("↳ 답장 대상: " + target.sender + " (#" + replyingToId + ") " + preview);
        replyBar.setVisible(true);
        replyBar.revalidate();
        inputField.requestFocusInWindow();
    }

    private void clearReplyTarget() {
        replyingToId = null;
        replyHint.setText("");
        replyBar.setVisible(false);
        replyBar.revalidate();
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > 1000) {
            JOptionPane.showMessageDialog(this, "메시지는 1000자 이하로 입력해주세요.");
            return;
        }
        // 답장일 때는 원본 메시지 번호를 함께 서버로 보낸다.
        // 서버가 답장 원본이 같은 방에 속하는지 확인한 후 DB에 저장한다.
        if (replyingToId != null) {
            if (commandSender == null) {
                JOptionPane.showMessageDialog(this, "답장 전송 기능이 연결되지 않았습니다.");
                return;
            }
            commandSender.accept("REPLY|" + replyingToId + "|" + text);
            clearReplyTarget();
        } else {
            messageSender.accept(text);
        }
        inputField.setText("");
        inputField.requestFocusInWindow();
    }

    /** 귓속말은 기존 서버 프로토콜 WHISPER|아이디|내용을 그대로 사용한다. */
    private void sendWhisper() {
        if (commandSender == null) return;
        String target = JOptionPane.showInputDialog(this,
                "같은 채팅방에 접속 중인 상대 회원의 아이디를 입력하세요.",
                "귓속말 상대 선택", JOptionPane.QUESTION_MESSAGE);
        if (target == null) return;
        target = target.trim();
        if (!target.matches("[A-Za-z0-9_]{3,30}") || target.equals(userId)) {
            JOptionPane.showMessageDialog(this,
                    "본인을 제외한 상대방의 올바른 아이디를 입력해주세요.");
            return;
        }
        String draft = inputField.getText();
        String body = (String) JOptionPane.showInputDialog(this,
                "" + target + "님에게 보낼 귓속말 (1~500자)",
                "귓속말 내용", JOptionPane.QUESTION_MESSAGE,
                null, null, draft);
        if (body == null) return;
        body = body.trim();
        if (body.isEmpty() || body.length() > 500 || body.contains("\n") || body.contains("\r")) {
            JOptionPane.showMessageDialog(this,
                    "귓속말은 줄바꿈 없는 1~500자 메시지여야 합니다.");
            return;
        }
        // 실제 전송 성공한 경우 서버가 WHISPER_SENT를 돌려준다.
        commandSender.accept("WHISPER|" + target + "|" + body);
        if (!draft.isEmpty() && draft.trim().equals(body)) inputField.setText("");
    }

    /** 기존 서버 SEARCH 기능을 GUI에서 바로 실행한다. */
    private void searchMessages() {
        if (commandSender == null) return;
        String keyword = JOptionPane.showInputDialog(this,
                "현재 채팅방에서 찾을 단어를 입력하세요. (1~100자)",
                "채팅 검색", JOptionPane.QUESTION_MESSAGE);
        if (keyword == null) return;
        keyword = keyword.trim();
        if (keyword.isEmpty() || keyword.length() > 100
                || keyword.contains("\n") || keyword.contains("\r") || keyword.contains("|")) {
            JOptionPane.showMessageDialog(this, "검색어는 1~100자이며 | 또는 줄바꿈을 포함할 수 없습니다.");
            return;
        }
        commandSender.accept("SEARCH|" + keyword);
    }

    /** 수신 또는 전송이 성공한 귓속말을 본인 채팅창에 표시. */
    public void appendWhisperFrom(String sender, String body) {
        appendText("[귓속말 ← " + sender + "] " + body);
        Toolkit.getDefaultToolkit().beep();
    }

    public void appendWhisperSent(String target, String body) {
        appendText("[귓속말 → " + target + "] " + body);
    }

    /**
     * 10주차: 이모티콘 선택창. Unicode 이모티콘을 기존 일반 채팅으로 전송하므로
     * 별도 서버 명령어 없이 같은 채팅방의 상대방에게 전달되고 DB에도 저장된다.
     */
    private void openEmojiPicker(JButton anchor) {
        JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new BorderLayout(4, 4));
        JLabel guide = new JLabel("  클릭해서 입력창에 추가 → 전송 버튼  ");
        guide.setBorder(BorderFactory.createEmptyBorder(7, 7, 4, 7));
        popup.add(guide, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        String[] categories = {"표정", "동물", "기타", "문자 이모티콘"};
        String[][] emojis = {
            {"😀", "😄", "😁", "😂", "😊", "😍", "😘", "😎",
             "😢", "😭", "😡", "😱", "🥳", "🤔", "😴", "🤗",
             "👍", "👏", "🙏", "❤️", "💖", "🎉", "🔥", "✨"},
            {"🐶", "🐱", "🐻", "🐰", "🐼", "🦊", "🐸", "🐣",
             "🐯", "🐵", "🐷", "🐹", "🐧", "🐥", "🐨", "🐝"},
            {"🌸", "🌈", "🍀", "⭐", "☕", "🍰", "🎮", "⚾",
             "🎵", "🎁", "💯", "🍕", "🌞", "🌙", "💐", "💬"},
            {"^^", "ㅎㅎ", "ㅋㅋ", "ㅠㅠ", "T_T", "^_^", "(>_<)", "(・ω・)",
             "(๑˃ᴗ˂)ﻭ", "(ง •̀_•́)ง", "(╥﹏╥)", "(｡♥‿♥｡)"}
        };

        for (int category = 0; category < categories.length; category++) {
            JPanel grid = new JPanel(new GridLayout(0, 8, 3, 3));
            grid.setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));
            for (String emoji : emojis[category]) {
                JButton item = new JButton(emoji);
                item.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 19));
                item.setMargin(new Insets(2, 2, 2, 2));
                item.setPreferredSize(new Dimension(45, 43));
                item.setFocusPainted(false);
                item.addActionListener(e -> {
                    // 커서가 있던 위치에 삽입. 서버가 검증하는 1000자 제한도 유지한다.
                    int position = inputField.getCaretPosition();
                    String before = inputField.getText();
                    int selectionStart = inputField.getSelectionStart();
                    int selectionEnd = inputField.getSelectionEnd();
                    int lengthAfter = before.length()
                            - (selectionEnd - selectionStart) + emoji.length();
                    if (lengthAfter > 1000) {
                        JOptionPane.showMessageDialog(this,
                                "메시지는 1000자 이하로 입력해주세요.");
                        return;
                    }
                    if (selectionEnd > selectionStart) {
                        inputField.setText(before.substring(0, selectionStart)
                                + emoji + before.substring(selectionEnd));
                        inputField.setCaretPosition(selectionStart + emoji.length());
                    } else {
                        inputField.setText(before.substring(0, position) + emoji
                                + before.substring(position));
                        inputField.setCaretPosition(position + emoji.length());
                    }
                    popup.setVisible(false);
                    inputField.requestFocusInWindow();
                });
                grid.add(item);
            }
            tabs.addTab(categories[category], grid);
        }
        popup.add(tabs, BorderLayout.CENTER);
        popup.show(anchor, 0, -popup.getPreferredSize().height);
    }

    private String settingKey(String setting) {
        return setting + "_" + userId + "_" + roomId;
    }

    private void loadSettings() {
        loadingSetting = true;
        int rgb = settings.getInt(settingKey("bg"), Color.WHITE.getRGB());
        applyBackground(new Color(rgb));

        String name = settings.get(settingKey("font"), "맑은 고딕");
        boolean found = false;
        for (int i = 0; i < fontSelect.getItemCount(); i++) {
            if (fontSelect.getItemAt(i).equals(name)) {
                found = true;
                break;
            }
        }
        fontSelect.setSelectedItem(found ? name : "Dialog");

        int size = settings.getInt(settingKey("fontSize"), 16);
        fontSizeSelect.setSelectedItem(size);
        loadingSetting = false;
        applyFont();
    }

    private void applyBackground(Color bg) {
        chatArea.setBackground(bg);
        int brightness = (bg.getRed() * 299 + bg.getGreen() * 587
                + bg.getBlue() * 114) / 1000;
        Color fg = brightness < 130 ? Color.WHITE : Color.BLACK;
        chatArea.setForeground(fg);
        chatArea.setCaretColor(fg);
    }

    private void updateFont() {
        if (loadingSetting) {
            return;
        }
        applyFont();
        settings.put(settingKey("font"), (String) fontSelect.getSelectedItem());
        settings.putInt(settingKey("fontSize"), (Integer) fontSizeSelect.getSelectedItem());
    }

    private void applyFont() {
        String fontName = (String) fontSelect.getSelectedItem();
        Integer size = (Integer) fontSizeSelect.getSelectedItem();
        if (fontName == null || size == null) {
            return;
        }
        Font font = new Font(fontName, Font.PLAIN, size);
        chatArea.setFont(font);
        inputField.setFont(font);
    }
}
