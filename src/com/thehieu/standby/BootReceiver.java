package com.thehieu.standby;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (new Prefs(c).enabled()) {
            try { StandbyService.start(c); } catch (Exception ignored) { }
        }
    }
}
