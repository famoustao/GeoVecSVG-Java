package com.geovecsvg.detection;

import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_imgproc.Vec4iVector;
import org.bytedeco.opencv.opencv_imgproc.Vec3fVector;
import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

import com.geovecsvg.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 几何基元检测模块
 */
public class PrimitiveDetector {

    /**
     * 霍夫概率直线检测
     */
    public static List<LineSegment> detectLines(Mat binaryImage, ProcessingParams params) {
        Vec4iVector linesVec = new Vec4iVector();
        HoughLinesP(binaryImage, linesVec,
                params.houghRho,
                params.houghTheta,
                params.houghThreshold,
                params.minLineLength,
                params.maxLineGap);

        List<LineSegment> lines = new ArrayList<>();
        long n = linesVec.size();
        for (long i = 0; i < n; i++) {
            int[] data = new int[4];
            linesVec.get(i).get(data);
            lines.add(new LineSegment(
                    new Point2D(data[0], data[1]),
                    new Point2D(data[2], data[3])
            ));
        }
        return lines;
    }

    /**
     * 合并近似共线的短线段
     */
    public static List<LineSegment> mergeCollinearLines(List<LineSegment> lines, double angleTolDeg, double distTol) {
        List<LineSegment> merged = new ArrayList<>();
        boolean[] used = new boolean[lines.size()];

        for (int i = 0; i < lines.size(); i++) {
            if (used[i]) continue;
            LineSegment current = lines.get(i).copy();
            used[i] = true;

            boolean changed = true;
            while (changed) {
                changed = false;
                for (int j = i + 1; j < lines.size(); j++) {
                    if (used[j]) continue;
                    LineSegment other = lines.get(j);

                    double angleDiff = Math.abs(current.angleDegrees() - other.angleDegrees()) % 180;
                    if (angleDiff > 90) angleDiff = 180 - angleDiff;
                    if (angleDiff > angleTolDeg) continue;

                    double d1 = current.distanceToPoint(other.start);
                    double d2 = current.distanceToPoint(other.end);
                    if (d1 > distTol && d2 > distTol) continue;

                    Point2D[] pts = {current.start, current.end, other.start, other.end};
                    double[] dir = {current.end.x - current.start.x, current.end.y - current.start.y};
                    double len = Math.sqrt(dir[0]*dir[0] + dir[1]*dir[1]);
                    if (len < 1e-6) continue;
                    dir[0] /= len; dir[1] /= len;

                    double maxT = -Double.MAX_VALUE, minT = Double.MAX_VALUE;
                    Point2D maxPt = null, minPt = null;
                    for (Point2D p : pts) {
                        double t = (p.x - current.start.x) * dir[0] + (p.y - current.start.y) * dir[1];
                        if (t > maxT) { maxT = t; maxPt = p; }
                        if (t < minT) { minT = t; minPt = p; }
                    }

                    if (maxPt != null && minPt != null) {
                        current = new LineSegment(minPt, maxPt);
                        used[j] = true;
                        changed = true;
                    }
                }
            }
            merged.add(current);
        }
        return merged;
    }

    /**
     * 霍夫圆检测
     */
    public static List<Circle> detectCircles(Mat grayImage, ProcessingParams params) {
        Vec3fVector circlesVec = new Vec3fVector();
        HoughCircles(grayImage, circlesVec, HOUGH_GRADIENT,
                params.houghCircDp,
                params.houghCircMinDist,
                params.houghCircParam1,
                params.houghCircParam2,
                params.minCircleRadius,
                params.maxCircleRadius);

        List<Circle> circles = new ArrayList<>();
        long n = circlesVec.size();
        for (long i = 0; i < n; i++) {
            float[] data = new float[3];
            circlesVec.get(i).get(data);
            circles.add(new Circle(new Point2D(data[0], data[1]), data[2]));
        }
        return circles;
    }

