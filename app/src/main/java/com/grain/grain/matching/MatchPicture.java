package com.grain.grain.matching;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class MatchPicture implements Runnable {
    String pathOriginal, pathSample;
    double outcome;

    MatchPicture(String Original, String Sample) {
        this.pathOriginal = Original;
        this.pathSample = Sample;
    }

    public synchronized void match() {
        Bitmap pictureOriginal = BitmapFactory.decodeFile(pathOriginal),
                pictureSample = BitmapFactory.decodeFile(pathSample);

    }

    @Override
    public void run() {
        match();
    }
}
