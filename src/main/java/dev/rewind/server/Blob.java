package dev.rewind.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.Deflater;
import java.util.zip.InflaterInputStream;

final class Blob {
    private static final int PENDING_SIZE = 2048;

    private CompoundTag tag;
    private byte[] bytes;

    private Blob(CompoundTag tag) {
        this.tag = tag;
    }

    synchronized CompoundTag read() {
        return tag != null ? tag.copy() : unpack(bytes);
    }

    synchronized int size() {
        return bytes != null ? bytes.length : PENDING_SIZE;
    }

    synchronized boolean packed() {
        return bytes != null;
    }

    private synchronized CompoundTag pending() {
        return tag;
    }

    private synchronized void packed(byte[] data) {
        bytes = data;
        tag = null;
    }

    private static CompoundTag unpack(byte[] blob) {
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(blob)))) {
            return NbtIo.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static final class Packer {
        private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Rewind packer");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        private final Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        private final ByteArrayOutputStream raw = new ByteArrayOutputStream(4096);
        private byte[] buffer = new byte[4096];

        Blob submit(CompoundTag tag) {
            Blob blob = new Blob(tag);
            executor.execute(() -> {
                CompoundTag pending = blob.pending();
                if (pending != null) {
                    blob.packed(pack(pending));
                }
            });
            return blob;
        }

        void shutdown() {
            executor.shutdown();
        }

        private byte[] pack(CompoundTag tag) {
            raw.reset();
            try {
                NbtIo.write(tag, new DataOutputStream(raw));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            byte[] input = raw.toByteArray();
            deflater.reset();
            deflater.setInput(input);
            deflater.finish();
            int length = 0;
            while (!deflater.finished()) {
                if (length == buffer.length) {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
                length += deflater.deflate(buffer, length, buffer.length - length);
            }
            return Arrays.copyOf(buffer, length);
        }
    }
}
