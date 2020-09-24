package com.grain.grain.match;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.Environment;

import com.grain.grain.R;
import com.grain.grain.io.Columns;
import com.grain.grain.io.PaperGrainDBHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.grain.grain.io.Columns.COLUMN_NAME_FINISHED;
import static com.grain.grain.io.Columns.COLUMN_NAME_TIME_END;
import static com.grain.grain.io.Columns.COLUMN_NAME_TIME_START;
import static com.grain.grain.io.Columns.TABLE_NAME;

public class WriteResult extends Thread {
    private Context context;
    private MatchUtils utils;
    private short index;

    public WriteResult(Context context, MatchUtils utils, short index) {
        this.context = context;
        this.utils = utils;
        this.index = index;
    }

    public void writeImages() throws IOException {
        PaperGrainDBHelper helper = new PaperGrainDBHelper(context);
        SQLiteDatabase write = helper.getWritableDatabase();
        ContentValues values = new ContentValues();

        File storageDir =
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES + "/" +
                        context.getString(R.string.ResultFolderName) + "/" +
                        utils.getStart());
        File originalDir = new File(storageDir, context.getString(R.string.OriginalFolderName));
        File sampleDir = new File(storageDir, context.getString(R.string.SampleFolderName));
        File SURFDir = new File(storageDir, context.getString(R.string.SURFFolderName));
        if (originalDir.mkdirs() && sampleDir.mkdirs() && SURFDir.mkdirs()) {
            File original = new File(originalDir, index + ".png");
            File sample = new File(sampleDir, index + ".png");
            File SURF = new File(SURFDir, index + ".png");

            values.put("original_" + index, original.getAbsolutePath());
            values.put("sample_" + index, sample.getAbsolutePath());
            values.put("surf_" + index, SURF.getAbsolutePath());
            values.put("SSIM_" + index, utils.getCW_SSIMValue());

            saveBitmap(utils.originalBMP, original);
            saveBitmap(utils.sampleBMP, sample);
            saveBitmap(utils.surfBMP, SURF);
        }
        values.put(COLUMN_NAME_TIME_START, utils.getStart());
        values.put(COLUMN_NAME_TIME_END, utils.getEnd());
        values.put(Columns.COLUMN_NAME_ORIGINAL, utils.getPath()[0]);
        values.put(Columns.COLUMN_NAME_SAMPLE, utils.getPath()[1]);
        values.put(Columns.COLUMN_NAME_DELETED, false);
        values.put(COLUMN_NAME_FINISHED, true);
        write.insert(TABLE_NAME, null, values);
        PaperGrainDBHelper.updateCount(write);
    }

    private void saveBitmap(Bitmap bitmap, File file) throws IOException {
        if (file.createNewFile())
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                // PNG is a lossless format, the compression factor (100) is ignored
            } catch (IOException ignored) {
            }
        else
            throw new IOException("Fail to create file!");
    }

    @Override
    public void run() {

    }
}
