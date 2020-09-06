package com.grain.grain.matching;

import android.graphics.Bitmap;
import android.util.Log;

import org.jetbrains.annotations.NotNull;
import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.Features2d;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.xfeatures2d.SURF;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.opencv.core.Core.add;
import static org.opencv.core.Core.divide;
import static org.opencv.core.Core.mean;
import static org.opencv.core.Core.multiply;
import static org.opencv.core.Core.subtract;
import static org.opencv.imgcodecs.Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE;
import static org.opencv.imgcodecs.Imgcodecs.imread;
import static org.opencv.imgproc.Imgproc.COLOR_GRAY2RGBA;
import static org.opencv.imgproc.Imgproc.COLOR_RGB2BGRA;
import static org.opencv.imgproc.Imgproc.GaussianBlur;
import static org.opencv.imgproc.Imgproc.calcHist;
import static org.opencv.imgproc.Imgproc.cvtColor;
import static org.opencv.imgproc.Imgproc.line;
import static org.opencv.imgproc.Imgproc.threshold;

public class MatchUtils extends Thread {
    public Bitmap originalBMP, sampleBMP, surfBMP;
    public Double ssimValue;
    private String original, sample;

    public MatchUtils(String _original, String _sample) {
        if (_original != null)
            setOriginal(_original);
        if (_sample != null)
            setSample(_sample);
    }

    private static double autoGetThreshold(Mat src) {
        List<Scalar> maximum = maximum(src);

        if (maximum.size() < 2) return -1;

        double firstX = 0, secondX = 0;
        double firstY = 0, secondY = 0;
        for (Scalar s : maximum) {
            double nowValue = src.get((int) s.val[0], 0)[0];
            if (nowValue > firstY)
                firstX = s.val[0];
            else if (nowValue > secondY)
                secondX = s.val[0];
        }
        return Math.max(firstX, secondX) - Math.abs(firstX - secondX) / 3;
    }

    public static Scalar getMSSIM(Mat image1, Mat image2) {
        Scalar C1 = new Scalar(6.5025), C2 = new Scalar(58.5225);
        int d = CvType.CV_32F;

        Mat I1 = new Mat(), I2 = new Mat();
        image1.convertTo(I1, d);           // cannot calculate on one byte large values
        image2.convertTo(I2, d);

        Mat I2_2 = I2.mul(I2);        // I2^2
        Mat I1_2 = I1.mul(I1);        // I1^2
        Mat I1_I2 = I1.mul(I2);        // I1 * I2

        Mat mu1 = new Mat(), mu2 = new Mat();   // PRELIMINARY COMPUTING
        GaussianBlur(I1, mu1, new Size(11, 11), 1.5);
        GaussianBlur(I2, mu2, new Size(11, 11), 1.5);

        Mat mu1_2 = mu1.mul(mu1);
        Mat mu2_2 = mu2.mul(mu2);
        Mat mu1_mu2 = mu1.mul(mu2);

        Mat sigma1_2 = new Mat(), sigma2_2 = new Mat(), sigma12 = new Mat();

        GaussianBlur(I1_2, sigma1_2, new Size(11, 11), 1.5);
        subtract(sigma1_2, mu1_2, sigma1_2);

        GaussianBlur(I2_2, sigma2_2, new Size(11, 11), 1.5);
        subtract(sigma2_2, mu2_2, sigma2_2);

        GaussianBlur(I1_I2, sigma12, new Size(11, 11), 1.5);
        subtract(sigma12, mu1_mu2, sigma12);

        ///////////////////////////////// FORMULA ////////////////////////////////
        Mat t1 = new Mat(), t2 = new Mat(), t3 = new Mat();

        multiply(mu1_mu2, new Scalar(2), mu1_mu2);
        add(mu1_mu2, C1, t1);

        multiply(sigma12, new Scalar(2), sigma12);
        add(sigma12, C2, t2);

        t3 = t1.mul(t2);       // t3 = ((2*mu1_mu2 + C1).*(2*sigma12 + C2))

        add(mu1_2, mu2_2, t1);
        add(t1, C1, t1);

        add(sigma1_2, sigma2_2, t2);
        add(t2, C2, t2);

        t1 = t1.mul(t2);               // t1 =((mu1_2 + mu2_2 + C1).*(sigma1_2 + sigma2_2 + C2))

        Mat ssim_map = new Mat();
        divide(t3, t1, ssim_map);      // ssim_map =  t3./t1;

        return mean(ssim_map);
    }

