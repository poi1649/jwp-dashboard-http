package org.apache.catalina.connector;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.catalina.servlet.ServletManger;
import org.apache.coyote.http11.Http11Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Connector implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Connector.class);

    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_ACCEPT_COUNT = 100;
    private static final int DEFAULT_THREAD_MAX_NUMBER = 250;
    private static final int CORE_POOL_SIZE = 10;
    private static final long IDLE_THREAD_ALIVE_TIME = 60L;
    private static final int SOCKET_BUFFER_SIZE = 10;

    private final ServerSocket serverSocket;
    private final ServletManger servletManger;
    private final ThreadPoolExecutor threadPool;
    private boolean stopped;

    public Connector(ServletManger servletManger) {
        this(
                DEFAULT_PORT,
                DEFAULT_ACCEPT_COUNT,
                servletManger,
                DEFAULT_THREAD_MAX_NUMBER
        );
    }

    public Connector(
            final int port,
            final int acceptCount,
            final ServletManger servletManger,
            final int threadMaxNumber
    ) {
        this.servletManger = servletManger;
        this.serverSocket = createServerSocket(port, SOCKET_BUFFER_SIZE);
        this.stopped = false;
        this.threadPool = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                threadMaxNumber,
                IDLE_THREAD_ALIVE_TIME,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(acceptCount)
        );
    }

    private ServerSocket createServerSocket(final int port, final int acceptCount) {
        try {
            final int checkedPort = checkPort(port);
            final int checkedAcceptCount = checkAcceptCount(acceptCount);
            return new ServerSocket(checkedPort, checkedAcceptCount);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void start() {
        var thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
        stopped = false;
        log.info("Web Application Server started {} port.", serverSocket.getLocalPort());
    }

    @Override
    public void run() {
        // 클라이언트가 연결될때까지 대기한다.
        while (!stopped) {
            connect();
        }
    }

    private void connect() {
        try {
            process(serverSocket.accept());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void process(final Socket connection) {
        if (connection == null) {
            return;
        }
        var processor = new Http11Processor(connection, servletManger);
        threadPool.execute(processor);
    }

    public void stop() {
        stopped = true;
        try {
            serverSocket.close();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private int checkPort(final int port) {
        final var MIN_PORT = 1;
        final var MAX_PORT = 65535;

        if (port < MIN_PORT || MAX_PORT < port) {
            return DEFAULT_PORT;
        }
        return port;
    }

    private int checkAcceptCount(final int acceptCount) {
        return Math.max(acceptCount, DEFAULT_ACCEPT_COUNT);
    }
}
