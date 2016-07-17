package ray.com.mecanumwheel;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.HandlerThread;

import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.zerokol.views.JoystickView;
import com.zerokol.views.JoystickView.OnJoystickMoveListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.UUID;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final String WHEEL_BT_NAME = "BioG1_2";

    private View mContentView;
    //private View mControlsView;

    private int mDirAngle = 90;
    private float mLinSpeed = 0.0f;
    private float mAngSpeed = 0.0f;

    //private JoystickView mJoystick;
    private RelativeLayout joypad_layout;
    private ImageView joystick;
    private SeekBar mLinSeekBar;
    private SeekBar mAngSeekBar;
    private TextView mDirAngleText;
    private TextView mLinSpeedText;
    private TextView mAngSpeedText;
    private TextView mWheelSpeedText;

    private BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mBTSocket = null;
    private Handler mBluetoothThreadHandler;
    private HandlerThread mBluetoothThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);

        // Hide action bar.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        mContentView = findViewById(R.id.fullscreen_content);
        mDirAngleText = (TextView) findViewById(R.id.dir_angle_text);
        mLinSpeedText = (TextView) findViewById(R.id.lin_speed_text);
        mAngSpeedText = (TextView) findViewById(R.id.ang_speed_text);
        mWheelSpeedText = (TextView) findViewById(R.id.wheel_text);

        /*
        ((JoystickView) findViewById(R.id.joystick)).setOnJoystickMoveListener(new OnJoystickMoveListener() {
            @Override
            public void onValueChanged(int angle, int power, int direction) {
                // map wigdet angle to polar coordinate angle
                mDirAngle = (360+90-angle)%360;
                //Log.v("joystick", "dir_angle: "+(360+90-angle)%360);
            }
        }, JoystickView.DEFAULT_LOOP_INTERVAL);
        */
        joystick = (ImageView) findViewById(R.id.joystick);
        joypad_layout = (RelativeLayout) findViewById(R.id.joypad);
        joypad_layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                double MAX_DISTANCE = 200;
                switch(event.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        if(distance(event.getX(),event.getY()) <= MAX_DISTANCE){
                            joystick.setX((int)event.getX()-joystick.getWidth()/2);
                            joystick.setY((int)event.getY()-joystick.getHeight()/2);
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                    case MotionEvent.ACTION_UP:
                        if(distance(event.getX(),event.getY()) <= MAX_DISTANCE){
                            joystick.setX((int)event.getX()-joystick.getWidth()/2);
                            joystick.setY((int)event.getY()-joystick.getHeight()/2);
                        }

                        int angle;
                        float x = event.getX()-joypad_layout.getWidth()/2;
                        float y = joypad_layout.getHeight()/2 - event.getY();
                        if(x >= 0 && y >= 0)
                            angle = (int) Math.toDegrees(Math.atan(y / x));
                        else if(x < 0 && y >= 0)
                            angle = (int) Math.toDegrees(Math.atan(y / x)) + 180;
                        else if(x < 0 && y < 0)
                            angle = (int) Math.toDegrees(Math.atan(y / x)) + 180;
                        else if(x >= 0 && y < 0)
                            angle = (int) Math.toDegrees(Math.atan(y / x)) + 360;
                        else
                            angle = 0;
                        mDirAngle = angle;
                        mDirAngleText.setText("direction angle : "+mDirAngle+" deg");

                        if (mBTSocket != null && event.getAction() == MotionEvent.ACTION_UP) {
                            mBluetoothThreadHandler.post(new WriteRunnable(mBTSocket, getTransmittingData()));
                        }
                        //Log.v("joystick", "dir_angle: "+angle);
                        break;
                }
                return true;
            }
        });


        mLinSeekBar = (SeekBar) findViewById(R.id.seekbar_lin_speed);
        mLinSeekBar.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()){
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        mLinSpeed = (float) mLinSeekBar.getProgress() / 100;
                        mLinSpeedText.setText("linear spped : "+mLinSpeed+" m/s");
                        break;
                    case MotionEvent.ACTION_UP:
                        mLinSpeed = (float) mLinSeekBar.getProgress() / 100;
                        mLinSpeedText.setText("linear spped : "+mLinSpeed+" m/s");
                        if (mBTSocket != null) {
                            mBluetoothThreadHandler.post(new WriteRunnable(mBTSocket, getTransmittingData()));
                        }
                        break;
                }

                return false;
            }
        });

        mAngSeekBar = (SeekBar) findViewById(R.id.seekbar_ang_speed);
        mAngSeekBar.setProgress(mAngSeekBar.getMax()/2);
        mAngSeekBar.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()){
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        mAngSpeed = (float) (180 - mAngSeekBar.getProgress()) / 180 * (float) Math.PI;
                        mAngSpeedText.setText("angular spped : "+mAngSpeed+" rad/s");
                        break;
                    case MotionEvent.ACTION_UP:
                        mAngSpeed = (float) (180 - mAngSeekBar.getProgress()) / 180 * (float) Math.PI;
                        mAngSpeedText.setText("angular spped : "+mAngSpeed+" rad/s");
                        if (mBTSocket != null) {
                            mBluetoothThreadHandler.post(new WriteRunnable(mBTSocket, getTransmittingData()));
                        }
                        break;
                }

                return false;
            }
        });

        findViewById(R.id.connect_button).setOnClickListener(mConnectBtnListener);
        findViewById(R.id.disconnect_button).setOnClickListener(mDisconnectBtnListener);
        findViewById(R.id.reset_button).setOnClickListener(mResetBtnListener);

        // Start HandlerThread for bluetooth.
        mBluetoothThread = new HandlerThread("connect");
        mBluetoothThread.start();
        mBluetoothThreadHandler = new Handler(mBluetoothThread.getLooper());
    }

    double distance(double x,double y){
        double centerX = joypad_layout.getWidth()/2;
        double centerY = joypad_layout.getHeight()/2;
        return Math.sqrt((x-centerX)*(x-centerX)+(y-centerY)*(y-centerY));
    }


    private void inflateViews(){

    }

    private byte[] getTransmittingData(){
        /*
         *    transmformation matrix
         *
         *    | w1 |       1     | 1     1    -0.355 |   | vy |
         *    | w2 | =  ______   | 1    -1     0.355 | * | vx |
         *    | w3 |             | 1    -1    -0.355 |   | w0 |
         *    | w4 |    0.0635   | 1     1     0.355 |
         *
         */
        float R = 0.0635f;
        float vy = mLinSpeed * (float)Math.sin(Math.PI / 180 * mDirAngle);
        float vx = mLinSpeed * (float)Math.cos(Math.PI / 180 * mDirAngle);
        int wait_us1 = (int) (Math.PI * 5/8 *63.5f / (vy +vx -0.355f * mAngSpeed));
        int wait_us2 = (int) (Math.PI * 5/8 *63.5f / (vy -vx +0.355f * mAngSpeed));
        int wait_us3 = (int) (Math.PI * 5/8 *63.5f / (vy -vx -0.355f * mAngSpeed));
        int wait_us4 = (int) (Math.PI * 5/8 *63.5f / (vy +vx +0.355f * mAngSpeed));
        wait_us1 -= wait_us1 % (10);
        wait_us2 -= wait_us2 % (10);
        wait_us3 -= wait_us3 % (10);
        wait_us4 -= wait_us4 % (10);

        mDirAngleText.setText("direction angle : "+mDirAngle+" deg");
        mLinSpeedText.setText("linear spped : "+mLinSpeed+" m/s");
        mAngSpeedText.setText("angular spped : "+mAngSpeed+" rad/s");
        mWheelSpeedText.setText("w1="+wait_us1+", w2="+wait_us2+", w3="+wait_us3+", w4="+wait_us4);
        //Log.v("calculation","vy="+vy+", vx="+vx+", w0="+mAngSpeed);
        //Log.v("calculation","w1="+wait_us1+", w2="+wait_us2+", w3="+wait_us3+", w4="+wait_us4);

        Log.v("raw wait_time", "w1:"+wait_us1+", w2:"+wait_us2+", w3:+"+wait_us3+", w4:"+wait_us4);

        // determine direction.
        byte direction = 0;
        if(wait_us1 < 0){
            direction += (1 << 7);
        }
        if(wait_us2 > 0) {
            direction += (1 << 5);
        }
        if(wait_us3 < 0) {
            direction += (1 << 3);
        }
        if(wait_us4 > 0) {
            direction += (1 << 1);
        }

        // abs value.
        wait_us1 = (wait_us1 > 0)? wait_us1: -wait_us1;
        wait_us2 = (wait_us2 > 0)? wait_us2: -wait_us2;
        wait_us3 = (wait_us3 > 0)? wait_us3: -wait_us3;
        wait_us4 = (wait_us4 > 0)? wait_us4: -wait_us4;

        // find min value.
        int min_wait_us = Integer.MAX_VALUE;
        if(min_wait_us > wait_us1)
            min_wait_us = wait_us1;
        if(min_wait_us > wait_us2)
            min_wait_us = wait_us2;
        if(min_wait_us > wait_us3)
            min_wait_us = wait_us3;
        if(min_wait_us > wait_us4)
            min_wait_us = wait_us4;

        int multiplier1 = (int)((float)wait_us1 / min_wait_us * 1);
        int multiplier2 = (int)((float)wait_us2 / min_wait_us * 1);
        int multiplier3 = (int)((float)wait_us3 / min_wait_us * 1);
        int multiplier4 = (int)((float)wait_us4 / min_wait_us * 1);

        Log.v("abs wait_time", "minwu:"+min_wait_us+", w1:"+wait_us1+", w2:"+wait_us2+", w3:+"+wait_us3+", w4:"+wait_us4);
        Log.v("multiplier", "m1:"+multiplier1+", m2:"+multiplier2+", m3:"+multiplier3+", m4:"+multiplier4);

        Log.v("tramsmitted data", "wait_us:"+wait_us1/1+", direction:"+direction+", m1:"+multiplier1+", m2:"+multiplier2+", m3:"+multiplier3+", m4:"+multiplier4);
        ByteBuffer b = ByteBuffer.allocate(21);
        b.putInt(min_wait_us / 1);
        b.put(direction);
        b.putInt(multiplier1*1);
        b.putInt(multiplier2*1);
        b.putInt(multiplier3*1);
        b.putInt(multiplier4*1);
        return b.array();
        //return ""+wait_us1;//String.format("%d %d %d %d", wait_us1, wait_us2, wait_us3, wait_us4);
    }

    private View.OnClickListener mConnectBtnListener = new View.OnClickListener(){
        @Override
        public void onClick(View v) {
            Log.v("bluetooth", "start connect");
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null) {
                // Device does not support Bluetooth
                Log.v("bluetooth", "Device does not support BT.");
                return;
            }
            if (!mBluetoothAdapter.isEnabled()) {
                Log.v("bluetooth", "request to enable BT.");
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
            connectBTDevice();
        }
    };

    private View.OnClickListener mDisconnectBtnListener = new View.OnClickListener(){
        @Override
        public void onClick(View v) {
            Log.v("bluetooth", "disconnect");
            if (mBTSocket == null) {
                return;
            }

            try{
                mBTSocket.close();
            } catch (IOException e) {}

            mBTSocket = null;

            ((TextView) findViewById(R.id.connect_status_text)).setText("Not connected");
        }
    };

    private View.OnClickListener mResetBtnListener = new View.OnClickListener(){
        @Override
        public void onClick(View v) {
            Log.v("bluetooth", "reset");

            mLinSeekBar.setProgress(0);
            mAngSeekBar.setProgress(180);
            mDirAngle = 90;
            mLinSpeed = 0;
            mAngSpeed = 0;
            mDirAngleText.setText("direction angle : 90 deg");
            mLinSpeedText.setText("linear spped : 0 m/s");
            mAngSpeedText.setText("angular spped : 0 rad/s");

            if (mBTSocket == null) {
                return;
            }
            ByteBuffer b = ByteBuffer.allocate(21);
            b.putInt(-1);
            b.put((byte)0);
            b.putInt(0);
            b.putInt(0);
            b.putInt(0);
            b.putInt(0);

            mBluetoothThreadHandler.post(new WriteRunnable(mBTSocket, b.array()));
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        if (requestCode == REQUEST_ENABLE_BT){
            if (resultCode == RESULT_OK){
                Log.v("bluetooth", "BT enabled.");
                connectBTDevice();
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    private void connectBTDevice(){
        Toast.makeText(MainActivity.this, "Connecting device...", Toast.LENGTH_SHORT).show();
        BluetoothDevice mBTDevice = null;
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals(WHEEL_BT_NAME)){
                    mBTDevice = device;
                    break;
                }
            }
        }
        if(mBTDevice == null){
            Log.v("bluetooth", "Cannot find device.");
            return;
        }

        UUID mUUID = mBTDevice.getUuids()[0].getUuid();
        try {
            mBTSocket = mBTDevice.createRfcommSocketToServiceRecord(mUUID);
            mBluetoothThreadHandler.post(new ConnectRunnable(mBTSocket));
        } catch (IOException e){
            e.printStackTrace();
            return;
        }
    }


    private class ConnectRunnable implements Runnable {
        private final BluetoothSocket mmSocket;

        ConnectRunnable(BluetoothSocket socket){
            mmSocket = socket;
        }
        @Override
        public void run() {
            try {
                mmSocket.connect();
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Connection failed. Try again.", Toast.LENGTH_SHORT).show();
                    }
                });
                return;
            }
            Log.v("bluetooth", "connected.");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Connection established.", Toast.LENGTH_SHORT).show();
                    ((TextView) findViewById(R.id.connect_status_text)).setText("Connected!");
                }
            });

            // acknowledge device.
            /*
            byte[] ack = new byte[1];
            ack[0] = 1;
            mBluetoothThreadHandler.post(new WriteRunnable(mBTSocket, ack));
            */
        }
    }

    private class WriteRunnable implements Runnable {
        private final byte[] mmData;
        private BluetoothSocket mmSocket;

        WriteRunnable(BluetoothSocket socket, byte[] dataBytes){
            mmSocket = socket;
            mmData = dataBytes;
        }
        @Override
        public void run() {
            try {
                OutputStream mmOutStream = mmSocket.getOutputStream();
                mmOutStream.write(mmData);
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    mBTSocket.close();
                } catch (IOException closeException) { }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Write failed. Connect again.", Toast.LENGTH_SHORT).show();
                    }
                });
                return;
            }
            Log.v("bluetooth", "data written.");
            /*
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Data written.", Toast.LENGTH_SHORT).show();
                }
            });
            */

        }
    }

}