    public static Bitmap matToBitmap(Mat mat) {
        Bitmap bmp = null;
        Mat canvas = new Mat(mat.rows(), mat.cols(), CvType.CV_8U, new Scalar(4));
        try {
            if (mat.channels() == 3)// RGB picture
                cvtColor(mat, canvas, COLOR_RGB2BGRA);
            else // Grayscale picture
                cvtColor(mat, canvas, COLOR_GRAY2RGBA, 4);
            bmp = Bitmap.createBitmap(canvas.cols(), canvas.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(canvas, bmp);
        } catch (CvException ignored) {
        }
        return bmp;
    }

    public static Mat surf(Mat imgObject, Mat imgScene) {
        //-- 步骤1：使用SURF Detector检测关键点，计算描述符
        double hessianThreshold = 400;
        int nOctaves = 4;
        int nOctaveLayers = 3;
        SURF detector = SURF.create(hessianThreshold, nOctaves, nOctaveLayers, false, false);
        MatOfKeyPoint keyPointsObject = new MatOfKeyPoint();
        MatOfKeyPoint keyPointsScene = new MatOfKeyPoint();
        Mat descriptorsObject = new Mat();
        Mat descriptorsScene = new Mat();
        detector.detectAndCompute(imgObject, new Mat(), keyPointsObject, descriptorsObject);
        detector.detectAndCompute(imgScene, new Mat(), keyPointsScene, descriptorsScene);

        //-- 步骤2：将描述符向量与基于FLANN的匹配器进行匹配
        // 由于SURF是浮点描述符，因此使用NORM_L2
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED);
        List<MatOfDMatch> knnMatches = new ArrayList<>();
        matcher.knnMatch(descriptorsObject, descriptorsScene, knnMatches, 2);
        //-- 使用Lowe比率测试过滤匹配项
        float ratioThresh = 0.75f;
        List<DMatch> listOfGoodMatches = new ArrayList<>();
        for (int i = 0; i < knnMatches.size(); i++) {
            if (knnMatches.get(i).rows() > 1) {
                DMatch[] matches = knnMatches.get(i).toArray();
                if (matches[0].distance < ratioThresh * matches[1].distance) {
                    listOfGoodMatches.add(matches[0]);
                }
            }
        }
        MatOfDMatch goodMatches = new MatOfDMatch();
        goodMatches.fromList(listOfGoodMatches);
        //-- 匹配
        Mat imgMatches = new Mat();
        Features2d.drawMatches(imgObject, keyPointsObject, imgScene, keyPointsScene, goodMatches, imgMatches, Scalar.all(-1),
                Scalar.all(-1), new MatOfByte(), Features2d.NOT_DRAW_SINGLE_POINTS);
        //-- 定位对象
        List<Point> obj = new ArrayList<>();
        List<Point> scene = new ArrayList<>();
        List<KeyPoint> listOfKeyPointsObject = keyPointsObject.toList();
        List<KeyPoint> listOfKeyPointsScene = keyPointsScene.toList();
        for (int i = 0; i < listOfGoodMatches.size(); i++) {
            //-- 从良好的匹配中获取关键点
            obj.add(listOfKeyPointsObject.get(listOfGoodMatches.get(i).queryIdx).pt);
            scene.add(listOfKeyPointsScene.get(listOfGoodMatches.get(i).trainIdx).pt);
        }
        MatOfPoint2f objMat = new MatOfPoint2f();
        MatOfPoint2f sceneMat = new MatOfPoint2f();
        objMat.fromList(obj);
        sceneMat.fromList(scene);
        double ransacReprojThreshold = 3.0;
        Mat H = Calib3d.findHomography(objMat, sceneMat, Calib3d.RANSAC, ransacReprojThreshold);
        //-- 从image_1（要“检测”的对象）获取角
        Mat objCorners = new Mat(4, 1, CvType.CV_32FC2), sceneCorners = new Mat();
        float[] objCornersData = new float[(int) (objCorners.total() * objCorners.channels())];
        objCorners.get(0, 0, objCornersData);
        objCornersData[0] = 0;
        objCornersData[1] = 0;
        objCornersData[2] = imgObject.cols();
        objCornersData[3] = 0;
        objCornersData[4] = imgObject.cols();
        objCornersData[5] = imgObject.rows();
        objCornersData[6] = 0;
        objCornersData[7] = imgObject.rows();
        objCorners.put(0, 0, objCornersData);
        Core.perspectiveTransform(objCorners, sceneCorners, H);
        float[] sceneCornersData = new float[(int) (sceneCorners.total() * sceneCorners.channels())];
        sceneCorners.get(0, 0, sceneCornersData);
        //-- 在角之间绘制线（场景中的映射对象-image_2）
        line(imgMatches, new Point(sceneCornersData[0] + imgObject.cols(), sceneCornersData[1]),
                new Point(sceneCornersData[2] + imgObject.cols(), sceneCornersData[3]), new Scalar(0, 255, 0), 4);
        line(imgMatches, new Point(sceneCornersData[2] + imgObject.cols(), sceneCornersData[3]),
                new Point(sceneCornersData[4] + imgObject.cols(), sceneCornersData[5]), new Scalar(0, 255, 0), 4);
        line(imgMatches, new Point(sceneCornersData[4] + imgObject.cols(), sceneCornersData[5]),
                new Point(sceneCornersData[6] + imgObject.cols(), sceneCornersData[7]), new Scalar(0, 255, 0), 4);
        line(imgMatches, new Point(sceneCornersData[6] + imgObject.cols(), sceneCornersData[7]),
                new Point(sceneCornersData[0] + imgObject.cols(), sceneCornersData[1]), new Scalar(0, 255, 0), 4);
        return imgMatches;
    }

