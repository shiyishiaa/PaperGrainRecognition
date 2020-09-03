package com.grain.grain.ui;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.grain.grain.R;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class brightness extends AppCompatActivity {
    // Various widgets.
    Switch switchBluetooth;
    TextView textBluetoothStatus, textBluetoothIsOpen;
    TextView textBrightness, textBrightnessPercentage;
    TextView textPairingStatus, textBluetoothIsPairing;
    SeekBar seekBarAdjustBrightness;
    LinearLayout BluetoothFunctionLayout;
    // Bottom menu bar.
    ImageButton imBtnBrightness, imBtnRecognition, imBtnResult;
    LinearLayout BrightnessLayout, RecognitionLayout, ResultLayout;
    // The bluetooth object.
    UUID defaultUuid;
    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice bluetoothDevice;
    BluetoothSocket bluetoothSocket;
    OutputStream outputStream;
    InputStream inputStream;
    // The handlers for auto check program.
    Handler bluetoothStatusHandler, pairingStatusHandler;
    // The OnOnCheckedChangeListener of the switchBluetooth.
    Switch.OnCheckedChangeListener mOnOnCheckedChangeListener = (compoundButton, b) -> {
        switchBluetooth.setEnabled(false);
        new ThreadToggleBluetooth(bluetoothAdapter).start();
    };
    // The OnSeekBarChangeListener of the seekBarAdjustBrightness.
    SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            textBrightnessPercentage.setText(String.valueOf(i));
            int brightness = i * 255 / 100;
            try {
                outputStream.write(brightness);
            } catch (IOException e) {
                backgroundedToast(e.getMessage(), Toast.LENGTH_SHORT);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };
    // xStart stores the location where swipe gesture starts.
    private float xStart = 0;
    // xEnd stores the location where swipe gesture ends.
    @SuppressWarnings("FieldCanBeLocal")
    private float xEnd = 0;
    private boolean mBackKeyPressed = false;
    private SharedPreferences Config;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.brightness);
        initialize();
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (!mBackKeyPressed) {
                backgroundedToast(R.string.textOneMoreClickToExit, Toast.LENGTH_SHORT);
                mBackKeyPressed = true;
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        mBackKeyPressed = false;
                    }
                }, 2000);
                return true;
            } else
                finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Swipe to change interface
     *
     * @param event Touch Event
     * @return false
     */
    @Override
    public boolean onTouchEvent(@NotNull MotionEvent event) {
        // Press start location
        if (event.getAction() == MotionEvent.ACTION_DOWN)
            xStart = event.getX();
            // Press end location
        else if (event.getAction() == MotionEvent.ACTION_UP) {
            xEnd = event.getX();
            // Change interface
            if (xStart > xEnd && Math.abs(xEnd - xStart) >= getResources().getInteger(R.integer.minimum_move_distance)) {
                Intent intent = new Intent();
                intent.setClass(brightness.this, recognition.class);
                startActivity(intent);
                this.finish();
            }
        }
        return false;
    }

    /**
     * Override finish() to add animation.
     */
    @Override
    public void finish() {
        super.finish();
        // Refresh bluetooth status when activity finished.
        switch (bluetoothAdapter.getState()) {
            case BluetoothAdapter.STATE_TURNING_OFF:
            case BluetoothAdapter.STATE_OFF:
                switchBluetooth.setChecked(false);
                break;
            case BluetoothAdapter.STATE_TURNING_ON:
            case BluetoothAdapter.STATE_ON:
                switchBluetooth.setChecked(true);
                break;
            default:
                backgroundedToast(R.string.textUnexpectedError, Toast.LENGTH_LONG);
        }
        overridePendingTransition(R.anim.in_from_right, R.anim.out_to_left);
    }

    /**
     * Initialize every task.
     */
    private void initialize() {
        connect();
        setFunction();

        autoCheckBluetoothStatus();
        autoCheckPairingStatus();
    }

    /**
     * Obtain every widget.
     */
    private void connect() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        defaultUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

        switchBluetooth = findViewById(R.id.switchBluetooth);
        textBluetoothStatus = findViewById(R.id.textBluetoothStatus);
        textBluetoothIsOpen = findViewById(R.id.textBluetoothIsOpen);
        textBrightness = findViewById(R.id.textBrightness);
        seekBarAdjustBrightness = findViewById(R.id.seekBarAdjustBrightness);
        textBrightnessPercentage = findViewById(R.id.textBrightnessPercentage);
        textPairingStatus = findViewById(R.id.textPairingStatus);
        textBluetoothIsPairing = findViewById(R.id.textBluetoothIsPairing);
        BluetoothFunctionLayout = findViewById(R.id.BluetoothFunctionLayout);

        imBtnBrightness = findViewById(R.id.imBtnBrightness);
        imBtnRecognition = findViewById(R.id.imBtnRecognition);
        imBtnResult = findViewById(R.id.imBtnResult);

        BrightnessLayout = findViewById(R.id.BrightnessLayout);
        RecognitionLayout = findViewById(R.id.RecognitionLayout);
        ResultLayout = findViewById(R.id.ResultLayout);

        bluetoothStatusHandler = new Handler();
        pairingStatusHandler = new Handler();
    }

    /**
     * Set buttons functions and texts contents.
     */
    private void setFunction() {
        // Menu bar
        imBtnRecognition.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setClass(brightness.this, recognition.class);
            startActivity(intent);
            this.finish();
        });
        imBtnResult.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setClass(brightness.this, result.class);
            startActivity(intent);
            this.finish();
        });
        BrightnessLayout.setBackgroundColor(this.getColor(R.color.AlphaGray));
        // Switch
        switchBluetooth.setChecked(bluetoothAdapter.isEnabled());
        switchBluetooth.setOnCheckedChangeListener(mOnOnCheckedChangeListener);
        // Seekbar
        seekBarAdjustBrightness.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
        // TextView
        if (bluetoothAdapter.isEnabled()) {
            textBluetoothIsOpen.setText(R.string.textON);
            textBluetoothIsOpen.setTextColor(this.getColor(R.color.Green));
            BluetoothFunctionLayout.setVisibility(View.VISIBLE);
        } else {
            textBluetoothIsOpen.setText(R.string.textOFF);
            textBluetoothIsOpen.setTextColor(this.getColor(R.color.Red));
            BluetoothFunctionLayout.setVisibility(View.INVISIBLE);
        }
        textBrightnessPercentage.setText(String.valueOf(seekBarAdjustBrightness.getProgress()));
    }

    /**
     * Check bluetooth status automatically.
     */
    private void autoCheckBluetoothStatus() {
        bluetoothStatusHandler.postDelayed(() -> {
            switch (bluetoothAdapter.getState()) {
                case BluetoothAdapter.STATE_ON:
                    bluetoothIsOn();
                    break;
                case BluetoothAdapter.STATE_OFF:
                    bluetoothIsOff();
                    break;
                case BluetoothAdapter.STATE_TURNING_ON:
                case BluetoothAdapter.STATE_TURNING_OFF:
                    break;
                default:
                    backgroundedToast(R.string.textUnexpectedError, Toast.LENGTH_LONG);
            }

            if (switchBluetooth.isChecked() != bluetoothAdapter.isEnabled()) {
                switchBluetooth.setOnCheckedChangeListener(null);
                switchBluetooth.setChecked(bluetoothAdapter.isEnabled());
                switchBluetooth.setOnCheckedChangeListener(mOnOnCheckedChangeListener);
            }
            autoCheckBluetoothStatus();
        }, getResources().getInteger(R.integer.bluetooth_delay_time));
    }

    private void bluetoothIsOn() {
        if (BluetoothFunctionLayout.getVisibility() == LinearLayout.INVISIBLE) {
            switchBluetooth.setEnabled(true);
            textBluetoothIsOpen.setTextColor(brightness.this.getColor(R.color.Green));
            textBluetoothIsOpen.setText(R.string.textON);
            toggleBluetoothLabel(true);
        }
    }

    private void bluetoothIsOff() {
        if (BluetoothFunctionLayout.getVisibility() == LinearLayout.VISIBLE) {
            switchBluetooth.setEnabled(true);
            textBluetoothIsOpen.setTextColor(brightness.this.getColor(R.color.Red));
            textBluetoothIsOpen.setText(R.string.textOFF);
            textBluetoothIsPairing.setText(R.string.textDisconnected);
            seekBarAdjustBrightness.setEnabled(false);
            toggleBluetoothLabel(false);
        }
    }

    /**
     * Check pairing status automatically.
     */
    private void autoCheckPairingStatus() {
        pairingStatusHandler.postDelayed(() -> {
            switch (bluetoothAdapter.getProfileConnectionState(BluetoothProfile.A2DP)) {
                case BluetoothAdapter.STATE_CONNECTING:
                    textBluetoothIsPairing.setText(R.string.textConnecting);
                    break;

                case BluetoothAdapter.STATE_CONNECTED:
                    textBluetoothIsPairing.setText(R.string.textConnected);
                    onConnection();
                    seekBarAdjustBrightness.setEnabled(true);
                    break;

                case BluetoothAdapter.STATE_DISCONNECTING:
                    textBluetoothIsPairing.setText(R.string.textDisconnecting);
                    seekBarAdjustBrightness.setEnabled(false);
                    break;

                case BluetoothAdapter.STATE_DISCONNECTED:
                    textBluetoothIsPairing.setText(R.string.textDisconnected);
                    break;
            }
            autoCheckPairingStatus();
        }, 500);
    }

    private void onConnection() {
//        new Thread() {
//            @Override
//            public void run() {
//                try {
//                    List<BluetoothDevice> mDevices = bluetoothProfile.getConnectedDevices();
//                    // 获得一个socket，安卓4.2以前蓝牙使用此方法，获得socket，4.2后为下面的方法
//                    // 不能进行配对
//                    // final BluetoothSocket socket = device.createRfcommSocketToServiceRecord
//                    // (UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
//                    // 安卓系统4.2以后的蓝牙通信端口为 1 ，但是默认为 -1，所以只能通过反射修改，才能成功
//                    final BluetoothSocket socket = (BluetoothSocket) bluetoothDevice.getClass()
//                            .getDeclaredMethod("createRfcommSocket", new Class[]{int.class})
//                            .invoke(bluetoothDevice, 1);
//                    Thread.sleep(500);
//
//                    // 这里建立蓝牙连接 socket.connect() 这句话必须单开一个子线程
//                    // 至于原因 暂时不知道为什么
//                    new Thread() {
//                        @Override
//                        public void run() {
//                            try {
//                                if (socket != null) {
//                                    socket.connect();
//                                    backgroundedToast("Connected", Toast.LENGTH_SHORT);
//                                }
//                            } catch (IOException e) {
//                                backgroundedToast(e.getMessage(), Toast.LENGTH_LONG);
//                            }
//                        }
//                    }.start();
//
//                    //建立蓝牙连接
//                    // 获得一个输出流
//                    outputStream = socket.getOutputStream();
//                    inputStream = socket.getInputStream();
//                    runOnUiThread(() -> Toast.makeText(brightness.this, "连接成功", Toast.LENGTH_SHORT).show());
//                } catch (Exception e) {
//                    Log.e("123", e.getMessage());
//                }
//            }
//        }.start();

        if (bluetoothAdapter.getProfileConnectionState(BluetoothProfile.A2DP) ==
                BluetoothAdapter.STATE_CONNECTED) {
            bluetoothAdapter.getProfileProxy(brightness.this, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int i, BluetoothProfile bluetoothProfile) {
                    List<BluetoothDevice> mDevices = bluetoothProfile.getConnectedDevices();
                    if (mDevices != null && mDevices.size() > 0) {
                        for (BluetoothDevice device : mDevices) {
                            backgroundedToast(device.getName() + "," + device.getAddress(), Toast.LENGTH_SHORT);
                            bluetoothDevice = device;
                            ThreadBluetoothCommunication communication = new ThreadBluetoothCommunication(device, bluetoothAdapter);
                            communication.start();
                            outputStream = communication.getOutputStream();
                            inputStream = communication.getInputStream();


//                            try {
//                                bluetoothSocket = createBluetoothSocket(bluetoothDevice);
//                                outputStream = bluetoothSocket.getOutputStream();
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }
// https://developer.android.com/reference/android/bluetooth/BluetoothDevice#createRfcommSocketToServiceRecord(java.util.UUID)
// Hint: If you are connecting to a Bluetooth serial board
// then try using the well-known SPP UUID 00001101-0000-1000-8000-00805F9B34FB.
// However if you are connecting to an Android peer then please generate your own unique UUID.
// Requires Manifest.permission.BLUETOOTH
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(int i) {
                }
            }, BluetoothProfile.A2DP);
        } else
            backgroundedToast(R.string.textWrongConnecting, Toast.LENGTH_LONG);
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device)
            throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, defaultUuid);
        } catch (Exception e) {
            Log.e("TAG", "Could not create Insecure RFComm Connection", e);
        }
        return device.createRfcommSocketToServiceRecord(defaultUuid);
    }

    public int dip2px(Context context, float dpValue) {
        float scale = 0;
        try {
            scale = context.getResources().getDisplayMetrics().density;
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return (int) (dpValue * scale + 0.5f);
    }

    protected void toggleBluetoothLabel(boolean enabled) {
        Animation translateAnimation, alphaAnimation;
        AnimationSet setAnimation = new AnimationSet(true);
        int duration = 500, deltaY = BluetoothFunctionLayout.getHeight();

        if (enabled) {
            translateAnimation = new TranslateAnimation(0, 0, -deltaY, 0);
            alphaAnimation = new AlphaAnimation(0, 1);
            alphaAnimation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    BluetoothFunctionLayout.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
        } else {
            translateAnimation = new TranslateAnimation(0, 0, 0, -deltaY);
            alphaAnimation = new AlphaAnimation(1, 0);
            alphaAnimation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    BluetoothFunctionLayout.setVisibility(View.INVISIBLE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });

        }
        translateAnimation.setDuration(duration);
        alphaAnimation.setDuration(duration);
        setAnimation.addAnimation(translateAnimation);
        setAnimation.addAnimation(alphaAnimation);
        BluetoothFunctionLayout.startAnimation(setAnimation);
    }


    private void backgroundedToast(@Nullable String msg, @DisplayTime int time) {
        Toast toast = Toast.makeText(brightness.this, msg, time);
        Objects.requireNonNull(toast.getView()).setBackgroundResource(R.drawable.toast_background);
        toast.show();
    }

    private void backgroundedToast(@StringRes int msg, @DisplayTime int time) {
        Toast toast = Toast.makeText(brightness.this, msg, time);
        Objects.requireNonNull(toast.getView()).setBackgroundResource(R.drawable.toast_background);
        toast.show();
    }

    @IntDef({Toast.LENGTH_LONG, Toast.LENGTH_SHORT})
    @Retention(RetentionPolicy.SOURCE)
    private @interface DisplayTime {
    }

    private static class ThreadToggleBluetooth extends Thread {
        BluetoothAdapter bluetoothAdapter;

        ThreadToggleBluetooth(BluetoothAdapter adapter) {
            this.bluetoothAdapter = adapter;
        }

        @Override
        public void run() {
            if (bluetoothAdapter.isEnabled())
                bluetoothAdapter.disable();
            else
                bluetoothAdapter.enable();
        }
    }

    private static class ThreadBluetoothCommunication extends Thread {

        private BluetoothDevice bluetoothDevice;
        private BluetoothAdapter bluetoothAdapter;
        private UUID defaultUuid;
        private InputStream inputStream;
        private OutputStream outputStream;

        ThreadBluetoothCommunication(BluetoothDevice device, BluetoothAdapter adapter) {
            this.bluetoothDevice = device;
            this.bluetoothAdapter = adapter;
            this.defaultUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
            this.inputStream = null;
            this.outputStream = null;
        }

        @Override
        public void run() {
            BluetoothSocket socket;
            try {
                socket = (BluetoothSocket) bluetoothDevice.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class).invoke(bluetoothDevice, defaultUuid);
                socket.connect();
            } catch (IOException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }

        public InputStream getInputStream() {
            return inputStream;
        }

        public OutputStream getOutputStream() {
            return outputStream;
        }
    }

}


