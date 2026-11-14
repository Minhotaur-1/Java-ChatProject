package ChatProject;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class IdCheckFrame extends JFrame {

    private JTextField idField;
    private JButton checkButton;
    private JLabel resultLabel;

    public IdCheckFrame() {

        setTitle("아이디 중복확인");
        setSize(400, 180);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new FlowLayout());

        idField = new JTextField(15);
        checkButton = new JButton("중복확인");
        resultLabel = new JLabel("아이디를 입력하세요.");

        panel.add(new JLabel("아이디"));
        panel.add(idField);
        panel.add(checkButton);
        panel.add(resultLabel);

        add(panel);

        checkButton.addActionListener(e -> checkId());

        setVisible(true);
    }

    private void checkId() {

        String id = idField.getText().trim();

        if (id.isEmpty()) {
            resultLabel.setText("아이디를 입력하세요.");
            return;
        }

        try {

            Socket socket = new Socket("localhost", 5000);

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(socket.getInputStream())
                    );

            PrintWriter writer =
                    new PrintWriter(socket.getOutputStream(), true);

            writer.println(id);

            String result = reader.readLine();

            if (Boolean.parseBoolean(result)) {
                resultLabel.setText("이미 사용 중인 아이디입니다.");
            } else {
                resultLabel.setText("사용 가능한 아이디입니다.");
            }

            reader.close();
            writer.close();
            socket.close();

        } catch (Exception e) {
            resultLabel.setText("서버 연결 실패");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new IdCheckFrame();
    }
}