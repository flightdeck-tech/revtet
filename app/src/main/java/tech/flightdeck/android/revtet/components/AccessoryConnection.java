package tech.flightdeck.android.revtet.components;

import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import tech.flightdeck.android.revtet.constant.Accessory;

public class AccessoryConnection {
    private UsbManager usbManager;
    private UsbAccessory accessory;
    private ParcelFileDescriptor fileDescriptor;
    private FileInputStream inStream;
    private FileOutputStream outStream;
    private volatile boolean running = false;
    private Handler handler;

    public AccessoryConnection(UsbManager usbManager, UsbAccessory accessory) {
        this.usbManager = usbManager;
        this.accessory = accessory;
    }

    public boolean open() {
        this.fileDescriptor = this.usbManager.openAccessory(this.accessory);
        if (this.fileDescriptor != null) {
            FileDescriptor fd = this.fileDescriptor.getFileDescriptor();
            inStream = new FileInputStream(fd);
            outStream = new FileOutputStream(fd);
            return true;
        }
        return false;
    }

    public boolean ready() {
        if (this.fileDescriptor != null && this.fileDescriptor.getFileDescriptor().valid()) {
            return true;
        }
        return false;
    }

    public void close() {
        this.running = false;
        try {
            if (this.inStream != null) {
                this.inStream.read();
                this.inStream.close();
            }
            if (this.outStream != null) {
                this.outStream.close();
            }
            if (fileDescriptor != null) {
                fileDescriptor.close();
            }
        } catch (IOException e) {

        } finally {
            this.inStream = null;
            this.outStream = null;
            fileDescriptor = null;
        }
    }

    public void send(byte[] msg) throws IOException {
        outStream.write(msg);
    }

    public void send(byte[] buf, int len) throws IOException {
        outStream.write(buf, 0, len);
    }

    public int read(byte[] buf) throws IOException {
        return this.inStream.read(buf);
    }

    @Override
    public int hashCode() {
        return this.accessory.hashCode();
    }

//    private class ConnectionThread extends Thread {
//        @Override
//        public void run() {
//            running = true;
//
//            while (running) {
//                byte[] msg = new byte[Accessory.BUFFER_SIZE_IN];
//                try {
//                    int len = inStream.read(msg);
//                    while (inStream != null && len > 0 && running) {
//                        onReceive(msg, len);
//                        Thread.sleep(10);
//                        len = inStream.read(msg);
//                    }
//                } catch (final Exception e) {
//                    onError("USB Receive Failed " + e.toString() + "\n");
//                    close();
//                }
//            }
//        }
//    }
}
