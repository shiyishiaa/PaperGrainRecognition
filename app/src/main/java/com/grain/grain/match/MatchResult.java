package com.grain.grain.match;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.Environment;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.grain.grain.R;
import com.grain.grain.io.Columns;
import com.grain.grain.io.PaperGrainDBHelper;

import org.opencv.core.Mat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.grain.grain.io.Columns.COLUMN_NAME_FINISHED;
import static com.grain.grain.io.Columns.COLUMN_NAME_TIME_END;
import static com.grain.grain.io.Columns.COLUMN_NAME_TIME_START;
import static com.grain.grain.io.Columns.TABLE_NAME;

public class MatchResult extends Thread {
    private MatchUtils util;
    private Context context;
    private short index;

    public MatchResult(Context _context, MatchUtils _util, short _index) {
        this.context = _context;
        this.util = _util;
        this.index = _index;
        initPython();
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

    private void initPython() {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(context));
        }
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

    public void writeImages() throws IOException {
        PaperGrainDBHelper helper = new PaperGrainDBHelper(context);
        SQLiteDatabase write = helper.getWritableDatabase();
        ContentValues values = new ContentValues();

        File storageDir =
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES + "/" +
                        context.getString(R.string.ResultFolderName) + "/" +
                        util.getStart());
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

            saveBitmap(util.originalBMP, original);
            saveBitmap(util.sampleBMP, sample);
            saveBitmap(util.surfBMP, SURF);

            util.setCW_SSIMValue(calcCW_SSIMValue(original.getAbsolutePath(), sample.getAbsolutePath(), 30));
            values.put("SSIM_" + index, util.getCW_SSIMValue());
        }
        values.put(COLUMN_NAME_TIME_START, util.getStart());
        values.put(COLUMN_NAME_TIME_END, util.getEnd());
        values.put(Columns.COLUMN_NAME_ORIGINAL, util.getPath()[0]);
        values.put(Columns.COLUMN_NAME_SAMPLE, util.getPath()[1]);
        values.put(Columns.COLUMN_NAME_DELETED, false);
        values.put(COLUMN_NAME_FINISHED, true);
        write.insert(TABLE_NAME, null, values);
        PaperGrainDBHelper.updateCount(write);
    }

    /**
     * Compute the complex wavelet SSIM (CW-SSIM) value from the reference image to the target image.
     *
     * @param img1  Input image to compare the reference image to. This may be a PIL Image object or,
     *              to save time, an SSIMImage object (e.g. the img member of another SSIM object).
     * @param img2  Same as img1
     * @param width width for the wavelet convolution (default: 30)
     * @return CW-SSIM Value
     */
    public double calcCW_SSIMValue(String img1, String img2, int width) {
        return Python.getInstance().getModule("SSIM").callAttr("SSIM", img1).callAttr("cw_ssim_value", img2, width).toDouble();
    }

    public PyObject cvtMat(Mat src) {
        return Python.getInstance().getModule("SSIM").callAttr("cvtMat", src);
    }

    public void setIndex(short index) {
        this.index = index;
    }

    @Override
    public void run() {

    }
}
