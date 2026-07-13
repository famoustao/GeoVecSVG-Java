package com.geovecsvg;

import com.geovecsvg.detection.PrimitiveDetector;
import com.geovecsvg.model.*;
import com.geovecsvg.optimization.ConstraintOptimizer;
import com.geovecsvg.preprocess.ImagePreprocessor;
import org.bytedeco.opencv.opencv_core.Mat;
import java.util.List;

/**
 * 矢量化处理管道
 * 串联：预处理 -> 基元检测 -> 约束优化 -> 返回结果
 */
public class ProcessingPipeline {

    private final ProcessingParams params;

    public ProcessingPipeline(ProcessingParams params) {
        this.params = params;
    }

    /**
     * 处理图像，返回矢量化结果
     */
    public VectorResult process(Mat colorImage) {
        VectorResult result = new VectorResult();
        result.imageWidth = colorImage.cols();
        result.imageHeight = colorImage.rows();

        // 1. 预处理
        ImagePreprocessor.PreprocessResult pre = ImagePreprocessor.preprocess(colorImage, params);

        // 2. 直线检测
        List<LineSegment> rawLines = PrimitiveDetector.detectLines(pre.cleaned, params);
        result.lines = PrimitiveDetector.mergeCollinearLines(rawLines, 1.0, 3.0);

        // 3. 圆检测（在灰度图上检测，然后用 RANSAC 优化）
        result.circles = PrimitiveDetector.detectCircles(pre.gray, params);

        // 4. 填充区域多边形
        if (pre.fillMask != null) {
            result.filledRegions = PrimitiveDetector.extractFilledPolygons(pre.fillMask);
        }

        // 5. 计算顶点（直线交点）
        result.vertices = PrimitiveDetector.computeVertices(
                result.lines, result.imageWidth, result.imageHeight, params.snapDistance);

        // 6. 约束优化
        ConstraintOptimizer optimizer = new ConstraintOptimizer(params);
        optimizer.optimize(result);

        // 释放临时 Mat
        pre.gray.close();
        pre.denoised.close();
        pre.binary.close();
        pre.textMask.close();
        pre.binaryNoText.close();
        pre.cleaned.close();
        if (pre.fillMask != null) pre.fillMask.close();

        return result;
    }
}
