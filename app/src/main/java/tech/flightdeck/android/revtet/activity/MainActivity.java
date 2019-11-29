package tech.flightdeck.android.revtet.activity;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.net.VpnService;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.TextView;

import java.util.logging.Logger;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import tech.flightdeck.android.revtet.R;
import tech.flightdeck.android.revtet.service.RevtetService;

public class MainActivity extends Activity {
    private static final Logger LOG = Logger.getLogger(MainActivity.class.getSimpleName());
    private static final int VPN_REQUEST_CODE = 0;
    public static final String ACTION_USB_ACCESSORY_ATTACHED = "android.hardware.usb.action.USB_ACCESSORY_ATTACHED";

    @BindView(R.id.lbl_log)
    TextView lblLog;

    private UsbManager usbManager;

    private final BroadcastReceiver usbConnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
            if (accessory != null) {
                sendAccessoryBroadcast(accessory);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        startService(new Intent(this, RevtetService.class));
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        this.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                lblLog.setText(intent.getStringExtra("message"));
            }
        }, new IntentFilter(RevtetService.ACTION_NEW_LOG));
        this.registerReceiver(usbConnectReceiver, new IntentFilter("android.hardware.usb.action.USB_ACCESSORY_ATTACHED"));
        bindService(new Intent(this, RevtetService.class), new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                ((RevtetService.RevtetBinder)service).fetchLog();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        }, Context.BIND_AUTO_CREATE);
        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        if (ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
            startRevtetService();
            startAccessory();
            finish();
        }
    }

    private boolean startRevtetService() {
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent == null) {
            LOG.info("VPN was already authorized.");
            RevtetService.start(this);
            return true;
        }
        LOG.warning("VPN required the authorization.");
        startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            RevtetService.start(this);
        }
    }

    private void startAccessory() {
        final UsbAccessory[] accessories = usbManager.getAccessoryList();
        if (accessories != null) {
            for (UsbAccessory accessory : accessories) {
                if (!usbManager.hasPermission(accessory)) {
                    PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(RevtetService.ACTION_USB_PERMISSION), 0);
                    this.usbManager.requestPermission(accessory, permissionIntent);
                } else {
                    sendAccessoryBroadcast(accessory);
                }
            }
        }
    }

    private void sendAccessoryBroadcast(UsbAccessory accessory) {
        Intent intent = new Intent(RevtetService.ACTION_USB_PERMISSION);
        intent.putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true);
        intent.putExtra(UsbManager.EXTRA_ACCESSORY, accessory);
        sendBroadcast(intent);
    }

    @OnClick(R.id.btn_start)
    public void onStartClick() {
        LOG.info("Start clicked!");
        RevtetService.start(this);
    }

    @OnClick(R.id.btn_stop)
    public void onStopClick() {
        LOG.info("Stop clicked!");
        RevtetService.stop(this);
    }
}
