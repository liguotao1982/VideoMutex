package com.android.hchina.app.uicore.video;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

import java.nio.ByteBuffer;

/**
 * H264 MediaCodec解码类
 *
 * copyright 2020 上海汁味信息科技有限公司 ----------------------------
 *            这不是一个自由软件，未经授权不许任何使用和传播。
 * @author li_guotao
 * @version $Id:1.0.0$
 * @since 2020-06-24
 */
public class H264CodecDecoder {
  private static final String TAG = "decoder";
  private static final boolean DEBUG = false;

  private final static String MIME_TYPE = "video/avc"; // H.264 Advanced
  // Video
  private final static int TIME_INTERNAL = 5;
  private MediaCodec mMediaCodec;
  private int mCount;
  // params
  private int mWidth;
  private int mHeight;
  private int mFps;

  public H264CodecDecoder() {}

  // 开始停止
  public void start(int width, int height, int fps, Surface surface) {
    if (width <= 0 || height <= 0 || surface == null) {
      return;
    }

    if (this.mWidth != width || this.mHeight != height || this.mFps != fps) {
      stop();
    }

    this.mWidth = width;
    this.mHeight = height;
    this.mFps = fps;

    try {
      // 初始化MediaFormat
      MediaFormat mediaFormat =
          MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
      mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFps);
      // 配置MediaFormat
      mMediaCodec = MediaCodec.createDecoderByType(MIME_TYPE);
      mMediaCodec.configure(mediaFormat, surface, null, 0);
      mMediaCodec.start();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // 停止
  public void stop() {
    if (mMediaCodec != null) {
      try {
        mMediaCodec.stop();
        mMediaCodec.release();
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        mMediaCodec = null;
      }
    }
  }

  // 帧数据
  public boolean onFrame(byte[] buffer, int offset, int length) {
    if (mMediaCodec == null) {
      return false;
    }

    // 获取输入buffer index
    ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
    // -1表示一直等待；0表示不等待；其他大于0的参数表示等待毫秒数
    int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
    if (inputBufferIndex < 0)
      return false;

    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
    // 清空buffer
    inputBuffer.clear();
    // put需要解码的数据
    inputBuffer.put(buffer, offset, length);
    // 解码
    mMediaCodec.queueInputBuffer(inputBufferIndex, 0, length,
                                 mCount * TIME_INTERNAL, 0);
    mCount++;

    // 获取输出buffer index
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 100);
    // 循环解码，直到数据全部解码完成
    while (outputBufferIndex >= 0) {
      // true : 将解码的数据显示到surface上
      mMediaCodec.releaseOutputBuffer(outputBufferIndex, true);
      outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
    }
    return true;
  }
}
