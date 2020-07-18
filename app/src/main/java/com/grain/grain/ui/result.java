package com.grain.grain.ui;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.grain.grain.DatabaseHelper;
import com.grain.grain.R;

import org.jetbrains.annotations.NotNull;

public class result extends AppCompatActivity {
    private TextView textGroup;
    private Spinner spinner;

    private TextView textSSIM, textValue, textMatchResult, textResult;
    private ImageView imageViewMatchPicture;

    private ImageButton imBtnBrightness, imBtnRecognition, imBtnResult;

    private LinearLayout BrightnessLayout, RecognitionLayout, ResultLayout;

    public SQLiteDatabase sqLiteDatabase;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.result);

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
            if (xStart < xEnd) {
                Intent intent = new Intent();
                intent.setClass(result.this, recognition.class);
                startActivity(intent);
                this.finish();
            }
        }
        return false;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.in_from_left, R.anim.out_to_right);
    }

    private void initialize() {
        //TODO Initialize UI.
        textGroup = findViewById(R.id.textGroup);
        spinner = findViewById(R.id.spinner);
        textSSIM = findViewById(R.id.textSSIM);
        textValue = findViewById(R.id.textValue);
        textMatchResult = findViewById(R.id.textMatchResult);
        textResult = findViewById(R.id.textResult);
        imageViewMatchPicture = findViewById(R.id.imageViewMatchPicture);

        imBtnBrightness = findViewById(R.id.imBtnBrightness);
        imBtnRecognition = findViewById(R.id.imBtnRecognition);
        imBtnResult = findViewById(R.id.imBtnResult);

        BrightnessLayout = findViewById(R.id.BrightnessLayout);
        RecognitionLayout = findViewById(R.id.RecognitionLayout);
        ResultLayout = findViewById(R.id.ResultLayout);

        databaseHelper = new DatabaseHelper(this, "History", null, 1);
        sqLiteDatabase = databaseHelper.getWritableDatabase();

        initializeMenuBar();
    }

    private void chooseGroup() {
        //TODO Check every group result.
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i == 1) {
                    recognition.setImageView(imageViewMatchPicture, "", "1.jpg");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    private void initializeMenuBar() {
        imBtnRecognition.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setClass(result.this, recognition.class);
            startActivity(intent);
            this.finish();
        });
        imBtnBrightness.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setClass(result.this, brightness.class);
            startActivity(intent);
            this.finish();
        });
        ResultLayout.setBackgroundColor(getResources().getColor(R.color.AlphaGray));
    }

    private void showSSIM() {
        //TODO Display SSIM value.
    }

    private void showResult() {
        //TODO Show the matching result.
    }

    private void showMatchPicture() {
        //TODO Show the matching picture.
    }

}