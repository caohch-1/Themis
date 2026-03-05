package indi.dc.testing;

public class Manual {
    public static void main(String[] args) {
        String test = "You are a Hadoop RPC testing expert. Generate a Java test case that validates RPC communication between a server and client using Hadoop's RPC framework. The generated test case will be packaged as a jar and uploaded and executed on an existing hadoop cluster by \"hadoop jar RPCTest-1.0-SNAPSHOT.jar rpctest.RPCTest\".\n" +
                "You will given input in following formats: \"serverClass: {code_of_serverClass}, rpcClass: {code_of_serverClass}, rpcMethod: {name_of_serverClass}\".\n" +
                "Follow these requirements and instructions:\n" +
                "1. Output test case must be named \"RPCTest.java\" with a main() method entry point;\n" +
                "2. Do NOT use JUnit or testing frameworks;\n" +
                "3. Your test case should follows three steps including extracting current configuration, creating and initializing the serverClass, creating and initializing the rpcClass, and finally invoking the rpcMethod.\n" +
                "Your reply must only contains the test case code without any introduction or explanation." +
                "Following is one example input and output:\n" +
                String.format("INPUT: serverClass: {%s}, rpcClass: {%s}, rpcMethod: {%s}\n", ExampleTestCase.SERVER_CLASS, ExampleTestCase.RPC_CLASS, ExampleTestCase.RPC_METHOD) +
                String.format("OUTPUT: %s", ExampleTestCase.TEST_CASE);
        System.out.println(test);
    }
}
