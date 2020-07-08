package com.android.hchina.app.uicore.video;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.text.TextUtils;

import com.hchina.android.api.HchinaAPI;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 视频合成 - 写MP4视频文件(H264+AAC)
 *
 * copyright 2020 上海汁味信息科技有限公司 ----------------------------
 *            这不是一个自由软件，未经授权不许任何使用和传播。
 * @author li_guotao
 * @version $Id:1.0.0$
 * @since 2020-06-11
 */
public class VideoMuxer {
  private static final String TAG = VideoMuxer.class.getSimpleName();
  public static final String VIDEO_EXT_FILE = "mp4";

  private static final String VIDEO_MIME_TYPE = "video/avc";
  private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";
  private static final int FRAME_RATE = 25;
  private static final int SAMPLE_RATE = 32000;
  private static final int ADT_HEADER = 7;
  private static final int CHANNEL_COUNT = 2;

  private static final boolean VIDEO = true;
  private static final boolean AUDIO = true;
  private static final int KEY_FRAME = 3;  // 关键视频帧
  private static final int WAIT_TIME = 30;  // 等待时间

  // 视频路径
  private Status mStatus = Status.unknown;
  private MediaCodec.BufferInfo mOutputAudioInfo = new MediaCodec.BufferInfo();
  private long mStartTime = 0;
  // 视频队列
  private Queue<VideoFrame> mVideoList;
  // 音频队列
  private Queue<AudioFrame> mAudioList;
  // 音视频对象锁
  private final Object mAVMutex = new Object();
  private final Object mWaitObject = new Object();
  // 音视频线程
  private AVThread mAVThread = null;

  private MediaCodec mAudioMediaCodec;
  private ByteBuffer[] mAudioInputBufferArray;
  private ByteBuffer[] mAudioOutputBufferArray;

  // 状态
  public enum Status { unknown, init, start, stop }

  // 视频帧
  public static class VideoFrame {
    byte[] data;
    int size;
    int width;
    int height;
    int frameType;

    // 判断是否有效
    public boolean isValid() {
      return data != null && size > 0;
    }
  }

  // 音频帧
  public static class AudioFrame {
    byte[] data;
    int size;

    // 判断是否有效
    public boolean isValid() {
      return data != null && size > 0;
    }
  }

  // 单例句柄
  @SuppressLint("StaticFieldLeak") private static VideoMuxer instance = null;
  public static VideoMuxer getInstance() {
    if (instance == null) {
      instance = new VideoMuxer();
    }
    return instance;
  }

  private VideoMuxer() {
    mVideoList = new ConcurrentLinkedQueue<>();
    mAudioList = new ConcurrentLinkedQueue<>();
  }

  public Status getStatus() {
    return mStatus;
  }

  // 开始
  public boolean start(String path) {
    if (TextUtils.isEmpty(path)) {
      return false;
    }

    mStatus = Status.init;
    CMp4V2.createMp4file(path);
    initAudioCodec();
    mAVThread = new AVThread();
    HchinaAPI.runTask(mAVThread);
    return true;
  }

  // 结束
  public void stop() {
    mStatus = Status.stop;
    synchronized (mAVMutex) {
      mVideoList.clear();
      mAudioList.clear();
    }
  }

  // 释放
  private void destory() {
    try {
      CMp4V2.closeMp4file();
      if(mAudioMediaCodec != null) {
        mAudioMediaCodec.stop();
        mAudioMediaCodec.release();
      }
    } catch (IllegalStateException e) {
      e.printStackTrace();
    }

    mAVThread = null;
    mStartTime = 0;
    mAudioMediaCodec = null;
  }

  // 初始化音频编码
  private void initAudioCodec() {
    if (mAudioMediaCodec != null) {
      return;
    }

    try {
      mAudioMediaCodec = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
      MediaFormat format =
              MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, SAMPLE_RATE, CHANNEL_COUNT);
      format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
      format.setInteger(MediaFormat.KEY_BIT_RATE, 320000);  // 比特率
      format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 100 * 1024);
      mAudioMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      mAudioMediaCodec.start();

