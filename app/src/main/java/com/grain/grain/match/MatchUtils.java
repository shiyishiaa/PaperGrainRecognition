package com.grain.grain.match;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.FloatRange;

import org.jetbrains.annotations.NotNull;
import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvException;
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
import org.opencv.imgproc.Imgproc;
import org.opencv.xfeatures2d.SURF;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static java.lang.Float.MAX_VALUE;
import static org.opencv.core.Core.absdiff;
import static org.opencv.core.Core.add;
import static org.opencv.core.Core.divide;
import static org.opencv.core.Core.mean;
import static org.opencv.core.Core.multiply;
import static org.opencv.core.Core.subtract;
import static org.opencv.core.CvType.CV_32F;
import static org.opencv.core.CvType.CV_32FC2;
import static org.opencv.core.CvType.CV_8U;
import static org.opencv.core.CvType.CV_8UC3;
import static org.opencv.imgproc.Imgproc.COLOR_GRAY2RGBA;
import static org.opencv.imgproc.Imgproc.COLOR_RGB2BGRA;
import static org.opencv.imgproc.Imgproc.GaussianBlur;
import static org.opencv.imgproc.Imgproc.calcHist;
import static org.opencv.imgproc.Imgproc.cvtColor;
import static org.opencv.imgproc.Imgproc.getRotationMatrix2D;
import static org.opencv.imgproc.Imgproc.line;
import static org.opencv.imgproc.Imgproc.threshold;
import static org.opencv.imgproc.Imgproc.warpAffine;

public class MatchUtils extends Thread {
    public Bitmap originalBMP, sampleBMP, surfBMP, tempBMP;
    private double MSSIMValue, SSIMValue, PSNRValue, CW_SSIMValue;
    private String original, sample, start, end;
    private Mat originalMat, sampleMat;

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
        int d = CV_32F;

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

    public static Scalar getSSIM(Mat image1, Mat image2) {
        double ux = mean(image1).val[0], uy = mean(image2).val[0];

        subtract(image1, new Scalar(ux), image1);
        subtract(image2, new Scalar(uy), image2);

        double sigma2x = mean(image1.mul(image1)).val[0];
        double sigma2y = mean(image2.mul(image2)).val[0];
        double sigmaxy = mean(image1.mul(image2)).val[0];

        double k1 = 0.01, k2 = 0.03, L = 255;
        double c1 = Math.pow((k1 * L), 2), c2 = Math.pow((k2 * L), 2), c3 = c2 / 2.0;

        double l = (2 * ux * uy + c1) / (ux * ux + uy * uy + c1);
        double c = (2 * Math.sqrt(sigma2x) * Math.sqrt(sigma2y) + c2) / (sigma2x + sigma2y + c2);
        double s = (sigmaxy + c3) / (Math.sqrt(sigma2x) * Math.sqrt(sigma2y) + c3);

        return new Scalar(l * c * s);
    }

