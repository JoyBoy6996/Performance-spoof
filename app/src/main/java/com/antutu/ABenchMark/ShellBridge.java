package com.antutu.ABenchMark;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Executes privileged shell commands through whichever backend is available.
 *
 * <p>Backends, in priority order:
 * <ol>
 *   <li>{@code su} - full root (Magisk / KernelSU / APatch).</li>
 *   <li>Shizuku - ADB shell identity, no root required.</li>
 *   <li>None - the app degrades to manual guidance.</li>
 * </ol>
 *
 * <p>Every call is best-effort: HyperOS surfaces differ between builds, so a
 * failing command is logged and skipped rather than propagated to the caller.
 */
public final class ShellBridge {

    private static final String TAG = "PerfSpoof/Shell";
    private static final long TIMEOUT_SECONDS = 20L;
    private static final long PROBE_TIMEOUT_SECONDS = 5L;

    /** Identifies which privileged backend produced a result. */
    public enum Backend { ROOT, SHIZUKU, NONE }

    private static volatile Backend cachedBackend = Backend.NONE;
    private static volatile boolean backendResolved = false;

    private ShellBridge() { }

    /** Returns the privileged backend that is usable right now. */
    public static Backend backend(Context context) {
        if (backendResolved) {
            return cachedBackend;
        }
        synchronized (ShellBridge.class) {
            if (backendResolved) {
                return cachedBackend;
            }
            // Shizuku is checked first on purpose. Its probe is a binder
            // round trip that never prompts, while `su` can block on a
            // pending Magisk authorisation dialog. Probing root first would
            // hang the caller and never fall through to a working Shizuku.
            if (ShizukuBridge.isUsable(context)) {
                cachedBackend = Backend.SHIZUKU;
            } else if (isRootAvailable()) {
                cachedBackend = Backend.ROOT;
            } else {
                cachedBackend = Backend.NONE;
            }
            backendResolved = true;
            Log.i(TAG, "privileged backend = " + cachedBackend);
        }
        return cachedBackend;
    }

    /**
     * Asks the Shizuku manager for the shell identity.
     *
     * <p>Public entry point for the UI layer, which lives in a different
     * package and cannot reach the package private bridge class.
     */
    public static void requestShizukuPermission(Context context) {
        ShizukuBridge.requestPermission(context);
    }

    /** Forces the next {@link #backend(Context)} call to probe again. */
    public static void invalidate() {
        synchronized (ShellBridge.class) {
            backendResolved = false;
            cachedBackend = Backend.NONE;
        }
    }

    /**
     * Returns the backend that was already resolved, without probing.
     *
     * <p>Safe to call on the main thread: the probe shells out to {@code su}
     * and must never run on the UI thread.
     */
    public static Backend resolvedBackend() {
        return cachedBackend;
    }

    /**
     * Runs a shell command and returns its combined stdout/stderr.
     *
     * @return the command output, or an empty string when the command failed
     *         or no privileged backend is available.
     */
    public static String exec(Context context, String command) {
        Backend backend = backend(context);
        if (backend == Backend.ROOT) {
            String output = runRoot(command);
            return output == null ? "" : output;
        }
        if (backend == Backend.SHIZUKU) {
            String output = ShizukuBridge.exec(command);
            return output == null ? "" : output;
        }
        return "";
    }

    /** Runs a command and reports whether it produced output. */
    public static boolean succeeds(Context context, String command) {
        String output = exec(context, command);
        if (output.isEmpty()) {
            // A command may legitimately produce no stdout, so probe the exit
            // signal instead of treating empty output as a hard failure.
            return backend(context) != Backend.NONE;
        }
        return !output.toLowerCase(java.util.Locale.ROOT)
                .contains("exception")
                && !output.toLowerCase(java.util.Locale.ROOT).contains("permission denial")
                && !output.contains("not found");
    }

    /**
     * Probes for a usable root shell.
     *
     * <p>Never call this from the main thread: {@code su} may show a prompt.
     */
    public static boolean isRootAvailable() {
        // A short bound: this is only a capability probe, and a pending su
        // authorisation dialog must not stall the caller for long.
        String probe = runRoot("id", PROBE_TIMEOUT_SECONDS);
        return probe != null && probe.contains("uid=0");
    }

    private static String runRoot(String command) {
        return runRoot(command, TIMEOUT_SECONDS);
    }

