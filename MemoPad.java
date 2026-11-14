
package ChatProject;

import javax.swing.*;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class MemoPad extends JFrame {

    private JTextArea textArea;
    private UndoManager undoManager;
    private File currentFile;

    public MemoPad() {

        setTitle("ChatProject 메모장");
        setSize(650, 500);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        // 텍스트 입력 및 편집 공간
        textArea = new JTextArea();
        textArea.setFont(new Font("맑은 고딕", Font.PLAIN, 16));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        add(new JScrollPane(textArea), BorderLayout.CENTER);

        // 실행 취소 기능
        undoManager = new UndoManager();
        textArea.getDocument().addUndoableEditListener(undoManager);

        // 메뉴 생성
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("파일");
        JMenu editMenu = new JMenu("편집");

        // ===== 파일 메뉴 =====

        JMenuItem newItem = new JMenuItem("새 파일");
        JMenuItem openItem = new JMenuItem("열기");
        JMenuItem saveItem = new JMenuItem("저장");
        JMenuItem saveAsItem = new JMenuItem("다른 이름으로 저장");
        JMenuItem exitItem = new JMenuItem("종료");

        fileMenu.add(newItem);
        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.add(saveAsItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        // 새 파일
        newItem.addActionListener(e -> {
            textArea.setText("");
            currentFile = null;
            undoManager.discardAllEdits();
            setTitle("ChatProject 메모장");
        });

        // 파일 열기
        openItem.addActionListener(e -> {

            JFileChooser chooser = new JFileChooser();

            if (chooser.showOpenDialog(this)
                    == JFileChooser.APPROVE_OPTION) {

                try {
                    currentFile = chooser.getSelectedFile();

                    String content = Files.readString(
                            currentFile.toPath(),
                            StandardCharsets.UTF_8);

                    textArea.setText(content);
                    undoManager.discardAllEdits();

                    setTitle("ChatProject 메모장 - "
                            + currentFile.getName());

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                            this, "파일 열기 실패!");
                }
            }
        });

        // 파일 저장
        saveItem.addActionListener(e -> saveFile(false));

        // 다른 이름으로 저장
        saveAsItem.addActionListener(e -> saveFile(true));

        // 종료
        exitItem.addActionListener(e -> dispose());

        // ===== 편집 메뉴 =====

        JMenuItem undoItem = new JMenuItem("실행 취소");
        JMenuItem redoItem = new JMenuItem("다시 실행");

        JMenuItem cutItem = new JMenuItem("잘라내기");
        JMenuItem copyItem = new JMenuItem("복사");
        JMenuItem pasteItem = new JMenuItem("붙여넣기");

        JMenuItem selectAllItem = new JMenuItem("전체 선택");
        JMenuItem findItem = new JMenuItem("찾기");
        JMenuItem replaceItem = new JMenuItem("바꾸기");

        // 단축키 설정
        undoItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));

        redoItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));

        cutItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK));

        copyItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));

        pasteItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));

        selectAllItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK));

        findItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));

        // 실행 취소
        undoItem.addActionListener(e -> {
            if (undoManager.canUndo()) {
                undoManager.undo();
            }
        });

        // 다시 실행
        redoItem.addActionListener(e -> {
            if (undoManager.canRedo()) {
                undoManager.redo();
            }
        });

        // 잘라내기
        cutItem.addActionListener(e -> textArea.cut());

        // 복사
        copyItem.addActionListener(e -> textArea.copy());

        // 붙여넣기
        pasteItem.addActionListener(e -> textArea.paste());

        // 전체 선택
        selectAllItem.addActionListener(
                e -> textArea.selectAll());

        // 텍스트 찾기
        findItem.addActionListener(e -> {

            String keyword = JOptionPane.showInputDialog(
                    this, "찾을 단어를 입력하세요.");

            if (keyword == null || keyword.isEmpty()) {
                return;
            }

            String text = textArea.getText();

            int start = text.indexOf(
                    keyword, textArea.getCaretPosition());

            if (start == -1) {
                start = text.indexOf(keyword);
            }

            if (start >= 0) {
                textArea.requestFocusInWindow();
                textArea.select(start, start + keyword.length());
            } else {
                JOptionPane.showMessageDialog(
                        this, "찾는 단어가 없습니다.");
            }
        });

        // 텍스트 바꾸기
        replaceItem.addActionListener(e -> {

            String oldText = JOptionPane.showInputDialog(
                    this, "바꿀 단어를 입력하세요.");

            if (oldText == null || oldText.isEmpty()) {
                return;
            }

            String newText = JOptionPane.showInputDialog(
                    this, "새로운 단어를 입력하세요.");

            if (newText == null) {
                return;
            }

            String content = textArea.getText();

            textArea.setText(
                    content.replace(oldText, newText));

            JOptionPane.showMessageDialog(
                    this, "텍스트 바꾸기 완료!");
        });

        // 편집 메뉴에 추가
        editMenu.add(undoItem);
        editMenu.add(redoItem);
        editMenu.addSeparator();

        editMenu.add(cutItem);
        editMenu.add(copyItem);
        editMenu.add(pasteItem);
        editMenu.add(selectAllItem);

        editMenu.addSeparator();
        editMenu.add(findItem);
        editMenu.add(replaceItem);

        // 메뉴바 등록
        menuBar.add(fileMenu);
        menuBar.add(editMenu);

        setJMenuBar(menuBar);

        setVisible(true);
    }

    // 파일 저장 기능
    private void saveFile(boolean saveAs) {

        if (currentFile == null || saveAs) {

            JFileChooser chooser = new JFileChooser();

            if (chooser.showSaveDialog(this)
                    != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File selected = chooser.getSelectedFile();

            if (!selected.getName().contains(".")) {
                selected = new File(
                        selected.getAbsolutePath() + ".txt");
            }

            currentFile = selected;
        }

        try {
            Files.writeString(
                    currentFile.toPath(),
                    textArea.getText(),
                    StandardCharsets.UTF_8);

            setTitle("ChatProject 메모장 - "
                    + currentFile.getName());

            JOptionPane.showMessageDialog(
                    this, "파일 저장 완료!");

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this, "파일 저장 실패!");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MemoPad());
    }
}
