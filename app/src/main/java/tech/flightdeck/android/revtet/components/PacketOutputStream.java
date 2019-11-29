package tech.flightdeck.android.revtet.components;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import tech.flightdeck.android.revtet.util.Binary;

public class PacketOutputStream extends OutputStream {
    private static final int MAX_IP_PACKET_LENGTH = 1 << 16;

    private final OutputStream target;
    private final ByteBuffer buffer = ByteBuffer.allocate(2 * MAX_IP_PACKET_LENGTH);

    public PacketOutputStream(OutputStream target) {
        this.target = target;
    }

    @Override
    public void close() throws IOException {
        target.close();
    }

    @Override
    public void flush() throws IOException {
        target.flush();
    }

    @Override
    public void write(int b) throws IOException {
        buffer.put((byte)b);
        buffer.flip();
        sink();
        buffer.compact();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (len > MAX_IP_PACKET_LENGTH) {
            throw new IOException("PacketOutputStream does not support writing more than one packet at a time");
        }

        buffer.put(b, off, len);
        buffer.flip();
        sink();
        buffer.compact();
    }

    private void sink() throws IOException {
        while (sinkPacket()) {

        }
    }

    private boolean sinkPacket() throws IOException {
        int version = readPacketVersion(buffer);
        if (version == -1) {
            return false;
        }
        if (version != 4) {
            // May drop multiple packet
            buffer.clear();
            return false;
        }
        int packetLength = readPacketLength(buffer);
        if (packetLength == -1 || packetLength > buffer.remaining()) {
            // No packet or broken packet
            return false;
        }

        target.write(buffer.array(), buffer.arrayOffset() + buffer.position(), packetLength);
        buffer.position(buffer.position() + packetLength);
        return true;
    }

    public static int readPacketVersion(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            return -1;
        }

        byte versionAndIHL = buffer.get(buffer.position());
        return (versionAndIHL & 0xf0) >> 4;
    }

    public static int readPacketLength(ByteBuffer buffer) {
        if (buffer.limit() < buffer.position()) {
            return -1;
        }
        return Binary.unsigned(buffer.getShort(buffer.position() + 2));
    }
}
