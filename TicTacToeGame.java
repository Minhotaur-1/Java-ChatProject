
package ChatProject;

import javax.swing.*;
import java.awt.*;

public class TicTacToeGame extends JFrame {

    private JButton[] buttons = new JButton[9];
    private JLabel statusLabel;

    private String currentPlayer = "X";
    private boolean gameOver = false;
    private int moveCount = 0;

    public TicTacToeGame() {

        setTitle("ChatProject - 틱택토 게임");
        setSize(450, 530);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // 현재 차례 표시
        statusLabel = new JLabel(
                "X 플레이어 차례", SwingConstants.CENTER);

        statusLabel.setFont(
                new Font("맑은 고딕", Font.BOLD, 22));

        add(statusLabel, BorderLayout.NORTH);

        // 3x3 게임판 생성
        JPanel board = new JPanel(new GridLayout(3, 3, 5, 5));

        for (int i = 0; i < 9; i++) {

            buttons[i] = new JButton("");

            buttons[i].setFont(
                    new Font("Arial", Font.BOLD, 60));

            buttons[i].setFocusPainted(false);
            buttons[i].setBackground(Color.WHITE);

            final int index = i;

            buttons[i].addActionListener(e -> {
                play(index);
            });

            board.add(buttons[i]);
        }

        add(board, BorderLayout.CENTER);

        // 다시 시작 버튼
        JButton resetButton = new JButton("다시 시작");

        resetButton.setFont(
                new Font("맑은 고딕", Font.BOLD, 17));

        resetButton.addActionListener(e -> resetGame());

        add(resetButton, BorderLayout.SOUTH);

        setVisible(true);
    }

    // 게임 진행
    private void play(int index) {

        if (gameOver || !buttons[index].getText().isEmpty()) {
            return;
        }

        buttons[index].setText(currentPlayer);

        if (currentPlayer.equals("X")) {
            buttons[index].setForeground(Color.BLUE);
        } else {
            buttons[index].setForeground(Color.RED);
        }

        moveCount++;

        // 승리 확인
        if (checkWinner()) {

            statusLabel.setText(
                    currentPlayer + " 플레이어 승리!");

            JOptionPane.showMessageDialog(
                    this, currentPlayer + " 플레이어가 승리했습니다!");

            gameOver = true;
            return;
        }

        // 무승부 확인
        if (moveCount == 9) {

            statusLabel.setText("무승부!");

            JOptionPane.showMessageDialog(
                    this, "무승부입니다!");

            gameOver = true;
            return;
        }

        // 플레이어 교체
        if (currentPlayer.equals("X")) {
            currentPlayer = "O";
        } else {
            currentPlayer = "X";
        }

        statusLabel.setText(
                currentPlayer + " 플레이어 차례");
    }

    // 승리 조건 확인
    private boolean checkWinner() {

        int[][] winCases = {
                {0, 1, 2},
                {3, 4, 5},
                {6, 7, 8},
                {0, 3, 6},
                {1, 4, 7},
                {2, 5, 8},
                {0, 4, 8},
                {2, 4, 6}
        };

        for (int[] line : winCases) {

            String a = buttons[line[0]].getText();
            String b = buttons[line[1]].getText();
            String c = buttons[line[2]].getText();

            if (!a.isEmpty()
                    && a.equals(b)
                    && b.equals(c)) {
                return true;
            }
        }

        return false;
    }

    // 게임 초기화
    private void resetGame() {

        for (JButton button : buttons) {
            button.setText("");
            button.setEnabled(true);
        }

        currentPlayer = "X";
        gameOver = false;
        moveCount = 0;

        statusLabel.setText("X 플레이어 차례");
    }

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            new TicTacToeGame();
        });
    }
}