    public static Mat plotRGBHist(Mat src) {
        List<Mat> bgrPlanes = new ArrayList<>();
        Core.split(src, bgrPlanes);
        int histSize = 256;
        float[] range = {0, 256}; //the upper boundary is exclusive
        MatOfFloat histRange = new MatOfFloat(range);
        Mat bHist = new Mat(), gHist = new Mat(), rHist = new Mat();
        calcHist(bgrPlanes, new MatOfInt(0), new Mat(), bHist, new MatOfInt(histSize), histRange, false);
        calcHist(bgrPlanes, new MatOfInt(1), new Mat(), gHist, new MatOfInt(histSize), histRange, false);
        calcHist(bgrPlanes, new MatOfInt(2), new Mat(), rHist, new MatOfInt(histSize), histRange, false);
        int histW = 512, histH = 400;
        int binW = (int) Math.round((double) histW / histSize);
        Mat histImage = new Mat(histH, histW, CvType.CV_8UC3, new Scalar(0, 0, 0));
        Core.normalize(bHist, bHist, 0, histImage.rows(), Core.NORM_MINMAX);
        Core.normalize(gHist, gHist, 0, histImage.rows(), Core.NORM_MINMAX);
        Core.normalize(rHist, rHist, 0, histImage.rows(), Core.NORM_MINMAX);
        float[] bHistData = new float[(int) (bHist.total() * bHist.channels())];
        bHist.get(0, 0, bHistData);
        float[] gHistData = new float[(int) (gHist.total() * gHist.channels())];
        gHist.get(0, 0, gHistData);
        float[] rHistData = new float[(int) (rHist.total() * rHist.channels())];
        rHist.get(0, 0, rHistData);
        for (int i = 1; i < histSize; i++) {
            line(histImage, new Point(binW * (i - 1), histH - Math.round(bHistData[i - 1])),
                    new Point(binW * (i), histH - Math.round(bHistData[i])), new Scalar(255, 0, 0), 2);
            line(histImage, new Point(binW * (i - 1), histH - Math.round(gHistData[i - 1])),
                    new Point(binW * (i), histH - Math.round(gHistData[i])), new Scalar(0, 255, 0), 2);
            line(histImage, new Point(binW * (i - 1), histH - Math.round(rHistData[i - 1])),
                    new Point(binW * (i), histH - Math.round(rHistData[i])), new Scalar(0, 0, 255), 2);
        }
        return histImage;
    }

    public static Mat plotGrayscaleHist(Mat src) {
        Mat hist = calcGrayscaleHist(src);
        int histSize = 256;
        int histW = 512, histH = 400;
        int binW = (int) Math.round((double) histW / histSize);
        Mat histImage = new Mat(histH, histW, CvType.CV_8UC3, new Scalar(0, 0, 0));
        Core.normalize(hist, hist, 0, histImage.rows(), Core.NORM_MINMAX);
        float[] histData = new float[(int) (hist.total() * hist.channels())];
        hist.get(0, 0, histData);
        for (int i = 1; i < histSize; i++)
            line(histImage, new Point(binW * (i - 1), histH - Math.round(histData[i - 1])),
                    new Point(binW * (i), histH - Math.round(histData[i])), new Scalar(255, 255, 255), 2);
        return histImage;
    }

