package ChatProject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.Scanner;

/**
 * 1차 통합 GUI - 1단계: MySQL 회원 로그인 화면.
 * 로그인 뒤에는 기존 채팅 클라이언트를 재사용한다.
 */
public class ChatLoginFrame extends JFrame {

    private final JTextField idField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JButton loginButton = new JButton("로그인");
    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);

    public ChatLoginFrame() {
        setTitle("ChatProject - 로그인");
        setSize(430, 360);
        setMinimumSize(new Dimension(390, 330));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Color bg = new Color(25, 27, 31);
        Color inputBg = new Color(51, 54, 61);
        Color fg = new Color(245, 245, 245);
        Color subdued = new Color(180, 184, 191);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(bg);
        root.setBorder(new EmptyBorder(25, 36, 20, 36));
        setContentPane(root);

        JLabel title = new JLabel("ChatProject", SwingConstants.CENTER);
        title.setFont(new Font("맑은 고딕", Font.BOLD, 27));
        title.setForeground(fg);
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        root.add(form, BorderLayout.CENTER);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(5, 0, 5, 0);

        JLabel idLabel = new JLabel("아이디");
        idLabel.setForeground(subdued);
        c.gridy = 0;
        form.add(idLabel, c);

        styleField(idField, inputBg, fg);
        c.gridy = 1;
        form.add(idField, c);

        JLabel pwLabel = new JLabel("비밀번호");
        pwLabel.setForeground(subdued);
        c.gridy = 2;
        form.add(pwLabel, c);

        styleField(passwordField, inputBg, fg);
        c.gridy = 3;
        form.add(passwordField, c);

        loginButton.setBackground(new Color(240, 198, 58));
        loginButton.setForeground(Color.BLACK);
        loginButton.setFont(new Font("맑은 고딕", Font.BOLD, 15));
        loginButton.setFocusPainted(false);
        loginButton.setPreferredSize(new Dimension(250, 41));
        c.gridy = 4;
        c.insets = new Insets(16, 0, 6, 0);
        form.add(loginButton, c);

        statusLabel.setForeground(new Color(245, 150, 145));
        statusLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        c.gridy = 5;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(statusLabel, c);

        JPanel shortcuts = new JPanel(new GridLayout(1, 3, 5, 0));
        shortcuts.setOpaque(false);
        JButton register = new JButton("회원가입");
        JButton findId = new JButton("아이디 찾기");
        JButton reset = new JButton("비밀번호 찾기");
        register.addActionListener(e -> new ChatRegisterFrame());
        findId.addActionListener(e -> new ChatRecoveryFrame(true));
        reset.addActionListener(e -> new ChatRecoveryFrame(false));
        shortcuts.add(register);shortcuts.add(findId);shortcuts.add(reset);
        root.add(shortcuts, BorderLayout.SOUTH);

        loginButton.addActionListener(e -> attemptLogin());
        getRootPane().setDefaultButton(loginButton);
        setVisible(true);
        SwingUtilities.invokeLater(idField::requestFocusInWindow);
    }

    private static void styleField(JTextField field, Color bg, Color fg) {
        field.setBackground(bg);
        field.setForeground(fg);
        field.setCaretColor(fg);
        field.setFont(new Font("맑은 고딕", Font.PLAIN, 15));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 83, 90)),
                new EmptyBorder(8, 10, 8, 10)));
    }

    private void attemptLogin() {
        String id = idField.getText().trim();
        char[] chars = passwordField.getPassword();
        String password = new String(chars);
        Arrays.fill(chars, '\0');

        if (id.isEmpty() || password.isEmpty()) {
            statusLabel.setText("아이디와 비밀번호를 입력해주세요.");
            return;
        }
        if (System.getenv("CHAT_DB_PASSWORD") == null) {
            statusLabel.setText("Eclipse 환경변수 CHAT_DB_PASSWORD를 설정해주세요.");
            return;
        }

        loginButton.setEnabled(false);
        statusLabel.setForeground(new Color(195, 202, 212));
        statusLabel.setText("회원 정보를 확인하는 중...");

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return ProfileManager.login(id, password);
            }

            @Override
            protected void done() {
            	try {
            		if (!get()) {
            			statusLabel.setForeground(new Color(245, 150, 145));
            			statusLabel.setText("로그인 실패: 계정 정보 또는 DB 연결을 확인해주세요.");
            			passwordField.setText("");
            return;
            		}

        // 로그인 성공 후 채팅 로비 실행
        dispose();

        Thread session = new Thread(() -> {
            ChatLobbyClient.startLoggedIn(id, new Scanner(System.in));

            // 채팅 종료 후 로그인 화면 다시 열기
            SwingUtilities.invokeLater(ChatLoginFrame::new);
        }, "ChatProject-ConsoleSession");

        session.setDaemon(false);
        session.start();

    } catch (Exception ex) {
        statusLabel.setForeground(new Color(245, 150, 145));
        statusLabel.setText("로그인 확인 중 오류가 발생했습니다.");
    } finally {
        loginButton.setEnabled(true);
    }
}

        }.execute();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatLoginFrame::new);
    }
}
