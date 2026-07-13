package com.geovecsvg.detection;

import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import com.geovecsvg.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 几何基元检测模块
 * - HoughLinesP 直线检测
 * - HoughCircles + RANSAC 圆检测
 * - 顶点/交点计算
 * - 填充区域多边形提取
 */
public class PrimitiveDetector {

    /**
     * 霍夫概率直线检测
     */
    public static List<LineSegment> detectLines(Mat binaryImage, ProcessingParams params) {
        Mat linesMat = new Mat();
        opencv_imgproc.HoughLinesP(binaryImage, linesMat,
                params.houghRho,
                params.houghTheta,
                params.houghThreshold,
                params.minLineLength,
                params.maxLineGap);

        List<LineSegment> lines = new ArrayList<>();
        int numLines = linesMat.rows();
        for (int i = 0; i < numLines; i++) {
            int x1 = linesMat.ptr(i).getInt(0 * 4);
            int y1 = linesMat.ptr(i).getInt(1 * 4);
            int x2 = linesMat.ptr(i).getInt(2 * 4);
            int y2 = linesMat.ptr(i).getInt(3 * 4);
            lines.add(new LineSegment(new Point2D(x1, y1), new Point2D(x2, y2)));
        }
        return lines;
    }

    /**
     * 合并近似共线的短线段为长线段
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

                    // 检查角度差
                    double angleDiff = Math.abs(current.angleDegrees() - other.angleDegrees()) % 180;
                    if (angleDiff > 90) angleDiff = 180 - angleDiff;
                    if (angleDiff > angleTolDeg) continue;

                    // 检查两个端点是否在对方直线上
                    double d1 = current.distanceToPoint(other.start);
                    double d2 = current.distanceToPoint(other.end);
                    if (d1 > distTol && d2 > distTol) continue;

                    // 合并：将两个线段的端点投影到直线上，取最远的两个
                    Point2D[] pts = {current.start, current.end, other.start, other.end};
                    // 选第一个方向的基准
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
        Mat circlesMat = new Mat();
        opencv_imgproc.HoughCircles(grayImage, circlesMat, opencv_imgproc.HOUGH_GRADIENT,
                params.houghCircDp,
                params.houghCircMinDist,
                params.houghCircParam1,
                params.houghCircParam2,
                params.minCircleRadius,
                params.maxCircleRadius);

        List<Circle> circles = new ArrayList<>();
        int numCircles = circlesMat.cols();
        for (int i = 0; i < numCircles; i++) {
            float x = circlesMat.ptr().getFloat(i * 3 * 4);
            float y = circlesMat.ptr().getFloat(i * 3 * 4 + 4);
            float r = circlesMat.ptr().getFloat(i * 3 * 4 + 8);
            circles.add(new Circle(new Point2D(x, y), r));
        }
        return circles;
    }

    /**
     * RANSAC 圆拟合（对边缘点集）
     */
    public static Circle ransacCircleFit(List<Point2D> points, int iterations, double inlierThreshold) {
        if (points.size() < 3) return null;

        Random rand = new Random(42);
        Circle bestCircle = null;
        int bestInliers = 0;

        for (int iter = 0; iter < iterations; iter++) {
            // 随机选3个点
            int i1 = rand.nextInt(points.size());
            int i2 = rand.nextInt(points.size());
            int i3 = rand.nextInt(points.size());
            if (i1 == i2 || i2 == i3 || i1 == i3) continue;

            Point2D p1 = points.get(i1);
            Point2D p2 = points.get(i2);
            Point2D p3 = points.get(i3);

            Circle c = circleFromThreePoints(p1, p2, p3);
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

                // 交点必须在图像范围内
                if (pt.x < -10 || pt.x > imgWidth + 10 || pt.y < -10 || pt.y > imgHeight + 10) continue;

                // 检查交点是否在两个线段上（或附近）
                if (!isPointNearSegment(pt, lines.get(i), snapDist)) continue;
                if (!isPointNearSegment(pt, lines.get(j), snapDist)) continue;

                // 与已有顶点合并
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

        Mat contours = new Mat();
        Mat hierarchy = new Mat();
        opencv_imgproc.findContours(fillMask, contours, hierarchy,
                opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);

        int numContours = contours.rows();
        for (int i = 0; i < numContours; i++) {
            Mat contour = contours.ptr(i).asMat();

            // 多边形近似
            Mat approx = new Mat();
            double epsilon = 0.02 * opencv_imgproc.arcLength(contour, true);
            opencv_imgproc.approxPolyDP(contour, approx, epsilon, true);

            List<Point2D> pts = new ArrayList<>();
            int n = approx.rows();
            for (int j = 0; j < n; j++) {
                int x = approx.ptr(j).getInt(0 * 2);
                int y = approx.ptr(j).getInt(1 * 2);
                pts.add(new Point2D(x, y));
            }

            if (pts.size() >= 3) {
                Polygon poly = new Polygon(pts);
                polygons.add(poly);
            }
        }
        return polygons;
    }
}
