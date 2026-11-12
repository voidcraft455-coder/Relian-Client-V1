package com.relian.launcher.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Owns a launched Minecraft process and captures its output without blocking the JavaFX thread. */
public final class MinecraftProcessManager implements AutoCloseable {
    private final AtomicReference<Process> active = new AtomicReference<>();
    private volatile CompletableFuture<Integer> exitCode = CompletableFuture.completedFuture(-1);

    public synchronized Process start(List<String> command, Path gameDirectory, Consumer<String> output)
            throws IOException {
        if (active.get() != null && active.get().isAlive())
            throw new IOException("A Minecraft process is already running.");
        if (command == null || command.isEmpty()) throw new IOException("The Minecraft launch command is empty.");
        Path working = gameDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(working)) throw new IOException("The Minecraft game directory does not exist.");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(working.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        active.set(process);
        Consumer<String> sink = output == null ? ignored -> {} : output;
        Thread.ofVirtual().name("relian-minecraft-output").start(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sink.accept(line);
            } catch (IOException ex) {
                sink.accept("[Relian] Could not read Minecraft output: " + ex.getMessage());
            }
        });
        exitCode = CompletableFuture.supplyAsync(() -> {
            try {
                int code = process.waitFor();
                sink.accept("[Relian] Minecraft exited with code " + code + ".");
                return code;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return -1;
            } finally {
                active.compareAndSet(process, null);
            }
        });
        return process;
    }

    public boolean isRunning() {
        Process process = active.get();
        return process != null && process.isAlive();
    }

    public CompletableFuture<Integer> exitCode() { return exitCode; }

    public void stop() {
        Process process = active.get();
        if (process == null || !process.isAlive()) return;
        process.destroy();
        try {
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    @Override public void close() { stop(); }
}