    public static Mat calcGrayscaleHist(Mat src) {
        List<Mat> plane = new ArrayList<>();
        plane.add(src);
        int histSize = 256;
        float[] range = {0, 256}; //the upper boundary is exclusive
        MatOfFloat histRange = new MatOfFloat(range);
        Mat hist = new Mat();
        calcHist(plane, new MatOfInt(0), new Mat(), hist, new MatOfInt(histSize), histRange, false);
        int histH = 400;
        Core.normalize(hist, hist, 0, histH, Core.NORM_MINMAX);
        return hist;
    }

    public static Mat randomSubmat(Mat mat) {
        return randomSubmat(new Mat[]{mat})[0];
    }

    public static Mat[] randomSubmat(@NotNull Mat[] mats) {
        Mat[] regions = new Mat[mats.length];
        Rect rect = randomRect(mats[0]);
        int horizontal = (int) mats[0].width() / 1000, vertical = (int) mats[0].height() / 1000;
        for (int i = 0; i < mats.length; i++) {
            Mat noEdgeMat = removeEdge(mats[i], horizontal, vertical);
            regions[i] = noEdgeMat.submat(rect);
        }
        return regions;
    }

    private static Rect randomRect(@NotNull Mat mat) {
        int horizontal = (int) mat.width() / 1000, vertical = (int) mat.height() / 1000;
        Mat noEdgeMat = removeEdge(mat, horizontal, vertical);

        int height = noEdgeMat.height();
        int width = noEdgeMat.width();

        int Y = randomInt(0, height - mat.height() / 10);
        int X = randomInt(0, width - mat.width() / 10);

        return new Rect(X, Y, mat.width() / 10, mat.height() / 10);
    }

    /**
     * Generating integer between [min, max].
     *
     * @param min min integer
     * @param max max integer
     */
    public static int randomInt(int min, int max) {
        return new Random().nextInt(max) % (max - min + 1) + min;
    }

    private static Mat removeEdge(Mat mat, int horizontal, int vertical) {
        int height = mat.height(), width = mat.width();
        // Remove blank edges.
        return mat.submat(
                height / vertical,
                height * (vertical - 1) / vertical,
                width / horizontal,
                width * (horizontal - 1) / horizontal);
    }

    public static Mat smooth(Mat src) {
        Mat dst = new Mat(src.rows(), src.cols(), src.type());
        // a(i+1) = tiny*data(i+1) + (1.0-tiny)*a(i)
        dst.put(0, 0, src.get(0, 0));
        double tiny = 0.5;
        for (int i = 1; i < dst.rows(); i++) {
            double a = dst.get(i - 1, 0)[0];
            double data = src.get(i, 0)[0];
            double[] value = new double[]{tiny * data + (1 - tiny) * a, 0, 0, 0};
            dst.put(i, 0, value);
        }
        return dst;
    }

    public static Mat smoothNTimes(Mat mat, int N) {
        try {
            for (int i = 0; i < N; i++)
                mat = smooth(mat);
        } catch (ArrayIndexOutOfBoundsException ignored) {
        }
        return mat;
    }

    public static Mat plotSmooth(Mat src) {
        return plotSmooth(src, 1);
    }

    public static Mat plotSmooth(Mat src, int N) {
        Mat smooth = smoothNTimes(src, N);
        int size = src.rows();
        int plotW = 512, plotH = 400;
        int binW = (int) Math.round((double) plotW / size);
        Mat smoothImage = new Mat(plotH, plotW, CvType.CV_8UC3, new Scalar(0, 0, 0));
        Core.normalize(smooth, smooth, -smoothImage.rows(), smoothImage.rows(), Core.NORM_MINMAX);
        float[] smoothData = new float[(int) (smooth.total() * smooth.channels())];
        smooth.get(0, 0, smoothData);
        for (int i = 1; i < size; i++)
            line(smoothImage, new Point(binW * (i - 1), plotH - Math.round(smoothData[i - 1])),
                    new Point(binW * (i), plotH - Math.round(smoothData[i])), new Scalar(255, 255, 255), 2);
        return smoothImage;
    }

    public static double minValue(Mat mat) {
        double min = mat.get(0, 0)[0];
        for (int i = 0; i < mat.height(); i++)
            for (int j = 0; j < mat.width(); j++)
                if (mat.get(i, j)[0] < min)
                    min = mat.get(i, j)[0];
        return min;
    }

    public static double maxValue(Mat mat) {
        Mat minusMat = new Mat();
        multiply(mat, new Scalar(-1), minusMat);
        return -minValue(minusMat);
    }

