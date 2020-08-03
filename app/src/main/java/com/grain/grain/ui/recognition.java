package com.grain.grain.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.icu.text.SimpleDateFormat;
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
import androidx.annotation.StringDef;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import com.grain.grain.FileUtils;
import com.grain.grain.PaperGrainDBHelper;
import com.grain.grain.R;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Date;
import java.util.Locale;

public class recognition extends AppCompatActivity {
    // Request codes
    static final int
            REQUEST_ORIGINAL_IMAGE = 0x1,
            REQUEST_SAMPLE_IMAGE = 0x2,
            REQUEST_ORIGINAL_CAMERA = 0x3,
            REQUEST_SAMPLE_CAMERA = 0x4;
    // Permission codes
    static final int
            PERMISSION_CAMERA = 0xFF00,
            PERMISSION_WRITE_EXTERNAL_STORAGE = 0xFF01,
            PERMISSION_READ_EXTERNAL_STORAGE = 0xFF02;
    // Storage path
    String originalPath, samplePath;
    // Various widgets
    Button btnOriginalChoosePicture, btnOriginalOpenCamera, btnOriginalClear, btnSampleChoosePicture, btnSampleOpenCamera, btnSampleClear;
    Button btnStart, btnStop;
    ImageView imgViewOriginalPicture, imgViewSamplePicture;
    ImageButton imBtnBrightness, imBtnRecognition, imBtnResult;
    LinearLayout BrightnessLayout, RecognitionLayout, ResultLayout, MainLayout, LaunchLayout;
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

        checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
        checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        initializeMenuBar();
    }

    private void connect() {
        btnOriginalChoosePicture = findViewById(R.id.btnOriginalChoose);
        btnOriginalChoosePicture.setOnClickListener(view -> choosePicture(PictureType.Original));
        btnOriginalOpenCamera = findViewById(R.id.btnOriginalCamera);
        btnOriginalOpenCamera.setOnClickListener(view -> {
            checkPermission(Manifest.permission.CAMERA);
            dispatchTakePictureIntent(PictureType.Original);
        });
        btnOriginalClear = findViewById(R.id.btnOriginalClear);
        btnOriginalClear.setOnClickListener(v -> {
            imgViewOriginalPicture.setImageBitmap(null);
            originalPath = null;
        });

        btnSampleChoosePicture = findViewById(R.id.btnSampleChoose);
        btnSampleChoosePicture.setOnClickListener(view -> choosePicture(PictureType.Sample));
        btnSampleOpenCamera = findViewById(R.id.btnSampleCamera);
        btnSampleOpenCamera.setOnClickListener(view -> {
            checkPermission(Manifest.permission.CAMERA);
            dispatchTakePictureIntent(PictureType.Sample);
        });
        btnSampleClear = findViewById(R.id.btnSampleClear);
        btnSampleClear.setOnClickListener(v -> {
            imgViewSamplePicture.setImageBitmap(null);
            samplePath = null;
        });


        btnStart = findViewById(R.id.btnStart);
        btnStart.setOnClickListener(v -> startMatching());

        btnStop = findViewById(R.id.btnStop);
        btnStop.setOnClickListener(v -> stopMatching());

        imgViewOriginalPicture = findViewById(R.id.imgViewOriginalPicture);
        imgViewSamplePicture = findViewById(R.id.imgViewSamplePicture);

        imBtnBrightness = findViewById(R.id.imBtnBrightness);
        imBtnRecognition = findViewById(R.id.imBtnRecognition);
        imBtnResult = findViewById(R.id.imBtnResult);

        BrightnessLayout = findViewById(R.id.BrightnessLayout);
        RecognitionLayout = findViewById(R.id.RecognitionLayout);
        ResultLayout = findViewById(R.id.ResultLayout);
        MainLayout = findViewById(R.id.MainLayout);
        LaunchLayout = findViewById(R.id.LaunchLayout);
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
        RecognitionLayout.setBackgroundColor(this.getColor(R.color.AlphaGray));
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
                    setImageView(imgViewOriginalPicture, originalPath);
                    break;
                case REQUEST_SAMPLE_CAMERA:
                    setImageView(imgViewSamplePicture, samplePath);
                    break;
                case REQUEST_ORIGINAL_IMAGE:
                    FileUtils originalFileUtils = new FileUtils(this);
                    originalPath = originalFileUtils.getPath(data.getData());
                    setImageView(imgViewOriginalPicture, originalPath);
                    break;
                case REQUEST_SAMPLE_IMAGE:
                    FileUtils sampleFileUtils = new FileUtils(this);
                    samplePath = sampleFileUtils.getPath(data.getData());
                    setImageView(imgViewSamplePicture, samplePath);
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
                    Toast.makeText(this, R.string.textCameraGranted, Toast.LENGTH_LONG).show();
                else
                    Toast.makeText(this, R.string.textCameraDenied, Toast.LENGTH_LONG).show();
                break;
            case PERMISSION_READ_EXTERNAL_STORAGE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    Toast.makeText(this, R.string.textReadExternalStorageGranted, Toast.LENGTH_LONG).show();
                else
                    Toast.makeText(this, R.string.textReadExternalStorageDenied, Toast.LENGTH_LONG).show();
                break;
            case PERMISSION_WRITE_EXTERNAL_STORAGE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    Toast.makeText(this, R.string.textWriteExternalStorageGranted, Toast.LENGTH_LONG).show();
                else
                    Toast.makeText(this, R.string.textWriteExternalStorageDenied, Toast.LENGTH_LONG).show();
                break;
        }
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
                Toast.makeText(this, R.string.textAfterDenying, Toast.LENGTH_SHORT).show();
            }
            // 申请权限
            switch (permission) {
                case Manifest.permission.CAMERA:
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
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
            Toast.makeText(this, R.string.textInstallFileManager, Toast.LENGTH_SHORT).show();
        }
    }

    private void startMatching() {
        if (originalPath == null || samplePath == null) {
            Toast.makeText(this, R.string.textNullImage, Toast.LENGTH_SHORT).show();
            return;
        }
        PaperGrainDBHelper helper = new PaperGrainDBHelper(this);
        SQLiteDatabase write = helper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(PaperGrainDBHelper.GrainEntry.COLUMN_NAME_ORIGINAL, originalPath);
        values.put(PaperGrainDBHelper.GrainEntry.COLUMN_NAME_SAMPLE, samplePath);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Date curDate = new Date(System.currentTimeMillis());//获取当前时间
        String str = formatter.format(curDate);

        values.put(PaperGrainDBHelper.GrainEntry.COLUMN_NAME_TIME_START, str);
        values.put(PaperGrainDBHelper.GrainEntry.COLUMN_NAME_DELETED, true);

        write.insert(PaperGrainDBHelper.GrainEntry.TABLE_NAME, null, values);
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
                Toast.makeText(this, R.string.textFailToLoadImage, Toast.LENGTH_LONG).show();
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this, "com.grain.grain.provider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, (type == PictureType.Original) ? REQUEST_ORIGINAL_CAMERA : REQUEST_SAMPLE_CAMERA);
            }
        }
    }

    private enum PictureType {Original, Sample}

    @StringDef({Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE})
    @Retention(RetentionPolicy.SOURCE)
    private @interface Permission {
    }
}



