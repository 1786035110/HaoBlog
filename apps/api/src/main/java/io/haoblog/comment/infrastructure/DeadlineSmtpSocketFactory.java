package io.haoblog.comment.infrastructure;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

/** 单封邮件最多占用套接字 15 秒；关闭真实连接，防止慢速 SMTP 持续占用调度线程。 */
public final class DeadlineSmtpSocketFactory extends SocketFactory {
    private static final Timer DEADLINES = new Timer("smtp-deadline", true);

    public static SocketFactory getDefault() { return new DeadlineSmtpSocketFactory(); }

    @Override
    public Socket createSocket() {
        return new Socket() {
            private final TimerTask deadline = new TimerTask() {
                @Override public void run() {
                    try { close(); } catch (IOException ignored) { }
                }
            };
            { DEADLINES.schedule(deadline, 15_000); }

            @Override public synchronized void close() throws IOException {
                deadline.cancel();
                DEADLINES.purge();
                super.close();
            }
        };
    }

    private Socket connect(InetSocketAddress remote, InetSocketAddress local) throws IOException {
        Socket socket = createSocket();
        try {
            if (local != null) socket.bind(local);
            socket.connect(remote, 5000);
            return socket;
        } catch (IOException | RuntimeException exception) {
            socket.close();
            throw exception;
        }
    }

    @Override public Socket createSocket(String host, int port) throws IOException {
        return connect(new InetSocketAddress(host, port), null);
    }
    @Override public Socket createSocket(InetAddress host, int port) throws IOException {
        return connect(new InetSocketAddress(host, port), null);
    }
    @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
        return connect(new InetSocketAddress(host, port), new InetSocketAddress(local, localPort));
    }
    @Override public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException {
        return connect(new InetSocketAddress(host, port), new InetSocketAddress(local, localPort));
    }
}
