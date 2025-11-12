package io.trik.capacitor.zeroconf;

// Native Android Imports
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
// Capacitor Imports
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
// Java Util Imports
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@CapacitorPlugin(
    name = "ZeroConf",
    permissions = {
        @Permission(
            strings = {
                Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_MULTICAST_STATE, Manifest.permission.INTERNET
            },
            alias = "internet"
        )
    }
)
public class ZeroConfPlugin extends Plugin {

    private static final String TAG = "ZeroConfPlugin";

    // NSD (Native) Service
    private NsdManager nsdManager;
    private WifiManager.MulticastLock multicastLock;
    private String hostname;

    // Store the listeners to unregister/unwatch them later
    private final Map<String, NsdManager.RegistrationListener> activeRegistrationListeners = new HashMap<>();
    private final Map<String, NsdManager.DiscoveryListener> activeDiscoveryListeners = new HashMap<>();
    private final Map<String, PluginCall> activeWatchCalls = new HashMap<>();

    /**
     * Load the plugin, get the NsdManager, and acquire the MulticastLock
     */
    @Override
    public void load() {
        super.load();
        try {
            this.nsdManager = (NsdManager) getContext().getSystemService(Context.NSD_SERVICE);

            WifiManager wifi = (WifiManager) getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            this.multicastLock = wifi.createMulticastLock("capacitor-zeroconf-multicastLock");
            this.multicastLock.setReferenceCounted(true);

            this.hostname = getHostNameFromActivity(getActivity());
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize ZeroConfPlugin", e);
        }
    }

    /**
     * Clean up all listeners when the plugin is destroyed (e.g., app closes)
     */
    @Override
    protected void handleOnDestroy() {
        this.stopAll();
    }

    /**
     * Get the device's mDNS hostname
     */
    @PluginMethod
    public void getHostname(PluginCall call) {
        if (this.hostname != null) {
            JSObject result = new JSObject();
            result.put("hostname", this.hostname);
            call.resolve(result);
        } else {
            call.reject("Error: undefined hostname");
        }
    }

    /**
     * Register a new service
     */
    @PluginMethod
    public void register(PluginCall call) {
        final String type = call.getString("type");
        final String name = call.getString("name");
        final int port = call.getInt("port", -1);
        final JSObject props = call.getObject("props");

        if (port == -1) {
            call.reject("Missing 'port' parameter");
            return;
        }

        if (activeRegistrationListeners.containsKey(name)) {
            call.reject("Service with this name is already registered");
            return;
        }

        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(name);
        serviceInfo.setServiceType(type);
        serviceInfo.setPort(port);

        // Set TXT records (props)
        // should be always true since min SDK is 23 (Android Marshmallow)
        if (props != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Iterator<String> keys = props.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                serviceInfo.setAttribute(key, props.getString(key));
            }
        }

        NsdManager.RegistrationListener listener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
                Log.d(TAG, "Service registered: " + name);
                // Save the listener
                activeRegistrationListeners.put(name, this);

