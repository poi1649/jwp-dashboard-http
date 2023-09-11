package org.apache.catalina.connector;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.catalina.servlet.ServletManger;
import org.apache.coyote.http11.Http11Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Connector implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Connector.class);

    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_ACCEPT_COUNT = 100;
    private static final int DEFAULT_THREAD_MAX_NUMBER = 250;

    private final ServerSocket serverSocket;
    private final ServletManger servletManger;
    private final ExecutorService threadPool;
    private final int maxThreadNumber;
    private final AtomicInteger workingThreadCount = new AtomicInteger(0);
    private boolean stopped;
    private final Object lock = new Object();

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
            final int maxThreadNumber
    ) {
        this.servletManger = servletManger;
        this.serverSocket = createServerSocket(port, acceptCount);
        this.stopped = false;
        this.threadPool = Executors.newFixedThreadPool(maxThreadNumber);
        this.maxThreadNumber = maxThreadNumber;
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
        while (!stopped) {
            connect();
        }
    }

    private void connect() {
        try {
            waitUntilThreadAvailable();
            process(serverSocket.accept());
        } catch (IOException | InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void waitUntilThreadAvailable() throws InterruptedException {
        if (workingThreadCount.get() >= maxThreadNumber) {
            synchronized (lock) {
                lock.wait();
            }
        }
    }

    private void process(final Socket connection) {
        if (connection == null) {
            return;
        }
        workingThreadCount.incrementAndGet();
        final var processor = new Http11Processor(connection, servletManger);
        final var task = CompletableFuture.runAsync(processor, threadPool);
        decrementWorkingThreadCountIfComplete(task);
    }

    private void decrementWorkingThreadCountIfComplete(CompletableFuture<Void> task) {
        task.whenCompleteAsync((result, throwable) -> {
                    workingThreadCount.decrementAndGet();
                    releaseLockIfThreadAvailable();
                }
        );
    }

    private synchronized void releaseLockIfThreadAvailable() {
        if (workingThreadCount.get() < maxThreadNumber) {
            synchronized (lock) {
                lock.notify();
            }
        }
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
