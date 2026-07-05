package com.silentauto.controller;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.util.Base64;
import android.view.Surface;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

final class H264VideoDecoder implements TextureView.SurfaceTextureListener {
    private final TextureView texture;
    private final Handler main;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "h264-video-decoder"));

    private Surface surface;
    private MediaCodec decoder;
    private String mime;
    private byte[] csd0;
    private byte[] csd1;
    private int width;
    private int height;
    private boolean waitingForKeyFrame = true;
    private boolean released;

    H264VideoDecoder(TextureView texture, Handler main) {
        this.texture = texture;
        this.main = main;
        texture.setSurfaceTextureListener(this);
        if (texture.isAvailable()) {
            onSurfaceTextureAvailable(texture.getSurfaceTexture(), texture.getWidth(), texture.getHeight());
        }
    }

    void configure(String mime, int width, int height, String csd0Base64, String csd1Base64) {
        run(() -> {
            this.mime = mime;
            this.width = width;
            this.height = height;
            this.csd0 = decode(csd0Base64);
            this.csd1 = decode(csd1Base64);
            waitingForKeyFrame = true;
            releaseDecoder();
            ensureDecoder();
        });
    }

    void queueSample(String base64, long ptsUs, int flags) {
        run(() -> {
            if (base64 == null || base64.isEmpty()) {
                return;
            }
            ensureDecoder();
            if (decoder == null) {
                return;
            }
            boolean keyFrame = (flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
            if (waitingForKeyFrame && !keyFrame) {
                return;
            }
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            int index = decoder.dequeueInputBuffer(0);
            if (index < 0) {
                drainOutput();
                return;
            }
            ByteBuffer buffer = decoder.getInputBuffer(index);
            if (buffer == null) {
                return;
            }
            buffer.clear();
            buffer.put(data);
            decoder.queueInputBuffer(index, 0, data.length, ptsUs, 0);
            waitingForKeyFrame = false;
            drainOutput();
        });
    }

    void release() {
        released = true;
        try {
            executor.execute(() -> {
                releaseDecoder();
                if (surface != null) {
                    surface.release();
                    surface = null;
                }
            });
        } catch (RejectedExecutionException ignored) {
            releaseDecoder();
            if (surface != null) {
                surface.release();
                surface = null;
            }
        }
        executor.shutdown();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        run(() -> {
            if (surface != null) {
                surface.release();
            }
            surface = new Surface(surfaceTexture);
            waitingForKeyFrame = true;
            ensureDecoder();
        });
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        run(() -> {
            releaseDecoder();
            if (surface != null) {
                surface.release();
                surface = null;
            }
        });
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }

    private void ensureDecoder() {
        if (released || decoder != null || surface == null || mime == null || width <= 0 || height <= 0) {
            return;
        }
        try {
            MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
            if (csd0 != null && csd0.length > 0) {
                format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0));
            }
            if (csd1 != null && csd1.length > 0) {
                format.setByteBuffer("csd-1", ByteBuffer.wrap(csd1));
            }
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, surface, null, 0);
            decoder.start();
            waitingForKeyFrame = true;
        } catch (Exception e) {
            releaseDecoder();
        }
    }

    private void drainOutput() {
        if (decoder == null) {
            return;
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            int index = decoder.dequeueOutputBuffer(info, 0);
            if (index >= 0) {
                decoder.releaseOutputBuffer(index, true);
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                continue;
            } else {
                return;
            }
        }
    }

    private void releaseDecoder() {
        if (decoder == null) {
            return;
        }
        try {
            decoder.stop();
        } catch (Exception ignored) {
        }
        try {
            decoder.release();
        } catch (Exception ignored) {
        }
        decoder = null;
    }

    private void run(Runnable runnable) {
        if (released) {
            return;
        }
        try {
            executor.execute(runnable);
        } catch (RejectedExecutionException ignored) {
        }
    }

    private byte[] decode(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        return Base64.decode(base64, Base64.DEFAULT);
    }
}
