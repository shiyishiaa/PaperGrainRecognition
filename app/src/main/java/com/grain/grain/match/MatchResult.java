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

public class MatchResult extends Thread {
    private boolean result;
    private MatchUtils[] utils;
    private Context context;

    public MatchResult(Context _context, MatchUtils[] _utils) {
        this.context = _context;
        this.utils = _utils;
    }

    private static double getWeight(double ssim) {
        final double errorWeight = -3.0, ambiguousWeight = 0.0, likelyWeigh = 0.2, rightWeight = 0.7;
        final double lowerBound = 0.5, upperBound = 0.8, trueBound = 0.9;
        if (ssim >= trueBound)
            return rightWeight;
        else if (ssim > upperBound)
            return likelyWeigh;
        else if (ssim >= lowerBound)
            return ambiguousWeight;
        else
            return errorWeight;

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

    private void writeImages() throws IOException {
        PaperGrainDBHelper helper = new PaperGrainDBHelper(context);
        SQLiteDatabase write = helper.getWritableDatabase();
        ContentValues values = new ContentValues();

        File storageDir =
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES + "/" +
                        context.getString(R.string.ResultFolderName) + "/" +
                        utils[0].getStart());
        File originalDir = new File(storageDir, context.getString(R.string.OriginalFolderName));
        File sampleDir = new File(storageDir, context.getString(R.string.SampleFolderName));
        File SURFDir = new File(storageDir, context.getString(R.string.SURFFolderName));
        if (originalDir.mkdirs() && sampleDir.mkdirs() && SURFDir.mkdirs())
            for (int i = 0; i < utils.length; i++) {
                File original = new File(originalDir, i + ".png");
                File sample = new File(sampleDir, i + ".png");
                File SURF = new File(SURFDir, i + ".png");

                values.put("original_" + i, original.getAbsolutePath());
                values.put("sample_" + i, sample.getAbsolutePath());
                values.put("surf_" + i, SURF.getAbsolutePath());

                values.put("SSIM_" + i, utils[i].getSSIMValue());

                saveBitmap(utils[i].originalBMP, original);
                saveBitmap(utils[i].sampleBMP, sample);
                saveBitmap(utils[i].surfBMP, SURF);
            }
        values.put(COLUMN_NAME_TIME_START, utils[0].getStart());
        values.put(COLUMN_NAME_TIME_END, utils[0].getEnd());
        values.put(Columns.COLUMN_NAME_ORIGINAL, utils[0].getPath()[0]);
        values.put(Columns.COLUMN_NAME_SAMPLE, utils[0].getPath()[1]);
        values.put(Columns.COLUMN_NAME_DELETED, false);
        values.put(COLUMN_NAME_FINISHED, true);
        write.insert(TABLE_NAME, null, values);
        PaperGrainDBHelper.updateCount(write);
    }


    public boolean getResult() {
        return result;
    }

    public synchronized void calcResult() {
        final double target = 10.0, lowest = 3.0;
        double ruler = 7.0;
        for (MatchUtils util : utils) {
            ruler += getWeight(util.getSSIMValue());
            if (ruler >= target)
                result = true;
            else if (ruler <= lowest)
                result = false;
        }
        result = false;
    }

    @Override
    public void run() {
        calcResult();
        try {
            writeImages();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
