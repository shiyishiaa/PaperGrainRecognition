package com.grain.grain.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowMetrics;
import android.view.animation.AnimationUtils;
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

import com.grain.grain.MatchUtils;
import com.grain.grain.R;
import com.grain.grain.io.FileUtils;
import com.grain.grain.io.WriteResult;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.ParsePosition;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    // Path change codes
    private static final int
            ORIGINAL_CHANGED = 0xCC01,
            SAMPLE_CHANGED = 0xCC02;
    // Match codes
    private static final int
            START_MATCHING = 0xDD00,
            ABORT_MATCHING = 0xDD01,
            MATCH_FINISHED = 0xDD02,
            MATCH_ABORTED = 0xDD03,
            WRITE_FINISHED = 0xDD04;
    private static final int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
    private static final ThreadPoolExecutor MatchExecutor = new ThreadPoolExecutor(
            NUMBER_OF_CORES,
            NUMBER_OF_CORES,
            10,
            TimeUnit.MINUTES,
            new ArrayBlockingQueue<>(10),
            new ThreadPoolExecutor.DiscardOldestPolicy());
    private static final ThreadPoolExecutor IOExecutor = new ThreadPoolExecutor(
            2 * NUMBER_OF_CORES + 1,
            3 * NUMBER_OF_CORES,
            10,
            TimeUnit.MINUTES,
            new ArrayBlockingQueue<>(10),
            new ThreadPoolExecutor.DiscardOldestPolicy());
    private Toast toast;
    private SharedPreferences Config;
    // Storage path
    private String originalPath, samplePath;
    // Various widgets
    private Button btnOriginalChoosePicture, btnOriginalOpenCamera, btnOriginalClear;
    private Button btnSampleChoosePicture, btnSampleOpenCamera, btnSampleClear;
    private Button btnStart, btnAbort;
    private ImageButton imgBtnOriginal, imgBtnSample;
    private ImageButton menuBtnBrightness, menuBtnRecognition, menuBtnResult;
    private ImageView expanded_image, loading_image;
    private LinearLayout BrightnessLayout, RecognitionLayout, ResultLayout, MainLayout, LaunchLayout;
    private String start, end;
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
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.CHINA);
    private MatchUtils[] utils;
    private Handler pathHandler = new Handler(Looper.getMainLooper(), msg -> {
        switch (msg.what) {
            case ORIGINAL_CHANGED:
                runOnUiThread(() -> setImageView(imgBtnOriginal, originalPath));
                return true;
            case SAMPLE_CHANGED:
                runOnUiThread(() -> setImageView(imgBtnSample, samplePath));
                return true;
            default:
                return false;
        }
    });
    private Handler toastHandler = new Handler(Looper.getMainLooper());
    private Handler matchHandler = new Handler(Looper.getMainLooper(), msg -> {
        switch (msg.what) {
            default:
                return false;
            case START_MATCHING:
                start = dateFormat.format(Calendar.getInstance().getTime());
                updateStartTime(start.substring(0, start.length() - 4), utils);
                autoCheckMatchingStatus();
                return true;
            case ABORT_MATCHING:
                autoCheckAbortingStatus();
                return true;
            case MATCH_FINISHED:
                end = dateFormat.format(Calendar.getInstance().getTime());
                updateEndTime(end.substring(0, end.length() - 4), utils);
                startWriting();
                autoCheckWritingStatus();
                return true;
            case WRITE_FINISHED:
                backgroundedToast(R.string.WriteDone, Toast.LENGTH_SHORT);
                end = dateFormat.format(Calendar.getInstance().getTime());
                Log.i("Match time cost", Math.abs(
                        dateFormat.parse(start, new ParsePosition(0)).getTime() -
                                dateFormat.parse(end, new ParsePosition(0)).getTime()) + " ms");
                for (MatchUtils util : utils)
                    Log.i("CW-SSIM Values", String.valueOf(util.getCW_SSIMValue()));
                enableLoading(false);
                return true;
            case MATCH_ABORTED:
                end = dateFormat.format(Calendar.getInstance().getTime());
                updateEndTime(end.substring(0, end.length() - 4), utils);
                backgroundedToast(R.string.textProcessAborted, Toast.LENGTH_SHORT);
                return true;
        }
    });

    public static Message createEmptyMessage(int msg) {
        Message message = new Message();
        message.what = msg;
        return message;
    }

    private static Message createMessage(int what, Object obj) {
        Message message = new Message();
        message.what = what;
        message.obj = obj;
        return message;
    }

    public static int getScreenWidth(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = activity.getWindowManager().getCurrentWindowMetrics();
            Insets insets = windowMetrics.getWindowInsets()
                    .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
            return windowMetrics.getBounds().width() - insets.left - insets.right;
        } else {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            return displayMetrics.widthPixels;
        }
    }

    public static int getScreenHeight(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = activity.getWindowManager().getCurrentWindowMetrics();
            Insets insets = windowMetrics.getWindowInsets()
                    .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
            return windowMetrics.getBounds().height() - insets.top - insets.bottom;
        } else {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            return displayMetrics.heightPixels;
        }
    }

    public static Boolean isActivityRunning(Class activityClass, Activity activity) {
        ActivityManager activityManager = (ActivityManager) activity.getBaseContext().getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(Integer.MAX_VALUE);

        for (ActivityManager.RunningTaskInfo task : tasks) {
            if (Objects.requireNonNull(activityClass.getCanonicalName()).equalsIgnoreCase(task.baseActivity.getClassName()))
                return true;
        }

        return false;
    }

    private void autoCheckWritingStatus() {
        matchHandler.postDelayed(() -> {
            if (isWriting())
                autoCheckWritingStatus();
            else
                matchHandler.sendMessage(createEmptyMessage(WRITE_FINISHED));
        }, 1000);
    }

    private boolean isWriting() {
        return IOExecutor.getActiveCount() != 0;
    }

    private boolean isMatching() {
        return MatchExecutor.getActiveCount() != 0;
    }

    private void updateStartTime(String s, MatchUtils[] utils) {
        for (MatchUtils util : utils) {
            util.setStart(s);
        }
    }

    private void updateEndTime(String s, MatchUtils[] utils) {
        for (MatchUtils util : utils) {
            util.setEnd(s);
        }
    }

    private void autoCheckAbortingStatus() {
        matchHandler.postDelayed(() -> {
            if (!isMatching()) {
                matchHandler.sendMessage(createMessage(MATCH_ABORTED, utils));
            } else
                autoCheckAbortingStatus();
        }, 1000);
    }

    private void autoCheckMatchingStatus() {
        matchHandler.postDelayed(() -> {
            if (!isMatching()) {
                matchHandler.sendMessage(createMessage(MATCH_FINISHED, utils));
            } else
                autoCheckMatchingStatus();
        }, 1000);
    }

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
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.e("OpenCV", "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
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
                        finish();
                    }
                }, 2000);
                return true;
            } else {
                MatchUtils.deleteRecursive(getApplicationContext().getCacheDir());
                finish();
            }
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
            if (new File(originalPath).exists()) {
                pathHandler.sendMessage(createEmptyMessage(ORIGINAL_CHANGED));
            }
        if (samplePath != null)
            if (new File(samplePath).exists()) {
                pathHandler.sendMessage(createEmptyMessage(SAMPLE_CHANGED));
            }
    }

    /**
     * Initialize menu bar
     */
    private void initializeMenuBar() {
        menuBtnBrightness.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setClass(recognition.this, brightness.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            overridePendingTransition(R.anim.in_from_left, R.anim.out_to_right);
        });
        menuBtnResult.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setClass(recognition.this, result.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            overridePendingTransition(R.anim.in_from_right, R.anim.out_to_left);
        });
        RecognitionLayout.setBackgroundColor(this.getColor(R.color.AlphaGray));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_ORIGINAL_CAMERA:
                    pathHandler.sendMessage(createEmptyMessage(ORIGINAL_CHANGED));
                    break;
                case REQUEST_SAMPLE_CAMERA:
                    pathHandler.sendMessage(createEmptyMessage(SAMPLE_CHANGED));
                    break;
                case REQUEST_ORIGINAL_IMAGE:
                    FileUtils originalFileUtils = new FileUtils(this);
                    originalPath = originalFileUtils.getPath(data.getData());
                    pathHandler.sendMessage(createEmptyMessage(ORIGINAL_CHANGED));
                    break;
                case REQUEST_SAMPLE_IMAGE:
                    FileUtils sampleFileUtils = new FileUtils(this);
                    samplePath = sampleFileUtils.getPath(data.getData());
                    pathHandler.sendMessage(createEmptyMessage(SAMPLE_CHANGED));
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
            imgBtnOriginal.setImageBitmap(null);
            originalPath = null;
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
            if (originalPath == null || samplePath == null || imgBtnOriginal.getDrawable() == null || imgBtnSample.getDrawable() == null) {
                backgroundedToast(R.string.textNullImage, Toast.LENGTH_SHORT);
                return;
            }
            if (!imgBtnOriginal.getDrawable().getBounds().equals(imgBtnSample.getDrawable().getBounds())) {
                backgroundedToast(R.string.textWrongPicture, Toast.LENGTH_LONG);
                return;
            }
            writeConfig();
            startMatching();
        });

        btnAbort = findViewById(R.id.btAbort);
        btnAbort.setOnClickListener(v -> {
            if (imgBtnSample.getDrawable() == null || imgBtnOriginal.getDrawable() == null)
                return;
            writeConfig();
            stopMatching();
        });

        imgBtnOriginal = findViewById(R.id.imgBtnOriginal);
        imgBtnOriginal.setOnClickListener(v -> runOnUiThread(() -> zoomImage(imgBtnOriginal, originalPath)));
        imgBtnSample = findViewById(R.id.imgBtnSample);
        imgBtnSample.setOnClickListener(v -> runOnUiThread(() -> zoomImage(imgBtnSample, samplePath)));

        expanded_image = findViewById(R.id.expanded_image);
        loading_image = findViewById(R.id.loading_image);

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
        readConfig();
        applyConfig();
    }

    private void zoomImage(final View thumbView, final String path) {
        // If there's an animation in progress, cancel it
        // immediately and proceed with this one.
        if (currentAnimator != null) {
            currentAnimator.cancel();
        }

        // Load the high-resolution "zoomed-in" image.
        final ImageView expandedImageView = findViewById(R.id.expanded_image);
        setImageView(expandedImageView, path);

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
        if (isMatching()) {
            backgroundedToast(R.string.textProcessing, Toast.LENGTH_SHORT);
            return;
        }

        utils = new MatchUtils[14];
        for (int i = 0; i < utils.length; i++) {
            utils[i] = new MatchUtils(getApplicationContext(), originalPath, samplePath);
            MatchExecutor.execute(utils[i]);
        }
        matchHandler.sendMessage(createEmptyMessage(START_MATCHING));
        backgroundedToast(R.string.textStartMatching, Toast.LENGTH_SHORT);
        enableLoading(true);
    }

    private void stopMatching() {
        if (!isMatching()) {
            backgroundedToast(R.string.textNotProcessing, Toast.LENGTH_SHORT);
            return;
        }

        backgroundedToast(R.string.textProcessAborting, Toast.LENGTH_SHORT);
        matchHandler.sendMessage(createEmptyMessage(ABORT_MATCHING));

        final long time_out = 5;//超时时间，自己根据任务特点设置
        //第一步，调用shutdown等待在执行的任务和提交等待的任务执行，同时不允许提交任务
        MatchExecutor.shutdown();
        try {
            if (!MatchExecutor.awaitTermination(time_out, TimeUnit.SECONDS)) {
                //如果等待一段时间后还有任务在执行中被中断或者有任务提交了未执行
                //1.正在执行被中断的任务需要编写任务代码的时候响应中断
                List<Runnable> waitToExecuteTaskList = MatchExecutor.shutdownNow();
                //2.处理提交了未执行的任务，一般情况不会出现
//                for (Runnable runnable : waitToExecuteTaskList) {
//
//                }
            }
        } catch (InterruptedException e) {//如果被中断了
            //1.正在执行被中断的任务需要编写任务代码的时候响应中断
            List<Runnable> waitToExecuteTaskList = MatchExecutor.shutdownNow();
            //2.处理提交了未执行的任务，一般情况不会出现
//            for (Runnable runnable : waitToExecuteTaskList) {
//
//            }
        }
    }

    private void startWriting() {
        Log.i("Match time cost", Math.abs(
                dateFormat.parse(start, new ParsePosition(0)).getTime() -
                        dateFormat.parse(end, new ParsePosition(0)).getTime()) + " ms");
        /* Remove the two maximum values and two minimum values */
        Map<Double, Integer> unsort = new TreeMap<>(), sorted = new TreeMap<>();
        for (int i = 0; i < utils.length; i++)
            unsort.put(utils[i].getCW_SSIMValue(), i);
        int order = 0;
        for (Double aDouble : unsort.keySet()) {
            if (order != 0 && order != 1 && order != 12 && order != 13)
                sorted.put(aDouble, unsort.get(aDouble));
            order++;
        }

        MatchUtils[] midUtils = new MatchUtils[10];
        Iterator<Integer> iterator = sorted.values().iterator();
        int index = 0;
        do {
            midUtils[index] = utils[iterator.next()];
            index++;
        } while (iterator.hasNext());
        utils = midUtils;
        /* Store results */
        WriteResult[] result = new WriteResult[10];
        for (short i = 0; i < result.length; i++) {
            result[i] = new WriteResult(this, utils[i], i);
            IOExecutor.execute(result[i]);
        }
        backgroundedToast(R.string.WriteStart, Toast.LENGTH_SHORT);
    }

    private void enableLoading(boolean toggle) {
        if (toggle) {
            int padding = (int) (getScreenWidth(this) * 0.4);
            loading_image.setPadding(padding, padding, padding, padding);
            loading_image.setAnimation(AnimationUtils.loadAnimation(this, R.anim.loading));
            loading_image.setVisibility(View.VISIBLE);

            expanded_image.setBackgroundColor(this.getColor(R.color.AlphaBlack));
            expanded_image.setVisibility(View.VISIBLE);

            btnOriginalChoosePicture.setEnabled(false);
            btnOriginalOpenCamera.setEnabled(false);
            btnOriginalClear.setEnabled(false);

            btnSampleChoosePicture.setEnabled(false);
            btnSampleOpenCamera.setEnabled(false);
            btnSampleClear.setEnabled(false);

            imgBtnOriginal.setEnabled(false);
            imgBtnSample.setEnabled(false);
        } else {
            loading_image.setAnimation(null);
            loading_image.setVisibility(View.INVISIBLE);

            expanded_image.setBackground(null);
            expanded_image.setVisibility(View.INVISIBLE);

            btnOriginalChoosePicture.setEnabled(true);
            btnOriginalOpenCamera.setEnabled(true);
            btnOriginalClear.setEnabled(true);

            btnSampleChoosePicture.setEnabled(true);
            btnSampleOpenCamera.setEnabled(true);
            btnSampleClear.setEnabled(true);

            imgBtnOriginal.setEnabled(true);
            imgBtnSample.setEnabled(true);
        }
    }

    private File createImageFile(PictureType type) throws IOException {
        // Create an image file name
        String imageFileName = Long.valueOf(System.currentTimeMillis()).toString();
        File storageDir =
                getExternalFilesDir(Environment.DIRECTORY_PICTURES + "/" +
                        getString(R.string.CameraFolderName) + "/" +
                        getString((type == PictureType.Original) ? R.string.OriginalFolderName : R.string.SampleFolderName));
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        if (type == PictureType.Original) {
            originalPath = image.getAbsolutePath();
            pathHandler.sendMessage(createEmptyMessage(ORIGINAL_CHANGED));
        } else {
            samplePath = image.getAbsolutePath();
            pathHandler.sendMessage(createEmptyMessage(SAMPLE_CHANGED));
        }

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
                backgroundedToast(R.string.textFailToSaveImage, Toast.LENGTH_LONG);
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
        if (toast == null)
            toast = new Toast(this);
        toast.setView(layout);
        toast.setDuration(time);
        toast.cancel();
        toastHandler.postDelayed(() -> {
            toast.show();   // 会发现延迟之后就显示出来了
        }, 20);  // 这个时间是自己拍脑袋写的，不影响体验就好，试过使用post也不行
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
