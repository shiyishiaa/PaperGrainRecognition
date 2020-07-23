package com.grain.grain.ui;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import androidx.appcompat.app.AppCompatActivity;

import com.grain.grain.R;

import org.jetbrains.annotations.NotNull;

import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

public class brightness extends AppCompatActivity {
    protected Switch switchBluetooth;
    protected TextView textPairingStatus, textIsConnected;
    protected TextView textBrightness, textBrightnessPercentage;
    protected SeekBar seekBarAdjustBrightness;
    protected LinearLayout BluetoothFunctionLayout;

    protected BluetoothAdapter bluetoothAdapter;

    private ImageButton imBtnBrightness, imBtnRecognition, imBtnResult;

    private LinearLayout BrightnessLayout, RecognitionLayout, ResultLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.brightness);
        connect();
        initialize();
    }

    // xStart stores the location where swipe gesture starts.
    private float xStart = 0;
    // xEnd stores the location where swipe gesture ends.
    @SuppressWarnings("FieldCanBeLocal")
    private float xEnd = 0;

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

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.in_from_right, R.anim.out_to_left);
    }

    private void connect() {
        switchBluetooth = findViewById(R.id.switchBluetooth);
        textPairingStatus = findViewById(R.id.textPairingStatus);
        textIsConnected = findViewById(R.id.textIsConnected);
        textBrightness = findViewById(R.id.textBrightness);
        seekBarAdjustBrightness = findViewById(R.id.seekBarAdjustBrightness);
        textBrightnessPercentage = findViewById(R.id.textBrightnessPercentage);
        BluetoothFunctionLayout = findViewById(R.id.BluetoothFunctionLayout);

        imBtnBrightness = findViewById(R.id.imBtnBrightness);
        imBtnRecognition = findViewById(R.id.imBtnRecognition);
        imBtnResult = findViewById(R.id.imBtnResult);

        BrightnessLayout = findViewById(R.id.BrightnessLayout);
        RecognitionLayout = findViewById(R.id.RecognitionLayout);
        ResultLayout = findViewById(R.id.ResultLayout);
    }

    private void initialize() {
        initializeMenuBar();
        initializeSwitch();
        initializeTextIsPaired();
        initializeSeekBarAdjustBrightness();
        initializeTextBrightnessPercentage();
    }

    private void initializeMenuBar() {
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
        BrightnessLayout.setBackgroundColor(getResources().getColor(R.color.AlphaGray));
    }

    private void initializeTextBrightnessPercentage() {
        textBrightnessPercentage.setText(String.valueOf(seekBarAdjustBrightness.getProgress()));
    }

    private void initializeSwitch() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        switchBluetooth.setChecked(bluetoothAdapter.isEnabled());

        switchBluetooth.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            switchBluetooth.setEnabled(false);

            if (bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.disable();
                textIsConnected = findViewById(R.id.textIsConnected);
                textIsConnected.setTextColor(this.getColor(R.color.Red));
                seekBarAdjustBrightness.setEnabled(false);
                toggleBluetoothLabel(false);
            } else {
                bluetoothAdapter.enable();
                textIsConnected.setTextColor(this.getColor(R.color.Green));
                seekBarAdjustBrightness.setEnabled(true);
                toggleBluetoothLabel(true);
            }

            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> switchBluetooth.setEnabled(true), getResources().getInteger(R.integer.bluetooth_delay_time));
        });
    }

    private void initializeTextIsPaired() {
        if (bluetoothAdapter.isEnabled()) {
            textIsConnected.setText(R.string.stringON);
            textIsConnected.setTextColor(getResources().getColor(R.color.Green));
            BluetoothFunctionLayout.setVisibility(View.VISIBLE);
        } else {
            textIsConnected.setText(R.string.stringOFF);
            textIsConnected.setTextColor(getResources().getColor(R.color.Red));
            BluetoothFunctionLayout.setVisibility(View.INVISIBLE);
        }
    }

    private void initializeSeekBarAdjustBrightness() {
        seekBarAdjustBrightness.setEnabled(bluetoothAdapter.isEnabled());
        seekBarAdjustBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textBrightnessPercentage.setText(String.valueOf(progress));
                if (bluetoothAdapter.getProfileConnectionState(BluetoothProfile.A2DP) ==
                        BluetoothAdapter.STATE_CONNECTED) {
                    bluetoothAdapter.getProfileProxy(brightness.this, new BluetoothProfile.ServiceListener() {
                        @Override
                        public void onServiceConnected(int i, BluetoothProfile bluetoothProfile) {
                            List<BluetoothDevice> mDevices = bluetoothProfile.getConnectedDevices();
                            if (mDevices != null && mDevices.size() > 0) {
                                for (BluetoothDevice device : mDevices) {
                                    Toast.makeText(brightness.this,
                                            device.getName() + "," + device.getAddress(), Toast.LENGTH_SHORT).show();
                                    //TODO Sending message through bluetooth.
                                }
                            }
                        }

                        @Override
                        public void onServiceDisconnected(int i) {

                        }
                    }, BluetoothProfile.A2DP);
                } else {
                    Toast.makeText(brightness.this, R.string.WrongPairing, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
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
                    BluetoothFunctionLayout.setVisibility(View.GONE);
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

    private void adjustBrightness() {
        //TODO Adjust brightness of the facility.
    }
}