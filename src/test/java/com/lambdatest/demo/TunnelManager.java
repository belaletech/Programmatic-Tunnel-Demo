package com.lambdatest.demo;

import com.lambdatest.tunnel.Tunnel;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Starts/stops a LambdaTest tunnel programmatically.
 *
 * TUNNEL_MODE=sdk    -> uses the lambdatest-tunnel Java SDK (com.lambdatest.tunnel.Tunnel)
 * TUNNEL_MODE=binary -> launches the LT CLI binary via ProcessBuilder and waits for it to be ready
 */
public class TunnelManager {

    private static final String READY_TEXT =
            System.getenv().getOrDefault("LT_TUNNEL_READY_TEXT", "You can start testing now");
    private static final long START_TIMEOUT_MS = 120_000;

    private final String mode;
    private Tunnel sdkTunnel;
    private Process binaryProcess;

    public TunnelManager(String mode) {
        this.mode = mode == null ? "sdk" : mode.trim().toLowerCase();
    }

    public void start(String user, String key, String tunnelName) throws Exception {
        System.out.println("[Tunnel] Starting in '" + mode + "' mode, name=" + tunnelName);
        if ("binary".equals(mode)) {
            startBinary(user, key, tunnelName);
        } else {
            startSdk(user, key, tunnelName);
        }
        System.out.println("[Tunnel] Started. Check Tunnels page on the dashboard for: " + tunnelName);
    }

    public void stop() {
        try {
            if (sdkTunnel != null) {
                sdkTunnel.stop();
                System.out.println("[Tunnel] SDK tunnel stopped");
            }
        } catch (Exception e) {
            System.out.println("[Tunnel] Error stopping SDK tunnel: " + e.getMessage());
        }

        if (binaryProcess != null && binaryProcess.isAlive()) {
            binaryProcess.destroy();
            try {
                if (!binaryProcess.waitFor(15, TimeUnit.SECONDS)) {
                    binaryProcess.destroyForcibly();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            System.out.println("[Tunnel] Binary tunnel stopped");
        }
    }

    // ---------- SDK mode ----------

    private void startSdk(String user, String key, String tunnelName) throws Exception {
        sdkTunnel = new Tunnel();
        HashMap<String, String> options = new HashMap<>();
        options.put("user", user);
        options.put("key", key);
        options.put("tunnelName", tunnelName);
        options.put("verbose", "true");
        sdkTunnel.start(options);
    }

    // ---------- Binary mode ----------

    private void startBinary(String user, String key, String tunnelName) throws Exception {
//        String binaryPath = System.getenv("LT_BINARY");
        String binaryPath = System.getenv().getOrDefault("LT_BINARY", System.getProperty("user.dir") + "/LT");
        if (binaryPath == null || !new File(binaryPath).canExecute()) {
            throw new IllegalStateException(
                    "Set LT_BINARY to the full path of the LT tunnel binary (and chmod +x it). Current: " + binaryPath);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(binaryPath);
        cmd.add("--user");
        cmd.add(user);
        cmd.add("--key");
        cmd.add(key);
        cmd.add("--tunnelName");
        cmd.add(tunnelName);
        cmd.add("--verbose");

        // Optional extra flags, e.g. LT_TUNNEL_EXTRA_ARGS="--proxy-host 10.0.0.1 --proxy-port 8080"
        String extra = System.getenv("LT_TUNNEL_EXTRA_ARGS");
        if (extra != null && !extra.isBlank()) {
            for (String a : extra.trim().split("\\s+")) cmd.add(a);
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        binaryProcess = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(binaryProcess.getInputStream()));
        long deadline = System.currentTimeMillis() + START_TIMEOUT_MS;
        boolean ready = false;

        // Read on a separate thread so the timeout works even if the binary goes quiet
        final boolean[] readyFlag = {false};
        Thread pump = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[LT] " + line);
                    if (line.contains(READY_TEXT)) {
                        synchronized (readyFlag) {
                            readyFlag[0] = true;
                            readyFlag.notifyAll();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }, "lt-tunnel-output");
        pump.setDaemon(true);
        pump.start();

        synchronized (readyFlag) {
            while (!readyFlag[0] && binaryProcess.isAlive() && System.currentTimeMillis() < deadline) {
                readyFlag.wait(1000);
            }
            ready = readyFlag[0];
        }

        if (!ready) {
            stop();
            throw new IllegalStateException("Tunnel did not become ready within "
                    + (START_TIMEOUT_MS / 1000) + "s. Check the [LT] output above.");
        }
    }
}
