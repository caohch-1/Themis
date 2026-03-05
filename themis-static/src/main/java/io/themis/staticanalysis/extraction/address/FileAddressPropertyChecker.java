package indi.dc.extraction.address;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class FileAddressPropertyChecker {
    private static final String DEEPSEEK_API_KEY = "";
    private static final String API_ENDPOINT = "https://api.deepseek.com/chat/completions";
    private static final Gson gson = new Gson();

    public static String checkIfFilePathProperty(String name, String value, String description) {
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
                "Comprehensively analyze whether a Hadoop XML configuration item is an file path:\n" +
                        "Name: %s\n" +
                        "Value: %s\n" +
                        "Description: %s\n\n" +
                        "Judgment criteria:\n" +
                        "1. Return PATH if it represents a file path\n" +
                        "2. Return NONE if unrelated to a file path\n\n" +
                        "Response format: PATH/NONE (UPPERCASE ONLY, no explanation)",
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
            return "NONE";
        }
    }





}