    private static String runRoot(String command, long timeoutSeconds) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            // The process is awaited before its output is read. Reading first
            // would block until end of stream, which never arrives while
            // Magisk is still waiting for the user to approve the request.
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                Log.w(TAG, "root command timed out: " + command);
                return null;
            }
            return readAll(process);
        } catch (Throwable throwable) {
            Log.w(TAG, "root exec failed: " + throwable);
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static String readAll(Process process) {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "read failed: " + throwable);
        }
        return builder.toString();
    }

    /**
     * Thin wrapper around the Shizuku API. Kept in one place so the app can
     * be built and run on devices without Shizuku installed.
     */
    static final class ShizukuBridge {

        private static final int REQUEST_CODE = 0x5E2;

        private ShizukuBridge() { }

        static boolean isUsable(Context context) {
            try {
                if (!rikka.shizuku.Shizuku.pingBinder()) {
                    return false;
                }
                return rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            } catch (Throwable throwable) {
                Log.i(TAG, "shizuku unavailable: " + throwable);
                return false;
            }
        }

        static void requestPermission(Context context) {
            try {
                if (!rikka.shizuku.Shizuku.pingBinder()) {
                    return;
                }
                if (rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                rikka.shizuku.Shizuku.requestPermission(REQUEST_CODE);
            } catch (Throwable throwable) {
                Log.w(TAG, "shizuku request failed: " + throwable);
            }
        }

        static String exec(String command) {
            moe.shizuku.server.IRemoteProcess process = null;
            java.io.InputStream stdout = null;
            java.io.InputStream stderr = null;
            try {
                android.os.IBinder binder = rikka.shizuku.Shizuku.getBinder();
                if (binder == null) {
                    return null;
                }
                moe.shizuku.server.IShizukuService service =
                        moe.shizuku.server.IShizukuService.Stub.asInterface(binder);
                if (service == null) {
                    return null;
                }
                process = service.newProcess(
                        new String[] { "/system/bin/sh", "-c", command }, null, null);
                if (process == null) {
                    return null;
                }
                // The remote process mirrors java.lang.Process: stdout comes
                // back through getInputStream(), stderr through getErrorStream().
                stdout = new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                        process.getInputStream());
                stderr = new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                        process.getErrorStream());

                // stderr is drained on its own thread. If the remote process
                // fills the stderr pipe while stdout is being read, the
                // process would block and the stdout read would never end.
                final StringBuilder stderrBuffer = new StringBuilder();
                final java.io.InputStream stderrStream = stderr;
                Thread stderrReader = new Thread(
                        () -> stderrBuffer.append(drain(stderrStream)), "perf-spoof-stderr");
                stderrReader.setDaemon(true);
                stderrReader.start();

                StringBuilder builder = new StringBuilder();
                builder.append(drain(stdout));
                stderrReader.join(1000L);
                builder.append(stderrBuffer);
                return builder.toString();
            } catch (Throwable throwable) {
                Log.w(TAG, "shizuku exec failed: " + throwable);
                return null;
            } finally {
                closeQuietly(stdout);
                closeQuietly(stderr);
                if (process != null) {
                    try {
                        process.destroy();
                    } catch (Throwable ignored) {
                        // Nothing useful to do while tearing down.
                    }
                }
            }
        }

        private static String drain(java.io.InputStream stream) {
            if (stream == null) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
            } catch (Throwable throwable) {
                // A truncated read is fine, the caller only needs the prefix.
            }
            return builder.toString();
        }

        private static void closeQuietly(java.io.Closeable closeable) {
            if (closeable == null) {
                return;
            }
            try {
                closeable.close();
            } catch (Throwable ignored) {
                // Nothing useful to do while tearing down.
            }
        }
    }

    /** Convenience for the settings value lookups used by the guard. */
    public static String settingsGet(Context context, String namespace, String key) {
        String output = exec(context, "settings get " + namespace + " " + key);
        if (output == null) {
            return null;
        }
        String trimmed = output.trim();
        if (trimmed.isEmpty() || "null".equals(trimmed)) {
            return null;
        }
        // settings get prints one value per line; keep the first meaningful one.
        int newline = trimmed.indexOf('\n');
        if (newline > 0) {
            trimmed = trimmed.substring(0, newline).trim();
        }
        return trimmed;
    }

    /**
     * Runs a command using the already resolved backend, without probing.
     *
     * <p>Safe to call on the main thread: no {@code su} probe is performed,
     * so the call returns immediately when no backend is available.
     */
    public static String execResolved(Context context, String command) {
        Backend backend = cachedBackend;
        if (backend == Backend.ROOT) {
            String output = runRoot(command);
            return output == null ? "" : output;
        }
        if (backend == Backend.SHIZUKU) {
            String output = ShizukuBridge.exec(command);
            return output == null ? "" : output;
        }
        return "";
    }

    /** Applies a list of shell commands, ignoring individual failures. */
    public static void execAll(Context context, List<String> commands) {
        for (String command : commands) {
            String output = exec(context, command);
            if (!output.isEmpty()) {
                Log.i(TAG, "> " + command + " :: " + output.trim());
            }
        }
    }
}
