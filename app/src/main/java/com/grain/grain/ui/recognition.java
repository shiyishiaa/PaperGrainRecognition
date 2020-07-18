package com.grain.grain.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.grain.grain.R;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public class recognition extends AppCompatActivity {
    private static final int
            REQUEST_WRITE_EXTERNAL_STORAGE = 1,
            REQUEST_CAMERA = 2;

    private Button btnOriginalChoosePicture, btnOriginalOpenCamera, btnSampleChoosePicture, btnSampleOpenCamere;
    private Button btnStart, btnStop;
    private ImageView imgViewOriginalPicture, imgViewSamplePicture;

    private ImageButton imBtnBrightness, imBtnRecognition, imBtnResult;

    private LinearLayout BrightnessLayout, RecognitionLayout, ResultLayout, MainLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recognition);
        initialize();
    }

    private void connect() {
        //原图：选择图片
        btnOriginalChoosePicture = findViewById(R.id.btnOriginalChoosePicture);
        btnOriginalChoosePicture.setOnClickListener(view -> choosePicture(PictureType.Original));
        //原图：打开相机
        btnOriginalOpenCamera = findViewById(R.id.btnOriginalOpenCamera);

        //样图：选择图片
        btnSampleChoosePicture = findViewById(R.id.btnSampleChoosePicture);
        btnSampleChoosePicture.setOnClickListener(view -> choosePicture(PictureType.Sample));
        //样图：打开相机
        btnSampleOpenCamere = findViewById(R.id.btnSampleOpenCamera);

        //开始和停止匹配
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);

        //原图样图显示区
        imgViewOriginalPicture = findViewById(R.id.imgViewOriginalPicture);
        imgViewSamplePicture = findViewById(R.id.imgViewSamplePicture);

        //菜单栏图片按钮
        imBtnBrightness = findViewById(R.id.imBtnBrightness);
        imBtnRecognition = findViewById(R.id.imBtnRecognition);
        imBtnResult = findViewById(R.id.imBtnResult);

        BrightnessLayout = findViewById(R.id.BrightnessLayout);
        RecognitionLayout = findViewById(R.id.RecognitionLayout);
        ResultLayout = findViewById(R.id.ResultLayout);
        MainLayout = findViewById(R.id.MainLayout);
    }

    private void initialize() {
        connect();
        checkPermission();
        initializeMenuBar();
    }

    /**
     * Initialize menu bar
     */
    private void initializeMenuBar() {
        imBtnBrightness.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setClass(recognition.this, brightness.class);
            startActivity(intent);
            this.finish();
            overridePendingTransition(R.anim.in_from_left, R.anim.out_to_right);
        });
        imBtnResult.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setClass(recognition.this, result.class);
            startActivity(intent);
            this.finish();
            overridePendingTransition(R.anim.in_from_right, R.anim.out_to_left);
        });
        RecognitionLayout.setBackgroundColor(getResources().getColor(R.color.AlphaGray));
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
        return false;
    }

    public static String getFilePath(Context context, String dir) {
        String directoryPath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {//判断外部存储是否可用
            directoryPath = context.getExternalFilesDir(dir).getAbsolutePath();
        } else {//没外部存储就使用内部存储
            directoryPath = context.getFilesDir() + File.separator + dir;
        }
        File file = new File(directoryPath);
        if (!file.exists()) {//判断文件目录是否存在
            file.mkdirs();
        }
        return directoryPath;
    }

    public static void getRoot(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            File[] files;
            files = context.getExternalFilesDirs(Environment.MEDIA_MOUNTED);
            for (File file : files) {
                Log.e("main", file.toString());
            }
        }
    }

    private void checkPermission() {
        //检查权限（NEED_PERMISSION）是否被授权 PackageManager.PERMISSION_GRANTED表示同意授权
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            //用户已经拒绝过一次，再次弹出权限申请对话框需要给用户一个解释
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "请开通相关权限，否则无法正常使用本应用！", Toast.LENGTH_SHORT).show();
            }
            //申请权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_EXTERNAL_STORAGE);
        }
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            //用户已经拒绝过一次，再次弹出权限申请对话框需要给用户一个解释
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
                Toast.makeText(this, "请开通相关权限，否则无法正常使用本应用！", Toast.LENGTH_SHORT).show();
            }
            //申请权限
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CAMERA);
        }
    }

    public static void setImageView(final ImageView imageView, String path, String file) {
        final String RootPath = Environment.getExternalStorageDirectory().getAbsolutePath();

        BitmapFactory.Options options = new BitmapFactory.Options();
        //设置图片加载属性:不加载图片内容,只获取图片信息
        options.inJustDecodeBounds = true;
        //加载图片信息
        String ultimatePath = RootPath + "/" + (path.isEmpty() ? "" : "/") + file;
        BitmapFactory.decodeFile(ultimatePath, options);
        //获取图片宽高
        int picWidth = options.outWidth;
        //获取屏幕大小
        int picHeight = options.outHeight;
        //获取宽高
        int width = imageView.getWidth(), height = imageView.getHeight();
        //计算压缩比
        int wr = picWidth / width, hr = picHeight / height, r = 1;
        r = Math.max(Math.max(wr, hr), r);
        //压缩图片
        options.inSampleSize = r;//设置压缩比
        options.inJustDecodeBounds = false;//设置加载图片内容
        Bitmap bm = BitmapFactory.decodeFile(path, options);
        imageView.setImageBitmap(bm);
    }

    /**
     * 从文件管理器选择需要匹配的图片
     */
    private void choosePicture(PictureType type) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*")
                //不允许打开多个文件
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(intent, "请选择图片"), 1);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.stringInstallFileManager, Toast.LENGTH_SHORT).show();
        }
    }

    private void openCamera() {
        //TODO Open camera.
    }

    private void startMatching() {
        //TODO Start matching provided picture.
    }

    private void stopMatching() {
        //TODO Stop  matching provided picture.
    }
}

enum PictureType {
    Original, Sample
}