    /**
     * RANSAC 圆拟合
     */
    public static Circle ransacCircleFit(List<Point2D> points, int iterations, double inlierThreshold) {
        if (points.size() < 3) return null;

        Random rand = new Random(42);
        Circle bestCircle = null;
        int bestInliers = 0;

        for (int iter = 0; iter < iterations; iter++) {
            int i1 = rand.nextInt(points.size());
            int i2 = rand.nextInt(points.size());
            int i3 = rand.nextInt(points.size());
            if (i1 == i2 || i2 == i3 || i1 == i3) continue;

            Circle c = circleFromThreePoints(points.get(i1), points.get(i2), points.get(i3));
            if (c == null) continue;

            int inliers = 0;
            for (Point2D p : points) {
                double dist = Math.abs(p.distanceTo(c.center) - c.radius);
                if (dist < inlierThreshold) inliers++;
            }

            if (inliers > bestInliers) {
                bestInliers = inliers;
                bestCircle = c;
                bestCircle.confidence = (double) inliers / points.size();
            }
        }
        return bestCircle;
    }

    private static Circle circleFromThreePoints(Point2D p1, Point2D p2, Point2D p3) {
        double x1 = p1.x, y1 = p1.y;
        double x2 = p2.x, y2 = p2.y;
        double x3 = p3.x, y3 = p3.y;

        double a1 = 2 * (x2 - x1);
        double b1 = 2 * (y2 - y1);
        double c1 = x2*x2 + y2*y2 - x1*x1 - y1*y1;

        double a2 = 2 * (x3 - x2);
        double b2 = 2 * (y3 - y2);
        double c2 = x3*x3 + y3*y3 - x2*x2 - y2*y2;

        double det = a1 * b2 - a2 * b1;
        if (Math.abs(det) < 1e-10) return null;

        double cx = (b2 * c1 - b1 * c2) / det;
        double cy = (a1 * c2 - a2 * c1) / det;
        double r = Math.sqrt((x1 - cx)*(x1 - cx) + (y1 - cy)*(y1 - cy));

        return new Circle(new Point2D(cx, cy), r);
    }

    /**
     * 计算所有直线的交点作为顶点
     */
    public static List<Point2D> computeVertices(List<LineSegment> lines, int imgWidth, int imgHeight, double snapDist) {
        List<Point2D> vertices = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            for (int j = i + 1; j < lines.size(); j++) {
                Point2D pt = LineSegment.intersection(lines.get(i), lines.get(j));
                if (pt == null) continue;

                if (pt.x < -10 || pt.x > imgWidth + 10 || pt.y < -10 || pt.y > imgHeight + 10) continue;

                if (!isPointNearSegment(pt, lines.get(i), snapDist)) continue;
                if (!isPointNearSegment(pt, lines.get(j), snapDist)) continue;

                boolean merged = false;
                for (Point2D v : vertices) {
                    if (v.distanceTo(pt) < snapDist) {
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    vertices.add(pt);
                }
            }
        }
        return vertices;
    }

    private static boolean isPointNearSegment(Point2D pt, LineSegment seg, double tol) {
        double dx = seg.end.x - seg.start.x;
        double dy = seg.end.y - seg.start.y;
        double lenSq = dx*dx + dy*dy;
        if (lenSq < 1e-10) return pt.distanceTo(seg.start) < tol;

        double t = ((pt.x - seg.start.x) * dx + (pt.y - seg.start.y) * dy) / lenSq;
        t = Math.max(0, Math.min(1, t));
        double px = seg.start.x + t * dx;
        double py = seg.start.y + t * dy;
        return Math.sqrt((pt.x - px)*(pt.x - px) + (pt.y - py)*(pt.y - py)) < tol;
    }

    /**
     * 从填充区域掩膜提取多边形轮廓
     */
    public static List<Polygon> extractFilledPolygons(Mat fillMask) {
        List<Polygon> polygons = new ArrayList<>();
        if (fillMask == null) return polygons;

        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(fillMask, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

        long n = contours.size();
        for (long i = 0; i < n; i++) {
            Mat contour = contours.get(i);

            Mat approx = new Mat();
            double epsilon = 0.02 * arcLength(contour, true);
            approxPolyDP(contour, approx, epsilon, true);

            List<Point2D> pts = new ArrayList<>();
            int rows = approx.rows();
            // approx 是 CV_32SC2 类型，即每行两个int
            int[] approxData = new int[rows * 2];
            approx.data().asBuffer().asIntBuffer().get(approxData);

            for (int j = 0; j < rows; j++) {
                int x = approxData[j * 2];
                int y = approxData[j * 2 + 1];
                pts.add(new Point2D(x, y));
            }

            if (pts.size() >= 3) {
                polygons.add(new Polygon(pts));
            }
        }
        return polygons;
    }
}
