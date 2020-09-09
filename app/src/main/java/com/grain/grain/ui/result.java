package com.grain.grain.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Environment;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.grain.grain.R;
import com.grain.grain.io.Columns;
import com.grain.grain.io.PaperGrainDBHelper;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import static com.grain.grain.io.Columns.COLUMN_NAME_DELETED;
import static com.grain.grain.io.Columns.COLUMN_NAME_TIME_START;
import static com.grain.grain.io.Columns.TABLE_NAME;
import static com.grain.grain.io.Columns._COUNT;
import static com.grain.grain.io.Columns._ID;

public class result extends AppCompatActivity {
    private TextView textHistory, textGroup;
    private TextView textSSIM, textSSIMValue, textMatchResult, textResult;
    private TextView labelOriginalPicture, labelSamplePicture;
    private ImageButton imgBtnOriginal, imgBtnSample, imgBtnMatch;
    private ImageButton menuBtnBrightness, menuBtnRecognition, menuBtnResult;
    private Spinner spinnerHistory, spinnerGroup;
    private LinearLayout BrightnessLayout, RecognitionLayout, ResultLayout;
    private Button btnDelete, btnClear, btnConfirm, btnCancel;
    // xStart stores the location where swipe gesture starts.
    private float xStart = 0;
    // xEnd stores the location where swipe gesture ends.
    @SuppressWarnings("FieldCanBeLocal")
    private float xEnd = 0;
    private History history;
    private String originalPath, samplePath, SURFResult;

    private Animator currentAnimator;
    private int shortAnimationDuration;
    private boolean mBackKeyPressed = false;
    private SharedPreferences Config;
    private String originalResult;
    private String sampleResult;

