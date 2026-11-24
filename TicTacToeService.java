package ChatProject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2인 틱택토: 서버가 모든 턴, 착수, 승리와 무승부를 결정한다.
 * 기존 채팅방 프로토콜/DB와 독립적으로 동작하며 한 계정은 한 게임에만 참여할 수 있다.
 */
public class TicTacToeService {

    private static final Map<String, String> incoming = new HashMap<>(); // target -> inviter
    private static final Map<String, String> outgoing = new HashMap<>(); // inviter -> target
    private static final Map<String, Game> active = new HashMap<>(); // user -> game
    private static long nextGameId = 1L;

    private static final class Game {
        final long id;
        final String xUser;
        final String oUser;
        final char[] board = new char[9];
        char turn = 'X';
        String result = "PLAY";

        Game(long id, String xUser, String oUser) {
            this.id = id;
            this.xUser = xUser;
            this.oUser = oUser;
            Arrays.fill(board, '.');
        }
        String boardText() { return new String(board); }
    }

    private static void send(String userId, String message,
            ConcurrentHashMap<String, ChatLobbyServer.ClientInfo> onlineUsers) {
        ChatLobbyServer.ClientInfo target = onlineUsers.get(userId);
        if (target != null) target.send(message);
    }

    private static boolean isBusy(String id) {
        return active.containsKey(id) || incoming.containsKey(id) || outgoing.containsKey(id);
    }

    public static synchronized void invite(ChatLobbyServer.ClientInfo sender, String targetId,
            ConcurrentHashMap<String, ChatLobbyServer.ClientInfo> onlineUsers) {
        if (!targetId.matches("[A-Za-z0-9_]{3,30}") || targetId.equals(sender.id)) {
            sender.send("TTT_ERROR|자신을 제외한 올바른 상대 아이디를 입력해주세요.");
            return;
        }
        if (!onlineUsers.containsKey(targetId)) {
            sender.send("TTT_ERROR|상대방이 로그인 중이 아닙니다.");
            return;
        }
        if (isBusy(sender.id)) {
            sender.send("TTT_ERROR|이미 게임 중이거나 처리 중인 초대가 있습니다.");
            return;
        }
        if (isBusy(targetId)) {
            sender.send("TTT_ERROR|상대방이 다른 게임 또는 초대를 처리하고 있습니다.");
            return;
        }
        incoming.put(targetId, sender.id);
        outgoing.put(sender.id, targetId);
        send(targetId, "TTT_INVITE_FROM|" + sender.id, onlineUsers);
        sender.send("TTT_INVITE_SENT|" + targetId);
    }

    public static synchronized void accept(ChatLobbyServer.ClientInfo target, String from,
            ConcurrentHashMap<String, ChatLobbyServer.ClientInfo> onlineUsers) {
        if (!from.equals(incoming.get(target.id))) {
            target.send("TTT_ERROR|대기 중인 대전 신청이 없습니다.");
            return;
        }
        incoming.remove(target.id);
        outgoing.remove(from);
        if (!onlineUsers.containsKey(from) || active.containsKey(from)
                || active.containsKey(target.id)) {
            target.send("TTT_ERROR|대전을 시작할 수 없습니다. 상대방 접속을 확인해주세요.");
            return;
        }
        Game g = new Game(nextGameId++, from, target.id);
        active.put(g.xUser, g);
        active.put(g.oUser, g);
        send(g.xUser, "TTT_START|" + g.id + "|X|" + g.oUser, onlineUsers);
        send(g.oUser, "TTT_START|" + g.id + "|O|" + g.xUser, onlineUsers);
        broadcastBoard(g, onlineUsers);
    }

    public static synchronized void decline(ChatLobbyServer.ClientInfo target, String from,
            ConcurrentHashMap<String, ChatLobbyServer.ClientInfo> onlineUsers) {
        if (!from.equals(incoming.get(target.id))) return;
        incoming.remove(target.id);
        outgoing.remove(from);
        send(from, "TTT_INVITE_DECLINED|" + target.id, onlineUsers);
        target.send("TTT_INVITE_DECLINED_ACK|" + from);
    }

    public static synchronized void move(ChatLobbyServer.ClientInfo sender, long gameId, int position,
            ConcurrentHashMap<String, ChatLobbyServer.ClientInfo> onlineUsers) {
        Game g = active.get(sender.id);
        if (g == null || g.id != gameId) {
            sender.send("TTT_ERROR|진행 중인 게임이 아닙니다.");
            return;
        }
        char symbol = sender.id.equals(g.xUser) ? 'X' : 'O';
        if (g.turn != symbol) {
            sender.send("TTT_ERROR|지금은 상대방 차례입니다.");
            return;
        }
        if (position < 0 || position > 8 || g.board[position] != '.') {
            sender.send("TTT_ERROR|이미 선택했거나 잘못된 칸입니다.");
            return;
        }
        g.board[position] = symbol;
        if (isWinner(g.board, symbol)) {
            g.result = String.valueOf(symbol);
        } else if (new String(g.board).indexOf('.') < 0) {
            g.result = "DRAW";
        } else {
            g.turn = (symbol == 'X') ? 'O' : 'X';
        }
        broadcastBoard(g, onlineUsers);
        if (!"PLAY".equals(g.result)) {
            active.remove(g.xUser);
            active.remove(g.oUser);
        }
    }

    private static boolean isWinner(char[] b, char x) {
        int[][] lines = {{0,1,2},{3,4,5},{6,7,8},
                {0,3,6},{1,4,7},{2,5,8},{0,4,8},{2,4,6}};
        for (int[] line : lines) {
            if (b[line[0]] == x && b[line[1]] == x && b[line[2]] == x) return true;
        }
        return false;
    }

    private static void broadcastBoard(Game g,
            ConcurrentHashMap<String, ChatLobbyServer.ClientInfo> onlineUsers) {
        String msg = "TTT_STATE|" + g.id + "|" + g.boardText()
                + "|" + g.turn + "|" + g.result;
        send(g.xUser, msg, onlineUsers);
        send(g.oUser, msg, onlineUsers);
    }

    public static synchronized void leave(ChatLobbyServer.ClientInfo sender, long gameId,
            ConcurrentHashMap<String, ChatLobbyServer.ClientInfo> onlineUsers) {
        Game g = active.get(sender.id);
        if (g == null || g.id != gameId) return;
        active.remove(g.xUser);
        active.remove(g.oUser);
        String peer = sender.id.equals(g.xUser) ? g.oUser : g.xUser;
        send(peer, "TTT_OPPONENT_LEFT|" + g.id, onlineUsers);
    }

    public static synchronized void onDisconnect(ChatLobbyServer.ClientInfo sender,
            ConcurrentHashMap<String, ChatLobbyServer.ClientInfo> onlineUsers) {
        // 초대 보낸 회원 연결 종료
        String target = outgoing.remove(sender.id);
        if (target != null) {
            incoming.remove(target);
            send(target, "TTT_INVITE_CANCELLED|" + sender.id, onlineUsers);
        }
        // 초대 받은 회원 연결 종료
        String from = incoming.remove(sender.id);
        if (from != null) {
            outgoing.remove(from);
            send(from, "TTT_INVITE_CANCELLED|" + sender.id, onlineUsers);
        }
        Game g = active.get(sender.id);
        if (g != null) leave(sender, g.id, onlineUsers);
    }
}
