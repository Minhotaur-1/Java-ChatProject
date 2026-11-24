package ChatProject;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.text.SimpleDateFormat;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

/** 11주차 2단계: 현재 채팅방의 이미지 DB 갤러리(목록/미리보기/확대 보기). */
public final class ChatImageGalleryFrame extends JDialog {
    private final int roomId;
    private final String viewerId;
    private final JPanel grid = new JPanel(new GridLayout(0, 3, 12, 12));
    private final JLabel status = new JLabel("불러오는 중...");
    private final JButton refresh = new JButton("새로고침");
    private int loadVersion = 0;

    public ChatImageGalleryFrame(Component parent, String viewerId, int roomId) {
        super(javax.swing.SwingUtilities.getWindowAncestor(parent),
                roomId + "번 채팅방 - 이미지 갤러리",
                ModalityType.MODELESS);
        this.roomId = roomId;
        this.viewerId = viewerId;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setSize(730, 540);
        setMinimumSize(new Dimension(480, 330));
        setLocationRelativeTo(parent);

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setBorder(BorderFactory.createEmptyBorder(12, 12, 2, 12));
        JLabel title = new JLabel(roomId + "번 채팅방 이미지 갤러리");
        title.setFont(new Font("맑은 고딕", Font.BOLD, 17));
        top.add(title, BorderLayout.WEST);
        top.add(refresh, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        grid.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(grid, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(wrapper);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(status);
        add(bottom, BorderLayout.SOUTH);

        refresh.addActionListener(e -> reload());
        setVisible(true);
        reload();
    }

    private void reload() {
        int version = ++loadVersion;
        refresh.setEnabled(false);
        status.setText("DB에서 이미지 목록을 조회하고 있습니다...");
        grid.removeAll();
        grid.revalidate();
        grid.repaint();
        new SwingWorker<List<ChatImageRepository.ImageInfo>, Void>() {
            @Override protected List<ChatImageRepository.ImageInfo> doInBackground() throws Exception {
                return ChatImageRepository.listImages(roomId, viewerId);
            }
            @Override protected void done() {
                if (version != loadVersion || !isDisplayable()) return;
                refresh.setEnabled(true);
                try {
                    List<ChatImageRepository.ImageInfo> items = get();
                    if (items.isEmpty()) {
                        status.setText("저장된 이미지가 없습니다. 같은 방에서 PNG/JPG/GIF를 전송해 보세요.");
                    } else {
                        status.setText("최근 이미지 " + items.size() + "개 (최대 48개 표시)");
                        for (ChatImageRepository.ImageInfo item : items) addCard(item, version);
                    }
                } catch (Exception ex) {
                    status.setText("목록 조회 실패: DB 연결 또는 채팅방 참여 여부를 확인하세요.");
                    JOptionPane.showMessageDialog(ChatImageGalleryFrame.this,
                            "이미지 목록을 불러올 수 없습니다.\n" + rootMessage(ex),
                            "갤러리 오류", JOptionPane.WARNING_MESSAGE);
                }
                grid.revalidate();
                grid.repaint();
            }
        }.execute();
    }

    private void addCard(ChatImageRepository.ImageInfo item, int version) {
        JPanel card = new JPanel(new BorderLayout(3, 4));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(7, 7, 7, 7)));
        JButton preview = new JButton("이미지 로딩...");
        preview.setPreferredSize(new Dimension(190, 150));
        preview.setHorizontalTextPosition(SwingConstants.CENTER);
        preview.setVerticalTextPosition(SwingConstants.BOTTOM);
        preview.setMargin(new Insets(2, 2, 2, 2));
        preview.addActionListener(e -> showFullImage(item));
        card.add(preview, BorderLayout.CENTER);
        String name = item.name.length() > 24 ? item.name.substring(0, 23) + "…" : item.name;
        JLabel info = new JLabel("<html>" + escapeHtml(name) + "<br>"
                + escapeHtml(item.sender) + " · " + (item.bytes / 1024) + " KB</html>");
        info.setToolTipText(item.name);
        card.add(info, BorderLayout.SOUTH);
        grid.add(card);

        // 이미지 다운로드/축소는 GUI 이벤트 스레드를 막지 않도록 별도 작업.
        new SwingWorker<ImageIcon, Void>() {
            @Override protected ImageIcon doInBackground() throws Exception {
                byte[] bytes = ChatImageRepository.loadImage(roomId, viewerId, item.id);
                return makeThumbnail(bytes);
            }
            @Override protected void done() {
                if (!isDisplayable() || version != loadVersion) return;
                try {
                    ImageIcon icon = get();
                    if (icon != null) {
                        preview.setIcon(icon);
                        preview.setText("크게 보기");
                    } else preview.setText("미리보기 불가 (클릭)");
                } catch (Exception ex) {
                    preview.setText("미리보기 오류 (클릭)");
                }
            }
        }.execute();
    }

    private void showFullImage(ChatImageRepository.ImageInfo item) {
        status.setText("이미지 #" + item.id + " 불러오는 중...");
        new SwingWorker<byte[], Void>() {
            @Override protected byte[] doInBackground() throws Exception {
                return ChatImageRepository.loadImage(roomId, viewerId, item.id);
            }
            @Override protected void done() {
                if (!isDisplayable()) return;
                try {
                    byte[] bytes = get();
                    if (bytes == null) throw new IllegalStateException("이미지를 찾지 못했습니다.");
                    ImageIcon icon = new ImageIcon(bytes);
                    if (icon.getIconWidth() < 1 || icon.getIconHeight() < 1)
                        throw new IllegalArgumentException("표시할 수 없는 이미지입니다.");
                    // GIF는 원본 ImageIcon으로 표시해 움직임 유지. 큰 이미지는 스크롤로 탐색.
                    JLabel imageLabel = new JLabel(icon);
                    imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
                    JScrollPane scroll = new JScrollPane(imageLabel);
                    scroll.setPreferredSize(new Dimension(
                            Math.min(880, Math.max(360, icon.getIconWidth() + 24)),
                            Math.min(640, Math.max(280, icon.getIconHeight() + 24))));
                    JDialog popup = new JDialog(ChatImageGalleryFrame.this,
                            item.name + " - " + item.sender, false);
                    popup.add(scroll);
                    popup.pack();
                    popup.setLocationRelativeTo(ChatImageGalleryFrame.this);
                    popup.setVisible(true);
                    status.setText("이미지 #" + item.id + " 열람 완료");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ChatImageGalleryFrame.this,
                            "이미지를 표시할 수 없습니다.\n" + rootMessage(ex),
                            "이미지 조회 오류", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private static ImageIcon makeThumbnail(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > ChatImageRepository.MAX_IMAGE_BYTES) return null;
        try {
            BufferedImage picture = ImageIO.read(new ByteArrayInputStream(bytes));
            if (picture == null) return null;
            if ((long) picture.getWidth() * picture.getHeight() > 24_000_000L) return null;
            double factor = Math.min(174.0 / picture.getWidth(), 112.0 / picture.getHeight());
            factor = Math.min(1.0, factor);
            int width = Math.max(1, (int) Math.round(picture.getWidth() * factor));
            int height = Math.max(1, (int) Math.round(picture.getHeight() * factor));
            Image scaled = picture.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception ex) { return null; }
    }

    private static String escapeHtml(String source) {
        return source.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String rootMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
