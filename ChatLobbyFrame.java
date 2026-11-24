package ChatProject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 1차 GUI - 간단한 채팅 로비.
 * 실제 채팅, DB, 초대 처리는 기존 ChatLobbyServer에 맡긴다.
 */
public class ChatLobbyFrame extends JFrame {
    private static final Color BACKGROUND = new Color(22, 23, 27);
    private static final Color CARD = new Color(47, 49, 55);
    private static final Color CARD_SELECTED = new Color(66, 70, 80);
    private static final Color TEXT = new Color(245, 246, 249);
    private static final Color MUTED = new Color(174, 178, 188);
    private static final Color ACCENT = new Color(245, 201, 67);

    private final String userId;
    private final Consumer<String> serverRequest;
    private final DefaultListModel<RoomInfo> roomModel = new DefaultListModel<>();
    private final JList<RoomInfo> roomList = new JList<>(roomModel);
    private final List<RoomInfo> pendingRooms = new ArrayList<>();
    private final JLabel statusLabel = new JLabel("채팅방 목록을 불러오는 중...");
    private final JLabel currentRoomLabel = new JLabel("현재 입장: 없음");
    private int currentRoomId = -1;

    private static class RoomInfo {
        final int id;
        final String name;
        final String type;
        final String count;

        RoomInfo(int id, String name, String type, String count) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.count = count;
        }

