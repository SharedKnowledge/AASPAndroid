package net.sharksystem.aasp.android.wifidirect;

import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;
import android.widget.Toast;

import net.sharksystem.util.AASPSession;
import net.sharksystem.aasp.android.AASP;
import net.sharksystem.aasp.android.AASPService;
import net.sharksystem.util.AASPSessionListener;
import net.sharksystem.util.tcp.TCPChannelMaker;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.os.Looper.getMainLooper;

public class AASPWifiP2PEngine implements
        WifiP2pManager.PeerListListener,
        WifiP2pManager.ConnectionInfoListener,
        AASPSessionListener,
        WifiP2pManager.ChannelListener {

    private static AASPWifiP2PEngine wifiP2PEngine = null;

    // https://developer.android.com/guide/topics/connectivity/wifip2p#java

    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private AASPWiFiDirectBroadcastReceiver mReceiver;

    private final AASPService aaspService;
    private final Context context;


    AASPWifiP2PEngine(AASPService aaspService, Context context) {
        this.aaspService = aaspService;
        this.context = context;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //                                 factory / singleton                              //
    //////////////////////////////////////////////////////////////////////////////////////

    public static AASPWifiP2PEngine getAASPWifiP2PEngine(AASPService aaspService, Context context) {
        if(AASPWifiP2PEngine.wifiP2PEngine == null) {
            AASPWifiP2PEngine.wifiP2PEngine = new AASPWifiP2PEngine(aaspService, context);
        }

        return AASPWifiP2PEngine.wifiP2PEngine;
    }

    public static AASPWifiP2PEngine getAASPWifiP2PEngine() {
        return AASPWifiP2PEngine.wifiP2PEngine;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //                                 live cycle methods                               //
    //////////////////////////////////////////////////////////////////////////////////////

    /////////////// WifiP2pManager.ChannelListener - see mManager.initialize
    public void onChannelDisconnected() {
        Toast.makeText(this.aaspService, "channel disconnected / restart wifip2p",
                Toast.LENGTH_SHORT).show();
        // TODO: that's ok?
        this.restartWifiP2P();
    }

    private void restartWifiP2P() {
        this.shutDownWifiP2P();
        this.setupWifiP2P();
    }

    private void shutDownWifiP2P() {
        if(this.mReceiver != null) {
            try {
                this.context.unregisterReceiver(this.mReceiver);
            }
            catch(RuntimeException e) {
                // ignore that one - happens when not registered
            }
            this.mReceiver = null;
        }

        if(this.mManager != null && this.mChannel != null) {
            try {
                this.mManager.cancelConnect(this.mChannel, null);
            }
            catch(RuntimeException e) {}

            try {
                this.mManager.removeGroup(this.mChannel, null);
            }
            catch(RuntimeException e) {}

            try {
                this.mManager.stopPeerDiscovery(this.mChannel, null);
            }
            catch(RuntimeException e) {}

            this.mManager = null;
        }

        /*
        if(this.mChannel != null) {
            try {
                this.mChannel.close(); // it does not work. why?
            }
            catch(RuntimeException e) {}
*/
            this.mChannel = null;
//        }
    }

    private void setupWifiP2P() {
        if(this.mManager == null) {
            // get P2P service on this device
            this.mManager = (WifiP2pManager) this.context.getSystemService(Context.WIFI_P2P_SERVICE);

            // get access to p2p framework: TODO shouldn't we have our own looper here?
            this.mChannel = mManager.initialize(this.context, getMainLooper(), this);

            // create broadcast listener to get publications regarding wifi (p2p)
            this.mReceiver = new AASPWiFiDirectBroadcastReceiver(this,
                    mManager, mChannel, this.context,
                    this, this);

            // define what broadcasts we are interested in
            IntentFilter mIntentFilter = new IntentFilter();

            // see broadcast receiver for details of those events
            mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

            // register (subscribe) to broadcasts
            try {
                this.context.registerReceiver(this.mReceiver, mIntentFilter);
            }
            catch(RuntimeException e) {
                // can happen when already registered - to be sure..
                try {
                    this.context.unregisterReceiver(this.mReceiver);
                    this.context.registerReceiver(this.mReceiver, mIntentFilter);
                }
                catch(RuntimeException e2) {
                    // for gods sake - ignore that - have no idea whats going on here.
                }
            }
        }
    }

    public void start() {
        Toast.makeText(this.aaspService, "start / start setup wifip2p",
                Toast.LENGTH_SHORT).show();
        this.setupWifiP2P();
    }

    public void stop() {
        Toast.makeText(this.aaspService, "stop / shutdown wifip2p", Toast.LENGTH_SHORT).show();
        this.shutDownWifiP2P();
    }

    /**
     * called to start peer discovery
     */
    void discoverPeers() {
        mManager.discoverPeers(this.mChannel,
            new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    // TODO remove that after debugging
                    Log.d("AASPWifiEngine:", "discoverPeers: peer discovery started");
                }

                @Override
                public void onFailure(int reasonCode) {
                    // TODO remove that after debugging
                    Log.d("AASPWifiEngine:", "discoverPeers: peer discovery failed");
                }
            });
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //                WifiP2pManager.PeerListListener interface support                 //
    //////////////////////////////////////////////////////////////////////////////////////

    public static final long DEFAULT_WAIT_BEFORE_RECONNECT_TIME = 1000*60; // a minute
    private long waitBeforeReconnect = DEFAULT_WAIT_BEFORE_RECONNECT_TIME;

    /** last encounter with new peers - last time pnPeersAvaiable called */
    private Date lastEncounter = new Date();

    /** keeps info about device we have tried (!!) recently to connect
     * <MAC address, connection time>
     */
    private Map<String, Date> connectDevices = new HashMap<>();

    /** list of devices which should be connected to */
    private List<WifiP2pDevice> devices2Connect = null;

    /**
     * called as result of a previous requestPeers call in wifip2pmanager
     * after receiving a ON_PEERS_CHANGED_ACTION. Note: There was a sheer
     * flood on those messages
     *
     * @param peers
     */
    @Override
    public void onPeersAvailable(WifiP2pDeviceList peers) {
        // got a list of peers  - check it out
        Log.d("AASPWifiEngine", "onPeersAvailable: peers available");

        // get current time, in its incarnation as date
        long nowInMillis = System.currentTimeMillis();
        long reconnectedBeforeInMillis = nowInMillis - this.waitBeforeReconnect;

        Date now = new Date(nowInMillis);
        Date reconnectBefore = new Date(reconnectedBeforeInMillis);

        Log.d("AASPWifiEngine","now: " + now.toString());
        Log.d("AASPWifiEngine","connectBefore: " + reconnectBefore.toString());

        // if our last general encounter was before our waiting period
        // we can drop the whole waiting list - any peer will be considered as
        // new - remove waiting queue

        if(this.lastEncounter.before(reconnectBefore)) {
            Log.d("AASPWifiEngine", "drop connectDevices list");
            this.connectDevices = new HashMap<>();
        }

        if(this.devices2Connect == null) {
            this.devices2Connect = new ArrayList<>();
        }

        // walk trough list of available peers
        for(WifiP2pDevice device : peers.getDeviceList()) {
            boolean connect = false;
            Log.d("AASPWifiEngine", "iterate new peer: " + device.deviceAddress);

            // device in known device list?
            Date lastEncounter = this.connectDevices.get(device.deviceAddress);

            if(lastEncounter != null) {
                Log.d("AASPWifiEngine", "device in connectDevices list");
                // it was in the list
                if(lastEncounter.before(reconnectBefore)) {
                    Log.d("AASPWifiEngine", "add to devices2connect");
                    // that encounter longer ago than waiting periode - remember that peer
                    devices2Connect.add(device);
                } else {
                    Log.d("AASPWifiEngine", "still in waiting period - ignore");
                }
            } else {
                Log.d("AASPWifiEngine", "device not in connectDevices list");
                Log.d("AASPWifiEngine", "add to devices2connect");
                // no entry at all - remember that device
                devices2Connect.add(device);
            }
        }

        // remember that encounter
        this.lastEncounter = now;

        // are there devices to connect to?
        if(!devices2Connect.isEmpty()) {
            this.connectDevices();
        }
    }

    private void connectDevices() {
        if(this.devices2Connect == null || this.devices2Connect.isEmpty()) return;

        Log.d("AASPWifiEngine", "connectDevices entered with non-empty list");

        // not null, not empty, go ahead
        for(WifiP2pDevice device : this.devices2Connect) {
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = device.deviceAddress;

            Log.d("AASPWifiEngine", "connectDevices: try address: " + device.deviceAddress);
            this.connectDevices.put(device.deviceAddress, new Date());
            Log.d("AASPWifiEngine", "added to connectDevices list");
            this.mManager.connect(this.mChannel, config,
                    new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d("AASPWifiEngine","connectDevices: connect called successfully");
                        }

                        @Override
                        public void onFailure(int reason) {
                            //failure logic
                            Log.d("AASPWifiEngine","connectDevices: connect called not successfully");
                        }
                    }
            ); // end connect
        }

        // done: remove list
        this.devices2Connect = null;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //                  ConnectionInfoListener interface support                        //
    //////////////////////////////////////////////////////////////////////////////////////

    TCPChannelMaker serverChannelCreator = null;

    /**
     * result of a requestConnectionInfo on wifip2pmanager after receiving a p2p connection
     * changed event via broadcast listener
     *
     * @param info
     */
    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        Log.d("AASPWifiEngine:", "onConnectionInfoAvailable");

        TCPChannelMaker.max_connection_loops = 10;

        TCPChannelMaker channelCreator = null;
        if(info.isGroupOwner) {
            Log.d("AASPWifiEngine:", "group owner - should create server");

            // create tcp server as group owner
            if(this.serverChannelCreator == null) {
                Log.d("AASPWifiEngine:", "start server channel maker");
                this.serverChannelCreator =
                        TCPChannelMaker.getTCPServerCreator(AASP.PORT_NUMBER, true);
            } else {
                Log.d("AASPWifiEngine:", "server channel maker already exists");
            }

            channelCreator = this.serverChannelCreator;

        } else {

            String hostAddress = info.groupOwnerAddress.getHostAddress();

            Log.d("AASPWifiEngine:", " start server channel maker: " + hostAddress);
            // create client connection to group owner
            channelCreator = TCPChannelMaker.getTCPClientCreator(hostAddress, AASP.PORT_NUMBER);
        }

        // create an AASPSession with connection parameters
        AASPSession aaspSession = new AASPSession(channelCreator, this.aaspService.getAASPEngine(),
                this, this.aaspService);

        aaspSession.start();
    }

    //////////////////////////////////////////////////////////////////////////////////////
    //                        AASPSessionListener interface support                     //
    //////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void aaspSessionEnded() {

    }
}
