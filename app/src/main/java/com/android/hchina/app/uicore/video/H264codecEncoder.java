package com.android.hchina.app.uicore.video;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * H264 MediaCodec编码类
 *
 * copyright 2020 上海汁味信息科技有限公司 ----------------------------
 *            这不是一个自由软件，未经授权不许任何使用和传播。
 * @author li_guotao
 * @version $Id:1.0.0$
 * @since 2020-06-24
 */
public class H264codecEncoder {
    private static final String TAG = H264codecEncoder.class.getSimpleName();
    private static final String VIDEO_EXT_FILE = "mp4";
    private static final boolean VIDEO = true;
    private static final boolean AUDIO = false;

    // 状态
    public enum Status { unknown, init, start, stop }

    // 上下文句柄
    private Context mContext;
    // 视频路径
    private String mVideoPath;
    private MediaMuxer mMediaMuxer;
    private Status mStatus = Status.unknown;
    private int mVideoTrack = -1;
    private int mAudioTrack = -1;
    private MediaCodec.BufferInfo mOutputVideoInfo = new MediaCodec.BufferInfo();
    private MediaCodec.BufferInfo mOutputAudioInfo = new MediaCodec.BufferInfo();
    private long mStartTime = 0;

    private H264codecEncoder(Context context) {
        mContext = context;
    }

    public Status getStatus() {
        return mStatus;
    }

    // 创建
    public void create(String path) {
        try {
            mVideoPath = path;
            mMediaMuxer = new MediaMuxer(mVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mStatus = Status.init;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 释放
    public void destory() {
        try {
            mStatus = Status.stop;
            writeEndStream();
            mMediaMuxer.stop();
            mMediaMuxer.release();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        mVideoPath = null;
        mVideoTrack = -1;
        mAudioTrack = -1;
        mStartTime = 0;
        mMediaMuxer = null;
    }

    // 初始化SPS和PPS
    private boolean initSpsAndPps(MediaFormat format, byte[] buffer, int length) {
        if (format == null || buffer == null || length <= 0) {
            return false;
        }

        // 判断I帧，然后根据具体情况获取sps, pps
        if ((buffer[4] & 0x1f) == 7) {
            int save_x = 0;
            Map<Integer, Integer> map = new HashMap<>();
            byte[] save = new byte[length];
            System.arraycopy(buffer, 0, save, 0, length);
            for (int i = 0; i < save.length - 3; i++) {
                if (save[i] == 0 && save[i + 1] == 0 && save[i + 2] == 0 && save[i + 3] == 1) {
                    map.put(save_x, i);
                    save_x++;
                    i = i + 3;
                } else if (save[i] == 0 && save[i + 1] == 0 && save[i + 2] == 1) {
                    map.put(save_x, i);
                    save_x++;
                    i = i + 2;
                }
            }

            if(map.size() <= 2) {
                return false;
            }

            int length_sps = map.get(1) - map.get(0) - 4;
            int offset_sps = map.get(0) + 3;
            byte[] sps = new byte[length_sps];
            System.arraycopy(save, offset_sps, sps, 0, length_sps);

            int length_pps = map.get(2) - map.get(1) - 4;
            int offset_pps = map.get(1) + 3;
            byte[] pps = new byte[length_pps];
            System.arraycopy(save, offset_pps, pps, 0, length_pps);
            format.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
            format.setByteBuffer("csd-1", ByteBuffer.wrap(pps));
            return true;
        }
        return false;
    }

    // 初始化视频编码
    private void initVideoCodec(byte[] buffer, int length, int width, int height) {
        if (mMediaMuxer == null || mVideoTrack != -1) {
            return;
        }

        MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
        if(!initSpsAndPps(format, buffer, length)) {
            return;
        }

        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        mVideoTrack = mMediaMuxer.addTrack(format);
        startMux();
    }

    // 初始化音频编码
    private void initAudioCodec(int sampleRate) {
        if (mMediaMuxer == null || mAudioTrack != -1) {
            return;
        }

        MediaFormat format = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, 2);
        // 指定PROFILE
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_STEREO); // CHANNEL_IN_STEREO 立体声
        int bitRate = sampleRate * AudioFormat.ENCODING_PCM_16BIT * 2;
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate); // 比特率
//    format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
//    format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
//    format.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_STEREO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
        }
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 100 * 1024);
        mAudioTrack = mMediaMuxer.addTrack(format);
        startMux();
    }

    // 开始
    private void startMux() {
        boolean video = !VIDEO || mVideoTrack != -1;
        boolean audio = !AUDIO || mAudioTrack != -1;
        if (video && audio) {
            mMediaMuxer.start();
            mStatus = Status.start;
        }
    }

    // 写入结束流
    private void writeEndStream() {
        if (mMediaMuxer == null || mStatus != Status.stop) {
            return;
        }

        ByteBuffer buffer;
        MediaCodec.BufferInfo eos;
        long presentationTimeUs = System.nanoTime() / 1000 - mStartTime;

        if (VIDEO && mVideoTrack != -1) {
            buffer = ByteBuffer.allocate(0);
            eos = new MediaCodec.BufferInfo();
            eos.set(0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            mMediaMuxer.writeSampleData(mVideoTrack, buffer, eos);
        }

        if (AUDIO && mVideoTrack != -1) {
            buffer = ByteBuffer.allocate(0);
            eos = new MediaCodec.BufferInfo();
            eos.set(0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            mMediaMuxer.writeSampleData(mAudioTrack, buffer, eos);
        }
    }

    // 增加视频(JLayer->notifyReceiveVideoData)
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void addVideo(byte[] data, int size, final int width, final int height, int frameType) {
        if (!VIDEO) {
            return;
        }

        if (frameType == 3) {
            initVideoCodec(data, size, width, height);
        }

        if (mMediaMuxer == null || mStatus != Status.start || data == null || size <= 0) {
            return;
        }

        int flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
        if (frameType == 3) {
            flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
        }

        if (mStartTime == 0) {
            mStartTime = System.nanoTime() / 1000;
        }

        long presentationTimeUs = System.nanoTime() / 1000 - mStartTime;
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.put(data, 0, size);
        mOutputVideoInfo.set(0, size, presentationTimeUs, flags);
        mMediaMuxer.writeSampleData(mVideoTrack, buffer, mOutputVideoInfo);
    }

    // 增加音视频(JLayer->notifyReceiveAudioData)
    public void addAudio(byte[] data, int size, int sampleRate, long timeStamp, int seq) {
        if (!AUDIO) {
            return;
        }

        initAudioCodec(sampleRate);
        if (mMediaMuxer == null || mStatus != Status.start || data == null || size <= 0) {
            return;
        }

        if ((VIDEO && mStartTime == 0) || (!VIDEO && mStartTime == 0)) {
            mStartTime = System.nanoTime() / 1000;
        }

        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.put(data, 0, size);
        long presentationTimeUs = System.nanoTime() / 1000 - mStartTime;
        mOutputAudioInfo.set(0, size, presentationTimeUs, MediaCodec.BUFFER_FLAG_SYNC_FRAME);
        mMediaMuxer.writeSampleData(mAudioTrack, buffer, mOutputAudioInfo);
    }
}
