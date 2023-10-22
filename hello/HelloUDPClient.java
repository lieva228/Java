package info.kgeorgiy.ja.shpraidun.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HelloUDPClient implements HelloClient {

    @Override
    public void run(String host, int port, String prefix, int threads, int requests) {
        InetSocketAddress socketAddress = new InetSocketAddress(host, port);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int i = 1; i < threads + 1; i++) {
            final String request = prefix + i + "_";
            executor.execute(() -> {
                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.setSoTimeout(100);
                    for (int j = 1; j < requests + 1; j++) {
                        byte[] requestData = (request + j).getBytes(StandardCharsets.UTF_8);
                        DatagramPacket requestPacket = new DatagramPacket(requestData, requestData.length, socketAddress);
                        byte[] dataResponse = new byte[socket.getReceiveBufferSize()];
                        DatagramPacket responsePacket = new DatagramPacket(dataResponse, socket.getReceiveBufferSize());
                        while (true) {
                            try {
                                socket.send(requestPacket);
                                socket.receive(responsePacket);
                            } catch (IOException ignored) {}
                            String response = new String(
                                    responsePacket.getData(),
                                    responsePacket.getOffset(),
                                    responsePacket.getLength(),
                                    StandardCharsets.UTF_8);
                            if (response.equals("Hello, " + request + j)) {
                                System.out.println(response);
                                break;
                            }
                        }
                    }
                } catch (SocketException e) {
                    System.err.println("Can't create socket " + e.getMessage());
                }
            });
        }
        executor.close();
    }

    public static void main(String[] args) {
        if (args == null || args.length < 1 || args.length > 5 || Arrays.asList(args).contains(null)) {
            System.err.println("Usage: HelloUDPClient <host> <port> <prefix> <threads> <requests>");
            return;
        }
        try {
            new HelloUDPClient().run(args[0],
                    Integer.parseInt(args[1]),
                    args[2],
                    Integer.parseInt(args[3]),
                    Integer.parseInt(args[4]));
        } catch (NumberFormatException e) {
            System.err.println("Wrong format of input data " + e.getMessage());
        }
    }
}
