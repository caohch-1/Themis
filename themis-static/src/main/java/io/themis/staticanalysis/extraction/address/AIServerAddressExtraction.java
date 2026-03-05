package indi.dc.extraction.address;

import cn.ac.ios.bridge.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class AIServerAddressExtraction {
    private static final String API_ENDPOINT = "https://api.deepseek.com/chat/completions";
    private static final String DEEPSEEK_API_KEY = "sk-c11c7c71d0cc4d2ba8d64c63e079cb5e";
    private static final Gson gson = new Gson();
    private static final List<Message> conversationHistory = new ArrayList<>();

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\nCode Here：");
            StringBuilder codeInput = new StringBuilder();
            // 读取多行输入直到遇到END
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.equalsIgnoreCase("END")) {
                    break;
                }
                if (line.equalsIgnoreCase("exit")) {
                    scanner.close();
                    return;
                }
                codeInput.append(line).append("\n");
            }


            String analysisResult = analyzeCode(String.valueOf(codeInput));
            System.out.println("Result Here: \n" + analysisResult);

        }
    }

    private static String analyzeCode(String funcBody) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {

            HttpPost httpPost = new HttpPost(API_ENDPOINT);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + DEEPSEEK_API_KEY);

            String requestBody = buildRequestBody(funcBody);
            httpPost.setEntity(new StringEntity(requestBody));

            try (CloseableHttpResponse response = client.execute(httpPost)) {
                String aiResponse = parseResponse(EntityUtils.toString(response.getEntity()));
                // 添加AI响应到对话历史
                addAssistantMessage(aiResponse);

                return extractValues(aiResponse);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "分析失败";
        }
    }

    private static String buildRequestBody(String funcBody) {
        JsonObject requestBody = new JsonObject();
//        requestBody.addProperty("model", "deepseek-reasoner");
        requestBody.addProperty("model", "deepseek-chat");

        JsonArray messages = new JsonArray();
        // 添加系统初始提示
        messages.add(createMessage("system",
                "You are a code analysis assistant. The user will provide a function that establishes an RPC server-side. Your task is to identify the configuration keys of the server address and port - these are key-value pairs read from a Configuration object.\n"
                        + "If found, directly reply with the key names in the format: 'FOUND_KEYS: key1, key2, ...'\n"
                        + "If the code is incomplete or lacks sufficient information, explicitly list the function names you need for further analysis in the format: 'NEED_MORE_CODE: functionName1, functionName2, ...'\n"
                        + "So the final reply format contains two parts : 'FOUND_KEYS: key1, key2, ...\nNEED_MORE_CODE: functionName1, functionName2, ...'. It's OK for one part is empty.\n"
                        + "Maintain conversational continuity. Each response must either contain extracted values or request more code. Strictly follow the response format and only respond with the required content - do not provide any explanations."
        ));

        // 添加历史对话记录
        for (Message msg : conversationHistory) {
            messages.add(createMessage(msg.role, msg.content));
        }

        messages.add(createMessage("user", "Following is the code: ```" + funcBody + "```"));
        conversationHistory.add(new Message("user", "Following is the code: ```" + funcBody + "```"));


        requestBody.add("messages", messages);
        return gson.toJson(requestBody);
    }

    private static JsonObject createMessage(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static String parseResponse(String responseJson) {
        try {
            JsonObject responseObj = gson.fromJson(responseJson, JsonObject.class);
            return responseObj.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        } catch (Exception e) {
            e.printStackTrace();
            return "响应解析失败";
        }
    }

    private static String extractValues(String aiResponse) {
        // 这里可以添加自定义的响应解析逻辑
        return aiResponse;
    }


    private static void addAssistantMessage(String content) {
        conversationHistory.add(new Message("assistant", content));
    }

    static class Message {
        String role;
        String content;

        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        @Override
        public String toString() {
            return "Role: " + role + " Content: " + content + "\n";
        }
    }
}
