package com.grain.grain.matching;

import android.graphics.Bitmap;

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
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.Features2d;
import org.opencv.imgproc.Imgproc;
import org.opencv.xfeatures2d.SURF;

import java.util.ArrayList;
import java.util.List;

import static org.opencv.core.Core.add;
import static org.opencv.core.Core.divide;
import static org.opencv.core.Core.mean;
import static org.opencv.core.Core.multiply;
import static org.opencv.core.Core.subtract;
import static org.opencv.imgcodecs.Imgcodecs.imread;
import static org.opencv.imgproc.Imgproc.*;
import static org.opencv.imgproc.Imgproc.COLOR_GRAY2RGBA;
import static org.opencv.imgproc.Imgproc.COLOR_RGB2BGRA;
import static org.opencv.imgproc.Imgproc.GaussianBlur;
import static org.opencv.imgproc.Imgproc.cvtColor;

public class MatchUtils implements Runnable {
    public Bitmap bmp;
    public Double value;
    private String originalPath, samplePath;

    public MatchUtils(String Original, String Sample) {
        this.originalPath = Original;
        this.samplePath = Sample;
    }

    public static Scalar getMSSIM(Mat i1, Mat i2) {
        Scalar C1 = new Scalar(6.5025), C2 = new Scalar(58.5225);
        int d = CvType.CV_32F;

        Mat I1 = new Mat(), I2 = new Mat();
        i1.convertTo(I1, d);           // cannot calculate on one byte large values
        i2.convertTo(I2, d);

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

    @Override
    public synchronized void run() {
        Mat original = imread(originalPath);
        Mat sample = imread(samplePath);
        bmp = matToBitmap(surf(original, sample));
    }
}
