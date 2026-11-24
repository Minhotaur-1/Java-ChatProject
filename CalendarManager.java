
package ChatProject;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

public class CalendarManager extends JFrame {

    private static final String URL =
            "jdbc:mysql://localhost:3306/chatdb";

    private static final String DB_USER = "root";

    private static final String DB_PASSWORD =
            "rlaalsgh1234";

    private final String userId;

    private LocalDate selectedDate = LocalDate.now();
    private YearMonth currentMonth =
            YearMonth.from(LocalDate.now());

    private JPanel calendarPanel;
    private JLabel monthLabel;
    private JLabel dateLabel;

    private JTable scheduleTable;
    private DefaultTableModel tableModel;

    public CalendarManager(String userId) {

        this.userId = userId;

        setTitle("ChatProject - 일정 관리 (" + userId + ")");
        setSize(850, 520);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout(10, 10));

        // ===== 달력 영역 =====
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setPreferredSize(new Dimension(420, 450));

        JPanel monthPanel = new JPanel(new BorderLayout());

        JButton prevButton = new JButton("◀");
        JButton nextButton = new JButton("▶");

        monthLabel = new JLabel("", SwingConstants.CENTER);
        monthLabel.setFont(
                new Font("맑은 고딕", Font.BOLD, 20));

        monthPanel.add(prevButton, BorderLayout.WEST);
        monthPanel.add(monthLabel, BorderLayout.CENTER);
        monthPanel.add(nextButton, BorderLayout.EAST);

        leftPanel.add(monthPanel, BorderLayout.NORTH);

        calendarPanel = new JPanel(
                new GridLayout(7, 7, 3, 3));

        leftPanel.add(calendarPanel, BorderLayout.CENTER);

        add(leftPanel, BorderLayout.CENTER);

        // 이전 달
        prevButton.addActionListener(e -> {
            currentMonth = currentMonth.minusMonths(1);
            selectedDate = currentMonth.atDay(1);
            refreshAll();
        });

        // 다음 달
        nextButton.addActionListener(e -> {
            currentMonth = currentMonth.plusMonths(1);
            selectedDate = currentMonth.atDay(1);
            refreshAll();
        });

        // ===== 일정 목록 영역 =====
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setPreferredSize(new Dimension(390, 450));

        dateLabel = new JLabel("", SwingConstants.CENTER);
        dateLabel.setFont(
                new Font("맑은 고딕", Font.BOLD, 16));

        rightPanel.add(dateLabel, BorderLayout.NORTH);

        // 일정 목록 테이블
        tableModel = new DefaultTableModel(
                new String[]{"번호", "제목", "내용"}, 0) {

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        scheduleTable = new JTable(tableModel);
        scheduleTable.setRowHeight(27);
        scheduleTable.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION);

        rightPanel.add(
                new JScrollPane(scheduleTable),
                BorderLayout.CENTER);

        // ===== 일정 관리 버튼 =====
        JPanel buttonPanel = new JPanel(
                new GridLayout(1, 3, 5, 5));