                JSObject status = new JSObject();
                status.put("action", "registered");
                status.put("service", jsonifyService(NsdServiceInfo, null));
                call.resolve(status);
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Registration failed for " + name + ". Error: " + errorCode);
                call.reject("Registration failed with error code: " + errorCode);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo arg0) {
                Log.d(TAG, "Service unregistered: " + name);
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Unregistration failed for " + name + ". Error: " + errorCode);
            }
        };

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener);
        } catch (Exception e) {
            call.reject("Error starting registration: " + e.getMessage());
        }
    }

    /**
     * Unregister a service
     */
    @PluginMethod
    public void unregister(PluginCall call) {
        final String name = call.getString("name");
        NsdManager.RegistrationListener listener = activeRegistrationListeners.remove(name);

        if (listener != null) {
            try {
                nsdManager.unregisterService(listener);
                call.resolve();
            } catch (Exception e) {
                call.reject("Error during unregistration: " + e.getMessage());
            }
        } else {
            call.reject("Service not found or already unregistered.");
        }
    }

    /**
     * Stop all registered services and watches
     */
    @PluginMethod
    public void stop(PluginCall call) {
        this.stopAll();
        call.resolve();
    }

    /**
     * Watch for services of a specific type
     */
    @PluginMethod(returnType = PluginMethod.RETURN_CALLBACK)
    public void watch(PluginCall call) {
        final String type = call.getString("type");
        if (type == null) {
            call.reject("Missing 'type' parameter");
            return;
        }

        // Tell Capacitor to keep this call alive
        call.setKeepAlive(true);

        if (activeDiscoveryListeners.containsKey(type)) {
            call.reject("A watch for this type is already active");
            return;
        }

        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override
            public void onServiceFound(NsdServiceInfo service) {
                Log.d(TAG, "Service found: " + service.getServiceName());
                resolveService(service, type);
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.d(TAG, "Service lost: " + service.getServiceName());
                JSObject status = new JSObject();
                status.put("action", "removed");
                status.put("service", jsonifyService(service, null));
                notifyListeners("discover", status, true);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery start failed. Error: " + errorCode);
                activeDiscoveryListeners.remove(type);
                activeWatchCalls.remove(type);
                call.reject("Discovery start failed with error code: " + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery stop failed. Error: " + errorCode);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "Discovery started for: " + serviceType);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Discovery stopped for: " + serviceType);
            }
        };

        try {
            // Store the listener and the call for later
            activeDiscoveryListeners.put(type, listener);
            activeWatchCalls.put(type, call);

            // Acquire multicast lock and start discovery
            this.multicastLock.acquire();
            nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener);
        } catch (Exception e) {
            if (this.multicastLock.isHeld()) {
                this.multicastLock.release(); // Release lock on error
            }
            activeDiscoveryListeners.remove(type);
            activeWatchCalls.remove(type);
            call.reject("Error starting discovery: " + e.getMessage());
        }
    }

    /**
     * Stop watching for services of a specific type
     */
    @PluginMethod
    public void unwatch(PluginCall call) {
        final String type = call.getString("type");
        NsdManager.DiscoveryListener listener = activeDiscoveryListeners.remove(type);
        PluginCall watchCall = activeWatchCalls.remove(type);

        if (listener != null) {
            try {
                if (this.multicastLock.isHeld()) {
                    this.multicastLock.release(); // Release the lock
                }
                nsdManager.stopServiceDiscovery(listener);
                if (watchCall != null) {
                    // This "completes" the original `watch` call.
                    watchCall.resolve();
                }
                call.resolve(); // This resolves the `unwatch` call.
            } catch (Exception e) {
                call.reject("Error stopping discovery: " + e.getMessage());
            }
        } else {
            call.reject("No active watch found for this type.");
        }
    }

    /**
     * Alias for stop
     */
    @PluginMethod
    public void close(PluginCall call) {
        this.stopAll();
        call.resolve();
    }

    /**
     * Stops all running services and discovery
     */
    private void stopAll() {
        for (NsdManager.RegistrationListener listener : activeRegistrationListeners.values()) {
            try {
                nsdManager.unregisterService(listener);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering service", e);
            }
        }
        activeRegistrationListeners.clear();

        for (NsdManager.DiscoveryListener listener : activeDiscoveryListeners.values()) {
            try {
                nsdManager.stopServiceDiscovery(listener);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping discovery", e);
            }
        }
        activeDiscoveryListeners.clear();
        activeWatchCalls.clear();

        if (this.multicastLock.isHeld()) {
            this.multicastLock.release();
        }
    }

    /**
     * Asynchronously resolves a service to get its host, IP, and port.
     * This is required after `onServiceFound`.
     */
    private void resolveService(NsdServiceInfo serviceInfo, String originalType) {
        nsdManager.resolveService(
            serviceInfo,
            new NsdManager.ResolveListener() {
                @Override
                public void onServiceResolved(NsdServiceInfo resolvedServiceInfo) {
                    Log.d(TAG, "Service resolved: " + resolvedServiceInfo.getServiceName());

                    JSObject status = new JSObject();
                    JSObject serviceJson;
                    try {
                        serviceJson = jsonifyService(resolvedServiceInfo, resolvedServiceInfo.getHost());
                    } catch (Exception e) {
                        Log.e(TAG, "CRASH during jsonifyService: ", e);
                        return;
                    }
                    status.put("action", "resolved");
                    status.put("service", serviceJson);

                    // 2. Use the 'originalType' from the parameter for the lookup
                    PluginCall watchCall = activeWatchCalls.get(originalType);

                    if (watchCall != null) {
                        notifyListeners("discover", status, true);
                    } else {
                        Log.w(TAG, "WATCH_CALL is null. No listener found for this type.");
                    }
                }

                @Override
                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.e(TAG, "Resolve failed for " + serviceInfo.getServiceName() + ". Error: " + errorCode);
                }
            }
        );
    }

    /**
     * Converts an NsdServiceInfo object into a JSObject for Capacitor.
     *
     * @param service The NsdServiceInfo object.
     * @param host    The InetAddress, which is ONLY available after resolution. Can be null.
     * @return A JSObject representing the service.
     */
    private JSObject jsonifyService(NsdServiceInfo service, InetAddress host) {
        JSObject obj = new JSObject();
        if (service == null) {
            return obj; // Return empty object if service is null
        }

        obj.put("domain", "local.");
        obj.put("type", service.getServiceType() != null ? service.getServiceType() : "");
        obj.put("name", service.getServiceName() != null ? service.getServiceName() : "");
        obj.put("port", service.getPort());

        if (host != null) {
            obj.put("hostname", host.getHostName() != null ? host.getHostName() : "");

            String hostAddress = host.getHostAddress();
            if (hostAddress != null) {
                obj.put("ipv4Addresses", new JSArray().put(hostAddress));
            } else {
                obj.put("ipv4Addresses", new JSArray());
            }
            // NsdManager only resolves one address.
            obj.put("ipv6Addresses", new JSArray());
        }

        // Get TXT records
        JSObject props = new JSObject();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Map<String, byte[]> attributes = service.getAttributes();
            if (attributes != null) { // Check if attributes map is null
                for (Map.Entry<String, byte[]> entry : attributes.entrySet()) {
                    String key = entry.getKey();
                    byte[] value = entry.getValue();
                    if (key != null && value != null) {
                        props.put(key, new String(value));
                    }
                }
            }
        }
        obj.put("txtRecord", props);

        return obj;
    }

    /**
     * Robust method for getting the device's hostname.
     */
    private static String getHostNameFromActivity(Activity activity)
        throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        @SuppressLint("DiscouragedPrivateApi")
        Method getString = Build.class.getDeclaredMethod("getString", String.class);
        getString.setAccessible(true);
        String hostName = getString.invoke(null, "net.hostname").toString();

        if (TextUtils.isEmpty(hostName) || hostName.equals("unknown")) {
            @SuppressLint("HardwareIds")
            String id = Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.ANDROID_ID);
            hostName = "android-" + id;
        }
        return hostName;
    }
}
