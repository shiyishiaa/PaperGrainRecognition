package com.grain.grain.ui;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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

import androidx.appcompat.app.AppCompatActivity;

import com.grain.grain.R;

import org.jetbrains.annotations.NotNull;

public class brightness extends AppCompatActivity {
    protected Switch switchBluetooth;
    protected TextView textPairingStatus, textIsPaired;
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
            if (xStart > xEnd) {
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
        textIsPaired = findViewById(R.id.textIsPaired);
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
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.disable();
                textIsPaired = findViewById(R.id.textIsPaired);
                textIsPaired.setText(R.string.stringOFF);
                textIsPaired.setTextColor(getResources().getColor(R.color.Red));
                seekBarAdjustBrightness.setEnabled(false);
                toggleBluetoothLabel(false);
            } else {
                bluetoothAdapter.enable();
//                        if (bluetoothAdapter.isEnabled()) {
                textIsPaired.setText(R.string.stringON);
                textIsPaired.setTextColor(getResources().getColor(R.color.Green));
                seekBarAdjustBrightness.setEnabled(true);
                toggleBluetoothLabel(true);
//                        } else {
//                            Toast.makeText(brightness.this, R.string.stringEnableBluetooth, Toast.LENGTH_LONG).show();
//                            switchBluetooth.setChecked(false);
//                    }
            }
        });
    }

    private void initializeTextIsPaired() {
        if (bluetoothAdapter.isEnabled()) {
            textIsPaired.setText(R.string.stringON);
            textIsPaired.setTextColor(getResources().getColor(R.color.Green));
            BluetoothFunctionLayout.setVisibility(View.VISIBLE);
        } else {
            textIsPaired.setText(R.string.stringOFF);
            textIsPaired.setTextColor(getResources().getColor(R.color.Red));
            BluetoothFunctionLayout.setVisibility(View.INVISIBLE);
        }
    }

    private void initializeSeekBarAdjustBrightness() {
        seekBarAdjustBrightness.setEnabled(bluetoothAdapter.isEnabled());
        seekBarAdjustBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 设置文本显示
                textBrightnessPercentage.setText(String.valueOf(progress));
//                // 获取文本宽度
//                float textWidth = textBrightnessPercentage.getWidth();
//                // 获取seekbar最左端的x位置
//                float left = seekBar.getLeft();
//                // 进度条的刻度值
//                float max = Math.abs(seekBar.getMax());
//                //这不叫thumb的宽度,叫seekbar距左边宽度,实验了一下，seekbar 不是顶格的，两头都存在一定空间
//                // 所以xml 需要用paddingStart 和 paddingEnd 来确定具体空了多少值,我这里设置15dp;
//                float thumb = dip2px(brightness.this, 15);
//                // 每移动1个单位，text应该变化的距离 = (seekBar的宽度 - 两头空的空间) / 总的progress长度
//                float average = (((float) seekBar.getWidth()) - 2 * thumb) / max;
//                //textview 应该所处的位置 = seekbar最左端 + seekbar左端空的空间 + 当前progress应该加的长度 - textview宽度的一半(保持居中作用)
//                float pox = left - textWidth / 2 + thumb + average * (float) progress;
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
