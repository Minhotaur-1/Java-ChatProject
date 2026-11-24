package ChatProject;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;
import javax.swing.*;

/** 2인 온라인 틱택토 클라이언트 화면. 착수/승패는 서버만 결정한다. */
public class TicTacToeOnline extends JFrame {
    private final String gameId;
    private final String opponentId;
    private final char mySymbol;
    private final Consumer<String> serverRequest;
    private final JButton[] cells = new JButton[9];
    private final JLabel status = new JLabel("게임 시작", SwingConstants.CENTER);
    private boolean finished = false;
    private boolean movePending = false;

    public TicTacToeOnline(String gameId, String symbol, String opponentId,
            Consumer<String> serverRequest) {
        this.gameId = gameId;
        this.opponentId = opponentId;
        this.mySymbol = symbol.charAt(0);
        this.serverRequest = serverRequest;
        setTitle("ChatProject - 틱택토: " + opponentId + "님과 대전 (" + mySymbol + ")");
        setSize(430, 530);
        setMinimumSize(new Dimension(350, 420));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JLabel info = new JLabel("내 기호: " + mySymbol + "     상대: " + opponentId,
                SwingConstants.CENTER);
        info.setFont(new Font("맑은 고딕", Font.PLAIN, 15));
        status.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        JPanel top = new JPanel(new GridLayout(2, 1, 0, 5));
        top.add(info);
        top.add(status);
        add(top, BorderLayout.NORTH);

        JPanel board = new JPanel(new GridLayout(3, 3, 6, 6));
        board.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        for (int i = 0; i < 9; i++) {
            final int pos = i;
            JButton b = new JButton("");
            b.setBackground(Color.WHITE);
            b.setOpaque(true);
            b.setFont(new Font("SansSerif", Font.BOLD, 64));
            b.setFocusPainted(false);
            b.setEnabled(false);
            b.addActionListener(e -> {
                if (finished || movePending) return;
                movePending = true;
                serverRequest.accept("TTT_MOVE|" + gameId + "|" + pos);
            });
            cells[i] = b;
            board.add(b);
        }
        add(board, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton rematch = new JButton("재대결 신청");
        rematch.setEnabled(false);
        rematch.addActionListener(e -> {
            serverRequest.accept("TTT_INVITE|" + opponentId);
            rematch.setEnabled(false);
        });
        JButton close = new JButton("닫기");
        close.addActionListener(e -> dispose());
        bottom.add(rematch);
        bottom.add(close);
        add(bottom, BorderLayout.SOUTH);
        this.rematchButton = rematch;

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                if (!finished) serverRequest.accept("TTT_LEAVE|" + gameId);
            }
        });
        setVisible(true);
    }

    private final JButton rematchButton;

    /** 서버의 9칸 상태를 그대로 렌더링; 항상 EDT에서 실행 */
    public void applyState(String board, String turn, String result) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> applyState(board, turn, result));
            return;
        }
        if (board.length() != 9 || turn.length() != 1) return;
        movePending = false;
        finished = !"PLAY".equals(result);
        boolean myTurn = !finished && turn.charAt(0) == mySymbol;
        for (int i = 0; i < 9; i++) {
            char c = board.charAt(i);
            cells[i].setText(c == '.' ? "" : String.valueOf(c));
            cells[i].setForeground(c == 'X' ? new Color(45, 105, 204) : new Color(212, 69, 78));
            cells[i].setEnabled(myTurn && c == '.');
        }
        if ("DRAW".equals(result)) {
            status.setText("무승부! 다시 대전할 수 있어요.");
        } else if ("X".equals(result) || "O".equals(result)) {
            status.setText(result.charAt(0) == mySymbol ? "승리했습니다! 🎉" : "상대방이 승리했습니다.");
        } else {
            status.setText(myTurn ? "내 차례입니다! 칸을 선택하세요." : "상대방 차례를 기다리는 중...");
        }
        rematchButton.setEnabled(finished);
    }

    public void opponentLeft() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::opponentLeft);
            return;
        }
        finished = true;
        status.setText("상대방이 게임을 종료했습니다.");
        for (JButton b : cells) b.setEnabled(false);
        rematchButton.setEnabled(true);
    }

    public void clearPending() { movePending = false; }

    public String getGameId() { return gameId; }
}
