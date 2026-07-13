package com.geovecsvg.preprocess;

import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import com.geovecsvg.model.ProcessingParams;

/**
 * 图像预处理模块
 * - 二值化、去噪、形态学操作、颜色分割、文字区域检测
 */
public class ImagePreprocessor {

    /**
     * 加载图像为灰度图
     */
    public static Mat loadImage(String path) {
        return opencv_imgcodecs.imread(path);
    }

    /**
     * 转灰度图
     */
    public static Mat toGray(Mat colorImage) {
        Mat gray = new Mat();
        opencv_imgproc.cvtColor(colorImage, gray, opencv_imgproc.COLOR_BGR2GRAY);
        return gray;
    }

    /**
     * 高斯去噪
     */
    public static Mat denoise(Mat gray, int ksize) {
        Mat denoised = new Mat();
        int k = ksize % 2 == 0 ? ksize + 1 : ksize;
        opencv_imgproc.GaussianBlur(gray, denoised, new Size(k, k), 0);
        return denoised;
    }

    /**
     * Otsu 二值化（反相（线条为白，背景为黑
     */
    public static Mat binarize(Mat gray) {
        Mat binary = new Mat();
        opencv_imgproc.threshold(gray, binary, 0, 255, opencv_imgproc.THRESH_BINARY_INV | opencv_imgproc.THRESH_OTSU);
        return binary;
    }

    /**
     * 形态学闭运算（连接断开的线条）
     */
    public static Mat morphClose(Mat binary, double size) {
        Mat result = new Mat();
        int s = (int) Math.max(1, size);
        Mat kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new Size(s, s));
        opencv_imgproc.morphologyEx(binary, result, opencv_imgproc.MORPH_CLOSE, kernel);
        return result;
    }

    /**
     * 形态学开运算（去除小噪点）
     */
    public static Mat morphOpen(Mat binary, int size) {
        Mat result = new Mat();
        Mat kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new Size(size, size));
        opencv_imgproc.morphologyEx(binary, result, opencv_imgproc.MORPH_OPEN, kernel);
        return result;
    }

    /**
     * HSV 颜色分割，提取指定色调范围内的填充区域（如红色区域）
     */
    public static Mat extractFilledRegion(Mat colorImage, ProcessingParams params) {
        Mat hsv = new Mat();
        opencv_imgproc.cvtColor(colorImage, hsv, opencv_imgproc.COLOR_BGR2HSV);

        Mat mask = new Mat();
        Scalar lower = new Scalar(params.fillHueMin, params.fillSatMin, params.fillValMin, 0);
        Scalar upper = new Scalar(params.fillHueMax, 255, 255, 0);
        opencv_core.inRange(hsv, lower, upper, mask);

        // 形态学闭运算填充孔洞
        Mat kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_ELLIPSE, new Size(5, 5));
        opencv_imgproc.morphologyEx(mask, mask, opencv_imgproc.MORPH_CLOSE, kernel);
        opencv_imgproc.morphologyEx(mask, mask, opencv_imgproc.MORPH_OPEN, kernel);

        hsv.close();
        kernel.close();
        return mask;
    }

    /**
     * 检测文字区域（通过连通域分析，小而紧凑的区域可能是文字）
     * 返回文字掩膜（文字区域为白色）
     */
    public static Mat detectTextRegions(Mat binary) {
        Mat labels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();
        opencv_imgproc.connectedComponentsWithStats(binary, labels, stats, centroids);

        Mat textMask = Mat.zeros(binary.rows(), binary.cols(), opencv_core.CV_8UC1).asMat();

        int numLabels = stats.rows();
        for (int i = 1; i < numLabels; i++) {
            int x = stats.ptr(i).getInt(0 * 4);
            int y = stats.ptr(i).getInt(1 * 4);
            int w = stats.ptr(i).getInt(2 * 4);
            int h = stats.ptr(i).getInt(3 * 4);
            int area = stats.ptr(i).getInt(4 * 4);

            // 文字特征：面积小、宽高比适中、不是细长线条
            double aspect = (double) w / h;
            if (area > 20 && area < 500 && aspect > 0.2 && aspect < 5.0) {
                // 膨胀一点形成包围盒
                int pad = 2;
                int x1 = Math.max(0, x - pad);
                int y1 = Math.max(0, y - pad);
                int x2 = Math.min(binary.cols() - 1, x + w + pad);
                int y2 = Math.min(binary.rows() - 1, y + h + pad);
                opencv_imgproc.rectangle(textMask, new Point(x1, y1), new Point(x2, y2),
                        new Scalar(255, 0, 0, 0), opencv_imgproc.FILLED);
            }
        }

        labels.close();
        stats.close();
        centroids.close();
        return textMask;
    }

    /**
     * 从二值图中移除文字区域（将文字区域设为背景）
     */
    public static Mat removeText(Mat binary, Mat textMask) {
        Mat result = binary.clone();
        // 将文字掩膜区域设为 0（黑色背景）
        for (int y = 0; y < result.rows(); y++) {
            for (int x = 0; x < result.cols(); x++) {
                if (textMask.ptr(y, x).get() != 0) {
                    result.ptr(y, x).put((byte) 0);
                }
            }
        }
        return result;
    }

    /**
     * 完整预处理流程
     */
    public static PreprocessResult preprocess(Mat colorImage, ProcessingParams params) {
        PreprocessResult result = new PreprocessResult();

        Mat gray = toGray(colorImage);
        Mat denoised = denoise(gray, params.denoiseSize);
        Mat binary = binarize(denoised);

        // 检测文字区域
        Mat textMask = detectTextRegions(binary);
        Mat binaryNoText = removeText(binary, textMask);

        // 形态学闭运算
        Mat cleaned = morphClose(binaryNoText, (int) params.morphCloseSize);

        // 提取填充区域
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
