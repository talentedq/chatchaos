package ru.chatchaos;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Анонимное чтение чата Twitch через IRC (порт 6697, SSL).
 * Никаких токенов и паролей не нужно: используется гостевой ник justinfanNNNN.
 */
public final class TwitchChat {
    public record Msg(String user, String text) {}

    private final Queue<Msg> inbox = new ConcurrentLinkedQueue<>();
    private volatile boolean running;
    private volatile String channel = "";
    private volatile Socket socket;
    private Thread thread;
    public volatile String status = "не подключён";

    public synchronized void connect(String channel) {
        disconnect();
        this.channel = channel;
        running = true;
        status = "подключение...";
        thread = new Thread(this::loop, "ChatChaos-Twitch");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void disconnect() {
        running = false;
        Socket s = socket;
        if (s != null) {
            try { s.close(); } catch (IOException ignored) {}
        }
        if (thread != null) thread.interrupt();
        thread = null;
        status = "не подключён";
    }

    public Msg poll() { return inbox.poll(); }

    /** Для тестов: притвориться, что зритель написал сообщение. */
    public void inject(String user, String text) { inbox.add(new Msg(user, text)); }

    public boolean isRunning() { return running; }

    private void loop() {
        int backoff = 2;
        while (running) {
            try (SSLSocket s = (SSLSocket) SSLSocketFactory.getDefault().createSocket("irc.chat.twitch.tv", 6697)) {
                socket = s;
                s.setSoTimeout(5 * 60 * 1000);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
                send(out, "PASS SCHMOOPIIE");
                send(out, "NICK justinfan" + (10000 + new Random().nextInt(80000)));
                send(out, "JOIN #" + channel);
                status = "подключение к #" + channel + "...";
                String line;
                while (running && (line = in.readLine()) != null) {
                    if (line.startsWith("PING")) {
                        send(out, "PONG :tmi.twitch.tv");
                    } else if (line.contains(" 001 ")) {
                        status = "подключён к #" + channel;
                        backoff = 2;
                    } else {
                        parse(line);
                    }
                }
            } catch (IOException e) {
                if (running) {
                    ChatChaos.LOG.warn("Twitch IRC: {}", e.toString());
                    status = "ошибка: " + e.getMessage();
                }
            }
            if (!running) break;
            status = "переподключение через " + backoff + " c";
            try {
                Thread.sleep(backoff * 1000L);
            } catch (InterruptedException e) {
                break;
            }
            backoff = Math.min(backoff * 2, 30);
        }
    }

    private synchronized void send(BufferedWriter out, String line) throws IOException {
        out.write(line + "\r\n");
        out.flush();
    }

    /** :nick!nick@nick.tmi.twitch.tv PRIVMSG #channel :текст */
    private void parse(String line) {
        int pm = line.indexOf(" PRIVMSG ");
        if (pm < 0 || !line.startsWith(":")) return;
        int bang = line.indexOf('!');
        if (bang < 1 || bang > pm) return;
        int colon = line.indexOf(" :", pm);
        if (colon < 0) return;
        String user = line.substring(1, bang);
        String text = line.substring(colon + 2).trim();
        if (inbox.size() < 2000) inbox.add(new Msg(user, text));
    }
}
