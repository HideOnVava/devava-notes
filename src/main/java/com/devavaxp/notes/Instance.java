package com.devavaxp.notes;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One window per notes folder: two would both save the same notes, each over what the other wrote.
 * The first launch holds a lock and listens on a port of this computer only; a second launch finds
 * the lock taken, knocks on that port so that the first window comes to the front, and ends.
 */
final class Instance {

    /** The locks and ports this app holds while it runs; the system lets go of them when it ends. */
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
     * (on a thread of its own), and this one is not.
     */
    static boolean claim(Path dir, Path notes, Runnable show) {
        String key = Integer.toHexString(notes.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT).hashCode());
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
                return !knock(portFile);   // if the other one cannot be reached, open anyway
            }
            ServerSocket server = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
            Files.writeString(portFile, String.valueOf(server.getLocalPort()));
            HELD.addAll(List.of(channel, server));
            Thread listener = new Thread(() -> {
                while (!server.isClosed()) {
                    try {
                        server.accept().close();
                        show.run();
                    } catch (IOException e) {
                        return;
                    }
                }
            }, "Devava Notes: another launch");
            listener.setDaemon(true);
            listener.start();
            return true;
        } catch (IOException e) {
            return true;   // no lock to be had (a folder it cannot write in): it opens, as before
        }
    }

    private static boolean knock(Path portFile) {
        try {
            new Socket(InetAddress.getLoopbackAddress(), Integer.parseInt(Files.readString(portFile).strip())).close();
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
