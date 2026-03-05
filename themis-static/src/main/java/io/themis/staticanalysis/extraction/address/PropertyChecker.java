package indi.dc.extraction.address;
import java.io.*;

import cn.ac.ios.bridge.util.Log;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import com.google.gson.*;
import org.apache.http.util.EntityUtils;

public class PropertyChecker {
    private static final String API_ENDPOINT = "https://api.deepseek.com/chat/completions";
    private static final Gson gson = new Gson();

    public static String checkIfNetworkProperty(String name, String value, String description) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(API_ENDPOINT);

            // 设置请求头
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + DEEPSEEK_API_KEY);

            // 构建请求体
            String requestBody = buildRequestBody(buildPrompt(name, value, description));
            httpPost.setEntity(new StringEntity(requestBody));

            // 执行请求
            try (CloseableHttpResponse response = client.execute(httpPost)) {
                String result = parseResponse(EntityUtils.toString(response.getEntity()));
//                Log.i(name, " ", result);
                return result;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Neither";
        }
    }

    private static String buildPrompt(String name, String value, String description) {
        return String.format(
                "Comprehensively analyze whether a Hadoop XML configuration item is an IP Address or Port:\n" +
                        "Name: %s\n" +
                        "Value: %s\n" +
                        "Description: %s\n\n" +
                        "Judgment criteria:\n" +
                        "1. Return ADDRESS if it represents a network location (e.g., IP address, domain name, hostname, etc.)\n" +
                        "2. Return PORT if it represents a network port\n" +
                        "3. Return NEITHER if unrelated to network positioning\n\n" +
                        "Response format: ADDRESS/PORT/NEITHER (UPPERCASE ONLY, no explanation)",
                name, value, description
        );
    }

    private static String buildRequestBody(String prompt) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "deepseek-chat");

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);

        requestBody.add("messages", messages);
        return gson.toJson(requestBody);
    }

    private static String parseResponse(String jsonResponse) {
        try {
            JsonObject responseJson = gson.fromJson(jsonResponse, JsonObject.class);
            String answer = responseJson.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString()
                    .trim();
            return answer;
        } catch (Exception e) {
            System.err.println("响应解析失败: " + jsonResponse);
            return "Neither";
        }
    }

    // 正则表达式检查是否是 IPv4 地址
    private static final String IPV4_PATTERN =
            "^(localhost|" +                                // localhost匹配
                    "(?:(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}" +  // IP地址部分
                    "(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9]))$";

    // 支持完整、压缩和混合IPv6表示法（包括::1）
    private static final String IPV6_PATTERN =
            "^(" +
                    // 完整格式（8组）
                    "([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}" +
                    "|" +
                    // 压缩格式（含::）
                    "(([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{0,4})?::" +
                    "(([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{0,4})" +
                    ")$";

    // 正则表达式检查是否是端口号
    private static final String PORT_PATTERN = "^([0-9]{1,5})$";

    // 判断是否是 IPv4 地址
    private static boolean isIPv4(String str) {
        return str != null && str.matches(IPV4_PATTERN);
    }

    // 增强版IPv6检测（包含::1等缩写形式）
    private static boolean isIPv6(String str) {
        return str.matches(IPV6_PATTERN) || str.matches("^::1$");  // 明确匹配环回地址
    }

    // 判断是否是 IP 地址（包含localhost）
    public static boolean isIPAddress(String str) {
        if (str == null) return false;

//        // 优先检查特殊名称
//        if (str.equalsIgnoreCase("localhost")) {
//            return true;
//        }

        // 检查IPv4/IPv6格式
        return isIPv4(str) || isIPv6(str);
    }

}

