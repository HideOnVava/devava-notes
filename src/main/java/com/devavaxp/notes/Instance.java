package com.devavaxp.notes;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * One window per notes folder: two would both save the same notes, each over what the other wrote.
 * The first launch holds a lock and listens on a port of this computer only; a second launch finds
 * the lock taken, knocks on that port so that the first window comes to the front, and ends. Each
 * side says who it is first, so neither takes another program on that port for the app.
 */
final class Instance {

    /** How long a second launch waits for the first one to listen: it may be starting itself. */
    private static final long PATIENCE = 3_000;
    private static final byte[] REPLY = "Devava Notes\n".getBytes(StandardCharsets.UTF_8);

    /** The lock and the port this app holds while it runs; the system lets go of them when it ends. */
    private static final List<AutoCloseable> HELD = new ArrayList<>();

    private Instance() {
    }

    /** What the app keeps for this computer only: not with the notes (OneDrive may sync them), not where it is installed. */
    static Path local() {
        String appData = System.getenv("LOCALAPPDATA");
        return appData != null ? Path.of(appData, "devava XP Studios", "Devava Notes") : Path.of(System.getProperty("user.home"), ".devava-notes");
    }

    /**
     * Whether this launch is the one for the notes in {@code notes} (its lock kept in {@code dir}).
     * When another launch has them, it is told to show its window, which {@code show} does there
     * (on a thread of its own), and this one is not, unless that one cannot be reached.
     */
    static synchronized boolean claim(Path dir, Path notes, Runnable show) {
        String key = Integer.toHexString(notes.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT).hashCode());
        byte[] hello = ("Devava Notes " + key + "\n").getBytes(StandardCharsets.UTF_8);
        Path portFile = dir.resolve("abierta-" + key + ".port");
        try {
            Files.createDirectories(dir);
            FileChannel channel = FileChannel.open(dir.resolve("abierta-" + key + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException e) {   // taken in this same Java (the tests)
                lock = null;
            }
            if (lock == null) {
                channel.close();
                return !knock(portFile, hello);   // if the other one cannot be reached, open anyway
            }
            HELD.add(channel);
            ServerSocket server = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
            HELD.add(server);
            Vault.write(portFile, String.valueOf(server.getLocalPort()));   // whole, or not at all
            Thread listener = new Thread(() -> listen(server, hello, show), "Devava Notes: another launch");
            listener.setDaemon(true);
            listener.start();
            return true;
        } catch (IOException e) {
            return true;   // no lock or no port to be had (a folder it cannot write in): it opens, as before
        }
    }

    /** Lets go of the lock and the port, as the app ends: another launch may open at once. */
    static synchronized void release() {
        for (AutoCloseable held : HELD.reversed()) {
            try {
                held.close();
            } catch (Exception ignored) {
                // Closing anyway: the system lets go of it when the app ends.
            }
        }
        HELD.clear();
    }

    /** Each knock that says hello for these notes gets the reply, and shows the window. */
    private static void listen(ServerSocket server, byte[] hello, Runnable show) {
        while (!server.isClosed()) {
            boolean launch;
            try (Socket knock = server.accept()) {
                knock.setSoTimeout(1000);
                launch = Arrays.equals(knock.getInputStream().readNBytes(hello.length), hello);   // else another program
                if (launch) knock.getOutputStream().write(REPLY);
            } catch (IOException e) {
                launch = false;   // a knock that went wrong; if the port closed, the loop ends
            }
            if (launch) show.run();
        }
    }

    /** Whether the launch listening on the port of the file answered, as soon as it listens. */
    private static boolean knock(Path portFile, byte[] hello) {
        long until = System.currentTimeMillis() + PATIENCE;
        while (true) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), Integer.parseInt(Files.readString(portFile).strip())), 1000);
                socket.setSoTimeout(1000);
                socket.getOutputStream().write(hello);
                if (Arrays.equals(socket.getInputStream().readNBytes(REPLY.length), REPLY)) return true;
            } catch (IOException | RuntimeException e) {
                // Not listening yet (it is starting), or not the app on that port.
            }
            if (System.currentTimeMillis() > until) return false;
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