    public static Mat diff(Mat src) {
        Mat dst = new Mat(src.rows(), src.cols(), src.type());
        // Δy(i)=y(i+1)-y(i)
        for (int i = 0; i < dst.rows() - 1; i++) {
            double y0 = src.get(i, 0)[0];
            double y1 = src.get(i + 1, 0)[0];
            double[] delta = new double[]{y1 - y0, 0, 0};
            dst.put(i, 0, delta);
        }
        dst.put(dst.rows() - 1, 0, src.get(dst.rows() - 1, 0));
        return dst;
    }

    public static Mat plotDiff(Mat src) {
        Mat diff = diff(src);
        Scalar minValue = new Scalar(-minValue(diff));

        int size = src.rows();
        int plotW = 512, plotH = 400;

        add(diff, new Scalar(plotH / 2.0), diff);
        int binW = (int) Math.round((double) plotW / size);
        Mat diffImage = new Mat(plotH, plotW, CvType.CV_8UC3, new Scalar(0, 0, 0));

        float[] diffData = new float[(int) (diff.total() * diff.channels())];
        diff.get(0, 0, diffData);
        for (int i = 1; i < size; i++)
            line(diffImage, new Point(binW * (i - 1), plotH - Math.round(diffData[i - 1])),
                    new Point(binW * (i), plotH - Math.round(diffData[i])), new Scalar(255, 255, 255), 2);
        return diffImage;
    }

    public static List<Scalar> zeroPoints(Mat src) {
        List<Scalar> points = new ArrayList<>();
        // if y(i)*y(i+1)<0, there lies a zero points;
        for (int i = 0; i < src.rows() - 1; i++) {
            double y0 = src.get(i, 0)[0];
            double y1 = src.get(i + 1, 0)[0];
            if (y0 * y1 < 0)
                points.add(new Scalar(i));
        }
        return points;
    }

    /**
     * Get all maximum points.
     *
     * @param src line
     * @return maximum points
     */
    public static List<Scalar> maximum(Mat src) {
        Mat diff = diff(src);
        List<Scalar> zeroPoints = zeroPoints(diff);
        List<Scalar> maximum = new ArrayList<>();
        for (Scalar s : zeroPoints) {
            if (src.get((int) s.val[0], 0)[0] > 0)
                maximum.add(s);
        }
        return maximum;
    }

    private static void printMat(Mat mat) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n");
        for (int i = 0; i < mat.height(); i++) {
            for (int j = 0; j < mat.width(); j++) {
                stringBuilder.append(Arrays.toString(mat.get(i, j)))
                        .append("\t");
            }
            stringBuilder.append("\n");
        }
        Log.i("Mat", String.valueOf(stringBuilder));
    }

    public static double whitePercent(Mat mat) {
        double count = 0, sum = mat.cols() * mat.rows();
        for (int i = 0; i < mat.height(); i++) {
            for (int j = 0; j < mat.width(); j++) {
                if (mat.get(i, j)[0] == 255)
                    count++;
            }
        }
        return count / sum;
    }

    public void setOriginal(String original) {
        this.original = original;
    }

    public void setSample(String sample) {
        this.sample = sample;
    }

    @Override
    public synchronized void run() {
        match();
    }

    private void match() {
//        Pre-rotate
//        Point center = new Point(sample.height() / 2.0, sample.width() / 2.0);
//        double angle = 180;
//        double scale = 1.0;
//        Mat m = Imgproc.getRotationMatrix2D(center, angle, scale);
//        Mat dst = new Mat();
//        Imgproc.warpAffine(sample, dst, m, sample.size());

        Mat[] mats = new Mat[]{imread(original, Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE), imread(sample, CV_LOAD_IMAGE_GRAYSCALE)};
        Mat[] regions;
        double threshold0, threshold1;
        Mat[] binary = new Mat[]{new Mat(), new Mat()};
        do {
            do {
                regions = randomSubmat(mats);
                threshold0 = autoGetThreshold(smoothNTimes(calcGrayscaleHist(regions[0]), 3));
            } while (threshold0 == -1);
            threshold(regions[0], binary[0], threshold0, 255, Imgproc.THRESH_BINARY);
            threshold(regions[1], binary[1], threshold0, 255, Imgproc.THRESH_BINARY);
        } while (whitePercent(binary[0]) >= 0.97 || whitePercent(binary[1]) >= 0.97);
        originalBMP = matToBitmap(binary[0]);
        sampleBMP = matToBitmap(binary[1]);
        surfBMP = matToBitmap(surf(binary[0], binary[1]));
        ssimValue = getMSSIM(binary[0], binary[1]).val[0];
    }
}
