package io.trik.capacitor.zeroconf;

import android.net.nsd.NsdServiceInfo;;

public interface ZeroConfServiceWatchCallback {
    String ADDED = "added";
    String REMOVED = "removed";
    String RESOLVED = "resolved";

    void serviceBrowserEvent(String action, NsdServiceInfo service);
}