    public static Bitmap matToBitmap(Mat mat) {
        Bitmap bmp = null;
        Mat canvas = new Mat(mat.rows(), mat.cols(), CV_8U, new Scalar(4));
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

    public static Mat[] surf(Mat imgObject, Mat imgScene) {
        Mat imgObjectCopy = imgObject.clone();
        Mat imgSceneCopy = imgScene.clone();
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

        //matchFeature(descriptorsObject, descriptorsScene, 0.9f);

        //-- 步骤2：将描述符向量与基于FLANN的匹配器进行匹配 由于SURF是浮点描述符，因此使用NORM_L2
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
        Features2d.drawMatches(
                imgObject,
                keyPointsObject,
                imgScene,
                keyPointsScene,
                goodMatches,
                imgMatches,
                Scalar.all(-1),
                Scalar.all(-1),
                new MatOfByte(),
                Features2d.NOT_DRAW_SINGLE_POINTS);
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
        Mat objCorners = new Mat(4, 1, CV_32FC2), sceneCorners = new Mat();
        // obj的左上、右上、左下、右下
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
        // scene的左上、右上、左下、右下
        float[] sceneCornersData = new float[(int) (sceneCorners.total() * sceneCorners.channels())];
        sceneCorners.get(0, 0, sceneCornersData);
        Point[] scenePoints = new Point[4];
        scenePoints[0] = new Point(sceneCornersData[0], sceneCornersData[1]);
        scenePoints[1] = new Point(sceneCornersData[2], sceneCornersData[3]);
        scenePoints[2] = new Point(sceneCornersData[4], sceneCornersData[5]);
        scenePoints[3] = new Point(sceneCornersData[6], sceneCornersData[7]);
        Boolean[] isInBoundary = new Boolean[4];
        int index = -1;
        for (int i = 0; i < isInBoundary.length; i++) {
            isInBoundary[i] = isPointOutOfBoundary(imgScene, scenePoints[i]);
            if (isInBoundary[i])
                index = i;
        }

        Mat matchedObj, matchedScene;
        Point scenePoint = new Point(), objPoint = new Point();
        if (index != -1) {
            scenePoint = scenePoints[index];
            objPoint = getSymmetricalPoint(scenePoint, new Point(imgObject.width() / 2.0, imgObject.height() / 2.0));
        }
        switch (index) {
            case 0:
                matchedObj = imgObjectCopy.submat(0, (int) objPoint.y, 0, (int) objPoint.x);
                matchedScene = imgSceneCopy.submat((int) scenePoint.y, imgScene.height(), (int) scenePoint.x, imgScene.width());
                break;
            case 1:
                matchedObj = imgObjectCopy.submat(0, (int) objPoint.y, (int) objPoint.x, imgObject.width());
                matchedScene = imgSceneCopy.submat((int) scenePoint.y, imgScene.height(), 0, (int) scenePoint.x);
                break;
            case 2:
                matchedObj = imgObjectCopy.submat((int) objPoint.y, imgObject.height(), (int) objPoint.x, imgObject.width());
                matchedScene = imgSceneCopy.submat(0, (int) scenePoint.y, 0, (int) scenePoint.x);
                break;
            case 3:
                matchedObj = imgObjectCopy.submat((int) objPoint.y, imgObject.height(), 0, (int) objPoint.x);
                matchedScene = imgSceneCopy.submat(0, (int) scenePoint.y, (int) scenePoint.x, imgScene.width());
                break;
            default:
                matchedObj = imgObjectCopy;
                matchedScene = imgSceneCopy;
        }

        Rect common = new Rect(
                0,
                0,
                Math.min(matchedObj.width(), matchedScene.width()),
                Math.min(matchedObj.height(), matchedScene.height()));
        matchedObj = matchedObj.submat(common);
        matchedScene = matchedScene.submat(common);

        //-- 在角之间绘制线（场景中的映射对象-image_2）
        line(imgMatches, new Point(sceneCornersData[0] + imgObject.cols(), sceneCornersData[1]),
                new Point(sceneCornersData[2] + imgObject.cols(), sceneCornersData[3]), new Scalar(0, 255, 0), 4);
        line(imgMatches, new Point(sceneCornersData[2] + imgObject.cols(), sceneCornersData[3]),
                new Point(sceneCornersData[4] + imgObject.cols(), sceneCornersData[5]), new Scalar(0, 255, 0), 4);
        line(imgMatches, new Point(sceneCornersData[4] + imgObject.cols(), sceneCornersData[5]),
                new Point(sceneCornersData[6] + imgObject.cols(), sceneCornersData[7]), new Scalar(0, 255, 0), 4);
        line(imgMatches, new Point(sceneCornersData[6] + imgObject.cols(), sceneCornersData[7]),
                new Point(sceneCornersData[0] + imgObject.cols(), sceneCornersData[1]), new Scalar(0, 255, 0), 4);
        return new Mat[]{imgMatches, matchedObj, matchedScene};
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
        Mat histImage = new Mat(histH, histW, CV_8UC3, new Scalar(0, 0, 0));
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
        Mat histImage = new Mat(histH, histW, CV_8UC3, new Scalar(0, 0, 0));
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
        int horizontal = mats[0].width() / 1000, vertical = mats[0].height() / 1000;
        for (int i = 0; i < mats.length; i++) {
            Mat noEdgeMat = removeEdge(mats[i], horizontal, vertical);
            regions[i] = noEdgeMat.submat(rect);
        }
        return regions;
    }

    private static Rect randomRect(@NotNull Mat mat) {
        int horizontal = mat.width() / 1000, vertical = mat.height() / 1000;
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

    /**
     * 普通正态随机分布
     *
     * @param u 均值
     * @param v 方差
     * @return 正态分布
     */
    public static double normalRandomDouble(double u, double v) {
        Random random = new Random();
        return Math.sqrt(v) * random.nextGaussian() + u;
    }

    public static Double normalRandomDouble(double lower, double upper, double v) {
        if (lower > upper) {
            double temp = upper;
            upper = lower;
            lower = temp;
        } else if (lower == upper)
            return null;
        double number;
        do {
            number = normalRandomDouble((lower + upper) / 2f, v);
        } while (number <= lower || number >= upper);
        return number;
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
        Mat smoothImage = new Mat(plotH, plotW, CV_8UC3, new Scalar(0, 0, 0));
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
        Mat diffImage = new Mat(plotH, plotW, CV_8UC3, new Scalar(0, 0, 0));

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

    public synchronized static void printMat(String TAG, Mat mat) {
        for (int i = 0; i < mat.height(); i++) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("\n");
            for (int j = 0; j < mat.width(); j++) {
                stringBuilder.append(Arrays.toString(mat.get(i, j)))
                        .append("\t");
            }
            stringBuilder.append("\n");
            Log.i(TAG, String.valueOf(stringBuilder));
        }
    }

    public static double whitePercent(Mat mat) {
        int count, sum = mat.cols() * mat.rows();
        count = Core.countNonZero(mat);
        return (double) count / sum;
    }

    public static boolean isPointOutOfBoundary(Mat mat, Point point) {
        return (0 <= point.x) && (point.x <= mat.width()) && (0 <= point.y) && (point.y <= mat.height());
    }

    public static Point getSymmetricalPoint(Point point, Point center) {
        return new Point(2 * center.x - point.x, 2 * center.y - point.y);
    }

    public static double getPSNR(Mat I1, Mat I2) {
        Mat s1 = new Mat();
        absdiff(I1, I2, s1);       // 计算两幅图像差值的绝对值|I1 - I2|
        s1.convertTo(s1, CV_32F);  // 在进行平方操作之前先加深图像深度
        s1 = s1.mul(s1);           //对差值绝对值进行求平方 |I1 - I2|^2
        Scalar s = Core.sumElems(s1);        // 对每个通道的像素值求和
        double sse = s.val[0] + s.val[1] + s.val[2]; // 三通道的总像素值
        if (sse <= 1e-10)           //如果两幅图像差值绝对值的平方三通道总和很小，则无差别
            return 0;
        else {
            double mse = sse / (double) (I1.channels() * I1.total());            //均方误差 = 差值平方和 / 总像素数
            return 10.0 * Math.log10((255.0 * 255.0) / mse);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private static Mat[] matchFeature(Mat descA, Mat descB, @FloatRange(from = 0.0, to = 1.0) float ratio) {
        // Number of descriptors for each image
        int NoDescA = descA.height(), NoDescB = descB.height();

        // Vector that holds the mapping between the descriptors
        Mat mapping = new Mat(NoDescA, 1, CV_32F);
        Mat D = new Mat(NoDescA, NoDescB, CV_32F);
        Mat HistB = new Mat(1, NoDescB, CV_32F);

        for (int i = 0; i < NoDescA; i++) {
            Mat d = new Mat(NoDescB, 1, CV_32F);
            Mat rowA = rowAt(descA, i);
            for (int j = 0; j < NoDescB; j++) {
                // Calculate normalized correlations of each descriptor from the
                // first image with every descriptor from the second image
                Mat rowB = rowAt(descB, j);
                double dot = getDot(rowA, rowB);
                double norm = getNorm(rowB);
                d.put(j, 0, dot / norm);
            }
            Mat dst = d.clone();
            divide(dst, new Scalar(getNorm(rowA)), dst);
            Mat acos = acos(dst);
            D = putRowAt(D, acos.t(), i);

            Mat min_1 = min(acos); // best
            double sd1 = min_1.get(0, 0)[0];
            int index1 = (int) min_1.get(1, 0)[0];

            acos = putRowAt(acos, all(1, acos.width(), MAX_VALUE), rowAt(min_1, 1));

            Mat min_2 = min(acos); // second best
            double sd2 = min_2.get(0, 0)[0];
            int index2 = (int) min_2.get(1, 0)[0];

            // If best< ratio*second_best => match is accepted
            if (d.get(index2, 0)[0] > 0
                    && sd1 / sd2 < ratio) {
                // counts of correspondences with index1
                HistB.put(0, index1, HistB.get(0, index1)[0] + 1);
                // save only single matches here
                mapping.put(i, 0, index1);
            }
        }

        // indices from descB with duplicate matches
        Mat multiIndB = find(HistB, 1, Find.More);

        // when duplicates do an inverse mapping
        if (!multiIndB.empty())
            for (int i = 0; i < multiIndB.width(); i++) {
                boolean[] multiMap = equals(mapping, multiIndB.get(0, i)[0]);
                assign(mapping, 0, multiMap);
                int newIndexA = (int) min(colAt(D, (int) multiIndB.get(0, i)[0])).get(1, 0)[0];
                // check availability
                if (rowAt(mapping, newIndexA).get(0, 0)[0] == 0)
                    mapping.put(newIndexA, 0, multiIndB.get(0, i)[0]);
            }

        Mat IndexA = find(mapping, 0, Find.More);
        Mat IndexB = get(mapping, IndexA);

        Mat numMatches = new Mat(1, 1, CV_8U);
        numMatches.put(0, 0, IndexA.height());

        return new Mat[]{mapping, numMatches, IndexA, IndexB};
    }

    public static boolean[] equals(Mat src, double d) {
        boolean[] dst = new boolean[src.height()];
        for (int i = 0; i < src.height(); i++) {
            dst[i] = src.get(i, 0)[0] == d;
        }
        return dst;
    }

    public static Mat get(Mat src, Mat index) throws CvException {
        Mat dst = new Mat(index.height(), 1, src.type());
        for (int i = 0; i < index.height(); i++)
            for (int j = 0; j < src.height(); j++)
                if (index.get(i, 0)[0] == j) {
                    dst.put(i, 0, src.get(j, 0));
                    break;
                }
        return dst;
    }

    private static void assign(Mat dst, double value, boolean[] booleans) throws CvException {
        if (dst.height() != booleans.length)
            throw new CvException("Mismatched matrices and arrays!");
        for (int i = 0; i < dst.height(); i++)
            if (booleans[i])
                dst.put(i, 0, value);
    }

    public static Mat all(int row, int col, double d) {
        Mat dst = new Mat(row, col, CV_32F);
        for (int i = 0; i < dst.height(); i++)
            for (int j = 0; j < dst.width(); j++)
                dst.put(i, j, d);
        return dst;
    }

    public static Mat find(Mat src, double d, Find find) {
        Mat dst = new Mat(1, 0, src.type());
        switch (find) {
            case Less:
                for (int i = 0; i < src.width(); i++) {
                    if (src.get(0, i)[0] < d) {
                        extend(dst);
                        src.put(0, src.width() - 1, i);
                    }
                }
                break;
            case More:
                for (int i = 0; i < src.width(); i++) {
                    if (src.get(0, i)[0] > d) {
                        extend(dst);
                        src.put(0, src.width() - 1, i);
                    }
                }
                break;
            case Equal:
                for (int i = 0; i < src.width(); i++) {
                    if (src.get(0, i)[0] == d) {
                        extend(dst);
                        src.put(0, src.width() - 1, i);
                    }
                }
                break;
        }
        return dst;
    }

    public static Mat extend(Mat src) {
        Mat dst = new Mat(src.rows(), src.cols() + 1, src.type());
        for (int j = 0; j < src.width(); j++) {
            putColAt(dst, colAt(src, j), j);
        }
        for (int i = 0; i < src.height(); i++)
            dst.put(i, dst.width(), 0);
        return dst;
    }

    public static Mat putRowAt(Mat src, Mat row, Mat index) throws CvException {
        Size size = new Size(src.width(), 1);
        if (!row.size().equals(size) || !index.size().equals(size))
            throw new CvException("Illegal row or index!");
        Mat dst = src.clone();
        for (int j = 0; j < dst.width(); j++)
            for (int i = 0; i < dst.height(); i++)
                if (index.get(0, j)[0] == i) {
                    dst.put(i, j, row.get(0, j));
                    break;
                }
        return dst;
    }

    public static Mat putColAt(Mat dst, Mat col, int at) throws CvException {
        if (at < 0 || at > dst.height()) throw new CvException("Index out of bound!");
        if (col.height() != 0) throw new CvException("Illegal column!");
        for (int j = 0; j < dst.width(); j++)
            if (j == at)
                for (int i = 0; i < dst.height(); i++)
                    dst.put(i, j, dst.get(i, j));
        return dst;
    }

    public static Mat rowAt(Mat src, int at) throws CvException {
        if (at < 0 || at > src.height()) throw new CvException("Index out of bound!");
        Mat dst = new Mat(1, src.width(), src.type());
        for (int i = 0; i < dst.width(); i++)
            dst.put(0, i, src.get(at, i));
        return dst;
    }

    public static Mat colAt(Mat src, int at) {
        return rowAt(src.t(), at).t();
    }

    /**
     * Calculate the maximum value per column.
     *
     * @param src source mat
     * @return max mat (The first line contains the maximum values; the second line contains their index.)
     */
    public static Mat max(Mat src) {
        Mat dst = new Mat(2, src.width(), src.type());
        for (int i = 0; i < src.width(); i++) {
            double[] max = _max(colAt(src, i));
            dst.put(0, i, max[0]);
            dst.put(1, i, max[1]);
        }
        return dst;
    }

    private static double[] _max(Mat src) {
        double max = src.get(0, 0)[0];
        double[] dst = new double[2];
        dst[0] = max;
        dst[1] = 0;
        for (int i = 0; i < src.height(); i++)
            if (src.get(0, i)[0] > max) {
                dst[0] = src.get(0, i)[0];
                dst[1] = i;
            }
        return dst;
    }

    /**
     * Calculate the minimum value per column.
     *
     * @param src source mat
     * @return min mat (The first line contains the minimum values; the second line contains their index.)
     */
    public static Mat min(Mat src) {
        Mat dst = new Mat(2, src.width(), src.type());
        for (int i = 0; i < src.width(); i++) {
            double[] min = _min(colAt(src, i));
            dst.put(0, i, min[0]);
            dst.put(1, i, min[1]);
        }
        return dst;
    }

    private static double[] _min(Mat src) {
        double min = src.get(0, 0)[0];
        double[] dst = new double[2];
        dst[0] = min;
        dst[1] = 0;
        for (int i = 0; i < src.height(); i++)
            if (src.get(i, 0)[0] < min) {
                dst[0] = src.get(i, 0)[0];
                dst[1] = i;
            }
        return dst;
    }

    public static Mat putRowAt(Mat src, Mat row, int at) throws CvException {
        if (at < 0 || at > src.height()) throw new CvException("Index out of bound!");
        if (row.height() != 1) throw new CvException("Illegal row!");
        for (int i = 0; i < src.height(); i++)
            if (i == at)
                for (int j = 0; j < src.width(); j++)
                    src.put(i, j, src.get(i, j));
        return src;
    }

    public static Mat acos(Mat src) {
        Mat dst = new Mat(src.size(), src.type());
        for (int i = 0; i < dst.rows(); i++)
            for (int j = 0; j < dst.cols(); j++)
                dst.put(i, j, Math.acos(src.get(i, j)[0]));
        return dst;
    }

    public static double getNorm(Mat src) {
        double sum = 0;
        for (int i = 0; i < src.width(); i++)
            sum += Math.pow(src.get(0, i)[0], 2);
        return Math.sqrt(sum);
    }

    public static double getDot(Mat srcA, Mat srcB) throws CvException {
        if (!srcA.size().equals(srcB.size()))
            throw new CvException("Mat size is not equivalent.");
        double sum = 0;
        for (int i = 0; i < srcA.width(); i++)
            sum += srcA.get(0, i)[0] * srcB.get(0, i)[0];
        return sum;
    }

    /**
     * Get rotated image according to the given angle surrounding the central point.
     *
     * @param src   Source Image.
     * @param angle Rotation angle in degrees. Positive values mean counter-clockwise rotation (the coordinate origin is assumed to be the top-left corner).
     * @return Rotated Image.
     */
    public static Mat rotate(Mat src, double angle) {
        Point center = new Point(src.cols() / 2.0, src.rows() / 2.0);
        Mat affine_matrix = getRotationMatrix2D(center, angle, 1.0);//求得旋转矩阵
        Mat dst = new Mat();
        warpAffine(src, dst, affine_matrix, src.size());
        return dst;
    }

    private static Mat keepBlack(Mat src) {
        Mat dst = src.clone();
        for (int i = 0; i < dst.height(); i++)
            for (int j = 0; j < dst.width(); j++)
                if (dst.get(i, j)[0] != 0)
                    dst.put(i, j, 255);
        return dst;
    }

    private static Bitmap minOverlapping(Mat mat1, Mat mat2) {
        // In degree.
        final double minOpenAngle = 10, angleStep = 1;
        double minPercentage = 1;
        double minAngle = 90;
        Bitmap bitmap;
        Mat minMat = new Mat();
        Mat rotate;
        for (int i = 0; i <= minOpenAngle; i += angleStep) {
            double angle = -minOpenAngle / 2.0 + i;
            rotate = rotate(mat2, angle);
            rotate = keepBlack(rotate);
            absdiff(mat1, rotate, rotate);
            if (whitePercent(rotate) < minPercentage) {
                minPercentage = whitePercent(rotate);
                minAngle = angle;
            }
        }
        rotate = rotate(mat2, minAngle);
        rotate = keepBlack(rotate);

        final double minDisplacementX = mat2.cols() / 50.0, xStep = 1;
        Mat xDis;
        double minX = 0;
        for (int i = 0; i <= minDisplacementX; i += xStep) {
            double x = -minDisplacementX / 2.0 + i;
            xDis = shift(rotate, x, 0);
            absdiff(mat1, xDis, xDis);
            if (whitePercent(xDis) < minPercentage) {
                minPercentage = whitePercent(xDis);
                minX = x;
            }
        }
        xDis = shift(rotate, minX, 0);
        xDis = keepBlack(xDis);

        final double minDisplacementY = mat2.rows() / 50.0, yStep = 1;
        Mat yDis;
        double minY = 0;
        for (int i = 0; i <= minDisplacementY; i += yStep) {
            double y = -minDisplacementY / 2.0 + i;
            yDis = shift(xDis, 0, y);
            absdiff(mat1, yDis, yDis);
            if (whitePercent(yDis) < minPercentage) {
                minPercentage = whitePercent(yDis);
                minY = y;
            }
        }
        yDis = shift(xDis, 0, minY);
        yDis = keepBlack(yDis);

        bitmap = matToBitmap(yDis);

        return bitmap;
    }

    public static Mat shift(Mat src, double x, double y) {
        Mat M = new Mat(2, 3, CV_32F);
        M.put(0, 0, 1, 0, x, 0, 1, y);

        Mat dst = new Mat(src.rows(), src.cols(), src.type());
        warpAffine(src, dst, M, dst.size());
        return dst;
    }

    public double getSSIMValue() {
        return SSIMValue;
    }

    public String[] getPath() {
        return new String[]{original, sample};
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    public double getMSSIMValue() {
        return MSSIMValue;
    }

    public double getPSNRValue() {
        return PSNRValue;
    }

    public double getCW_SSIMValue() {
        return CW_SSIMValue;
    }

    public void setOriginal(String original) {
        this.original = original;
    }

    public void setSample(String sample) {
        this.sample = sample;
    }

    public void setOriginalMat(Mat originalMat) {
        this.originalMat = originalMat;
    }

    public void setSampleMat(Mat sampleMat) {
        this.sampleMat = sampleMat;
    }

    public void setCW_SSIMValue(double CW_SSIMValue) {
        this.CW_SSIMValue = CW_SSIMValue;
    }

    @Override
    public synchronized void run() {
        match();
    }

    private void match() {
        Mat[] mats = new Mat[]{
                originalMat.submat(100, originalMat.rows() - 100, 100, originalMat.cols() - 100),
                sampleMat.submat(100, sampleMat.rows() - 100, 100, sampleMat.cols() - 100)};
        Mat[] regions;
        double threshold0;//, threshold1;
        Mat[] binary = new Mat[]{new Mat(), new Mat()};
        do {
            do {
                regions = randomSubmat(mats);
                threshold0 = autoGetThreshold(smoothNTimes(calcGrayscaleHist(regions[0]), 3));
                //threshold1 = autoGetThreshold(smoothNTimes(calcGrayscaleHist(regions[1]), 3));
            } while (threshold0 == -1); //|| threshold1 == -1);
            // DO NOT use Otsu method.
            threshold(regions[0], binary[0], threshold0, 255, Imgproc.THRESH_BINARY);
            threshold(regions[1], binary[1], threshold0, 255, Imgproc.THRESH_BINARY);
        } while (whitePercent(binary[0]) >= 0.95 || whitePercent(binary[1]) >= 0.95);
        Mat[] matched = surf(binary[0], binary[1]);

        surfBMP = matToBitmap(matched[0]);
        originalBMP = matToBitmap(matched[1]);
        sampleBMP = matToBitmap(matched[2]);

        MSSIMValue = getMSSIM(matched[1], matched[2]).val[0];
        SSIMValue = getSSIM(matched[1], matched[2]).val[0];
        PSNRValue = getPSNR(matched[1], matched[2]);
    }


    public enum Find {More, Less, Equal}
}
