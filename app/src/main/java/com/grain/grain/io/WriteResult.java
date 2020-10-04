package com.grain.grain.io;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.Environment;

import com.grain.grain.MatchUtils;
import com.grain.grain.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.grain.grain.io.Columns.COLUMN_NAME_DELETED;
import static com.grain.grain.io.Columns.COLUMN_NAME_FINISHED;
import static com.grain.grain.io.Columns.COLUMN_NAME_ORIGINAL;
import static com.grain.grain.io.Columns.COLUMN_NAME_SAMPLE;
import static com.grain.grain.io.Columns.COLUMN_NAME_TIME_END;
import static com.grain.grain.io.Columns.COLUMN_NAME_TIME_START;
import static com.grain.grain.io.Columns.TABLE_NAME;

/**
 * Runnable to save match results.
 */
public class WriteResult implements Runnable {
    private Context context;
    private MatchUtils utils;
    private short index;

    /**
     * Construction for instantiate a writer.
     *
     * @param context Application context
     * @param utils   Match result
     * @param index   Order
     */
    public WriteResult(Context context, MatchUtils utils, short index) {
        this.context = context;
        this.utils = utils;
        this.index = index;
    }

    /**
     * Save image lossless to .png format
     *
     * @param bitmap Image to be saved
     * @param file   Image file instance
     * @throws IOException Fail to image
     */
    private void saveBitmap(Bitmap bitmap, File file) throws IOException {
        if (file.createNewFile())
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (IOException ignored) {
            }
        else
            throw new IOException("Fail to create file!");
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void run() {
        try {
            /* Make needed directories */
            File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES + "/" + context.getString(R.string.ResultFolderName) + "/" + utils.getStart());
            File originalDir = new File(storageDir, context.getString(R.string.OriginalFolderName));
            if (!originalDir.exists()) originalDir.mkdirs();
            File SURFDir = new File(storageDir, context.getString(R.string.SURFFolderName));
            if (!SURFDir.exists()) SURFDir.mkdirs();
            File sampleDir = new File(storageDir, context.getString(R.string.SampleFolderName));
            if (!sampleDir.exists()) sampleDir.mkdirs();

            /* Construct Image files */
            File original = new File(originalDir, index + ".png");
            File sample = new File(sampleDir, index + ".png");
            File SURF = new File(SURFDir, index + ".png");

            /* Save images */
            saveBitmap(utils.originalBMP, original);
            saveBitmap(utils.sampleBMP, sample);
            saveBitmap(utils.surfBMP, SURF);

            /* Create Database instance */
            PaperGrainDBHelper helper = new PaperGrainDBHelper(context);
            SQLiteDatabase write = helper.getWritableDatabase();
            ContentValues values = new ContentValues();

            /* Save paths and cw-ssim values to database */
            values.put("original_" + index, original.getAbsolutePath());
            values.put("sample_" + index, sample.getAbsolutePath());
            values.put("surf_" + index, SURF.getAbsolutePath());
            values.put("SSIM_" + index, utils.getCW_SSIMValue());

            /* Save stamps */
            values.put(COLUMN_NAME_TIME_START, utils.getStart());
            values.put(COLUMN_NAME_TIME_END, utils.getEnd());
            values.put(COLUMN_NAME_ORIGINAL, utils.getPath()[0]);
            values.put(COLUMN_NAME_SAMPLE, utils.getPath()[1]);
            values.put(COLUMN_NAME_DELETED, false);
            values.put(COLUMN_NAME_FINISHED, true);

            /* Write data to database */
            if (write.update(TABLE_NAME, values, COLUMN_NAME_TIME_START + " =  " + utils.getStart(), null) == 0)
                write.insert(TABLE_NAME, null, values);

            PaperGrainDBHelper.updateCount(write);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
