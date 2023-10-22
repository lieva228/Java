package info.kgeorgiy.ja.shpraidun.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;

public class HelloUDPNonblockingClient implements HelloClient {

    private static final int TIMEOUT = 100;

    private static class Attachment {
        int thread;
        int requests = 1;

        public Attachment(int thread) {
            this.thread = thread + 1;
        }
    }

    private ByteBuffer buffer;

    @Override
    public void run(String host, int port, String prefix, int threads, int requests) {
        SocketAddress address = new InetSocketAddress(host, port);
        try {
            Selector selector = Selector.open();
            for (int i = 0; i < threads; i++) {
                DatagramChannel channel = DatagramChannel.open();
                channel.configureBlocking(false);
                channel.connect(address);
                channel.register(selector, SelectionKey.OP_WRITE, new Attachment(i));
            }

            while (!Thread.interrupted() && !selector.keys().isEmpty()) {
                selector.select(TIMEOUT);
                if (selector.selectedKeys().isEmpty()) {
                    for (final SelectionKey key : selector.keys()) {
                        key.interestOps(SelectionKey.OP_WRITE);
                    }
                }
                final Iterator<SelectionKey> i = selector.selectedKeys().iterator();
                while (i.hasNext()) {
                    final SelectionKey key = i.next();
                    Attachment attachment = (Attachment) key.attachment();
                    String request = prefix + attachment.thread + "_" + attachment.requests;
                     if (key.isWritable()) {
                        send(request, address, key);
                    } else if (key.isReadable()) {
                        receive(key, requests, request);
                    }
                    i.remove();
                }
            }
        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
        }
    }

    private void send(String request, SocketAddress address, SelectionKey key) throws IOException {
        final DatagramChannel channel = (DatagramChannel) key.channel();
        if (buffer == null) {
            buffer = ByteBuffer.allocate(channel.socket().getReceiveBufferSize());
        }
        buffer.clear();
        buffer.put(request.getBytes());
        buffer.flip();
        channel.send(buffer, address);
        key.interestOps(SelectionKey.OP_READ);
    }

    private void receive(SelectionKey key, int requests, String request) throws IOException {
        final DatagramChannel channel = (DatagramChannel) key.channel();
        Attachment attachment = (Attachment) key.attachment();
        if (buffer == null) {
            buffer = ByteBuffer.allocate(channel.socket().getReceiveBufferSize());
        }
        buffer.clear();
        channel.receive(buffer);
        buffer.flip();
        String response = StandardCharsets.UTF_8.decode(buffer).toString();
        if (response.contains(request)) {
            System.out.println(response);
            attachment.requests++;
            key.interestOps(SelectionKey.OP_WRITE);
            if (attachment.requests == requests + 1) {
                channel.close();
                key.cancel();
            }
        }
    }

    public static void main(String[] args) {
        if (args == null || args.length < 1 || args.length > 5 || Arrays.asList(args).contains(null)) {
            System.err.println("Usage: HelloUDPNonblockingClient <host> <port> <prefix> <threads> <requests>");
            return;
        }
        try {
            new HelloUDPNonblockingClient().run(args[0],
                    Integer.parseInt(args[1]),
                    args[2],
                    Integer.parseInt(args[3]),
                    Integer.parseInt(args[4]));
        } catch (NumberFormatException e) {
            System.err.println("Wrong format of input data " + e.getMessage());
        }
    }
}