package com.grain.grain.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import com.grain.grain.FileUtils;
import com.grain.grain.R;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class recognition extends AppCompatActivity {
    // Request codes
    static final int
            REQUEST_ORIGINAL_IMAGE = 0x1,
            REQUEST_SAMPLE_IMAGE = 0x2,
            REQUEST_ORIGINAL_CAMERA = 0x3,
            REQUEST_SAMPLE_CAMERA = 0x4,
            REQUEST_WRITE_EXTERNAL_STORAGE = 0x5;
    // Permission codes
    static final int
            PERMISSION_CAMERA = 1;
    // Storage path
    String mCurrentPhotoPath;
    // Various widgets
    Button btnOriginalChoosePicture, btnOriginalOpenCamera, btnSampleChoosePicture, btnSampleOpenCamere;
    Button btnStart, btnStop;
    ImageView imgViewOriginalPicture, imgViewSamplePicture;
    ImageButton imBtnBrightness, imBtnRecognition, imBtnResult;
    LinearLayout BrightnessLayout, RecognitionLayout, ResultLayout, MainLayout;
    // xStart stores the location where swipe gesture starts.
    float xStart = 0;
    // xEnd stores the location where swipe gesture ends.
    @SuppressWarnings("FieldCanBeLocal")
    float xEnd = 0;

    /**
     * Load image with compression to save time.
     *
     * @param imageView the ImageView to be set
     * @param imagePath the path of image to be loaded
     */
    public static void setImageView(final ImageView imageView, String imagePath) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        //设置图片加载属性:不加载图片内容,只获取图片信息
        options.inJustDecodeBounds = true;
        //加载图片信息
        BitmapFactory.decodeFile(imagePath, options);
        //获取图片宽高
        int picWidth = options.outWidth;
        int picHeight = options.outHeight;
        //获取宽高
        int width = imageView.getWidth(), height = imageView.getHeight();
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

    private void initialize() {
        connect();
        checkPermission();
        initializeMenuBar();
    }

    private void connect() {
        //原图：选择图片
        btnOriginalChoosePicture = findViewById(R.id.btnOriginalChoosePicture);
        btnOriginalChoosePicture.setOnClickListener(view -> choosePicture(PictureType.Original));
        //原图：打开相机
        btnOriginalOpenCamera = findViewById(R.id.btnOriginalOpenCamera);
        btnOriginalOpenCamera.setOnClickListener(view -> {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA);
            else
                dispatchTakePictureIntent(PictureType.Original);
        });

        //样图：选择图片
        btnSampleChoosePicture = findViewById(R.id.btnSampleChoosePicture);
        btnSampleChoosePicture.setOnClickListener(view -> choosePicture(PictureType.Sample));
        //样图：打开相机
        btnSampleOpenCamere = findViewById(R.id.btnSampleOpenCamera);
        btnSampleOpenCamere.setOnClickListener(view -> {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA);
            else
                dispatchTakePictureIntent(PictureType.Sample);
        });

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

    /**
     * Swipe to change interface.
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
                    setImageView(imgViewOriginalPicture, mCurrentPhotoPath);
                    break;
                case REQUEST_SAMPLE_CAMERA:
                    setImageView(imgViewSamplePicture, mCurrentPhotoPath);
                    break;
                case REQUEST_ORIGINAL_IMAGE:
                    FileUtils originalFileUtils = new FileUtils(this);
                    String originalPath = originalFileUtils.getPath(data.getData());
                    setImageView(imgViewOriginalPicture, originalPath);
                    break;
                case REQUEST_SAMPLE_IMAGE:
                    FileUtils sampleFileUtils = new FileUtils(this);
                    String samplePath = sampleFileUtils.getPath(data.getData());
                    setImageView(imgViewSamplePicture, samplePath);
                    break;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CAMERA) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.textCameraGranted, Toast.LENGTH_LONG).show();
                Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(cameraIntent, REQUEST_ORIGINAL_CAMERA);
            } else {
                Toast.makeText(this, R.string.textCameraDenied, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Check if camera permission is given.
     */
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
                    REQUEST_ORIGINAL_CAMERA);
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
            Toast.makeText(this, R.string.textInstallFileManager, Toast.LENGTH_SHORT).show();
        }
    }

    private void startMatching() {
        //TODO Start matching provided image.
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
        mCurrentPhotoPath = image.getAbsolutePath();
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
                Toast.makeText(this, R.string.textFailToLoadImage, Toast.LENGTH_LONG).show();
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this, "com.grain.grain.provider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, type == PictureType.Original ? REQUEST_ORIGINAL_CAMERA : REQUEST_SAMPLE_CAMERA);
            }
        }
    }

    private enum PictureType {
        Original, Sample
    }
}