      mAudioInputBufferArray = mAudioMediaCodec.getInputBuffers();
      mAudioOutputBufferArray = mAudioMediaCodec.getOutputBuffers();
      mOutputAudioInfo = new MediaCodec.BufferInfo();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  // 音视频线程
  private class AVThread implements Runnable {
    @Override
    public void run() {
      do {
        synchronized (mAVMutex) {
          do {
            if (!mVideoList.isEmpty()) {
              writeVideoFrame(mVideoList.poll());
            }
          } while (!mVideoList.isEmpty());

          do {
            if (!mAudioList.isEmpty()) {
              AudioFrame frame = mAudioList.poll();
              if (frame != null && frame.isValid()) {
                encodeAudioData(frame.data, frame.size);
              }
            }
          } while (!mAudioList.isEmpty());
        }

        try {
          synchronized (mWaitObject) {
            mWaitObject.wait(WAIT_TIME);
          }
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

      } while (mStatus != Status.stop);
      destory();
    }
  }

  // 增加视频
  public void addVideo(byte[] data, int size, final int width, final int height, int frameType) {
    if (!VIDEO || mStatus == Status.stop) {
      return;
    }

    synchronized (mAVMutex) {
      VideoFrame frame = new VideoFrame();
      frame.data = data;
      frame.size = size;
      frame.width = width;
      frame.height = height;
      frame.frameType = frameType;
      mVideoList.add(frame);
    }
  }

  // 增加音视频
  public void addAudio(byte[] data, int size) {
    if (!AUDIO || mStatus == Status.stop) {
      return;
    }

    synchronized (mAVMutex) {
      AudioFrame frame = new AudioFrame();
      frame.data = data;
      frame.size = size;
      mAudioList.add(frame);
    }
  }

  // 编码音频数据
  private void encodeAudioData(byte[] data, int size) {
    if (mAudioMediaCodec == null || data == null || size <= 0) {
      return;
    }

    // dequeueInputBuffer（time）需要传入一个时间值，-1表示一直等待，0表示不等待有可能会丢帧，其他表示等待多少毫秒
    int inputIndex = mAudioMediaCodec.dequeueInputBuffer(-1);  // 获取输入缓存的index
    if (inputIndex >= 0) {
      ByteBuffer inputByteBuf = mAudioInputBufferArray[inputIndex];
      inputByteBuf.clear();
      inputByteBuf.put(data);  // 添加数据
      inputByteBuf.limit(size);  // 限制ByteBuffer的访问长度
      mAudioMediaCodec.queueInputBuffer(inputIndex, 0, size, 0, 0);
    }

    int outputIndex = mAudioMediaCodec.dequeueOutputBuffer(mOutputAudioInfo, 0);
    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
      MediaFormat format = mAudioMediaCodec.getOutputFormat();
    }

    while (outputIndex >= 0) {
      writeAudioFrame(outputIndex);
      mAudioMediaCodec.releaseOutputBuffer(outputIndex, false);
      outputIndex = mAudioMediaCodec.dequeueOutputBuffer(mOutputAudioInfo, 0);
    }
  }

  // 写入音频帧数据
  private void writeAudioFrame(int outputIndex) {
    if (outputIndex < 0) {
      return;
    }

    // 获取缓存信息的长度
    int byteBufSize = mOutputAudioInfo.size;
    ByteBuffer buffer = mAudioOutputBufferArray[outputIndex];
    buffer.position(mOutputAudioInfo.offset);
    buffer.limit(mOutputAudioInfo.offset + mOutputAudioInfo.size);

    long presentationTimeUs = System.nanoTime() / 1000 - mStartTime;
    byte[] targetByte = new byte[mOutputAudioInfo.size];
    buffer.get(targetByte, 0, byteBufSize);
    buffer.position(mOutputAudioInfo.offset);
    mOutputAudioInfo.presentationTimeUs = presentationTimeUs;
    CMp4V2.writeAudio(targetByte, mOutputAudioInfo.size);
  }

  // 写入视频帧数据
  private void writeVideoFrame(VideoFrame frame) {
    if (frame != null && frame.isValid()) {
      CMp4V2.writeVideo(frame.data, frame.size, frame.width,
              frame.height, frame.frameType == KEY_FRAME);
    }
  }
}