        @Override
        public String toString() { return id + "번 " + name; }
    }

    public ChatLobbyFrame(String userId, Consumer<String> serverRequest) {
        this.userId = userId;
        this.serverRequest = serverRequest;
        setTitle("ChatProject - 채팅 로비");
        setSize(800, 600);
        setMinimumSize(new Dimension(625, 490));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(BACKGROUND);
        root.setBorder(new EmptyBorder(22, 28, 20, 28));
        setContentPane(root);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("◯  ChatProject");
        title.setFont(new Font("맑은 고딕", Font.BOLD, 22));
        title.setForeground(TEXT);
        header.add(title, BorderLayout.WEST);
        JLabel user = new JLabel("로그인: " + userId);
        user.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        user.setForeground(TEXT);
        user.setBorder(new EmptyBorder(8, 14, 8, 14));
        user.setOpaque(true);
        user.setBackground(CARD);
        JPanel accountTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        accountTools.setOpaque(false);
        JButton profileButton = makeButton("내 정보 조회·수정", false);
        profileButton.addActionListener(e -> new ChatProfileFrame(userId));
        accountTools.add(profileButton);
        accountTools.add(user);
        header.add(accountTools, BorderLayout.EAST);
        // GUI 로그인 후 날씨를 확인할 수 있도록 헤더 아래에 표시
        JPanel topArea = new JPanel(new BorderLayout(0, 8));
        topArea.setOpaque(false);
        topArea.add(header, BorderLayout.NORTH);

        JPanel weatherBar = new JPanel(new BorderLayout(8, 0));
        weatherBar.setBackground(CARD);
        weatherBar.setBorder(new EmptyBorder(10, 14, 10, 12));
        JLabel weatherLabel = new JLabel("안양 날씨: 불러오는 중...");
        weatherLabel.setForeground(TEXT);
        weatherLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        weatherBar.add(weatherLabel, BorderLayout.CENTER);

        JButton weatherRefreshButton = makeButton("날씨 새로고침", false);
        weatherRefreshButton.addActionListener(e ->
                refreshWeather(weatherLabel, weatherRefreshButton));
        weatherBar.add(weatherRefreshButton, BorderLayout.EAST);
        topArea.add(weatherBar, BorderLayout.SOUTH);
        root.add(topArea, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        root.add(center, BorderLayout.CENTER);

        JPanel rowHeading = new JPanel(new BorderLayout());
        rowHeading.setOpaque(false);
        JLabel heading = new JLabel("채팅방 목록");
        heading.setForeground(TEXT);
        heading.setFont(new Font("맑은 고딕", Font.BOLD, 18));
        rowHeading.add(heading, BorderLayout.WEST);
        JButton refresh = makeButton("새로고침", false);
        refresh.addActionListener(e -> serverRequest.accept("ROOMS"));
        rowHeading.add(refresh, BorderLayout.EAST);
        center.add(rowHeading, BorderLayout.NORTH);

        roomList.setBackground(BACKGROUND);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomList.setFixedCellHeight(84);
        roomList.setCellRenderer((list, room, index, selected, focused) -> {
            JPanel cell = new JPanel(new BorderLayout(0, 6));
            cell.setBackground(selected ? CARD_SELECTED : CARD);
            cell.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(selected ? ACCENT : BACKGROUND, selected ? 2 : 1),
                    new EmptyBorder(13, 15, 12, 15)));
            JLabel roomName = new JLabel("[" + room.id + "번]  " + room.name);
            roomName.setForeground(TEXT);
            roomName.setFont(new Font("맑은 고딕", Font.BOLD, 15));
            JLabel details = new JLabel(("DIRECT".equals(room.type) ? "1:1" : "그룹")
                    + " 채팅방  ·  참여 인원 " + room.count + "명");
            details.setForeground(MUTED);
            details.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
            cell.add(roomName, BorderLayout.NORTH);
            cell.add(details, BorderLayout.SOUTH);
            return cell;
        });
        roomList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && roomList.getSelectedValue() != null) {
                    joinSelectedRoom();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(roomList);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BACKGROUND);
        center.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(1, 4, 8, 0));
        actions.setOpaque(false);
        JButton join = makeButton("방 입장", true);
        join.addActionListener(e -> joinSelectedRoom());
        JButton create = makeButton("+ 방 만들기", false);
        create.addActionListener(e -> createRoom());
        JButton invite = makeButton("친구 초대", false);
        invite.addActionListener(e -> inviteMember());
        JButton inviteList = makeButton("초대 대상 조회", false);
        inviteList.addActionListener(e -> {
            if (currentRoomId < 0) {
                JOptionPane.showMessageDialog(this, "먼저 그룹 채팅방에 입장해주세요.");
            } else {
                serverRequest.accept("INVITE_LIST");
                showStatus("초대 가능한 회원을 조회하는 중...");
            }
        });
        actions.add(join);
        actions.add(create);
        actions.add(invite);
        actions.add(inviteList);
        center.add(actions, BorderLayout.SOUTH);

        JPanel footer = new JPanel(new BorderLayout(0, 12));
        footer.setOpaque(false);
        root.add(footer, BorderLayout.SOUTH);
        JPanel extras = new JPanel(new GridLayout(1, 4, 10, 0));
        extras.setOpaque(false);
        JButton memo = makeButton("메모장", false);
        JButton calendar = makeButton("캘린더", false);
        JButton game = makeButton("틱택토", false);
        JButton paint = makeButton("그림판", false);
        memo.addActionListener(e -> new MemoPad());
        calendar.addActionListener(e -> new CalendarManager(userId));
        game.addActionListener(e -> {
            String other = JOptionPane.showInputDialog(this,
                    "현재 로그인 중인 상대 회원 아이디를 입력하세요.",
                    "2인 틱택토 대전 신청", JOptionPane.QUESTION_MESSAGE);
            if (other == null) return;
            other = other.trim();
            if (!other.matches("[A-Za-z0-9_]{3,30}") || other.equals(userId)) {
                JOptionPane.showMessageDialog(this, "자신을 제외한 올바른 상대 아이디를 입력해주세요.");
                return;
            }
            serverRequest.accept("TTT_INVITE|" + other);
            showStatus(other + "님에게 틱택토 대전 신청 중...");
        });
        paint.addActionListener(e -> new PaintBoard(data -> serverRequest.accept("IMAGE|" + data)));
        extras.add(memo);
        extras.add(calendar);
        extras.add(game);
        extras.add(paint);
        footer.add(extras, BorderLayout.NORTH);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        statusLabel.setForeground(MUTED);
        statusLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        currentRoomLabel.setForeground(MUTED);
        currentRoomLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        bottom.add(statusLabel, BorderLayout.WEST);
        bottom.add(currentRoomLabel, BorderLayout.EAST);
        footer.add(bottom, BorderLayout.SOUTH);

        setVisible(true);
        refreshWeather(weatherLabel, weatherRefreshButton);
    }

    /** 날씨 조회는 네트워크 작업이므로 Swing 화면을 멈추지 않도록 백그라운드에서 실행. */
    private void refreshWeather(JLabel label, JButton refreshButton) {
        label.setText("안양 날씨: 불러오는 중...");
        refreshButton.setEnabled(false);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return fetchAnyangWeather();
            }

            @Override
            protected void done() {
                try {
                    if (isDisplayable()) {
                        label.setText(get());
                    }
                } catch (Exception ex) {
                    if (isDisplayable()) {
                        label.setText("안양 날씨: 조회 실패 (새로고침으로 다시 시도)");
                    }
                    System.out.println("로비 날씨 조회 오류: " + ex.getMessage());
                } finally {
                    refreshButton.setEnabled(true);
                }
            }
        }.execute();
    }

    /** 기존 날씨 기능과 같은 Open-Meteo를 사용하며 별도 API 키가 필요 없다. */
    private static String fetchAnyangWeather() throws Exception {
        String endpoint = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=37.3943&longitude=126.9568"
                + "&current=temperature_2m,relative_humidity_2m,weather_code"
                + "&timezone=Asia%2FSeoul";
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setRequestProperty("Accept", "application/json");
        try {
            if (conn.getResponseCode() != 200) {
                throw new IllegalStateException("날씨 API 응답 코드: " + conn.getResponseCode());
            }
            StringBuilder json = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
            }
            Matcher current = Pattern.compile("\"current\"\\s*:\\s*\\{([^}]*)\\}")
                    .matcher(json.toString());
            if (!current.find()) {
                throw new IllegalStateException("현재 날씨 데이터가 없습니다.");
            }
            String part = current.group(1);
            double temperature = parseWeatherNumber(part, "temperature_2m");
            int humidity = (int) Math.round(parseWeatherNumber(part, "relative_humidity_2m"));
            int weatherCode = (int) parseWeatherNumber(part, "weather_code");
            return String.format(java.util.Locale.KOREA,
                    "안양 현재 날씨: %s  ·  %.1f°C  ·  습도 %d%%",
                    describeWeatherCode(weatherCode), temperature, humidity);
        } finally {
            conn.disconnect();
        }
    }

    private static double parseWeatherNumber(String currentJson, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key)
                + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)")
                .matcher(currentJson);
        if (!matcher.find()) {
            throw new IllegalStateException("날씨 데이터 누락: " + key);
        }
        return Double.parseDouble(matcher.group(1));
    }

    private static String describeWeatherCode(int code) {
        if (code == 0) return "맑음";
        if (code <= 3) return "대체로 흐림";
        if (code == 45 || code == 48) return "안개";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "비";
        if ((code >= 71 && code <= 77) || (code >= 85 && code <= 86)) return "눈";
        if (code >= 95 && code <= 99) return "뇌우";
        return "날씨 코드 " + code;
    }

    private JButton makeButton(String name, boolean accent) {
        JButton button = new JButton(name);
        button.setFocusPainted(false);
        button.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
        button.setBackground(accent ? ACCENT : CARD);
        button.setForeground(accent ? Color.BLACK : TEXT);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accent ? ACCENT : new Color(72, 75, 82)),
                new EmptyBorder(10, 12, 10, 12)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void joinSelectedRoom() {
        RoomInfo room = roomList.getSelectedValue();
        if (room == null) {
            JOptionPane.showMessageDialog(this, "입장할 채팅방을 선택해주세요.");
            return;
        }
        serverRequest.accept("JOIN|" + room.id);
        showStatus(room.id + "번 방 입장 요청 중...");
    }

    private void createRoom() {
        Object[] options = {"1:1 채팅방", "그룹 채팅방", "취소"};
        int type = JOptionPane.showOptionDialog(this,
                "어떤 채팅방을 만들까요?", "방 만들기",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (type == 0) {
            String other = JOptionPane.showInputDialog(this, "상대 회원 아이디:", "1:1 채팅방", JOptionPane.QUESTION_MESSAGE);
            if (other == null) return;
            other = other.trim();
            if (!other.matches("[A-Za-z0-9_]{3,30}")) {
                JOptionPane.showMessageDialog(this, "회원 아이디는 영문·숫자·밑줄 3~30자여야 합니다.");
                return;
            }
            serverRequest.accept("CREATE_DIRECT|" + other);
            showStatus("1:1 채팅방 생성 요청 중...");
        } else if (type == 1) {
            JTextField name = new JTextField();
            JTextField members = new JTextField();
            JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
            form.add(new JLabel("방 이름:"));
            form.add(name);
            form.add(new JLabel("초대할 회원 아이디 (쉼표로 구분, 예: test01,test02):"));
            form.add(members);
            int result = JOptionPane.showConfirmDialog(this, form,
                    "그룹 채팅방 만들기", JOptionPane.OK_CANCEL_OPTION);
            if (result != JOptionPane.OK_OPTION) return;
            String roomName = name.getText().trim();
            String ids = members.getText().replaceAll("\\s+", "");
            if (roomName.isEmpty() || roomName.length() > 100
                    || roomName.contains("|") || roomName.contains("\n")
                    || !ids.matches("[A-Za-z0-9_]{3,30}(,[A-Za-z0-9_]{3,30})*")) {
                JOptionPane.showMessageDialog(this, "방 이름 또는 회원 아이디 목록을 확인해주세요.");
                return;
            }
            serverRequest.accept("CREATE_GROUP|" + roomName + "|" + ids);
            showStatus("그룹 채팅방 생성 요청 중...");
        }
    }

    private void inviteMember() {
        if (currentRoomId < 0) {
            JOptionPane.showMessageDialog(this, "먼저 그룹 채팅방에 입장해주세요.");
            return;
        }
        String other = JOptionPane.showInputDialog(this,
                "현재 " + currentRoomId + "번 방에 초대할 회원 아이디:",
                "친구 초대", JOptionPane.QUESTION_MESSAGE);
        if (other == null) return;
        other = other.trim();
        if (!other.matches("[A-Za-z0-9_]{3,30}")) {
            JOptionPane.showMessageDialog(this, "회원 아이디를 확인해주세요.");
            return;
        }
        serverRequest.accept("INVITE|" + other);
        showStatus(other + "님에게 초대 요청 중...");
    }

    /** 기존 INVITE_LIST 결과를 콘솔뿐 아니라 로비에 표시한다. */
    public void showInviteCandidateList(List<String> candidates) {
        SwingUtilities.invokeLater(() -> {
            if (!isDisplayable()) return;
            if (candidates.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "지금 초대할 수 있는 회원이 없습니다.\n"
                        + "(상대방이 로비에 접속해 있어야 합니다.)");
                return;
            }
            String chosen = (String) JOptionPane.showInputDialog(this,
                    "초대할 회원을 선택하세요.", "초대 가능한 회원",
                    JOptionPane.QUESTION_MESSAGE, null,
                    candidates.toArray(new String[0]), candidates.get(0));
            if (chosen != null && !chosen.trim().isEmpty()) {
                serverRequest.accept("INVITE|" + chosen);
                showStatus(chosen + "님에게 채팅방 초대 요청 중...");
            }
        });
    }

    // 아래 메서드들은 서버 수신 스레드에서 호출되어도 안전하도록 EDT로 전달한다.
    public void beginRooms() {
        SwingUtilities.invokeLater(pendingRooms::clear);
    }

    public void appendRoom(String id, String name, String type, String count) {
        SwingUtilities.invokeLater(() -> {
            try {
                pendingRooms.add(new RoomInfo(Integer.parseInt(id), name, type, count));
            } catch (NumberFormatException ignored) { }
        });
    }

    public void endRooms() {
        SwingUtilities.invokeLater(() -> {
            Integer selectedId = roomList.getSelectedValue() == null ? null : roomList.getSelectedValue().id;
            roomModel.clear();
            int selectionIndex = -1;
            for (RoomInfo room : pendingRooms) {
                if (selectedId != null && selectedId == room.id) selectionIndex = roomModel.size();
                roomModel.addElement(room);
            }
            if (selectionIndex >= 0) roomList.setSelectedIndex(selectionIndex);
            showStatus("채팅방 " + roomModel.size() + "개 · 더블클릭으로 입장");
        });
    }

    public void enteredRoom(int roomId) {
        SwingUtilities.invokeLater(() -> {
            currentRoomId = roomId;
            currentRoomLabel.setText("현재 입장: " + roomId + "번 방");
            showStatus(roomId + "번 방 입장 성공! 채팅창이 열립니다.");
        });
    }

    public void showStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    public void showServerError(String text) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("오류: " + text);
            if (isDisplayable()) JOptionPane.showMessageDialog(this, text, "서버 안내", JOptionPane.WARNING_MESSAGE);
        });
    }

    public void receivedInvitation(int roomId, String inviter) {
        SwingUtilities.invokeLater(() -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    inviter + "님이 " + roomId + "번 채팅방으로 초대했습니다. 수락할까요?",
                    "채팅방 초대", JOptionPane.YES_NO_OPTION);
            serverRequest.accept((choice == JOptionPane.YES_OPTION ? "INVITE_ACCEPT|" : "INVITE_DECLINE|") + roomId);
        });
    }
}
