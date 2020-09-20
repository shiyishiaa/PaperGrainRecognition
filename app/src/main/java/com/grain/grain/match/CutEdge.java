package com.grain.grain.match;

import android.graphics.Bitmap;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import static com.grain.grain.match.MatchUtils.matToBitmap;

public class CutEdge extends Thread {
    public Bitmap noEdge;
    private String path;
    private Mat uncut, cut;

    public CutEdge(String _path) {
        path = _path;
    }

    public static Rect houghLines(Mat src) {
        Mat dst = new Mat(), cdstP = new Mat();
        // Edge detection
        Imgproc.Canny(src, dst, 50, 200, 3, false);
        // Copy edges to the images that will display the results in BGR
        Imgproc.cvtColor(dst, cdstP, Imgproc.COLOR_GRAY2BGR);
        // Probabilistic Line Transform
        Mat linesP = new Mat(); // will hold the results of the detection
        Imgproc.HoughLinesP(dst, linesP, 1, Math.PI / 180, 50, 700, 50); // runs the actual detection
        // Draw the lines
        Point[] top = new Point[]{new Point(0, src.height()), new Point(src.width(), src.height())};
        Point[] bottom = new Point[]{new Point(0, 0), new Point(src.width(), 0)};
        Point[] left = new Point[]{new Point(src.width(), 0), new Point(src.width(), src.height())};
        Point[] right = new Point[]{new Point(0, 0), new Point(0, src.height())};
        for (int x = 0; x < linesP.rows(); x++) {
            double[] l = linesP.get(x, 0);
            Point p1 = new Point(l[0], l[1]), p2 = new Point(l[2], l[3]);
            double angle = tileAngle(p1, p2);
            if (85 <= angle && angle <= 95) {
                if (Math.max(p1.x, p2.x) <= Math.min(left[0].x, left[1].x))
                    left = new Point[]{p1, p2};
                else if (Math.min(p1.x, p2.x) >= Math.max(right[0].x, right[1].x))
                    right = new Point[]{p1, p2};
            } else if (angle <= 5 || angle >= 175) {
                if (Math.max(p1.y, p2.y) <= Math.min(top[0].y, top[1].y))
                    top = new Point[]{p1, p2};
                else if (Math.min(p1.y, p2.y) >= Math.max(bottom[0].y, bottom[1].y)) {
                    bottom = new Point[]{p1, p2};
                }
            }
        }
        return new Rect(
                new Point(Math.min(left[0].x, left[1].x), Math.min(top[0].y, top[1].y)),
                new Point(Math.max(right[0].x, right[1].x), Math.max(bottom[0].y, bottom[1].y)));
    }

    public static Double tileAngle(Point p1, Point p2) throws IllegalArgumentException {
        if (p1.equals(p2))
            throw new IllegalArgumentException("Points cannot be are at the same place!");
        if (p1.x == p2.x) return 90.0;
        return Math.toDegrees(Math.atan2(p2.y - p1.y, p2.x - p1.x));
    }

    public static Mat addMask(Mat src, Rect mask) {
        Mat dst = src.clone();
        Mat subImage = dst.submat(mask);
        subImage.setTo(new Scalar(0));
        return dst;
    }

    private static Mat flat(Mat src, Rect rect) {
        Mat pts1 = new Mat(4, 2, CvType.CV_32F);
        pts1.put(0, 0,
                rect.x, rect.y,
                rect.x + rect.width, rect.y,
                rect.x + rect.width, rect.y + rect.height,
                rect.x, rect.y + rect.height);
        Mat pts2 = new Mat(4, 2, CvType.CV_32F);
        pts2.put(0, 0,
                0, 0,
                src.width(), 0,
                src.width(), src.height(),
                0, src.height());

        Mat M = Imgproc.getPerspectiveTransform(pts1, pts2);
        Mat dst = new Mat();
        Imgproc.warpPerspective(src, dst, M, src.size());
        return dst;
    }

    public Mat getUncut() {
        return uncut;
    }

    public Mat getCut() {
        return cut;
    }

    @Override
    public synchronized void run() {
        crop();
    }

    private void crop() {
        uncut = Imgcodecs.imread(path, Imgcodecs.CV_LOAD_IMAGE_GRAYSCALE);
        Rect mask = new Rect(1000, 1000, 4000, 6000);
        Mat masked = addMask(uncut, mask);
        cut = flat(uncut, houghLines(masked));
        noEdge = matToBitmap(cut);
    }
}
