package indi.dc.testing;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class AITestGeneration {

    private static final String API_ENDPOINT = "https://api.deepseek.com/chat/completions";
    private static final String DEEPSEEK_API_KEY = "sk-c11c7c71d0cc4d2ba8d64c63e079cb5e";
    private static final Gson gson = new Gson();
    private static final List<Message> conversationHistory = new ArrayList<>();
//    private static final String MODEL = "deepseek-chat";
    private static final String MODEL = "deepseek-reasoner";

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);
        boolean fix = false;
        while (true) {
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

            String analysisResult = initTestCase(String.valueOf(codeInput), fix);
            fix = true;
            System.out.println("Result Here: \n" + analysisResult);

        }
    }

    private static String initTestCase(String funcBody, boolean fix) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {

            HttpPost httpPost = new HttpPost(API_ENDPOINT);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + DEEPSEEK_API_KEY);

            String requestBody;
            if (!fix) requestBody = buildInitRequestBody(funcBody);
            else requestBody = buildFixRequestBody(funcBody);

            httpPost.setEntity(new StringEntity(requestBody));

            try (CloseableHttpResponse response = client.execute(httpPost)) {
                String aiResponse = parseResponse(EntityUtils.toString(response.getEntity()));
                // 添加AI响应到对话历史
                addAssistantMessage(aiResponse);

                return aiResponse;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "分析失败";
        }
    }

    private static String buildInitRequestBody(String promptText) {
        JsonObject requestBody = new JsonObject();
//        requestBody.addProperty("model", "deepseek-reasoner");
        requestBody.addProperty("model", MODEL);

        JsonArray messages = new JsonArray();
        // 添加系统初始提示
        messages.add(createMessage("system",
                "You are a Hadoop RPC testing expert. Generate a Java test case that validates RPC communication between a server and client using Hadoop's RPC framework. The generated test case will be packaged as a jar and uploaded and executed on an existing hadoop cluster by \"hadoop jar RPCTest-1.0-SNAPSHOT.jar rpctest.RPCTest\".\n" +
                        "You will given input in following formats: \"serverClass: {code_of_serverClass}, rpcClass: {code_of_serverClass}, rpcMethod: {name_of_serverClass}\".\n" +
                        "Follow these requirements and instructions:\n" +
                        "1. Output test case must be named \"RPCTest.java\" with a main() method entry point;\n" +
                        "2. Do NOT use JUnit or testing frameworks;\n" +
                        "3. Your test case should follows three steps including extracting current configuration, creating and initializing the serverClass, creating and initializing the rpcClass, and finally invoking the rpcMethod.\n" +
                        "Your reply must only contains the test case code without any introduction or explanation." +
                        "Following is one example input and output:\n" +
                        String.format("INPUT: serverClass: {%s}, rpcClass: {%s}, rpcMethod: {%s}\n", ExampleTestCase.SERVER_CLASS, ExampleTestCase.RPC_CLASS, ExampleTestCase.RPC_METHOD) +
                        String.format("OUTPUT: %s", ExampleTestCase.TEST_CASE)
        ));

//        // 添加历史对话记录
//        for (Message msg : conversationHistory) {
//            messages.add(createMessage(msg.role, msg.content));
//        }

//        messages.add(createMessage("user", String.format("serverClass: {%s}, rpcClassName: {%s}, rpcMethodName: {%s}", serverClassName, rpcClassName, rpcMethodName)));
        messages.add(createMessage("user", promptText));
//        conversationHistory.add(new Message("user", String.format("serverClass: {%s}, rpcClassName: {%s}, rpcMethodName: {%s}", serverClassName, rpcClassName, rpcMethodName)));
        conversationHistory.add(new Message("user", promptText));


        requestBody.add("messages", messages);
        return gson.toJson(requestBody);
    }

    private static String buildFixRequestBody(String promptText) {
        JsonObject requestBody = new JsonObject();
//        requestBody.addProperty("model", "deepseek-reasoner");
        requestBody.addProperty("model", MODEL);

        JsonArray messages = new JsonArray();
        // 添加系统初始提示
        messages.add(createMessage("system",
                "You are a Hadoop RPC testing expert. Fixing a Java test case that validates RPC communication between a server and client using Hadoop's RPC framework. The generated test case will be packaged as a jar and uploaded and executed on an existing hadoop cluster by \"hadoop jar RPCTest-1.0-SNAPSHOT.jar rpctest.RPCTest\".\n" +
                        "You will given the output with error after executing the test case.\n" +
                        "Follow these requirements and instructions:\n" +
                        "Your reply must only contains the fixed test case code without any introduction or explanation."
        ));

        // 添加历史对话记录
        for (Message msg : conversationHistory) {
            messages.add(createMessage(msg.role, msg.content));
        }

//        messages.add(createMessage("user", String.format("serverClass: {%s}, rpcClassName: {%s}, rpcMethodName: {%s}", serverClassName, rpcClassName, rpcMethodName)));
        messages.add(createMessage("user", promptText));
//        conversationHistory.add(new Message("user", String.format("serverClass: {%s}, rpcClassName: {%s}, rpcMethodName: {%s}", serverClassName, rpcClassName, rpcMethodName)));
        conversationHistory.add(new Message("user", promptText));


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
