package tech.flightdeck.android.revtet.service;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.Nullable;
import tech.flightdeck.android.revtet.R;
import tech.flightdeck.android.revtet.components.AccessoryConnection;
import tech.flightdeck.android.revtet.components.Forwarder;
import tech.flightdeck.android.revtet.components.Net;

public class RevtetService extends VpnService {
    private static final String TAG = RevtetService.class.getSimpleName();
    public static final String ACTION_USB_PERMISSION = "tech.flightdeck.android.revtet.USB_PERMISSION";
    public static final String ACTION_LOG = "tech.flightdeck.android.revtet.LOG";
    public static final String ACTION_NEW_LOG = "tech.flightdeck.android.revtet.NEW_LOG";
    public static final String ACTION_START_VPN = "tech.flightdeck.android.revtet.START_VPN";
    public static final String ACTION_STOP_VPN = "tech.flightdeck.android.revtet.STOP_VPN";
    private UsbManager usbManager;
    private AccessoryConnection conn;
    private ParcelFileDescriptor vpnInterface = null;
    private Forwarder forwarder = null;

    private static final InetAddress VPN_ADDRESS = Net.toInetAddress(new byte[] {10, 0, 0, 2});
    private static final int MTU = 0x4000;

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            final UsbAccessory[] accessories = usbManager.getAccessoryList();
            if (accessories != null) {
                for (UsbAccessory accessory : accessories) {
                    if (usbManager.hasPermission(accessory)) {
                        openAccessory(accessory);
                    }
                }
            }
        }
    };
    private Timer timer = new Timer();

    @Override
    public void onCreate() {
        super.onCreate();
        this.usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        this.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                log(intent.getStringExtra("message"));
            }
        }, new IntentFilter(ACTION_LOG));

        this.forwarder = new Forwarder(this);
        this.timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                handler.sendEmptyMessage(0);
            }
        }, 0, 1000);
    }

    public void tryToStartForwarder() {
        if (this.forwarder.ready()) {
            this.forwarder.start();
        }
    }

    public void openAccessory(UsbAccessory accessory) {
        if (this.conn != null) {
            if (this.conn.ready()) {
                return;
            }
            log("Connection exists and not ready, close it.");
            this.conn.close();
            this.forwarder.stop();
            this.forwarder.setAccessoryConnection(null);
        }
        this.conn = new AccessoryConnection(this.usbManager, accessory);
        log("Opening new connection.");
        if (this.conn.open()) {
            log(String.format("Device %s connected.", accessory.getSerial()));
            this.forwarder.setAccessoryConnection(this.conn);
            tryToStartForwarder();
        } else {
            log(String.format("Failed to connect to device %s.", accessory.getSerial()));
        }
    }

    private List<String> logs = new ArrayList<>(10);

    public void log(String msg) {
        if (logs.size() >= 10) {
            logs.remove(0);
        }
        logs.add(msg);
        StringBuilder sb = new StringBuilder();
        for (String line : logs) {
            sb.append(line).append('\n');
        }
        Intent intent = new Intent(ACTION_NEW_LOG);
        intent.putExtra("message", sb.toString());
        this.sendBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new RevtetBinder();
    }

    public class RevtetBinder extends Binder {
        public void fetchLog() {
            log("Service bound.");
        }

        public void log(String msg) {
            RevtetService.this.log(msg);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (ACTION_START_VPN.equals(action)) {
            if (isRunning()) {
                log("VPN already running.");
            } else {
                startVpn();
            }
        } else if (ACTION_STOP_VPN.equals(action)) {
            stopVpn();
        }
        return START_NOT_STICKY;
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, RevtetService.class);
        intent.setAction(ACTION_START_VPN);
        context.startService(intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, RevtetService.class);
        intent.setAction(ACTION_STOP_VPN);
        context.startService(intent);
    }

    private boolean isRunning() {
        return this.vpnInterface != null;
    }

    private void startVpn() {
        if (setupVpn()) {
            //TODO: start forwarder
            this.forwarder.setVpnFileDescriptor(this.vpnInterface.getFileDescriptor());
            tryToStartForwarder();
        }
    }

    private void stopVpn() {
        if (!isRunning()) {
            return;
        }
        try {
            this.forwarder.stop();
            this.forwarder.setVpnFileDescriptor(null);
            vpnInterface.close();
            vpnInterface = null;
        } catch (IOException e) {
            log("Cannot close VPN file descriptor\n" + e.toString());
        }
    }

    private boolean setupVpn() {
        Builder builder = new Builder();
        builder.addAddress(VPN_ADDRESS, 32);
        builder.setSession(getString(R.string.app_name));

        builder.addRoute("0.0.0.0", 0);
        builder.addDnsServer("8.8.8.8");

        builder.setBlocking(true);
        builder.setMtu(MTU);

        vpnInterface = builder.establish();
        if (vpnInterface == null) {
            log("VPN starting failed.");
            return false;
        }

        setAsUnderlyingNetwork();
        return true;
    }

    private Network findVpnNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network[] networks = cm.getAllNetworks();
        for (Network network : networks) {
            LinkProperties linkProperties = cm.getLinkProperties(network);
            List<LinkAddress> addresses = linkProperties.getLinkAddresses();
            for (LinkAddress addr : addresses) {
                if (addr.getAddress().equals(VPN_ADDRESS)) {
                    return network;
                }
            }
        }
        return null;
    }

    private void setAsUnderlyingNetwork() {
        if (Build.VERSION.SDK_INT >= 22) {
            Network vpnNetwork = findVpnNetwork();
            if (vpnNetwork != null) {
                setUnderlyingNetworks(new Network[]{vpnNetwork});
            }
        }
    }
}
