package info.kgeorgiy.ja.shpraidun.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class HelloUDPNonblockingServer implements HelloServer {

    private static final int TIMEOUT = 100;

    private Selector selector;
    private DatagramChannel channel;
    private ExecutorService service;
    private Queue<Map.Entry<ByteBuffer, SocketAddress>> responses;
    private ByteBuffer buffer;

    @Override
    public void start(int port, int threads) {
        try {
            selector = Selector.open();
            channel = DatagramChannel.open();
            channel.configureBlocking(false);
            channel.bind(new InetSocketAddress(port));
            channel.register(selector, SelectionKey.OP_READ);
            buffer = ByteBuffer.allocate(channel.socket().getReceiveBufferSize());
            responses = new ConcurrentLinkedDeque<>();
        } catch (IOException e) {
            System.err.println("Can't start" + e);
        }

        service = new ThreadPoolExecutor(threads + 1, threads + 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(threads + 100));
        service.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    selector.select(TIMEOUT);
                    Iterator<SelectionKey> i = selector.selectedKeys().iterator();
                    if (i.hasNext()) {
                        final SelectionKey key = i.next();
                        if (key.isReadable()) {
                            receive(key);
                        } else {
                            send(key);
                        }
                        i.remove();
                    }
                } catch (IOException e) {
                    System.err.println("IOException: " + e.getMessage());
                }
            }
        });
    }

    private void receive(SelectionKey key) throws IOException {
        buffer.clear();
        SocketAddress address = channel.receive(buffer);
        buffer.flip();
        String request = StandardCharsets.UTF_8.decode(buffer).toString();
        service.execute(() -> {
            responses.add(Map.entry(ByteBuffer.wrap(
                    ("Hello, " + request).getBytes(StandardCharsets.UTF_8))
                    , address));
            key.interestOpsOr(SelectionKey.OP_WRITE);
            selector.wakeup();
        });
    }

    private void send(SelectionKey key) throws IOException {
        if (!responses.isEmpty()) {
            Map.Entry<ByteBuffer, SocketAddress> response = responses.remove();
            channel.send(response.getKey(), response.getValue());
            key.interestOpsOr(SelectionKey.OP_READ);
        }
    }


    @Override
    public void close() {
        service.shutdown();
        try {
            channel.close();
            selector.close();
        } catch (IOException e) {
            System.err.println("Can't close " + e);
        }
        service.close();
    }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length < 1 || args.length > 2 || Arrays.asList(args).contains(null)) {
            System.err.println("Usage: HelloUDPNonblockingServer <port> <threads>");
            return;
        }
        try (HelloServer hello = new HelloUDPNonblockingServer();
             Scanner sc = new Scanner(System.in)) {
            hello.start(
                    Integer.parseInt(args[0]),
                    Integer.parseInt(args[1]));
            sc.next();
        } catch (NumberFormatException e) {
            System.err.println("Wrong format of input data " + e.getMessage());
        }
    }
}
