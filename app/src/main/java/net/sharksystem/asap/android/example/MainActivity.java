package net.sharksystem.asap.android.example;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import net.sharksystem.asap.android.ASAP;
import net.sharksystem.asap.android.ASAPService;
import net.sharksystem.asap.android.ASAPServiceMethods;
import net.sharksystem.aaspandroid.R;

public class MainActivity extends AppCompatActivity {
    private static final CharSequence TESTURI ="bubble://testuri";
    private static final CharSequence TESTMESSAGE = "Hi there from aasp writing activity";

    private static final int MY_PERMISSIONS_REQUEST = 42;


    /** Messenger for communicating with the service. */
    Messenger mService = null;

    /** Flag indicating whether we have called bind on the service. */
    boolean mBound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // create broadcast receiver
        ExampleASAPBroadcastReceiver br = new ExampleASAPBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ASAP.BROADCAST_ACTION);
        this.registerReceiver(br, filter);

        // required permissions
        String[] permissions = new String[] {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.CHANGE_NETWORK_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };

        // check for write permissions
        this.askForPermissions(permissions);

        // start service - which allows service to outlive unbind
        Intent aaspServiceCreationIntent = new Intent(this, ASAPService.class);
        aaspServiceCreationIntent.putExtra(ASAP.USER, "alice");

        this.startService(aaspServiceCreationIntent);
    }

    protected void onDestroy() {
        this.sendMessage2Service(ASAPServiceMethods.STOP_WIFI_DIRECT);
        // and kill it
        this.stopService(new Intent(this, ASAPService.class));

        super.onDestroy();
    }

    private void askForPermissions(String[] permissions) {
        if(permissions == null || permissions.length < 1) return;

        int requestNumber = 0;

        for(String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {

                // ask for missing permission
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{permission},
                        requestNumber++);
            }
        }
    }

    public void onRequestPermissionsResult(int requestCode, String permissions[],
                                           int[] grantResults) {
        // just notified - cannot do anything without that permission
        Toast.makeText(getApplicationContext(), "got permission response",
                Toast.LENGTH_SHORT).show();

    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to the service
        bindService(new Intent(this, ASAPService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        this.sendMessage2Service(ASAPServiceMethods.STOP_WIFI_DIRECT);
    }

    private void sendMessage2Service(int messageNumber) {
        Message msg = Message.obtain(null, messageNumber, 0, 0);
        try {
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void onClick(View view) {
        View writeButton = findViewById(R.id.writeButton);
        View startButton = findViewById(R.id.start);
        View stopButton = findViewById(R.id.stop);

        if(view == writeButton) {
            // Create and send a message to the service, using a supported 'what' value
            Message msg = Message.obtain(null, ASAPServiceMethods.ADD_MESSAGE, 0, 0);
            Bundle msgData = new Bundle();
            msgData.putCharSequence(ASAP.URI, TESTURI);
            msgData.putCharSequence(ASAP.MESSAGE_CONTENT, TESTMESSAGE);
            msg.setData(msgData);

            try {
                mService.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        else if(view == startButton) {
            this.sendMessage2Service(ASAPServiceMethods.START_WIFI_DIRECT);
        }

        else if(view == stopButton) {
            this.sendMessage2Service(ASAPServiceMethods.STOP_WIFI_DIRECT);
        }
    }

    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            mService = new Messenger(service);
            mBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
            mBound = false;
        }
    };
}