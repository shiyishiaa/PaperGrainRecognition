package com.grain.grain.matching;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class matchPicture implements Runnable {
    public static Double match(String pathOriginal, String pathSample) {
        Double outcome = 0.0;
        Bitmap pictureOriginal = BitmapFactory.decodeFile(pathOriginal),
                pictureSample = BitmapFactory.decodeFile(pathSample);
        try {
            String l;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
        return outcome;
    }

    @Override
    public void run() {

    }
}