        JButton addButton = new JButton("일정 등록");
        JButton editButton = new JButton("일정 수정");
        JButton deleteButton = new JButton("일정 삭제");

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);

        rightPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(rightPanel, BorderLayout.EAST);

        // 버튼 이벤트
        addButton.addActionListener(e -> addSchedule());
        editButton.addActionListener(e -> editSchedule());
        deleteButton.addActionListener(e -> deleteSchedule());

        refreshAll();
        setVisible(true);
    }

    // DB 연결
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                URL, DB_USER, DB_PASSWORD);
    }

    // 화면 갱신
    private void refreshAll() {
        showCalendar();
        loadSchedules();
    }

    // 월별 일정 개수 조회
    private Map<Integer, Integer> getMonthlyCounts() {

        Map<Integer, Integer> counts = new HashMap<>();

        String sql =
                "SELECT DAYOFMONTH(schedule_date) AS day_num, "
                + "COUNT(*) AS cnt "
                + "FROM schedules "
                + "WHERE user_id = ? "
                + "AND schedule_date >= ? "
                + "AND schedule_date < ? "
                + "GROUP BY schedule_date";

        try (Connection conn = getConnection();
             PreparedStatement pstmt =
                     conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            pstmt.setDate(2, Date.valueOf(currentMonth.atDay(1)));
            pstmt.setDate(3, Date.valueOf(
                    currentMonth.plusMonths(1).atDay(1)));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    counts.put(
                            rs.getInt("day_num"),
                            rs.getInt("cnt"));
                }
            }

        } catch (SQLException e) {
            showError("달력 조회 실패", e);
        }

        return counts;
    }

    // 달력 출력
    private void showCalendar() {

        calendarPanel.removeAll();

        monthLabel.setText(
                currentMonth.getYear() + "년 "
                + currentMonth.getMonthValue() + "월");

        String[] days = {
                "월", "화", "수", "목", "금", "토", "일"
        };

        for (String day : days) {
            JLabel label = new JLabel(
                    day, SwingConstants.CENTER);
            label.setFont(
                    new Font("맑은 고딕", Font.BOLD, 14));
            calendarPanel.add(label);
        }

        int startDay =
                currentMonth.atDay(1).getDayOfWeek().getValue();

        int totalDays = currentMonth.lengthOfMonth();

        Map<Integer, Integer> counts = getMonthlyCounts();

        // 6주 분량의 달력 칸 생성
        for (int cell = 0; cell < 42; cell++) {

            int day = cell - startDay + 2;

            if (day < 1 || day > totalDays) {
                calendarPanel.add(new JLabel(""));
                continue;
            }

            String title = String.valueOf(day);

            if (counts.containsKey(day)) {
                title += " (" + counts.get(day) + ")";
            }

            JButton dayButton = new JButton(title);

            dayButton.setFont(
                    new Font("맑은 고딕", Font.PLAIN, 12));

            LocalDate date = currentMonth.atDay(day);

            if (date.equals(selectedDate)) {
                dayButton.setBackground(
                        new Color(180, 215, 255));
                dayButton.setOpaque(true);
            }

            dayButton.addActionListener(e -> {
                selectedDate = date;
                refreshAll();
            });

            calendarPanel.add(dayButton);
        }

        calendarPanel.revalidate();
        calendarPanel.repaint();
    }

    // 일정 조회
    private void loadSchedules() {

        tableModel.setRowCount(0);

        dateLabel.setText(
                selectedDate.toString() + " 일정 목록");

        String sql =
                "SELECT schedule_id, title, description "
                + "FROM schedules "
                + "WHERE user_id = ? AND schedule_date = ? "
                + "ORDER BY schedule_id";

        try (Connection conn = getConnection();
             PreparedStatement pstmt =
                     conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            pstmt.setDate(2, Date.valueOf(selectedDate));

            try (ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {

                    tableModel.addRow(new Object[]{
                            rs.getInt("schedule_id"),
                            rs.getString("title"),
                            rs.getString("description")
                    });
                }
            }

        } catch (SQLException e) {
            showError("일정 조회 실패", e);
        }
    }

    // 일정 입력 창
    private String[] showScheduleDialog(
            String title, String content) {

        JTextField titleField = new JTextField(title, 20);

        JTextArea contentArea =
                new JTextArea(content, 5, 20);

        contentArea.setLineWrap(true);

        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JPanel top = new JPanel(new BorderLayout());
        top.add(new JLabel("일정 제목:"), BorderLayout.NORTH);
        top.add(titleField, BorderLayout.CENTER);

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(contentArea),
                BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(
                this,
                panel,
                "일정 정보 입력 - " + selectedDate,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String newTitle = titleField.getText().trim();
        String newContent = contentArea.getText();

        if (newTitle.isEmpty() || newTitle.length() > 100) {
            JOptionPane.showMessageDialog(
                    this, "일정 제목은 1~100자로 입력해주세요.");
            return null;
        }

        return new String[]{newTitle, newContent};
    }

    // 일정 등록
    private void addSchedule() {

        String[] input = showScheduleDialog("", "");

        if (input == null) {
            return;
        }

        String sql =
                "INSERT INTO schedules "
                + "(user_id, schedule_date, title, description) "
                + "VALUES (?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt =
                     conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            pstmt.setDate(2, Date.valueOf(selectedDate));
            pstmt.setString(3, input[0]);
            pstmt.setString(4, input[1]);

            pstmt.executeUpdate();

            JOptionPane.showMessageDialog(
                    this, "일정 등록 완료!");

            refreshAll();

        } catch (SQLException e) {
            showError("일정 등록 실패", e);
        }
    }

    // 일정 수정
    private void editSchedule() {

        int row = scheduleTable.getSelectedRow();

        if (row == -1) {
            JOptionPane.showMessageDialog(
                    this, "수정할 일정을 선택해주세요.");
            return;
        }

        int scheduleId = (int) tableModel.getValueAt(row, 0);

        String oldTitle =
                (String) tableModel.getValueAt(row, 1);

        String oldContent =
                (String) tableModel.getValueAt(row, 2);

        String[] input = showScheduleDialog(
                oldTitle,
                oldContent == null ? "" : oldContent);

        if (input == null) {
            return;
        }

        String sql =
                "UPDATE schedules "
                + "SET title = ?, description = ? "
                + "WHERE schedule_id = ? AND user_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt =
                     conn.prepareStatement(sql)) {

            pstmt.setString(1, input[0]);
            pstmt.setString(2, input[1]);
            pstmt.setInt(3, scheduleId);
            pstmt.setString(4, userId);

            int result = pstmt.executeUpdate();

            if (result > 0) {
                JOptionPane.showMessageDialog(
                        this, "일정 수정 완료!");
                refreshAll();
            }

        } catch (SQLException e) {
            showError("일정 수정 실패", e);
        }
    }

    // 일정 삭제
    private void deleteSchedule() {

        int row = scheduleTable.getSelectedRow();

        if (row == -1) {
            JOptionPane.showMessageDialog(
                    this, "삭제할 일정을 선택해주세요.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "선택한 일정을 삭제할까요?",
                "일정 삭제",
                JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        int scheduleId = (int) tableModel.getValueAt(row, 0);

        String sql =
                "DELETE FROM schedules "
                + "WHERE schedule_id = ? AND user_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt =
                     conn.prepareStatement(sql)) {

            pstmt.setInt(1, scheduleId);
            pstmt.setString(2, userId);

            int result = pstmt.executeUpdate();

            if (result > 0) {
                JOptionPane.showMessageDialog(
                        this, "일정 삭제 완료!");
                refreshAll();
            }

        } catch (SQLException e) {
            showError("일정 삭제 실패", e);
        }
    }

    // DB 오류 안내
    private void showError(String message, Exception e) {
        JOptionPane.showMessageDialog(
                this,
                message + "\n" + e.getMessage(),
                "오류",
                JOptionPane.ERROR_MESSAGE);

        e.printStackTrace();
    }

    // 단독 실행 테스트
    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            String id = JOptionPane.showInputDialog(
                    null, "테스트할 회원 아이디를 입력하세요.");

            if (id != null && !id.trim().isEmpty()) {
                new CalendarManager(id.trim());
            }
        });
    }
}
