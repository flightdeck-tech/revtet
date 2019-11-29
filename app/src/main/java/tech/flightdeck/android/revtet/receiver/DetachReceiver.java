package tech.flightdeck.android.revtet.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

public class DetachReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Process.killProcess(Process.myPid());
    }
}
