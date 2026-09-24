package com.sacco.mvp.service;

public record LogoUploadPolicy(
    int minWidthPx,
    int minHeightPx,
    int maxWidthPx,
    int maxHeightPx,
    int maxFileSizeKb
) {
    public long maxFileSizeBytes() {
        return maxFileSizeKb * 1024L;
    }

    public String dimensionsLabel() {
        return minWidthPx + "x" + minHeightPx + " and " + maxWidthPx + "x" + maxHeightPx + " pixels";
    }

    public String getDimensionsLabel() {
        return dimensionsLabel();
    }

    public String maxFileSizeLabel() {
        return maxFileSizeKb >= 1024 && maxFileSizeKb % 1024 == 0
            ? (maxFileSizeKb / 1024) + " MB"
            : maxFileSizeKb + " KB";
    }

    public String getMaxFileSizeLabel() {
        return maxFileSizeLabel();
    }

    public String helpText() {
        return "Use PNG or JPEG only, between " + dimensionsLabel() + ", up to " + maxFileSizeLabel() + ".";
    }

    public String getHelpText() {
        return helpText();
    }
}
