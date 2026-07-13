package com.geovecsvg.model;

/**
 * 处理参数
 */
public class ProcessingParams {
    // 预处理
    public int binaryThreshold = 0; // 0 = Otsu自动
    public int denoiseSize = 3;
    public double morphCloseSize = 2.0;

    // 直线检测
    public double houghRho = 1;
    public double houghTheta = Math.PI / 180;
    public int houghThreshold = 50;
    public double minLineLength = 30;
    public double maxLineGap = 10;

    // 圆检测
    public double houghCircDp = 1.5;
    public double houghCircMinDist = 30;
    public int houghCircParam1 = 100;
    public double houghCircParam2 = 30;
    public int minCircleRadius = 5;
    public int maxCircleRadius = 100;

    // 填充区域检测
    public boolean detectFilledRegions = true;
    public int fillHueMin = 0;
    public int fillHueMax = 15;
    public int fillSatMin = 30;
    public int fillValMin = 180;

    // 约束优化
    public boolean enableOptimization = true;
    public double snapDistance = 3.0;
    public double angleToleranceDeg = 1.0;
    public int optimizationIterations = 20;
    public boolean enforceHorizontalVertical = true;
}
