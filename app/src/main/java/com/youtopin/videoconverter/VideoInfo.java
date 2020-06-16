package com.youtopin.videoconverter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoInfo {
    private String path;
    private String output;
    private int width;
    private int height;
    private int frameRate;
    private int bitrate;
    private int rotationValue;
    private boolean secret;
    private long originalDuration;
    private long startTime;
    private long endTime;
    private boolean needCompress;
    private MediaController.VideoConvertorListener videoConvertorListener;
}
