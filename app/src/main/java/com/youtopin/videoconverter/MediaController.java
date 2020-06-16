package com.youtopin.videoconverter;

import android.annotation.SuppressLint;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;

public class MediaController {
    public final static String VIDEO_MIME_TYPE = "video/avc";
    public final static String AUIDO_MIME_TYPE = "audio/mp4a-latm";
    private static volatile MediaController Instance;

    public static MediaController getInstance() {
        MediaController localInstance = Instance;
        if (localInstance == null) {
            synchronized (MediaController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new MediaController();
                }
            }
        }
        return localInstance;
    }

    private static class VideoConvertRunnable implements Runnable {
        private final VideoInfo videoInfo;

        private VideoConvertRunnable(VideoInfo videoInfo) {
            this.videoInfo = videoInfo;
        }

        @Override
        public void run() {
            MediaController.getInstance().convertVideo(videoInfo);
        }

        public static void runConversion(VideoInfo videoInfo) {
            new Thread(() -> {
                try {
                    VideoConvertRunnable wrapper = new VideoConvertRunnable(videoInfo);
                    Thread th = new Thread(wrapper, "VideoConvertRunnable");
                    th.start();
                    th.join();
                } catch (Exception e) {
                    Log.e("", e.getMessage(), e);
                }
            }).start();
        }
    }

    private boolean convertVideo(VideoInfo videoInfo) {
        String videoPath = videoInfo.getPath();
        long startTime = videoInfo.getStartTime();
        long endTime = videoInfo.getEndTime();
        int resultWidth = videoInfo.getWidth();
        int resultHeight = videoInfo.getHeight();
        int rotationValue = videoInfo.getRotationValue();
        int framerate = videoInfo.getFrameRate();
        int bitrate = videoInfo.getBitrate();
        int rotateRender = 0;
        boolean isSecret = videoInfo.isSecret();
        final File cacheFile = new File(videoInfo.getOutput());
        if (cacheFile.exists()) {
            cacheFile.delete();
        }


        long duration;
        if (startTime > 0 && endTime > 0) {
            duration = endTime - startTime;
        } else if (endTime > 0) {
            duration = endTime;
        } else if (startTime > 0) {
            duration = videoInfo.getOriginalDuration() - startTime;
        } else {
            duration = videoInfo.getOriginalDuration();
        }

        if (framerate == 0) {
            framerate = 25;
        } else if (framerate > 59) {
            framerate = 59;
        }

        if (rotationValue == 90) {
            int temp = resultHeight;
            resultHeight = resultWidth;
            resultWidth = temp;
            rotationValue = 0;
            rotateRender = 270;
        } else if (rotationValue == 180) {
            rotateRender = 180;
            rotationValue = 0;
        } else if (rotationValue == 270) {
            int temp = resultHeight;
            resultHeight = resultWidth;
            resultWidth = temp;
            rotationValue = 0;
            rotateRender = 90;
        }

        boolean needCompress = videoInfo.isNeedCompress() || rotateRender != 0;
        VideoConvertorListener callback = videoInfo.getVideoConvertorListener();
        MediaCodecVideoConvertor videoConvertor = new MediaCodecVideoConvertor();
        boolean error = videoConvertor.convertVideo(videoPath, cacheFile,
                rotationValue, isSecret,
                resultWidth, resultHeight,
                framerate, bitrate,
                startTime, endTime,
                needCompress, duration,
                callback);

        if(error){
            callback.onCancel();
        }else {
            callback.onFinish();
        }

        return true;
    }

    public interface VideoConvertorListener {
        void onCancel();
        void onFinish();
        boolean checkConversionCanceled();
        void didWriteData(long availableSize,float progress);
    }

    public static int findTrack(MediaExtractor extractor, boolean audio) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (audio) {
                if (mime.startsWith("audio/")) {
                    return i;
                }
            } else {
                if (mime.startsWith("video/")) {
                    return i;
                }
            }
        }
        return -5;
    }

    @SuppressLint("NewApi")
    public static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        MediaCodecInfo lastCodecInfo = null;
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType)) {
                    lastCodecInfo = codecInfo;
                    String name = lastCodecInfo.getName();
                    if (name != null) {
                        if (!name.equals("OMX.SEC.avc.enc")) {
                            return lastCodecInfo;
                        } else if (name.equals("OMX.SEC.AVC.Encoder")) {
                            return lastCodecInfo;
                        }
                    }
                }
            }
        }
        return lastCodecInfo;
    }

    @SuppressLint("NewApi")
    public static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        int lastColorFormat = 0;
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                lastColorFormat = colorFormat;
                if (!(codecInfo.getName().equals("OMX.SEC.AVC.Encoder") && colorFormat == 19)) {
                    return colorFormat;
                }
            }
        }
        return lastColorFormat;
    }

    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }
}
