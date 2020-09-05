package com.grain.grain.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.StringDef;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import com.grain.grain.Columns;
import com.grain.grain.FileUtils;
import com.grain.grain.PaperGrainDBHelper;
import com.grain.grain.R;
import com.grain.grain.matching.MatchUtils;

import org.jetbrains.annotations.NotNull;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class recognition extends AppCompatActivity {
    // Request codes
    private static final int
            REQUEST_ORIGINAL_IMAGE = 0x1,
            REQUEST_SAMPLE_IMAGE = 0x2,
            REQUEST_ORIGINAL_CAMERA = 0x3,
            REQUEST_SAMPLE_CAMERA = 0x4;
    // Permission codes
    private static final int
            PERMISSION_CAMERA = 0xFF00,
            PERMISSION_WRITE_EXTERNAL_STORAGE = 0xFF01,
            PERMISSION_READ_EXTERNAL_STORAGE = 0xFF02;
    private SharedPreferences Config;
    // Storage path
    private String originalPath, samplePath;
    // Various widgets
    private Button btnOriginalChoosePicture, btnOriginalOpenCamera, btnOriginalClear;
    private Button btnSampleChoosePicture, btnSampleOpenCamera, btnSampleClear;
    private Button btnStart, btnStop;
    private ImageButton imgBtnOriginal, imgBtnSample;
    private ImageButton menuBtnBrightness, menuBtnRecognition, menuBtnResult;
    private LinearLayout BrightnessLayout, RecognitionLayout, ResultLayout, MainLayout, LaunchLayout;
    // xStart stores the location where swipe gesture starts.
    private float xStart = 0;
    // xEnd stores the location where swipe gesture ends.
    @SuppressWarnings("FieldCanBeLocal")
    private float xEnd = 0;
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                Log.i("OpenCV", "OpenCV loaded successfully");
            } else {
                super.onManagerConnected(status);
            }
        }
    };
    private boolean mBackKeyPressed;
    private Animator currentAnimator;
    private int shortAnimationDuration;

    /**
     * Load image with compression to save time.
     *
     * @param imageView the ImageView to be set
     * @param imagePath the path of image to be loaded
     */
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recognition);
        initialize();
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

    private void applyConfig() {
        if (originalPath != null)
            if (new File(originalPath).exists())
                setImageView(imgBtnOriginal, originalPath);
        if (samplePath != null)
            if (new File(samplePath).exists())
                setImageView(imgBtnSample, samplePath);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.e("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    /**
     * Initialize menu bar
     */
    private void initializeMenuBar() {
        menuBtnBrightness.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setClass(recognition.this, brightness.class);
            startActivity(intent);
            this.finish();
            overridePendingTransition(R.anim.in_from_left, R.anim.out_to_right);
        });
        menuBtnResult.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setClass(recognition.this, result.class);
            startActivity(intent);
            this.finish();
            overridePendingTransition(R.anim.in_from_right, R.anim.out_to_left);
        });
        RecognitionLayout.setBackgroundColor(this.getColor(R.color.AlphaGray));
    }

    @Override
    public boolean onTouchEvent(@NotNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN)
            // Press start location
            xStart = event.getX();
        else if (event.getAction() == MotionEvent.ACTION_UP) {
            // Press end location
            xEnd = event.getX();

            // Change interface
            if (Math.abs(xEnd - xStart) >= getResources().getInteger(R.integer.minimum_move_distance)) {
                Intent intent = new Intent();
                if (xStart < xEnd) {
                    intent.setClass(recognition.this, brightness.class);
                    startActivity(intent);
                    this.finish();
                    overridePendingTransition(R.anim.in_from_left, R.anim.out_to_right);
                } else if (xStart > xEnd) {
                    intent.setClass(recognition.this, result.class);
                    startActivity(intent);
                    this.finish();
                    overridePendingTransition(R.anim.in_from_right, R.anim.out_to_left);
                }
            }
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_ORIGINAL_CAMERA:
                    setImageView(imgBtnOriginal, originalPath);
                    break;
                case REQUEST_SAMPLE_CAMERA:
                    setImageView(imgBtnSample, samplePath);
                    break;
                case REQUEST_ORIGINAL_IMAGE:
                    FileUtils originalFileUtils = new FileUtils(this);
                    originalPath = originalFileUtils.getPath(data.getData());
                    setImageView(imgBtnOriginal, originalPath);
                    break;
                case REQUEST_SAMPLE_IMAGE:
                    FileUtils sampleFileUtils = new FileUtils(this);
                    samplePath = sampleFileUtils.getPath(data.getData());
                    setImageView(imgBtnSample, samplePath);
                    break;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_CAMERA:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    backgroundedToast(R.string.textCameraGranted, Toast.LENGTH_LONG);
                else
                    backgroundedToast(R.string.textCameraDenied, Toast.LENGTH_LONG);
                break;
            case PERMISSION_READ_EXTERNAL_STORAGE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    backgroundedToast(R.string.textReadExternalStorageGranted, Toast.LENGTH_LONG);
                else
                    backgroundedToast(R.string.textReadExternalStorageDenied, Toast.LENGTH_LONG);
                break;
            case PERMISSION_WRITE_EXTERNAL_STORAGE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    backgroundedToast(R.string.textWriteExternalStorageGranted, Toast.LENGTH_LONG);
                else
                    backgroundedToast(R.string.textWriteExternalStorageDenied, Toast.LENGTH_LONG);
                break;
        }
    }

    private void initialize() {
        connect();
        readConfig();
        applyConfig();

        checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
        checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        initializeMenuBar();
    }

    private void connect() {
        btnOriginalChoosePicture = findViewById(R.id.btnOriginalChoose);
        btnOriginalChoosePicture.setOnClickListener(view -> choosePicture(PictureType.Original));
        btnOriginalOpenCamera = findViewById(R.id.btnOriginalCamera);
        btnOriginalOpenCamera.setOnClickListener(view -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                backgroundedToast(R.string.textAllowCamera, Toast.LENGTH_LONG);
                checkPermission(Manifest.permission.CAMERA);

            } else
                dispatchTakePictureIntent(PictureType.Original);
        });
        btnOriginalClear = findViewById(R.id.btnOriginalClear);
        btnOriginalClear.setOnClickListener(v -> {
//            imgBtnOriginal.setImageBitmap(null);
//            originalPath = null;
            MatchUtils utils = new MatchUtils(originalPath, samplePath);
            utils.run();

            //backgroundedToast(utils.value.toString(), Toast.LENGTH_LONG);
            imgBtnOriginal.setImageBitmap(utils.bmp1);
            imgBtnSample.setImageBitmap(utils.surf);
            backgroundedToast(utils.ssim.toString(), Toast.LENGTH_LONG);
        });

        btnSampleChoosePicture = findViewById(R.id.btnSampleChoose);
        btnSampleChoosePicture.setOnClickListener(view -> choosePicture(PictureType.Sample));
        btnSampleOpenCamera = findViewById(R.id.btnSampleCamera);
        btnSampleOpenCamera.setOnClickListener(view -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                backgroundedToast(R.string.textAllowCamera, Toast.LENGTH_LONG);
                checkPermission(Manifest.permission.CAMERA);
            } else
                dispatchTakePictureIntent(PictureType.Sample);
        });
        btnSampleClear = findViewById(R.id.btnSampleClear);
        btnSampleClear.setOnClickListener(v -> {
            imgBtnSample.setImageBitmap(null);
            samplePath = null;
        });


        btnStart = findViewById(R.id.btnStart);
        btnStart.setOnClickListener(v -> {
            if (imgBtnSample.getDrawable() == null || imgBtnOriginal.getDrawable() == null)
                backgroundedToast(R.string.textNullImage, Toast.LENGTH_SHORT);
            writeConfig();
            startMatching();
        });

        btnStop = findViewById(R.id.btnStop);
        btnStop.setOnClickListener(v -> {
            if (imgBtnSample.getDrawable() == null || imgBtnOriginal.getDrawable() == null)
                backgroundedToast(R.string.textNullImage, Toast.LENGTH_SHORT);
            writeConfig();
            stopMatching();
        });

        imgBtnOriginal = findViewById(R.id.imgBtnOriginal);
        imgBtnOriginal.setOnClickListener(v -> zoomImageFromThumb(imgBtnOriginal, originalPath));
        imgBtnSample = findViewById(R.id.imgBtnSample);
        imgBtnSample.setOnClickListener(v -> zoomImageFromThumb(imgBtnSample, samplePath));

        menuBtnBrightness = findViewById(R.id.imBtnBrightness);
        menuBtnRecognition = findViewById(R.id.imBtnRecognition);
        menuBtnResult = findViewById(R.id.imBtnResult);

        BrightnessLayout = findViewById(R.id.BrightnessLayout);
        RecognitionLayout = findViewById(R.id.RecognitionLayout);
        ResultLayout = findViewById(R.id.ResultLayout);
        MainLayout = findViewById(R.id.MainLayout);
        LaunchLayout = findViewById(R.id.LaunchLayout);

        Config = getSharedPreferences("Config", MODE_PRIVATE);
        shortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);
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

    /**
     * Check if camera permission is given.
     */
    private void checkPermission(@Permission String permission) {
        // 检查权限（NEED_PERMISSION）是否被授权 PackageManager.PERMISSION_GRANTED表示同意授权
        if (ActivityCompat.checkSelfPermission(this,
                permission) != PackageManager.PERMISSION_GRANTED) {
            // 用户已经拒绝过一次，再次弹出权限申请对话框需要给用户一个解释
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    permission)) {
                backgroundedToast(R.string.textAfterDenying, Toast.LENGTH_SHORT);
            }
            // 申请权限
            switch (permission) {
                case Manifest.permission.CAMERA:
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.CAMERA},
                            PERMISSION_CAMERA);
                    break;
                case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            PERMISSION_WRITE_EXTERNAL_STORAGE);
                    break;
                case Manifest.permission.READ_EXTERNAL_STORAGE:
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            PERMISSION_READ_EXTERNAL_STORAGE);
                    break;
            }
        }
    }

    /**
     * Choose image from file manager.
     */
    private void choosePicture(PictureType type) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*")
                //不允许打开多个文件
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            if (type == PictureType.Original)
                startActivityForResult(intent, REQUEST_ORIGINAL_IMAGE);
            else if (type == PictureType.Sample)
                startActivityForResult(intent, REQUEST_SAMPLE_IMAGE);
        } catch (ActivityNotFoundException e) {
            backgroundedToast(R.string.textInstallFileManager, Toast.LENGTH_SHORT);
        }
    }

    private void startMatching() {
        if (originalPath == null || samplePath == null) {
            backgroundedToast(R.string.textNullImage, Toast.LENGTH_SHORT);
            return;
        }
        backgroundedToast(R.string.textStartMatching, Toast.LENGTH_SHORT);
        PaperGrainDBHelper helper = new PaperGrainDBHelper(this);
        SQLiteDatabase write = helper.getWrite();

        ContentValues values = new ContentValues();
        values.put(Columns.COLUMN_NAME_ORIGINAL, originalPath);
        values.put(Columns.COLUMN_NAME_SAMPLE, samplePath);
        values.put(Columns.COLUMN_NAME_TIME_START,
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).
                        format(Calendar.getInstance().getTime()));
        values.put(Columns.COLUMN_NAME_FINISHED, false);
        values.put(Columns.COLUMN_NAME_DELETED, false);
        write.insert(Columns.TABLE_NAME, null, values);

        // Resort the data after odd out the deleted records.
        PaperGrainDBHelper.updateCount(write);
    }

    private void stopMatching() {
        //TODO Stop  matching provided image.
    }

    private File createImageFile(PictureType type) throws IOException {
        // Create an image file name
        String imageFileName = Long.valueOf(System.currentTimeMillis()).toString();
        File storageDir =
                getExternalFilesDir(Environment.DIRECTORY_PICTURES + "/" +
                        getString((type == PictureType.Original) ? R.string.OriginalFolderName : R.string.SampleFolderName));
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        if (type == PictureType.Original)
            originalPath = image.getAbsolutePath();
        else
            samplePath = image.getAbsolutePath();

        return image;
    }

    private void dispatchTakePictureIntent(PictureType type) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile(type);
            } catch (IOException ex) {
                // Error occurred while creating the File
                backgroundedToast(R.string.textFailToLoadImage, Toast.LENGTH_LONG);
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this, "com.grain.grain.provider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, (type == PictureType.Original) ? REQUEST_ORIGINAL_CAMERA : REQUEST_SAMPLE_CAMERA);
            }
        }
    }

    private void backgroundedToast(@StringRes int msg, @DisplayTime int time) {
        backgroundedToast(this.getString(msg), time);
    }

    private void backgroundedToast(String msg, @DisplayTime int time) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast, findViewById(R.id.custom_toast_container));

        TextView text = layout.findViewById(R.id.textToast);
        text.setText(msg);

        Toast toast = new Toast(getApplicationContext());
        toast.setDuration(time);
        toast.setView(layout);
        toast.show();
    }

    private enum PictureType {Original, Sample}

    @StringDef({Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE})
    @Retention(RetentionPolicy.SOURCE)
    private @interface Permission {
    }

    @IntDef({Toast.LENGTH_LONG, Toast.LENGTH_SHORT})
    @Retention(RetentionPolicy.SOURCE)
    private @interface DisplayTime {
    }
}
