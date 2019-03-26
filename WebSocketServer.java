package net.websocket;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.regex.*;
import javax.xml.bind.DatatypeConverter;

import parallel.actor.*;

public class WebSocketServer extends Actor {
    private int port;
    private PrintStream logger;
    private boolean running = false;
    private ServerSocket socket;
    private ExecutorService pool = Executors.newCachedThreadPool();
    private Map<String, WebSocketConnection> id2Connection;
    private Function<String, Integer> onOpen = id -> 0;
    private Function<String, Integer> onClose = id -> 0;
    private BiFunction<String, String, Integer> onText = (id, text) -> 0;
    private BiFunction<String, byte[], Integer> onBinary = (id, bin) -> 0;

    public WebSocketServer(int port, PrintStream logger) throws IOException {
        this.port = port;
        this.logger = logger;
        socket = new ServerSocket(port);
        id2Connection = new Hashtable<>();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    public WebSocketServer(int port) throws IOException {
        this(port, System.out);
    }

    public WebSocketServer() throws IOException {
        this(0, System.out);
    }

    @Override
    protected void executeMethod(Method m, Object[] arguments) {
        try {
            m.invoke(this, arguments);
        } catch (IllegalAccessException | InvocationTargetException e) {
            logError("Message executing error", e);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public void setOnOpen(Function<String, Integer> handler) {
        this.onOpen = handler;
    }

    public void setOnClose(Function<String, Integer> handler) {
        this.onClose = handler;
    }

    public void setOnText(BiFunction<String, String, Integer> handler) {
        this.onText = handler;
    }

    public void setOnBinary(BiFunction<String, byte[], Integer> handler) {
        this.onBinary = handler;
    }

    public void send(String id, String text) {
        Actor c = id2Connection.getOrDefault(id, null);
        if (c != null) c.post("send", text, true);
    }

    public void send(String id, byte[] bin) {
        Actor c = id2Connection.getOrDefault(id, null);
        if (c != null) c.post("send", bin, true);
    }

    public void sendAll(String text) {
        id2Connection.forEach((id, connection) -> connection.post("send", text, true));
    }

    public void sendAll(byte[] bin) {
        id2Connection.forEach((id, connection) -> connection.post("send", bin, true));
    }

    public void close(String id) {
        Actor c = id2Connection.getOrDefault(id, null);
        if (c != null) c.post("close");
    }

    public void start() {
        running = true;
        pool.submit(this::accept);
        pool.submit(this::listen);
        logInfo("WebSocket server started. Listening on port " + getPort() + " ... ");
    }

    public void stop() {
        running = false;
        id2Connection.forEach((id, connection) -> connection.post("close"));
        try {
            socket.close();
            logInfo("Server stopped");
        } catch (IOException e) {
            logError("Server stopping error", e);
        }
    }

    public void handleOpen(WebSocketConnection c) {
        id2Connection.put(c.id, c);
        int status = onOpen.apply(c.id);
        if (status != 0) logInfo("onOpen exited on status code: " + status);
    }

    public void handleClose(WebSocketConnection c) {
        int status = onClose.apply(c.id);
        id2Connection.remove(c.id);
        if (status != 0) logInfo("onClose exited on status code: " + status);
    }

    public void handleText(WebSocketConnection c, String text) {
        int status = onText.apply(c.id, text);
        if (status != 0) logInfo("onText exited on status code: " + status);
    }

    public void handleBinary(WebSocketConnection c, byte[] bin) {
        int status = onBinary.apply(c.id, bin);
        if (status != 0) logInfo("onBinary exited on status code: " + status);
    }

    public void handleInfo(WebSocketConnection c, String log) {
        logInfo("(" + c.id + ") " + log);
    }

    public void handleError(WebSocketConnection c, String log, Throwable e) {
        logError("(" + c.id + ") " + log, e);
    }

    private void accept() {
        try {
            new WebSocketConnection(this, socket.accept());
        } catch (IOException e) {
            logError("Client accepting error", e);
        } finally {
            if (running) pool.submit(this::accept);
        }
    }

    private void listen() {
        id2Connection.forEach((id, connection) -> {
            try {
                if (!connection.closed && connection.socket.getInputStream().available() > 0)
                    connection.post("checkInput");
            } catch (IOException e) {
                logError("Input checking error", e);
                connection.post("close");
            }
        });
        if (running) pool.submit(this::listen);
    }

    private void logInfo(String message) {
        logger.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")) + "] " + message);
    }

    private void logError(String message, Throwable e) {
        logInfo(message);
        e.printStackTrace(logger);
    }


    private class WebSocketConnection extends Actor {
        private boolean closed = false;
        private Socket socket;
        private String id;
        private Actor server;
        private InputStream inputStream;
        private OutputStream outputStream;
        private List<Frame> bufferedFrames = new ArrayList<>();

        WebSocketConnection(WebSocketServer server, Socket socket) {
            this.server = server;
            this.socket = socket;
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                id = handShake();
                if (id == null) {
                    logInfo("Unsuccessful handshake attempted: " + socket.getInetAddress().getHostAddress());
                    close();
                }
                logInfo("Connection established: " + socket.getInetAddress().getHostAddress());
                server.post("handleOpen", this);
            } catch (IOException e) {
                logError("Connection establishing error", e);
            }
        }

        @Override
        protected void executeMethod(Method m, Object[] arguments) {
            try {
                m.invoke(this, arguments);
            } catch (IllegalAccessException | InvocationTargetException e) {
                logError("Message executing error", e);
            }
        }

        private void checkInput() {
            if (closed) return;
            try {
                acceptMessage();
            } catch (IOException e) {
                logError("Input accepting error", e);
                if (!closed) close();
            }
        }

        private void send(String text, Boolean FIN) {
            if (closed) return;
            sendFrame(new Frame(FIN, (byte) 1, text.getBytes(StandardCharsets.UTF_8)));
        }

        private void send(byte[] data, Boolean FIN) {
            if (closed) return;
            sendFrame(new Frame(FIN, (byte) 2, data));
        }

        private void close() {
            if (closed) return;
            closed = true;
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                logError("Stream closing error", e);
            } finally {
                try {
                    if (socket != null) socket.close();
                    logInfo("Connection closed");
                } catch (IOException e) {
                    logError("Connection closing error", e);
                }
            }
            server.post("handleClose", this);
        }

        private void logInfo(String log) {
            server.post("handleInfo", this, log);
        }

        private void logError(String log, Exception e) {
            server.post("handleError", this, log, e);
        }

        private void acceptMessage() throws IOException {
            Frame f = acceptFrame();
            if (f == null) throw new IOException();
            if (!f.MASK) throw new IOException();
            bufferedFrames.add(f);
            if (f.FIN) {
                byte[] data = concatenateFrames(bufferedFrames);
                switch (f.opcode) {
                    case (byte) 0x1:
                        logInfo("Text received");
                        server.post("handleText", this, new String(data, StandardCharsets.UTF_8));
                        break;
                    case (byte) 0x2:
                        logInfo("Binary received");
                        server.post("handleBinary", this, data);
                        break;
                    case (byte) 0x8:
                        logInfo("Closing request received");
                        sendFrame(new Frame(true, (byte) 0x8, f.bData));
                        if (!closed) close();
                        break;
                    case (byte) 0x9:
                        logInfo("Ping received");
                        sendFrame(new Frame(true, (byte) 0xa, f.bData));
                        break;
                    case (byte) 0xa:
                        logInfo("Pong received");
                        break;
                }
                bufferedFrames = new ArrayList<>();
            }
        }

        private byte[] concatenateFrames(List<Frame> frames) {
            int length = 0, i = 0;
            for (Frame f : frames) length += f.length;
            byte[] data = new byte[length];
            for (Frame f : frames) {
                System.arraycopy(f.bData, 0, data, i, f.bData.length);
                i += f.bData.length;
            }
            return data;
        }

        private Frame acceptFrame() throws IOException {
            Frame f = new Frame();
            // Accept head
            f.bHead = (byte) inputStream.read();
            f.FIN = (f.bHead >> 7 & (byte) 1) == 1;
            f.opcode = (byte) (f.bHead & (byte) 0xf);
            // Accept length
            byte bLen = (byte) inputStream.read();
            byte[] bLenX = new byte[0];
            f.length = (bLen & (byte) 127);
            if (f.length == 126) {
                bLenX = new byte[2];
                for (int i = 0; i < 2; i ++) bLenX[i] = (byte) inputStream.read();
                f.length = (((int) bLenX[0]) << 8) + (int) bLenX[1];
            } else if (f.length == 127) {
                bLenX = new byte[8];
                for (int i = 0; i < 8; i ++) bLenX[i] = (byte) inputStream.read();
                f.length = 0;
                for (int i = 0; i < 8; i++)
                    f.length += ((int) bLenX[7 - i]) << (i * 8);
            }
            f.bLen = new byte[1 + bLenX.length];
            f.bLen[0] = bLen;
            System.arraycopy(bLenX, 0, f.bLen, 1, bLenX.length);
            f.MASK = (f.bLen[0] >> 7 & (byte) 1) == 1;
            f.bMasks = new byte[4];
            f.bData = new byte[f.length];
            // Accept masks & data
            for (int i = 0; i < 4; i ++) f.bMasks[i] = (byte) inputStream.read();
            for (int i = 0; i < f.length; i ++) f.bData[i] = (byte) inputStream.read();
            // Unmask data
            for (int i = 0; i < f.length; i ++) f.bData[i] = (byte) (f.bData[i] ^ f.bMasks[i % 4]);

            logInfo("Frame received: " + f);
            return f;
        }

        private void sendFrame(Frame f) {
            try {
                byte[] encoded = new byte[1 + f.bLen.length + f.bData.length];
                encoded[0] = f.bHead;
                System.arraycopy(f.bLen, 0, encoded, 1, f.bLen.length);
                System.arraycopy(f.bData, 0, encoded, 1 + f.bLen.length, f.bData.length);
                outputStream.write(encoded);
                outputStream.flush();
                logInfo("Frame sent: " + f);
            } catch (IOException e) {
                logError("Frame sending error: " + f, e);
            }
        }

        private String handShake() throws IOException {
            String data = new Scanner(inputStream, StandardCharsets.UTF_8).useDelimiter("\\r\\n\\r\\n").next();
            // System.out.println(data);
            Matcher get = Pattern.compile("^GET").matcher(data);
            if (get.find()) {
                Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
                if (match.find()) {
                    try {
                        String id = DatatypeConverter.printBase64Binary(
                                MessageDigest.getInstance("SHA-1")
                                        .digest((match.group(1) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                                .getBytes(StandardCharsets.UTF_8)));
                        byte[] response = (
                                "HTTP/1.1 101 Switching Protocols\r\n"
                                        + "Connection: Upgrade\r\n"
                                        + "Upgrade: websocket\r\n"
                                        + "Sec-WebSocket-Accept: " + id + "\r\n\r\n"
                        ).getBytes(StandardCharsets.UTF_8);
                        outputStream.write(response, 0, response.length);
                        return id;
                    } catch (NoSuchAlgorithmException e) {
                        logError("NoSuchAlgorithmException", e);
                    }
                }
            }
            byte[] response = (
                    "HTTP/1.1 400 Bad Request\r\n"
                            + "Date: " + ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME) + "\r\n\r\n"
            ).getBytes(StandardCharsets.UTF_8);
            outputStream.write(response, 0, response.length);
            return null;
        }

        private class Frame {
            boolean FIN, MASK;
            byte opcode;
            int length;
            byte bHead;
            byte[] bLen, bMasks, bData;

            Frame() {}

            Frame(boolean f, byte op, byte[] d) {
                FIN = f;
                MASK = false;
                opcode = op;
                length = d.length;
                bData = d;
                bHead = (byte) (opcode + (FIN ? 0b1000_0000 : 0));
                if (length <= 125) {
                    bLen = new byte[1];
                    bLen[0] = (byte) length;
                } else if (length <= 65535) {
                    bLen = new byte[3];
                    bLen[0] = (byte) 126;
                    bLen[1] = (byte) ((length >> 8) & (byte) 255);
                    bLen[2] = (byte) (length & (byte) 255);
                } else {
                    bLen = new byte[9];
                    bLen[0] = (byte) 127;
                    for (int i = 0; i < 8; i++)
                        bLen[8 - i] = (byte) ((length >> (i * 8)) & (byte) 255);
                }
            }

            @Override
            public String toString() {
                return "[FIN: " + FIN + ", opcode: " + opcode + ", MASK: " + MASK + ", length: " + length + "]";
            }
        }
    }
}
