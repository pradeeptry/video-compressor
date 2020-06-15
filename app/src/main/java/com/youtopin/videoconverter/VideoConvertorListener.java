package com.youtopin.videoconverter;

public interface VideoConvertorListener {
    boolean checkConversionCanceled();
    void didWriteData(long availableSize,float progress);
}
