package info.kgeorgiy.ja.shpraidun.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.*;

public class HelloUDPServer implements HelloServer {
    private ExecutorService service;
    private DatagramSocket socket;

    @Override
    public void start(int port, int threads) {
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            System.err.println("Can't create socket " + e);
        }
        service = new ThreadPoolExecutor(threads + 1, threads + 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(threads + 100));
        service.submit(() -> {
            while (!socket.isClosed() && !Thread.currentThread().isInterrupted()) {
                try {
                    byte[] dataResponse = new byte[socket.getReceiveBufferSize()];
                    DatagramPacket responsePacket = new DatagramPacket(dataResponse, dataResponse.length);
                    socket.receive(responsePacket);
                    service.execute(() -> {
                        String request = "Hello, " + new String(
                                responsePacket.getData(),
                                responsePacket.getOffset(),
                                responsePacket.getLength());
                        byte[] requestData = request.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket requestPacket = new DatagramPacket(requestData,
                                requestData.length, responsePacket.getSocketAddress());
                        try {
                            socket.send(requestPacket);
                        } catch (IOException e) {
                            System.err.println("Can't send to client " + e);
                        }
                    });
                } catch (SocketException e) {
                    System.err.println("Error with socket " + e);
                } catch (IOException e) {
                    System.err.println("Can't receive from client " + e);
                }
            }
        });
    }

    @Override
    public void close() {
        //NOTE: завершение до shutdown
        service.shutdown();
        socket.close();
        service.close();
    }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length < 1 || args.length > 2 || Arrays.asList(args).contains(null)) {
            System.err.println("Usage: HelloUDPServer <port> <threads>");
            return;
        }
        try (HelloServer hello = new HelloUDPServer();
             Scanner sc = new Scanner(System.in)) {
            //NOTE: нет ожидания
            hello.start(
                    Integer.parseInt(args[0]),
                    Integer.parseInt(args[1]));
            sc.next();
        } catch (NumberFormatException e) {
            System.err.println("Wrong format of input data " + e.getMessage());
        }
    }
}
