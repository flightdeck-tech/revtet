package tech.flightdeck.android.revtet.components;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import tech.flightdeck.android.revtet.service.RevtetService;

public class Forwarder {
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(3);
    private static final byte[] DUMMY_ADDRESS = {42, 42, 42, 42};
    private static final int DUMMY_PORT = 4242;

    private static final int BUFSIZE = 4096;

    private FileDescriptor vpnFileDescriptor;
    private AccessoryConnection accessoryConnection;

    private Future hostToVpnFuture;
    private Future vpnToHostFuture;

    private RevtetService revtetService;

    public Forwarder(RevtetService revtetService) {
        this.revtetService = revtetService;
    }

    public void start() {
        hostToVpnFuture = EXECUTOR_SERVICE.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    forwardHostToVpn();
                } catch (InterruptedIOException e) {
                    revtetService.log("Host to vpn interrupted.");
                } catch (IOException e) {
                    revtetService.log("Host to vpn exception " + e.toString());
                }
            }
        });
        vpnToHostFuture = EXECUTOR_SERVICE.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    forwardVpnToHost();
                } catch (InterruptedIOException e) {
                    revtetService.log("Vpn to host interrupted.");
                } catch (IOException e) {
                    revtetService.log("Vpn to host exception " + e.toString());
                }
            }
        });
    }

    public void stop() {
        hostToVpnFuture.cancel(true);
        vpnToHostFuture.cancel(true);
        wakeUpReadWorkaround();
    }

    public boolean ready() {
        return vpnFileDescriptor != null && accessoryConnection != null && accessoryConnection.ready();
    }

    public void setVpnFileDescriptor(FileDescriptor vpnFileDescriptor) {
        this.vpnFileDescriptor = vpnFileDescriptor;
    }

    public void setAccessoryConnection(AccessoryConnection accessoryConnection) {
        this.accessoryConnection = accessoryConnection;
    }

    private void forwardVpnToHost() throws IOException {
        FileInputStream vpnInput = new FileInputStream(vpnFileDescriptor);
        byte[] buf = new byte[BUFSIZE];
        while (true) {
            int len = vpnInput.read(buf);
            if (len == -1) {
                this.revtetService.log("VPN closed.");
                break;
            }
            if (len > 0) {
                //TODO: Filter unsupported packet
                int version = buf[0] >> 4;
                if (version != 4) {
                    this.revtetService.log("Unexpected packet IP version: " + version);
                } else {
                    this.accessoryConnection.send(buf, len);
                }
            }
        }
        this.revtetService.log("Vpn to host forwarding stopped.");
    }

    private void forwardHostToVpn() throws IOException {
        FileOutputStream vpnOutput = new FileOutputStream(vpnFileDescriptor);
        PacketOutputStream packetOutputStream = new PacketOutputStream(vpnOutput);

        byte[] buffer = new byte[BUFSIZE];
        while (true) {
            try {
                int len = this.accessoryConnection.read(buffer);
                if (len == -1) {
                    this.revtetService.log("Accessory connection closed.");
                    break;
                }
                if (len > 0) {
                    packetOutputStream.write(buffer, 0, len);
//                    vpnOutput.write(buffer, 0, len);
                }
            } catch (Exception e) {
                this.revtetService.log("Host to vpn exception: " + e.toString() + "\n" + e.getCause());
            }
        }
        this.revtetService.log("Host to vpn forwarding stopped.");
    }

    private void wakeUpReadWorkaround() {
        // network actions may not be called from the main thread
        EXECUTOR_SERVICE.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    DatagramSocket socket = new DatagramSocket();
                    InetAddress dummyAddr = InetAddress.getByAddress(DUMMY_ADDRESS);
                    DatagramPacket packet = new DatagramPacket(new byte[0], 0, dummyAddr, DUMMY_PORT);
                    socket.send(packet);
                } catch (IOException e) {
                    // ignore
                }
            }
        });
    }

    public static int readPacketVersion(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            // buffer is empty
            return -1;
        }
        // version is stored in the 4 first bits
        byte versionAndIHL = buffer.get(buffer.position());
        return (versionAndIHL & 0xf0) >> 4;
    }
}
