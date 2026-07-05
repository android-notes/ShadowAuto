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
    private static final int I_FRAME_INTERVAL_SECONDS = 1;
    private static final long REPEAT_PREVIOUS_FRAME_AFTER_US = 150_000L;
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
        Prepared prepared = prepareBest(width, height);
        this.width = prepared.width;
        this.height = prepared.height;
        encoder = prepared.encoder;
        inputSurface = prepared.surface;
        thread = new Thread(this, "video-streamer");
        if (this.width != width || this.height != height) {
            logs.info(this.taskId, "video encoder size adjusted: " + width + "x" + height + " -> " + this.width + "x" + this.height);
        }
    }

    private Prepared prepareBest(int preferredWidth, int preferredHeight) throws Exception {
        Exception last = null;
        int[][] candidates = candidates(preferredWidth, preferredHeight);
        for (int[] candidate : candidates) {
            try {
                return prepareOne(candidate[0], candidate[1]);
            } catch (Exception e) {
                last = e;
            }
        }
        throw last == null ? new IllegalStateException("No video encoder size available") : last;
    }

    private Prepared prepareOne(int width, int height) throws Exception {
        MediaCodec codec = MediaCodec.createEncoderByType(MIME);
        MediaFormat format = MediaFormat.createVideoFormat(MIME, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRateFor(width, height));
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS);
        format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, REPEAT_PREVIOUS_FRAME_AFTER_US);
        format.setInteger(MediaFormat.KEY_PRIORITY, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            format.setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0);
            format.setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, FRAME_RATE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
        }
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            return new Prepared(width, height, codec, codec.createInputSurface());
        } catch (Exception e) {
            try {
                codec.release();
            } catch (Throwable ignored) {
            }
            throw e;
        }
    }

    int width() {
        return width;
    }

    int height() {
        return height;
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

    private static int bitRateFor(int width, int height) {
        long pixels = (long) width * height;
        long target = pixels * FRAME_RATE / 4;
        return (int) Math.max(900_000L, Math.min(5_000_000L, target));
    }

    private static int even(int value) {
        if (value <= 0) {
            return 2;
        }
        if ((value & 1) == 0) {
            return value;
        }
        return value - 1;
    }

    private static int alignDown(int value, int align) {
        int aligned = value / align * align;
        if (aligned < align) {
            return align;
        }
        return aligned;
    }

    private static int[][] candidates(int width, int height) {
        int exactWidth = even(width);
        int exactHeight = even(height);
        int alignedWidth = alignDown(exactWidth, 16);
        int alignedHeight = alignDown(exactHeight, 16);
        int[] scaled = scaleMaxSide(exactWidth, exactHeight, 1920);
        return new int[][]{
                {exactWidth, exactHeight},
                {alignedWidth, alignedHeight},
                scaled
        };
    }

    private static int[] scaleMaxSide(int width, int height, int maxSide) {
        int max = Math.max(width, height);
        if (max <= maxSide) {
            return new int[]{width, height};
        }
        float ratio = (float) maxSide / max;
        return new int[]{alignDown(Math.round(width * ratio), 16), alignDown(Math.round(height * ratio), 16)};
    }

    private static final class Prepared {
        final int width;
        final int height;
        final MediaCodec encoder;
        final Surface surface;

        Prepared(int width, int height, MediaCodec encoder, Surface surface) {
            this.width = width;
            this.height = height;
            this.encoder = encoder;
            this.surface = surface;
        }
    }
}
