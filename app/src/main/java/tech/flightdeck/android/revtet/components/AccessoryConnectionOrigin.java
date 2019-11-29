package tech.flightdeck.android.revtet.components;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import tech.flightdeck.android.revtet.constant.Accessory;

public abstract class AccessoryConnectionOrigin {
    private static final String TAG = AccessoryConnectionOrigin.class.getSimpleName();
    private static final String ACTION_USB_PERMISSION = "tech.flightdeck.android.revtet.USB_PERMISSION";
    private UsbManager usbManager;
    private Context context;
    private Handler sendHandler;
    private ParcelFileDescriptor fileDescriptor;
    private FileInputStream inStream;
    private FileOutputStream outStream;
    private boolean running;

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbAccessory accessory = (UsbAccessory)intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (accessory != null) {
                            openAccessory(accessory);
                        }
                    } else {
                        Log.i(TAG, "Permission denied.");
                    }
                }
            }
        }
    };

    private final BroadcastReceiver usbConnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
            if (accessory != null) {
                openAccessory(accessory);
            }
        }
    };

    public AccessoryConnectionOrigin(final Context context) {
        this.context = context;

        usbManager = (UsbManager)this.context.getSystemService(Context.USB_SERVICE);

        PendingIntent permissionIntent = PendingIntent.getBroadcast(this.context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter permissionFilter = new IntentFilter(ACTION_USB_PERMISSION);
        this.context.registerReceiver(usbPermissionReceiver, permissionFilter);
        IntentFilter connectFilter = new IntentFilter("android.hardware.usb.action.USB_ACCESSORY_ATTACHED");
        this.context.registerReceiver(usbConnectReceiver, connectFilter);

        final UsbAccessory[] accessories = usbManager.getAccessoryList();

        if (accessories == null || accessories.length == 0) {
            onError("No accessory found");
        } else {
            usbManager.requestPermission(accessories[0], permissionIntent);
        }
    }

    public abstract void onReceive(final byte[] payload, final int length);

    public abstract void onError(String msg);

    public abstract void onConnected();

    public abstract void onDisconnected();

    private void openAccessory(UsbAccessory accessory) {
        try {
            fileDescriptor = usbManager.openAccessory(accessory);
            if (fileDescriptor != null) {
                FileDescriptor fd = fileDescriptor.getFileDescriptor();
                inStream = new FileInputStream(fd);
                outStream = new FileOutputStream(fd);

                new ConnectionThread().start();

                sendHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        try {
                            outStream.write((byte[]) msg.obj);
                        } catch (final Exception e) {
                            onError("USB Send Failed " + e.toString() + "\n");
                        }
                    }
                };

                onConnected();
            } else {
                onError("Could not connect");
            }
        } catch (final Exception e) {
            onError("Failed to connect\n" + e.toString());
        }
    }

    public void send(byte[] payload) {
        if (sendHandler != null) {
            Message msg = sendHandler.obtainMessage();
            msg.obj = payload;
            sendHandler.sendMessage(msg);
        }
    }

    private void receive(final byte[] payload, final int length) {
        onReceive(payload, length);
    }

    private void closeAccessory() {
        running = false;

        try {
            if (fileDescriptor != null) {
                fileDescriptor.close();
            }
        } catch (IOException e) {

        } finally {
            fileDescriptor = null;
        }

        onDisconnected();
    }

    private class ConnectionThread extends Thread {
        @Override
        public void run() {
            running = true;

            while (running) {
                byte[] msg = new byte[Accessory.BUFFER_SIZE_IN];
                try {
                    int len = inStream.read(msg);
                    while (inStream != null && len > 0 && running) {
                        receive(msg, len);
                        Thread.sleep(10);
                        len = inStream.read(msg);
                    }
                } catch (final Exception e) {
                    onError("USB Receive Failed " + e.toString() + "\n");
                    closeAccessory();
                }
            }
        }
    }
}
