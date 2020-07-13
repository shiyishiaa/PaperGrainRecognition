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
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.grain.grain.R;

import java.io.File;

public class recognition extends AppCompatActivity {
    private static final int
            REQUEST_WRITE_EXTERNAL_STORAGE = 1,
            REQUEST_CAMERA = 2;

    private int width, height;

    private Button btnOriginalChoosePicture, btnOriginalOpenCamera, btnSampleChoosePicture, btnSampleOpenCamere;
    private Button btnStart, btnStop;
    private ImageView imgViewOriginalPicture, imgViewSamplePicture;

    private ImageButton imBtnBrightness, imBtnRecognition, imBtnResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recognition);
        initial();

        checkPermission();
    }

    private void initial() {
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
        imgViewOriginalPicture.post(() -> getViewWidthAndHeight(imgViewOriginalPicture));
        imgViewSamplePicture = findViewById(R.id.imgViewSamplePicture);
        imgViewSamplePicture.post(() -> getViewWidthAndHeight(imgViewSamplePicture));

        //菜单栏图片按钮
        imBtnBrightness = findViewById(R.id.imBtnBrightness);
        imBtnRecognition = findViewById(R.id.imBtnRecognition);
        imBtnResult = findViewById(R.id.imBtnResult);
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

    private void setImageView(final ImageView imageView, String path, String file) {
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
        //获取窗口管理器
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        //计算压缩比
        int wr = picWidth / width, hr = picHeight / height, r = 1;
        r = Math.max(Math.max(wr, hr), r);
        //压缩图片
        options.inSampleSize = r;//设置压缩比
        options.inJustDecodeBounds = false;//设置加载图片内容
        Bitmap bm = BitmapFactory.decodeFile(path, options);
        imgViewOriginalPicture.setImageBitmap(bm);
    }

    /**
     * 获取控件宽、高
     */
    private void getViewWidthAndHeight(View view) {
        width = view.getWidth();
        height = view.getHeight();
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

