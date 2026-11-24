package ChatProject;

import java.awt.Component;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.swing.ImageIcon;

/**
 * PNG/GIF 이미지 이모티콘 카탈로그.
 * 11주차: DB 원본 이미지 우선 로드, DB가 안 되면 기존 리소스 이미지 사용.
 * 기존 EMOTICON|id 및 [EMOTICON:id] 프로토콜은 변경하지 않습니다.
 */
public final class EmoticonCatalog {
    private EmoticonCatalog() { }

    private static final String[] IDS = {
        "smile", "heart", "party", "cry", "wave", "sparkle"
    };
    private static final String[] LABELS = {
        "웃음", "하트", "축하", "슬픔", "손 흔들기 (GIF)", "반짝임 (GIF)"
    };
    private static final String[] FILES = {
        "smile.png", "heart.png", "party.png", "cry.png", "wave.gif", "sparkle.gif"
    };

    private static final Map<String, byte[]> cachedImages = new HashMap<>();
    private static boolean seedAttempted = false;

    public static String[] ids() { return IDS.clone(); }

    private static int indexOf(String id) {
        if (id == null) return -1;
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) return i;
        }
        return -1;
    }

    public static boolean isAllowed(String id) { return indexOf(id) >= 0; }

    public static String toMessage(String id) {
        if (!isAllowed(id)) throw new IllegalArgumentException("알 수 없는 이모티콘: " + id);
        return "[EMOTICON:" + id + "]";
    }

    public static String extractId(String content) {
        if (content == null || !content.startsWith("[EMOTICON:") || !content.endsWith("]")) {
            return null;
        }
        String id = content.substring("[EMOTICON:".length(), content.length() - 1);
        return isAllowed(id) ? id : null;
    }

    public static String label(String id) {
        int i = indexOf(id);
        return i < 0 ? "알 수 없는 이모티콘" : LABELS[i];
    }

    public static String describe(String content) {
        String id = extractId(content);
        return id == null ? content : "[이미지 이모티콘: " + label(id) + "]";
    }

    /** 최초 이모티콘 사용 시 기존 리소스 6종을 DB에 등록합니다. */
    private static synchronized void trySeedOnce() {
        if (seedAttempted) return;
        seedAttempted = true;
        try {
            int count = EmoticonDbRepository.seedBundledEmoticons(IDS, LABELS, FILES);
            if (count > 0) System.out.println("[11주차] 이모티콘 DB 신규 등록: " + count + "개");
        } catch (Exception ex) {
            System.err.println("[11주차] 이모티콘 DB 사용 불가, 기존 이미지로 표시: " + ex.getMessage());
        }
    }

    /**
     * 서버는 ID만 검증하고, GUI는 이미지가 필요할 때만 DB를 조회합니다.
     * byte[] 기반 ImageIcon도 움직이는 GIF를 지원합니다.
     */
    public static ImageIcon icon(String id, Component repaintTarget) {
        int i = indexOf(id);
        if (i < 0) return null;

        trySeedOnce();
        byte[] imageBytes;
        synchronized (cachedImages) { imageBytes = cachedImages.get(id); }
        if (imageBytes == null) {
            try {
                imageBytes = EmoticonDbRepository.loadImage(id);
                if (imageBytes != null) {
                    synchronized (cachedImages) { cachedImages.put(id, imageBytes); }
                }
            } catch (Exception ex) {
                // DB 조회 실패 시 기존 classpath 리소스를 사용합니다.
            }
        }

        ImageIcon result;
        if (imageBytes != null) {
            result = new ImageIcon(imageBytes, LABELS[i]);
        } else {
            URL url = EmoticonCatalog.class.getResource("/ChatProject/emoticons/" + FILES[i]);
            if (url == null) return null;
            result = new ImageIcon(url, LABELS[i]);
        }
        if (repaintTarget != null) result.setImageObserver(repaintTarget);
        return result;
    }
}
