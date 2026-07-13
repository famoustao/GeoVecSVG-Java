package com.geovecsvg.preprocess;

import org.bytedeco.opencv.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;

import com.geovecsvg.model.ProcessingParams;

/**
 * 图像预处理模块
 */
public class ImagePreprocessor {

    public static Mat loadImage(String path) {
        return imread(path);
    }

    public static Mat toGray(Mat colorImage) {
        Mat gray = new Mat();
        cvtColor(colorImage, gray, COLOR_BGR2GRAY);
        return gray;
    }

    public static Mat denoise(Mat gray, int ksize) {
        Mat denoised = new Mat();
        int k = ksize % 2 == 0 ? ksize + 1 : ksize;
        GaussianBlur(gray, denoised, new Size(k, k), 0);
        return denoised;
    }

    public static Mat binarize(Mat gray) {
        Mat binary = new Mat();
        threshold(gray, binary, 0, 255, THRESH_BINARY_INV | THRESH_OTSU);
        return binary;
    }

    public static Mat morphClose(Mat binary, int size) {
        Mat result = new Mat();
        int s = Math.max(1, size);
        Mat kernel = getStructuringElement(MORPH_RECT, new Size(s, s));
        morphologyEx(binary, result, MORPH_CLOSE, kernel);
        return result;
    }

    public static Mat morphOpen(Mat binary, int size) {
        Mat result = new Mat();
        Mat kernel = getStructuringElement(MORPH_RECT, new Size(size, size));
        morphologyEx(binary, result, MORPH_OPEN, kernel);
        return result;
    }

    /**
     * HSV 颜色分割，提取红色填充区域
     */
    public static Mat extractFilledRegion(Mat colorImage, ProcessingParams params) {
        Mat hsv = new Mat();
        cvtColor(colorImage, hsv, COLOR_BGR2HSV);

        int rows = hsv.rows();
        int cols = hsv.cols();
        Mat mask = new Mat(rows, cols, CV_8UC1, new Scalar(0));

        // 手动 HSV 颜色范围筛选
        byte[] hsvData = new byte[rows * cols * hsv.channels()];
        hsv.data().get(hsvData);
        byte[] maskData = new byte[rows * cols];

        int hMin = params.fillHueMin;
        int hMax = params.fillHueMax;
        int sMin = params.fillSatMin;
        int vMin = params.fillValMin;

        int idx = 0;
        for (int i = 0; i < maskData.length; i++) {
            int h = hsvData[idx] & 0xFF;
            int s = hsvData[idx + 1] & 0xFF;
            int v = hsvData[idx + 2] & 0xFF;
            if (h >= hMin && h <= hMax && s >= sMin && v >= vMin) {
                maskData[i] = (byte) 255;
            }
            idx += 3;
        }
        mask.data().put(maskData);

        // 形态学操作
        Mat kernel = getStructuringElement(MORPH_ELLIPSE, new Size(5, 5));
        morphologyEx(mask, mask, MORPH_CLOSE, kernel);
        morphologyEx(mask, mask, MORPH_OPEN, kernel);

        hsv.close();
        return mask;
    }

    /**
     * 检测文字区域（通过连通域分析）
     */
    public static Mat detectTextRegions(Mat binary) {
        int rows = binary.rows();
        int cols = binary.cols();
        Mat textMask = new Mat(rows, cols, CV_8UC1, new Scalar(0));

        Mat labels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();
        connectedComponentsWithStats(binary, labels, stats, centroids);

        int numLabels = stats.rows();
        // stats 是 CV_32S 类型，每行5个值: x, y, w, h, area
        int[] statsData = new int[numLabels * 5];
        stats.data().asBuffer().asIntBuffer().get(statsData);

        for (int i = 1; i < numLabels; i++) {
            int base = i * 5;
            int x = statsData[base];
            int y = statsData[base + 1];
            int w = statsData[base + 2];
            int h = statsData[base + 3];
            int area = statsData[base + 4];

            double aspect = (double) w / h;
            if (area > 20 && area < 500 && aspect > 0.2 && aspect < 5.0) {
                int pad = 2;
                int x1 = Math.max(0, x - pad);
                int y1 = Math.max(0, y - pad);
                int x2 = Math.min(cols - 1, x + w + pad);
                int y2 = Math.min(rows - 1, y + h + pad);
                rectangle(textMask, new Point(x1, y1), new Point(x2, y2),
                        new Scalar(255, 0, 0, 0), -1, 8, 0);
            }
        }

        labels.close();
        stats.close();
        centroids.close();
        return textMask;
    }

    /**
     * 从二值图中移除文字区域
     */
    public static Mat removeText(Mat binary, Mat textMask) {
        Mat result = binary.clone();
        int rows = result.rows();
        int cols = result.cols();

        byte[] resultData = new byte[rows * cols];
        byte[] maskData = new byte[rows * cols];
        result.data().get(resultData);
        textMask.data().get(maskData);

        for (int i = 0; i < resultData.length; i++) {
            if (maskData[i] != 0) {
                resultData[i] = 0;
            }
        }
        result.data().put(resultData);

        return result;
    }

    public static PreprocessResult preprocess(Mat colorImage, ProcessingParams params) {
        PreprocessResult result = new PreprocessResult();

        Mat gray = toGray(colorImage);
        Mat denoised = denoise(gray, params.denoiseSize);
        Mat binary = binarize(denoised);
        Mat textMask = detectTextRegions(binary);
        Mat binaryNoText = removeText(binary, textMask);
        Mat cleaned = morphClose(binaryNoText, (int) params.morphCloseSize);

        Mat fillMask = null;
        if (params.detectFilledRegions) {
            fillMask = extractFilledRegion(colorImage, params);
        }

        result.gray = gray;
        result.denoised = denoised;
        result.binary = binary;
        result.textMask = textMask;
        result.binaryNoText = binaryNoText;
        result.cleaned = cleaned;
        result.fillMask = fillMask;

        return result;
    }

    public static class PreprocessResult {
        public Mat gray;
        public Mat denoised;
        public Mat binary;
        public Mat textMask;
        public Mat binaryNoText;
        public Mat cleaned;
        public Mat fillMask;
    }
}
