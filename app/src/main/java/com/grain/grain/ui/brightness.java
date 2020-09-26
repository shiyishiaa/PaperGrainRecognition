package com.grain.grain.ui;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.grain.grain.R;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import static com.grain.grain.ui.recognition.createEmptyMessage;

public class brightness extends AppCompatActivity {
    // Bluetooth action codes
    private static final int
            BLUETOOTH_ENABLE = 0x0001,
            BLUETOOTH_DISABLE = 0x0002;
    private final Handler toastHandler = new Handler(Looper.getMainLooper());
    // Various widgets.
    private SwitchCompat switchBluetooth;
    private TextView textBluetoothStatus, textBluetoothIsOpen;
    private TextView textBrightness, textBrightnessPercentage;
    private TextView textPairingStatus, textBluetoothIsPairing;
    private SeekBar seekBarAdjustBrightness;
    private LinearLayout BluetoothFunctionLayout;
    // Bottom menu bar.
    private ImageButton menuBtnBrightness, menuBtnRecognition, menuBtnResult;
    private LinearLayout BrightnessLayout, RecognitionLayout, ResultLayout;
    // The bluetooth object.
    private UUID defaultUuid;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private boolean mBackKeyPressed = false;
    private SharedPreferences Config;
    private CompoundButton.OnCheckedChangeListener listener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            switchBluetooth.setEnabled(false);
            if (isChecked)
                bluetoothStatusHandler.sendMessage(createEmptyMessage(BLUETOOTH_ENABLE));
            else
                bluetoothStatusHandler.sendMessage(createEmptyMessage(BLUETOOTH_DISABLE));
        }
    };
    // The handlers for auto check program.
    private final Handler bluetoothStatusHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case BLUETOOTH_ENABLE:
                    bluetoothAdapter.enable();
                    switchBluetooth.setOnCheckedChangeListener(null);
                    switchBluetooth.setChecked(true);
                    switchBluetooth.setOnCheckedChangeListener(listener);
                    if (BluetoothFunctionLayout.getVisibility() == LinearLayout.INVISIBLE) {
                        switchBluetooth.setEnabled(true);
                        textBluetoothIsOpen.setTextColor(brightness.this.getColor(R.color.Green));
                        textBluetoothIsOpen.setText(R.string.textON);
                        toggleBluetoothLabel(true);
                    }
                    return true;
                case BLUETOOTH_DISABLE:
                    bluetoothAdapter.disable();
                    switchBluetooth.setOnCheckedChangeListener(null);
                    switchBluetooth.setChecked(false);
                    switchBluetooth.setOnCheckedChangeListener(listener);
                    if (BluetoothFunctionLayout.getVisibility() == LinearLayout.VISIBLE) {
                        switchBluetooth.setEnabled(true);
                        textBluetoothIsOpen.setTextColor(brightness.this.getColor(R.color.Red));
                        textBluetoothIsOpen.setText(R.string.textOFF);
                        textBluetoothIsPairing.setText(R.string.textDisconnected);
                        seekBarAdjustBrightness.setEnabled(false);
                        toggleBluetoothLabel(false);
                    }
                    return true;
                default:
                    return false;
            }
        }
    });
    // 搜索到新设备广播广播接收器
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                new Thread(() -> {
                    try {
                        bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(defaultUuid);
                        bluetoothSocket.connect();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();
                autoCheckBluetoothSocket();
                SharedPreferences.Editor editor = Config.edit();
                editor.putString("MAC", bluetoothDevice.getAddress());
                editor.apply();
            }
        }
    };
    private Toast toast;

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
    }

    /**
     * Initialize every task.
     */
    private void initialize() {
        connect();
        setFunction();
        autoCheckBluetoothStatus();
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

        menuBtnBrightness = findViewById(R.id.imBtnBrightness);
        menuBtnRecognition = findViewById(R.id.imBtnRecognition);
        menuBtnResult = findViewById(R.id.imBtnResult);

        BrightnessLayout = findViewById(R.id.BrightnessLayout);
        RecognitionLayout = findViewById(R.id.RecognitionLayout);
        ResultLayout = findViewById(R.id.ResultLayout);

        Config = getSharedPreferences("Config", MODE_PRIVATE);

        IntentFilter foundFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, foundFilter);
    }

    /**
     * Set buttons functions and texts contents.
     */
    private void setFunction() {
        // Menu bar
        menuBtnRecognition.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setClass(brightness.this, recognition.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            this.finish();
            overridePendingTransition(R.anim.in_from_right, R.anim.out_to_left);
        });
        menuBtnResult.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setClass(brightness.this, result.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            this.finish();
            overridePendingTransition(R.anim.in_from_right, R.anim.out_to_left);
        });
        BrightnessLayout.setBackgroundColor(this.getColor(R.color.AlphaGray));
        // Switch
        switchBluetooth.setChecked(bluetoothAdapter.isEnabled());
        switchBluetooth.setOnCheckedChangeListener(listener);
        // Seekbar
        seekBarAdjustBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                textBrightnessPercentage.setText(String.valueOf(i));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                String brightness = String.valueOf(255 - seekBarAdjustBrightness.getProgress() * 254 / 100);
                String lightStatus = "1";
                String toBeSent = lightStatus + "," + brightness;

                try {
                    outputStream.write(toBeSent.getBytes());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        seekBarAdjustBrightness.setEnabled(false);
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
                    switchBluetooth.setEnabled(true);
                    bluetoothStatusHandler.sendMessage(createEmptyMessage(BLUETOOTH_ENABLE));
                    break;
                case BluetoothAdapter.STATE_OFF:
                    switchBluetooth.setEnabled(true);
                    bluetoothStatusHandler.sendMessage(createEmptyMessage(BLUETOOTH_DISABLE));
                    break;
                case BluetoothAdapter.STATE_TURNING_ON:
                case BluetoothAdapter.STATE_TURNING_OFF:
                    switchBluetooth.setEnabled(false);
                    break;
                default:
                    backgroundedToast(R.string.textUnexpectedError, Toast.LENGTH_LONG);
            }
            autoCheckBluetoothStatus();
        }, getResources().getInteger(R.integer.bluetooth_delay_time));
    }

    private void autoCheckBluetoothSocket() {
        bluetoothStatusHandler.postDelayed(() -> {
            if (bluetoothSocket.isConnected()) {
                try {
                    String name = bluetoothDevice.getName();
                    if (name != null)
                        textBluetoothIsPairing.setText(name);
                    else
                        textBluetoothIsPairing.setText(bluetoothDevice.getAddress());
                    seekBarAdjustBrightness.setEnabled(true);
                    // 用来收数据
                    // 用来发数据
                    outputStream = bluetoothSocket.getOutputStream();
                    seekBarAdjustBrightness.setEnabled(true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                textBluetoothIsPairing.setText(R.string.textDisconnected);
                seekBarAdjustBrightness.setEnabled(false);
            }
            autoCheckBluetoothSocket();
        }, 1000);
    }

    private void toggleBluetoothLabel(boolean enabled) {
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

    private void backgroundedToast(@StringRes int msg, @DisplayTime int time) {
        backgroundedToast(this.getString(msg), time);
    }

    private void backgroundedToast(String msg, @DisplayTime int time) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast, findViewById(R.id.custom_toast_container));

        TextView text = layout.findViewById(R.id.textToast);
        text.setText(msg);
        if (toast == null)
            toast = new Toast(this);
        toast.setView(layout);
        toast.setDuration(time);
        toast.cancel();
        toastHandler.postDelayed(() -> {
            toast.show();   // 会发现延迟之后就显示出来了
        }, 20);  // 这个时间是自己拍脑袋写的，不影响体验就好，试过使用post也不行
    }

    @IntDef({Toast.LENGTH_LONG, Toast.LENGTH_SHORT})
    @Retention(RetentionPolicy.SOURCE)
    private @interface DisplayTime {
    }
}