    @SuppressWarnings("UnusedReturnValue")
    public static boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : Objects.requireNonNull(fileOrDirectory.listFiles()))
                deleteRecursive(child);
        return fileOrDirectory.delete();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.result);

        initialize();
    }

    private void initialize() {
        connect();
        readConfig();
        initializeMenuBar();
        initializeSpinner();
        updateImage();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (!mBackKeyPressed) {
                backgroundedToast(R.string.textOneMoreClickToExit, Toast.LENGTH_SHORT);
                writeConfig();
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

    private void readConfig() {
        originalPath = Config.getString("originalPath", null);
        samplePath = Config.getString("samplePath", null);
    }

    private void writeConfig() {
        SharedPreferences.Editor editor = Config.edit();
        editor.putString("originalPath", originalPath);
        editor.putString("samplePath", samplePath);
        editor.apply();
    }

    private void updateImage() {
        try {
            StringBuilder builder = new StringBuilder();
            final int position = spinnerGroup.getSelectedItemPosition();
            builder.append("SELECT ")
                    .append(Columns.COLUMN_NAME_SURF_0).replace(builder.length() - 1, builder.length(), String.valueOf(position))
                    .append(",")
                    .append(Columns.COLUMN_NAME_ORIGINAL_0).replace(builder.length() - 1, builder.length(), String.valueOf(position))
                    .append(",")
                    .append(Columns.COLUMN_NAME_SAMPLE_0).replace(builder.length() - 1, builder.length(), String.valueOf(position))
                    .append(",")
                    .append(Columns.COLUMN_NAME_SSIM_0).replace(builder.length() - 1, builder.length(), String.valueOf(position))
                    .append(" FROM ")
                    .append(TABLE_NAME)
                    .append(" WHERE ")
                    .append(_COUNT).append("=").append(spinnerHistory.getSelectedItemPosition() + 1);
            Cursor cursor = history.getDatabase().rawQuery(String.valueOf(builder), null);
            String SSIM = "";
            if (cursor.moveToFirst()) {
                SURFResult = cursor.getString(0);
                originalResult = cursor.getString(1);
                sampleResult = cursor.getString(2);
                SSIM = cursor.getString(3);
            }
            cursor.close();
            // Update images.
            if (SURFResult != null)
                runOnUiThread(() -> setImageView(imgBtnMatch, SURFResult));
            else
                imgBtnMatch.setImageBitmap(null);
            if (originalResult != null) {
                runOnUiThread(() -> setImageView(imgBtnOriginal, originalResult));
            } else
                imgBtnOriginal.setImageBitmap(null);
            if (sampleResult != null) {
                runOnUiThread(() -> setImageView(imgBtnSample, sampleResult));
            } else
                imgBtnSample.setImageBitmap(null);
            // Update texts.
            if (SSIM != null) {
                textSSIMValue.setText(SSIM);
            } else
                textSSIMValue.setText(null);
        } catch (NullPointerException ignored) {
        }
    }

    public void setImageView(final ImageView imageView, String imagePath) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        //设置图片加载属性:不加载图片内容,只获取图片信息
        options.inJustDecodeBounds = true;
        //加载图片信息
        BitmapFactory.decodeFile(imagePath, options);
        //获取图片宽高
        int picWidth = options.outWidth;
        int picHeight = options.outHeight;
        //获取宽高
        Display defaultDisplay = getWindowManager().getDefaultDisplay();
        Point point = new Point();
        defaultDisplay.getSize(point);
        int width = point.x;
        int height = point.y;
        //计算压缩比
        int wr = picWidth / width;
        int hr = picHeight / height;
        int r = 1;
        r = Math.max(Math.max(wr, hr), r);
        //压缩图片
        options.inSampleSize = r;//设置压缩比
        options.inJustDecodeBounds = false;//设置加载图片内容
        Bitmap bm = BitmapFactory.decodeFile(imagePath, options);
        imageView.setImageBitmap(bm);
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
            if (xStart < xEnd && Math.abs(xEnd - xStart) >= getResources().getInteger(R.integer.minimum_move_distance)) {
                Intent intent = new Intent();
                intent.setClass(result.this, recognition.class);
                startActivity(intent);
                this.finish();
            }
        }
        return false;
    }

    /**
     * Rewrite finish(), adding Animation.
     */
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.in_from_left, R.anim.out_to_right);
    }

    private void connect() {
        textHistory = findViewById(R.id.textHistory);
        spinnerHistory = findViewById(R.id.spinnerHistory);

        textGroup = findViewById(R.id.textGroup);
        spinnerGroup = findViewById(R.id.spinnerGroup);

        textSSIM = findViewById(R.id.textSSIM);
        textSSIMValue = findViewById(R.id.textSSIMValue);
        textMatchResult = findViewById(R.id.textMatchResult);
        textResult = findViewById(R.id.textResult);

        labelOriginalPicture = findViewById(R.id.labelOriginalPicture);
        labelSamplePicture = findViewById(R.id.labelSamplePicture);

        imgBtnOriginal = findViewById(R.id.imgBtnOriginal);
        imgBtnOriginal.setOnClickListener(v -> zoomImageFromThumb(imgBtnOriginal, originalResult));

        imgBtnSample = findViewById(R.id.imgBtnSample);
        imgBtnSample.setOnClickListener(v -> zoomImageFromThumb(imgBtnSample, sampleResult));

        imgBtnMatch = findViewById(R.id.imgBtnMatch);
        imgBtnMatch.setOnClickListener(v -> zoomImageFromThumb(imgBtnMatch, SURFResult));

        btnDelete = findViewById(R.id.btnDelete);
        btnDelete.setOnClickListener(v -> {
            if (spinnerHistory.isEnabled()) {
                spinnerHistory.setAdapter(history.delete(spinnerHistory.getSelectedItemPosition()));
                updateImage();
            } else
                backgroundedToast(R.string.textEmptyDatabase, Toast.LENGTH_SHORT);
        });
        btnClear = findViewById(R.id.btnClear);
        btnClear.setOnClickListener(v -> {
            if (spinnerHistory.isEnabled()) {
                AlertDialog.Builder customDialog = new AlertDialog.Builder(this);
                View dialogView = getLayoutInflater().inflate(R.layout.clear_dialog, null);
                customDialog.setTitle(getString(R.string.textWarning))
                        .setIcon(R.drawable.warning)
                        .setView(dialogView)
                        .setPositiveButton(R.string.textConfirm, (dialog, which) -> {
                            SQLiteDatabase.deleteDatabase(getDatabasePath(PaperGrainDBHelper.DATABASE_NAME));
                            spinnerHistory.setEnabled(false);
                            spinnerHistory.setAdapter(new SimpleCursorAdapter(result.this, R.layout.spinner, null, null, null, 0));
                            spinnerGroup.setEnabled(false);

                            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES + "/" + getString(R.string.ResultFolderName) + "/");
                            if (storageDir != null)
                                deleteRecursive(storageDir);

                            imgBtnMatch.setImageBitmap(null);
                            imgBtnOriginal.setImageBitmap(null);
                            imgBtnSample.setImageBitmap(null);

                            textSSIMValue.setText(null);
                            backgroundedToast(R.string.textClearSucceeded, Toast.LENGTH_SHORT);
                        })
                        .setNegativeButton(R.string.textCancel, (dialog, which) -> dialog.dismiss())
                        .show();
            } else
                backgroundedToast(R.string.textEmptyDatabase, Toast.LENGTH_SHORT);
        });

        menuBtnBrightness = findViewById(R.id.imBtnBrightness);
        menuBtnRecognition = findViewById(R.id.imBtnRecognition);
        menuBtnResult = findViewById(R.id.imBtnResult);

        BrightnessLayout = findViewById(R.id.BrightnessLayout);
        RecognitionLayout = findViewById(R.id.RecognitionLayout);
        ResultLayout = findViewById(R.id.ResultLayout);

        history = new History(this, SQLiteDatabase.openOrCreateDatabase(this.getDatabasePath(PaperGrainDBHelper.DATABASE_NAME), null));
        shortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);

        Config = getSharedPreferences("Config", MODE_PRIVATE);
    }

    private void zoomImageFromThumb(final View thumbView, String imagePath) {
        // If there's an animation in progress, cancel it
        // immediately and proceed with this one.
        if (currentAnimator != null) {
            currentAnimator.cancel();
        }

        // Load the high-resolution "zoomed-in" image.
        final ImageView expandedImageView = findViewById(R.id.expanded_image);
        Bitmap bm = BitmapFactory.decodeFile(imagePath);
        expandedImageView.setImageBitmap(bm);

        // Calculate the starting and ending bounds for the zoomed-in image.
        // This step involves lots of math. Yay, math.
        final Rect startBounds = new Rect();
        final Rect finalBounds = new Rect();
        final Point globalOffset = new Point();

        // The start bounds are the global visible rectangle of the thumbnail,
        // and the final bounds are the global visible rectangle of the container
        // view. Also set the container view's offset as the origin for the
        // bounds, since that's the origin for the positioning animation
        // properties (X, Y).
        thumbView.getGlobalVisibleRect(startBounds);
        findViewById(R.id.container).getGlobalVisibleRect(finalBounds, globalOffset);
        startBounds.offset(-globalOffset.x, -globalOffset.y);
        finalBounds.offset(-globalOffset.x, -globalOffset.y);

        // Adjust the start bounds to be the same aspect ratio as the final
        // bounds using the "center crop" technique. This prevents undesirable
        // stretching during the animation. Also calculate the start scaling
        // factor (the end scaling factor is always 1.0).
        float startScale;
        if ((float) finalBounds.width() / finalBounds.height() > (float) startBounds.width() / startBounds.height()) {
            // Extend start bounds horizontally
            startScale = (float) startBounds.height() / finalBounds.height();
            float startWidth = startScale * finalBounds.width();
            float deltaWidth = (startWidth - startBounds.width()) / 2;
            startBounds.left -= deltaWidth;
            startBounds.right += deltaWidth;
        } else {
            // Extend start bounds vertically
            startScale = (float) startBounds.width() / finalBounds.width();
            float startHeight = startScale * finalBounds.height();
            float deltaHeight = (startHeight - startBounds.height()) / 2;
            startBounds.top -= deltaHeight;
            startBounds.bottom += deltaHeight;
        }

        // Hide the thumbnail and show the zoomed-in view. When the animation
        // begins, it will position the zoomed-in view in the place of the
        // thumbnail.
        thumbView.setAlpha(0f);
        expandedImageView.setVisibility(View.VISIBLE);

        // Set the pivot point for SCALE_X and SCALE_Y transformations
        // to the top-left corner of the zoomed-in view (the default
        // is the center of the view).
        expandedImageView.setPivotX(0f);
        expandedImageView.setPivotY(0f);

        // Construct and run the parallel animation of the four translation and
        // scale properties (X, Y, SCALE_X, and SCALE_Y).
        AnimatorSet set = new AnimatorSet();
        set
                .play(ObjectAnimator.ofFloat(expandedImageView, View.X, startBounds.left, finalBounds.left))
                .with(ObjectAnimator.ofFloat(expandedImageView, View.Y, startBounds.top, finalBounds.top))
                .with(ObjectAnimator.ofFloat(expandedImageView, View.SCALE_X, startScale, 1f))
                .with(ObjectAnimator.ofFloat(expandedImageView, View.SCALE_Y, startScale, 1f));
        set.setDuration(shortAnimationDuration);
        set.setInterpolator(new DecelerateInterpolator());
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentAnimator = null;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                currentAnimator = null;
            }
        });
        set.start();
        currentAnimator = set;

        // Upon clicking the zoomed-in image, it should zoom back down
        // to the original bounds and show the thumbnail instead of
        // the expanded image.
        final float startScaleFinal = startScale;
        expandedImageView.setOnClickListener(view -> {
            if (currentAnimator != null) {
                currentAnimator.cancel();
            }

            // Animate the four positioning/sizing properties in parallel,
            // back to their original values.
            AnimatorSet set1 = new AnimatorSet();
            set1.play(ObjectAnimator
                    .ofFloat(expandedImageView, View.X, startBounds.left))
                    .with(ObjectAnimator
                            .ofFloat(expandedImageView,
                                    View.Y, startBounds.top))
                    .with(ObjectAnimator
                            .ofFloat(expandedImageView,
                                    View.SCALE_X, startScaleFinal))
                    .with(ObjectAnimator
                            .ofFloat(expandedImageView,
                                    View.SCALE_Y, startScaleFinal));
            set1.setDuration(shortAnimationDuration);
            set1.setInterpolator(new DecelerateInterpolator());
            set1.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    thumbView.setAlpha(1f);
                    expandedImageView.setVisibility(View.GONE);
                    currentAnimator = null;
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    thumbView.setAlpha(1f);
                    expandedImageView.setVisibility(View.GONE);
                    currentAnimator = null;
                }
            });
            set1.start();
            currentAnimator = set1;
        });
    }

    private void initializeSpinner() {
        spinnerHistory.setAdapter(history.getAdapter());
        spinnerHistory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (spinnerHistory.getAdapter().getCount() != 0)
                    spinnerHistory.setSelection(spinnerHistory.getAdapter().getCount() - 1);
                spinnerGroup.setEnabled(true);
                updateImage();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        spinnerGroup.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateImage();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        spinnerGroup.setEnabled(false);
    }

    private void initializeMenuBar() {
        menuBtnRecognition.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setClass(result.this, recognition.class);
            startActivity(intent);
            this.finish();
        });
        menuBtnBrightness.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setClass(result.this, brightness.class);
            startActivity(intent);
            this.finish();
        });
        ResultLayout.setBackgroundColor(this.getColor(R.color.AlphaGray));
    }

    private void backgroundedToast(@StringRes int msg, @DisplayTime int time) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast, findViewById(R.id.custom_toast_container));

        TextView text = layout.findViewById(R.id.textToast);
        text.setText(msg);

        Toast toast = new Toast(getApplicationContext());
        toast.setDuration(time);
        toast.setView(layout);
        toast.show();
    }

    @IntDef({Toast.LENGTH_LONG, Toast.LENGTH_SHORT})
    @Retention(RetentionPolicy.SOURCE)
    private @interface DisplayTime {
    }

    private class History {
        private Context context;
        private String[] name;
        private SimpleCursorAdapter adapter;
        private SQLiteDatabase database;

        History(Context context, SQLiteDatabase database) {
            try {
                String sql = "SELECT " +
                        _ID + "," + _COUNT + "," + COLUMN_NAME_TIME_START +
                        " FROM " +
                        TABLE_NAME +
                        " WHERE " +
                        COLUMN_NAME_DELETED + "=" + 0 +
                        " ORDER BY " +
                        _COUNT;
                Cursor cursor = database.rawQuery(sql, null);
                this.context = context;
                this.name = new String[cursor.getCount()];
                if (cursor.moveToFirst()) {
                    int index = 0;
                    do {
                        this.name[index] = cursor.getString(2);
                        index++;
                    } while (cursor.moveToNext());
                }
                this.adapter = new SimpleCursorAdapter(
                        context,
                        R.layout.spinner,
                        cursor,
                        new String[]{_COUNT, COLUMN_NAME_TIME_START},
                        new int[]{R.id.textCount, R.id.textTime},
                        0);
                this.database = database;
                if (cursor.getCount() == 0) {
                    backgroundedToast(R.string.textEmptyDatabase, Toast.LENGTH_SHORT);
                    spinnerHistory.setEnabled(false);
                    spinnerGroup.setEnabled(false);
                }
            } catch (SQLException e) {
                backgroundedToast(R.string.textEmptyDatabase, Toast.LENGTH_SHORT);
            }
        }

        private void updateOrderAfterDelete() {
            PaperGrainDBHelper.updateCount(database);
        }

        public String[] getName() {
            return name;
        }

        public CursorAdapter getAdapter() {
            return adapter;
        }

        public CursorAdapter delete(int position) {
            String delete = " UPDATE " +
                    TABLE_NAME +
                    " SET " + COLUMN_NAME_DELETED + "=" + 1 +
                    " WHERE " + _COUNT + "=" + (position + 1);
            database.execSQL(delete);
            updateOrderAfterDelete();
            String sql = " SELECT " +
                    _ID + "," + _COUNT + "," + COLUMN_NAME_TIME_START +
                    " FROM " +
                    TABLE_NAME +
                    " WHERE " +
                    COLUMN_NAME_DELETED + "=" + 0 +
                    " ORDER BY " +
                    _COUNT;
            Cursor cursor = database.rawQuery(sql, null);
            backgroundedToast(R.string.textDeleteSucceeded, Toast.LENGTH_SHORT);
            adapter = new SimpleCursorAdapter(
                    context,
                    R.layout.spinner,
                    cursor,
                    new String[]{_COUNT, COLUMN_NAME_TIME_START},
                    new int[]{R.id.textCount, R.id.textTime},
                    0);
            if (cursor.getCount() == 0) {
                backgroundedToast(R.string.textEmptyDatabase, Toast.LENGTH_SHORT);

                labelOriginalPicture.setText(R.string.labelOriginal);
                labelSamplePicture.setText(R.string.labelSample);

                spinnerHistory.setEnabled(false);
                spinnerGroup.setEnabled(false);
            }
            return adapter;
        }

        public SQLiteDatabase getDatabase() {
            return database;
        }
    }
}