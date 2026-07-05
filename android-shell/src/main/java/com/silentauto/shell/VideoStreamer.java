package com.silentauto.shell;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.Surface;

import java.nio.ByteBuffer;

final class VideoStreamer implements Runnable {
    private static final String MIME = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int FRAME_RATE = 8;
    private static final int BIT_RATE = 700_000;
    private static final int I_FRAME_INTERVAL_SECONDS = 1;
    private static final long DRAIN_TIMEOUT_US = 100_000L;

    private final LogHub logs;
    private final String taskId;
    private final int width;
    private final int height;
    private final MediaCodec encoder;
    private final Surface inputSurface;
    private final Thread thread;

    private volatile boolean running;

    VideoStreamer(LogHub logs, String taskId, int width, int height) throws Exception {
        this.logs = logs;
        this.taskId = taskId == null ? "" : taskId;
        this.width = width;
        this.height = height;
        encoder = MediaCodec.createEncoderByType(MIME);
        MediaFormat format = MediaFormat.createVideoFormat(MIME, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            format.setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0);
            format.setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, FRAME_RATE);
        }
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface = encoder.createInputSurface();
        thread = new Thread(this, "video-streamer");
    }

    Surface inputSurface() {
        return inputSurface;
    }

    void start() {
        running = true;
        encoder.start();
        requestKeyFrame();
        thread.start();
    }

    void stop() {
        running = false;
        try {
            encoder.signalEndOfInputStream();
        } catch (Throwable ignored) {
        }
        try {
            thread.join(1200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        release();
    }

    @Override
    public void run() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            while (running) {
                int index = encoder.dequeueOutputBuffer(info, DRAIN_TIMEOUT_US);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    logs.videoConfig(taskId, encoder.getOutputFormat());
                } else if (index >= 0) {
                    drainOutput(index, info);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
            }
        } catch (Throwable e) {
            logs.error(taskId, "video stream failed", e);
        }
    }

    private void drainOutput(int index, MediaCodec.BufferInfo info) {
        ByteBuffer data = encoder.getOutputBuffer(index);
        try {
            if (data == null || info.size <= 0) {
                return;
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                return;
            }
            byte[] bytes = new byte[info.size];
            data.position(info.offset);
            data.limit(info.offset + info.size);
            data.get(bytes);
            String sample = Base64.encodeToString(bytes, Base64.NO_WRAP);
            logs.videoSample(taskId, info.presentationTimeUs, info.flags, sample);
        } finally {
            encoder.releaseOutputBuffer(index, false);
        }
    }

    private void requestKeyFrame() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        try {
            Bundle params = new Bundle();
            params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            encoder.setParameters(params);
        } catch (Throwable ignored) {
        }
    }

    private void release() {
        try {
            encoder.stop();
        } catch (Throwable ignored) {
        }
        try {
            encoder.release();
        } catch (Throwable ignored) {
        }
        try {
            inputSurface.release();
        } catch (Throwable ignored) {
        }
    }
}
