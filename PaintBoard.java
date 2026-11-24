
package ChatProject;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.imageio.ImageIO;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Base64;
import java.util.function.Consumer;

public class PaintBoard extends JFrame {

    private DrawPanel drawPanel;

    private Color penColor = Color.BLACK;
    private int penSize = 5;

    private JLabel colorLabel;

    // 채팅 클라이언트로 이미지를 전달하는 기능
    private Consumer<String> imageSender;

    // 단독 실행용 생성자
    public PaintBoard() {
        this(null);
    }

    // 채팅 프로그램 연결용 생성자
    public PaintBoard(Consumer<String> imageSender) {

        this.imageSender = imageSender;

        setTitle("ChatProject - 그림판");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // ===== 도구 영역 =====

        JPanel toolPanel = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 10, 10));

        JButton colorButton = new JButton("색상 선택");
        JButton eraserButton = new JButton("지우개");
        JButton clearButton = new JButton("전체 지우기");
        JButton saveButton = new JButton("이미지 저장");
        JButton sendButton = new JButton("채팅으로 전송");

        colorLabel = new JLabel("현재 색상");
        colorLabel.setOpaque(true);
        colorLabel.setBackground(penColor);
        colorLabel.setForeground(Color.WHITE);
        colorLabel.setPreferredSize(new Dimension(80, 30));
        colorLabel.setHorizontalAlignment(
                SwingConstants.CENTER);

        JLabel sizeLabel = new JLabel("펜 굵기: 5");

        JSlider sizeSlider = new JSlider(1, 30, 5);
        sizeSlider.setPreferredSize(new Dimension(120, 40));

        toolPanel.add(colorButton);
        toolPanel.add(colorLabel);
        toolPanel.add(new JLabel("굵기:"));
        toolPanel.add(sizeSlider);
        toolPanel.add(sizeLabel);
        toolPanel.add(eraserButton);
        toolPanel.add(clearButton);
        toolPanel.add(saveButton);
        toolPanel.add(sendButton);

        add(toolPanel, BorderLayout.NORTH);

        // ===== 그림 영역 =====

        drawPanel = new DrawPanel();

        add(new JScrollPane(drawPanel), BorderLayout.CENTER);

        // 색상 선택
        colorButton.addActionListener(e -> {

            Color selected = JColorChooser.showDialog(
                    this, "펜 색상 선택", penColor);

            if (selected != null) {
                penColor = selected;
                colorLabel.setBackground(selected);
                colorLabel.setText("색상 선택됨");

                colorLabel.setForeground(
                        selected.equals(Color.WHITE)
                        ? Color.BLACK : Color.WHITE);
            }
        });

        // 펜 굵기
        sizeSlider.addChangeListener(e -> {

            penSize = sizeSlider.getValue();
            sizeLabel.setText("펜 굵기: " + penSize);
        });

        // 지우개
        eraserButton.addActionListener(e -> {

            penColor = Color.WHITE;
            colorLabel.setBackground(Color.WHITE);
            colorLabel.setForeground(Color.BLACK);
            colorLabel.setText("지우개");
        });

        // 전체 지우기
        clearButton.addActionListener(e -> {

            int result = JOptionPane.showConfirmDialog(
                    this,
                    "그림을 모두 지우시겠습니까?",
                    "그림 초기화",
                    JOptionPane.YES_NO_OPTION);

            if (result == JOptionPane.YES_OPTION) {
                drawPanel.clearCanvas();
            }
        });

        // 이미지 저장
        saveButton.addActionListener(e -> saveImage());

        // 채팅으로 이미지 전송
        sendButton.addActionListener(e -> sendImage());

        if (imageSender == null) {
            sendButton.setEnabled(false);
            sendButton.setToolTipText(
                    "채팅 클라이언트에서 실행해야 사용할 수 있습니다.");
        }

        setVisible(true);
    }

    // ===== 그림판 내부 클래스 =====

    class DrawPanel extends JPanel {

        private BufferedImage canvas;

        private int lastX;
        private int lastY;

        public DrawPanel() {

            setPreferredSize(new Dimension(850, 480));
            setBackground(Color.WHITE);

            canvas = new BufferedImage(
                    850, 480, BufferedImage.TYPE_INT_RGB);

            clearCanvas();

            MouseAdapter mouse = new MouseAdapter() {

                @Override
                public void mousePressed(MouseEvent e) {

                    lastX = e.getX();
                    lastY = e.getY();

                    drawLine(lastX, lastY, lastX, lastY);
                }

                @Override
                public void mouseDragged(MouseEvent e) {

                    int x = e.getX();
                    int y = e.getY();

                    drawLine(lastX, lastY, x, y);

                    lastX = x;
                    lastY = y;
                }
            };

            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        private void drawLine(
                int x1, int y1, int x2, int y2) {

            Graphics2D g = canvas.createGraphics();

            try {
                g.setColor(penColor);

                g.setStroke(new BasicStroke(
                        penSize,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));

                g.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                g.drawLine(x1, y1, x2, y2);

            } finally {
                g.dispose();
            }

            repaint();
        }

        public void clearCanvas() {

            Graphics2D g = canvas.createGraphics();

            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0,
                        canvas.getWidth(),
                        canvas.getHeight());

            } finally {
                g.dispose();
            }

            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {

            super.paintComponent(g);
            g.drawImage(canvas, 0, 0, null);
        }

        public BufferedImage getCanvas() {
            return canvas;
        }
    }

    // ===== PNG 이미지 저장 =====

    private void saveImage() {

        JFileChooser chooser = new JFileChooser();

        chooser.setDialogTitle("그림 저장");

        chooser.setFileFilter(
                new FileNameExtensionFilter(
                        "PNG 이미지 (*.png)", "png"));

        chooser.setSelectedFile(
                new File("그림판_이미지.png"));

        if (chooser.showSaveDialog(this)
                != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();

        if (!file.getName().toLowerCase().endsWith(".png")) {
            file = new File(file.getAbsolutePath() + ".png");
        }

        if (file.exists()) {

            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "파일이 이미 존재합니다. 덮어쓸까요?",
                    "저장 확인",
                    JOptionPane.YES_NO_OPTION);

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {

            ImageIO.write(
                    drawPanel.getCanvas(), "png", file);

            JOptionPane.showMessageDialog(
                    this, "이미지 저장 성공!");

        } catch (Exception e) {

            JOptionPane.showMessageDialog(
                    this, "이미지 저장 실패: " + e.getMessage());
        }
    }

    // ===== 채팅 이미지 전송 =====

    private void sendImage() {

        if (imageSender == null) {
            JOptionPane.showMessageDialog(
                    this, "채팅 클라이언트에서 실행해주세요.");
            return;
        }

        try {

            // 그림판 이미지를 PNG 데이터로 변환
            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            boolean saved = ImageIO.write(
                    drawPanel.getCanvas(), "png", output);

            if (!saved) {
                throw new Exception("PNG 변환 실패");
            }

            byte[] imageBytes = output.toByteArray();

            if (imageBytes.length > 2_000_000) {

                JOptionPane.showMessageDialog(
                        this, "이미지는 2MB 이하만 전송 가능합니다.");
                return;
            }

            // Base64로 변환
            String imageData = Base64.getEncoder()
                    .encodeToString(imageBytes);

            // 채팅 클라이언트에 데이터 전달
            imageSender.accept(imageData);

            System.out.println(
                    "그림판 이미지 전송 요청 완료!");

            JOptionPane.showMessageDialog(
                    this,
                    "이미지 전송을 요청했습니다.\n"
                    + "채팅 콘솔에서 전송 결과를 확인해주세요.");

        } catch (Exception e) {

            JOptionPane.showMessageDialog(
                    this,
                    "이미지 전송 요청 실패: " + e.getMessage());
        }
    }

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            new PaintBoard();
        });
    }
}
