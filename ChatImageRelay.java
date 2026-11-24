
package ChatProject;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;

public class ChatImageRelay {

    // PNG 이미지 최대 크기: 2MB
    private static final int MAX_IMAGE_SIZE = 2_000_000;

    public static void sendImage(
            ChatLobbyServer.ClientInfo sender,
            String imageData,
            Map<String, ChatLobbyServer.ClientInfo> users) {

        // 채팅방 입장 확인
        if (sender.roomId == -1 ||
                !ChatLobbyServer.isRoomMember(
                        sender.roomId, sender.id)) {

            sender.send("ERROR|먼저 채팅방에 입장해주세요.");
            return;
        }

        // 이미지 크기 확인
        if (imageData.length() > 2800000) {
            sender.send("ERROR|이미지 크기가 너무 큽니다.");
            return;
        }

        try {
            // Base64 문자열을 이미지 데이터로 변환
            byte[] imageBytes =
                    Base64.getDecoder().decode(imageData);

            if (imageBytes.length > MAX_IMAGE_SIZE) {
                sender.send("ERROR|이미지는 2MB 이하만 가능합니다.");
                return;
            }

            // 실제 이미지인지 확인
            BufferedImage image = ImageIO.read(
                    new ByteArrayInputStream(imageBytes));

            if (image == null
                    || image.getWidth() > 2000
                    || image.getHeight() > 2000) {

                sender.send("ERROR|올바르지 않은 이미지입니다.");
                return;
            }

            int count = 0;

            // 같은 채팅방에 있는 회원에게만 전송
            for (ChatLobbyServer.ClientInfo target
                    : users.values()) {

                if (target == sender) {
                    continue;
                }

                if (target.roomId == sender.roomId
                        && ChatLobbyServer.isRoomMember(
                                sender.roomId, target.id)) {

                    target.send("IMAGE_FROM|"
                            + sender.id + "|" + imageData);

                    count++;
                }
            }

            // 보낸 사람에게 전송 결과 안내
            sender.send("IMAGE_SENT|" + count);

            System.out.println("[이미지 전송 완료] "
                    + sender.id + " / 채팅방: "
                    + sender.roomId + " / 수신자: "
                    + count + "명");

        } catch (Exception e) {
            sender.send("ERROR|이미지 전송 실패");
            e.printStackTrace();
        }
    }
}
