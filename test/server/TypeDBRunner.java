/*
 * Copyright (C) 2021 Vaticle
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.vaticle.typedb.common.test.server;

import com.vaticle.typedb.common.test.Runner;
import org.zeroturnaround.exec.StartedProcess;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public abstract class TypeDBRunner extends Runner {

    private static final int SERVER_STARTUP_TIMEOUT_MILLIS = 30000;
    private static final int SERVER_ALIVE_POLL_INTERVAL_MILLIS = 500;
    private static final int SERVER_ALIVE_POLL_MAX_RETRIES = SERVER_STARTUP_TIMEOUT_MILLIS / SERVER_ALIVE_POLL_INTERVAL_MILLIS;

    protected final Path dataDir;
    protected final Path logsDir;
    private StartedProcess serverProcess;

    public TypeDBRunner() throws InterruptedException, TimeoutException, IOException {
        super();
        this.dataDir = rootPath.resolve("server").resolve("data");
        this.logsDir = rootPath.resolve("server").resolve("logs");
    }

    protected abstract List<String> command();

    private String displayName() {
        return name() + "(" + address() + ")";
    }

    protected String host() {
        return "127.0.0.1";
    }

    protected abstract int port();

    public String address() {
        return host() + ":" + port();
    }

    public void start() {
        try {
            assertFalse(isPortOpen(host(), port()));
            System.out.println(displayName() + ": starting... ");
            System.out.println(displayName() + ": database server is located at " + rootPath.toAbsolutePath().toString());
            System.out.println(displayName() + ": database directory is located at " + dataDir.toAbsolutePath());
            System.out.println(displayName() + ": command = " + command());
            serverProcess = executor.command(command()).start();
            boolean started = checkServerStarted().await(SERVER_STARTUP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (!started) {
                throw new RuntimeException(
                        displayName() + ": process exited with code '" + serverProcess.getProcess().exitValue() +"'. " +
                                "stdout = '" + read(serverProcess.getProcess().getInputStream()) + "'. " +
                                "stderr = '" + read(serverProcess.getProcess().getErrorStream()) + "'"
                );
            } else {
                System.out.println(displayName() + " database server started");
            }
        } catch (Throwable e) {
            printLogs();
            throw new RuntimeException(e);
        }
    }

    private CountDownLatch checkServerStarted() {
        CountDownLatch latch = new CountDownLatch(1);
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            int retryNumber = 0;

            @Override
            public void run() {
                retryNumber++;
                if (retryNumber % 4 == 0) {
                    System.out.println(String.format("%s: waiting for server to start (%ds)...",
                                                     displayName(), retryNumber * SERVER_ALIVE_POLL_INTERVAL_MILLIS / 1000));
                }
                if (isPortOpen(host(), port())) {
                    latch.countDown();
                    timer.cancel();
                }
                if (retryNumber > SERVER_ALIVE_POLL_MAX_RETRIES) timer.cancel();
            }
        }, 0, 500);
        return latch;
    }

    protected static boolean isPortOpen(String host, int port) {
        try {
            Socket s = new Socket(host, port);
            s.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void stop() {
        if (serverProcess != null) {
            try {
                System.out.println(displayName() + ": stopping...");
                serverProcess.getProcess().destroyForcibly();
                System.out.println(displayName() + ": stopped.");
            } catch (Exception e) {
                printLogs();
                throw e;
            }
        }
    }

    private void printLogs() {
        System.out.println("================");
        System.out.println(displayName() + " Logs:");
        System.out.println("================");
        Path logPath = logsDir.resolve("typedb.log").toAbsolutePath();
        try {
            executor.command("cat", logPath.toString()).execute();
        } catch (IOException | InterruptedException | TimeoutException e) {
            System.out.println(displayName() + ": unable to print '" + logPath + "'");
            e.printStackTrace();
        }
    }

    @Override
    protected File distributionArchive() {
        String[] args = System.getProperty("sun.java.command").split(" ");
        assert args.length > 1;
        return new File(args[1]);
    }

    private static String read(InputStream inputStream) {
        return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));
    }
}
