
package ChatProject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WeatherAPITest {

    // JSON에서 숫자 데이터 추출
    public static double getNumber(String json, String key) {
        String regex = "\"" + key
                + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)";

        Matcher matcher = Pattern.compile(regex).matcher(json);

        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }

        throw new IllegalArgumentException(
                "날씨 데이터가 없습니다: " + key);
    }

    // 날씨 코드를 한글로 변환
    public static String getWeatherStatus(int code) {

        if (code == 0) return "맑음";
        if (code == 1 || code == 2) return "대체로 맑음";
        if (code == 3) return "흐림";
        if (code == 45 || code == 48) return "안개";
        if (code >= 51 && code <= 57) return "이슬비";
        if (code >= 61 && code <= 67) return "비";
        if (code >= 71 && code <= 77) return "눈";
        if (code >= 80 && code <= 82) return "소나기";
        if (code == 85 || code == 86) return "눈 소나기";
        if (code >= 95 && code <= 99) return "뇌우";

        return "기타 날씨";
    }

    public static void main(String[] args) {

        System.out.println("===== 실시간 날씨 조회 =====");
        System.out.println("날씨 정보를 불러오는 중...");

        try {
            // 안양시 위치 기준
            String apiUrl =
                    "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=37.3943"
                    + "&longitude=126.9568"
                    + "&current=temperature_2m,"
                    + "relative_humidity_2m,weather_code"
                    + "&timezone=Asia%2FSeoul";

            // HTTP 연결
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            // API 요청 및 응답
            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println("API 요청 실패!");
                System.out.println("HTTP 상태 코드: "
                        + response.statusCode());
                return;
            }

            String json = response.body();

            // JSON 데이터 추출
            double temperature =
                    getNumber(json, "temperature_2m");

            double humidity =
                    getNumber(json, "relative_humidity_2m");

            int weatherCode =
                    (int) getNumber(json, "weather_code");

            // 결과 출력
            System.out.println();
            System.out.println("===== 안양시 현재 날씨 =====");

            System.out.printf("현재 기온: %.1f°C%n",
                    temperature);

            System.out.printf("현재 습도: %.0f%%%n",
                    humidity);

            System.out.println("날씨 상태: "
                    + getWeatherStatus(weatherCode));

            System.out.println();
            System.out.println("날씨 조회 성공!");
            System.out.println("데이터 제공: Open-Meteo");

        } catch (Exception e) {
            System.out.println("날씨 조회 중 오류 발생!");
            System.out.println("오류 내용: " + e.getMessage());
        }
    }
}
