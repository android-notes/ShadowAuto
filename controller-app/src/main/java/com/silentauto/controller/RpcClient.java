package com.silentauto.controller;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

final class RpcClient {
    interface Listener {
        void onLine(String line);

        void onClosed(Exception error);
    }

    private final String host;
    private final int port;
    private final Listener listener;
    private final AtomicInteger ids = new AtomicInteger(1);

    private Socket socket;
    private BufferedWriter writer;

    RpcClient(String host, int port, Listener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    synchronized void send(String method, JSONObject params) throws Exception {
        connect();
        JSONObject request = new JSONObject();
        request.put("jsonrpc", "2.0");
        request.put("id", ids.getAndIncrement());
        request.put("method", method);
        request.put("params", params);
        writer.write(request.toString());
        writer.write('\n');
        writer.flush();
    }

    private synchronized void connect() throws Exception {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return;
        }
        socket = new Socket(host, port);
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        new Thread(() -> readLoop(reader), "rpc-reader").start();
    }

    private void readLoop(BufferedReader reader) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                listener.onLine(line);
            }
        } catch (Exception e) {
            listener.onClosed(e);
        } finally {
            clearConnection();
        }
    }

    private synchronized void clearConnection() {
        socket = null;
        writer = null;
    }
}
