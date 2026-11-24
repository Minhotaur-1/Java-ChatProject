package ChatProject;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.*;
import java.util.Arrays;

/** 로그인 회원의 프로필 조회·수정 + 프로필 사진 미리보기. */
public class ChatProfileFrame extends JFrame {

    private final String userId;
    private final JLabel idLabel = new JLabel();
    private final JLabel imagePreview = new JLabel("등록된 사진 없음", SwingConstants.CENTER);
    private final JLabel photoStatus = new JLabel(" ", SwingConstants.CENTER);
    private final JTextField nickname = new JTextField();
    private final JTextField zip = new JTextField();
    private final JTextField address = new JTextField();
    private final JTextField detail = new JTextField();
    private final UserDAO dao = new UserDAO();

    public ChatProfileFrame(String userId) {
        this.userId = userId;
        setTitle("ChatProject - 내 회원정보");
        setSize(610, 640);
        setMinimumSize(new Dimension(540, 590));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 22, 16, 22));
        setContentPane(root);

        // 사진을 경로 문자열이 아니라 실제 이미지로 보여주는 영역
        imagePreview.setPreferredSize(new Dimension(168, 168));
        imagePreview.setMinimumSize(new Dimension(168, 168));
        imagePreview.setOpaque(true);
        imagePreview.setBackground(new Color(244, 245, 247));
        imagePreview.setBorder(BorderFactory.createLineBorder(new Color(195, 197, 201)));
        imagePreview.setFont(new Font("맑은 고딕", Font.PLAIN, 13));

        JPanel photoBox = new JPanel(new BorderLayout(4, 5));
        JPanel photoCenter = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        photoCenter.add(imagePreview);
        photoBox.add(photoCenter, BorderLayout.CENTER);
        photoStatus.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        photoBox.add(photoStatus, BorderLayout.SOUTH);
        root.add(photoBox, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridLayout(0, 2, 10, 10));
        fields.setBorder(BorderFactory.createEmptyBorder(10, 8, 8, 8));
        fields.add(new JLabel("아이디")); fields.add(idLabel);
        fields.add(new JLabel("닉네임")); fields.add(nickname);
        fields.add(new JLabel("우편번호")); fields.add(zip);
        fields.add(new JLabel("기본주소")); fields.add(address);
        fields.add(new JLabel("상세주소")); fields.add(detail);
        root.add(fields, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(2, 2, 8, 8));
        JButton reload = new JButton("다시 조회");
        JButton saveNickname = new JButton("닉네임 변경");
        JButton saveAddress = new JButton("주소 변경");
        JButton saveImage = new JButton("프로필 사진 변경");
        JButton changePw = new JButton("비밀번호 변경");
        actions.add(reload);
        actions.add(saveNickname);
        actions.add(saveAddress);
        actions.add(saveImage);
        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.add(actions, BorderLayout.CENTER);
        bottom.add(changePw, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);

        reload.addActionListener(e -> load());
        saveNickname.addActionListener(e -> work(() -> {
            if (!dao.updateNickname(userId, nickname.getText()))
                throw new Exception("변경 실패: 닉네임 중복 또는 입력값을 확인하세요.");
        }));
        saveAddress.addActionListener(e -> work(() -> {
            if (!zip.getText().trim().matches("[0-9]{5}"))
                throw new Exception("우편번호는 숫자 5자리입니다.");
            if (!dao.updateAddress(userId, zip.getText().trim(),
                    address.getText().trim(), detail.getText().trim()))
                throw new Exception("주소 변경 실패");
        }));
        saveImage.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("프로필 사진 선택");
            chooser.setFileFilter(new FileNameExtensionFilter(
                    "이미지 파일 (*.png, *.jpg, *.jpeg)", "png", "jpg", "jpeg"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
                return;
            String path = chooser.getSelectedFile().getAbsolutePath();
            work(() -> {
                String savedPath = ProfileImageUtil.resizeImage(path);
                if (savedPath == null || !dao.updateProfileImage(userId, savedPath))
                    throw new Exception("프로필 사진 저장 실패");
            });
        });
        changePw.addActionListener(e -> changePassword());
        setVisible(true);
        load();
    }

    /** DB에 저장된 파일 경로로부터 사진을 읽어 미리보기로 출력. */
    private void displayProfileImage(String savedPath) {
        imagePreview.setIcon(null);
        imagePreview.setText("등록된 사진 없음");
        imagePreview.setToolTipText(null);
        photoStatus.setText("사진 변경 버튼으로 이미지를 등록할 수 있습니다.");
        if (savedPath == null || savedPath.trim().isEmpty()) return;

        try {
            // Eclipse에서 실행하면 상대 경로는 일반적으로 프로젝트 폴더 기준.
            File file = new File(savedPath.trim());
            if (!file.isFile()) {
                imagePreview.setText("사진 파일을 찾을 수 없음");
                photoStatus.setText("사진 변경 버튼으로 파일을 다시 선택해 주세요.");
                imagePreview.setToolTipText(savedPath);
                return;
            }
            BufferedImage original = ImageIO.read(file);
            if (original == null) throw new Exception("지원되지 않는 이미지 형식");

            final int size = 158;
            double ratio = Math.min((double) size / original.getWidth(),
                    (double) size / original.getHeight());
            int width = Math.max(1, (int) Math.round(original.getWidth() * ratio));
            int height = Math.max(1, (int) Math.round(original.getHeight() * ratio));
            Image scaled = original.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            imagePreview.setText("");
            imagePreview.setIcon(new ImageIcon(scaled));
            imagePreview.setToolTipText(file.getAbsolutePath());
            photoStatus.setText("프로필 사진");
        } catch (Exception ex) {
            imagePreview.setIcon(null);
            imagePreview.setText("이미지 읽기 실패");
            photoStatus.setText("사진 변경 버튼으로 다른 사진을 선택해 주세요.");
        }
    }

    private interface Job { void run() throws Exception; }

    private void work(Job task) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                task.run();
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(ChatProfileFrame.this, "저장되었습니다.");
                    load(); // DB 경로를 다시 읽고 사진도 갱신
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ChatProfileFrame.this,
                            "변경 실패: " + cause(ex));
                }
            }
        }.execute();
    }

    private void load() {
        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() throws Exception {
                try (Connection conn = MemberGuiService.connection();
                     PreparedStatement pstmt = conn.prepareStatement(
                             "SELECT id,nickname,profile_image,zipcode,address,detail_address "
                             + "FROM users WHERE id=?")) {
                    pstmt.setString(1, userId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (!rs.next()) throw new SQLException("회원 정보가 없습니다.");
                        String[] values = new String[6];
                        for (int i = 0; i < 6; i++) values[i] = rs.getString(i + 1);
                        return values;
                    }
                }
            }
            @Override
            protected void done() {
                try {
                    String[] values = get();
                    idLabel.setText(values[0]);
                    nickname.setText(empty(values[1]));
                    zip.setText(empty(values[3]));
                    address.setText(empty(values[4]));
                    detail.setText(empty(values[5]));
                    displayProfileImage(values[2]);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(ChatProfileFrame.this,
                            "회원정보 조회 실패: " + cause(ex));
                }
            }
        }.execute();
    }

    private void changePassword() {
        JPasswordField oldPw = new JPasswordField();
        JPasswordField newPw = new JPasswordField();
        JPasswordField confirm = new JPasswordField();
        JPanel inputs = new JPanel(new GridLayout(0, 1));
        inputs.add(new JLabel("현재 비밀번호")); inputs.add(oldPw);
        inputs.add(new JLabel("새 비밀번호 (8~100자)")); inputs.add(newPw);
        inputs.add(new JLabel("확인")); inputs.add(confirm);
        if (JOptionPane.showConfirmDialog(this, inputs, "비밀번호 변경",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        char[] a = oldPw.getPassword();
        char[] b = newPw.getPassword();
        char[] c = confirm.getPassword();
        if (!Arrays.equals(b, c)) {
            Arrays.fill(a, '\0'); Arrays.fill(b, '\0'); Arrays.fill(c, '\0');
            JOptionPane.showMessageDialog(this, "새 비밀번호가 일치하지 않습니다.");
            return;
        }
        String currentPassword = new String(a);
        String newPassword = new String(b);
        Arrays.fill(a, '\0'); Arrays.fill(b, '\0'); Arrays.fill(c, '\0');
        work(() -> {
            if (!dao.changePassword(userId, currentPassword, newPassword))
                throw new Exception("현재 비밀번호 또는 새 비밀번호 조건을 확인하세요.");
        });
        JOptionPane.showMessageDialog(this,
                "비밀번호를 변경한 경우 보안을 위해 다시 로그인하는 것이 좋습니다.");
    }

    private static String empty(String s) { return s == null ? "" : s; }
    private static String cause(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage();
    }
}
