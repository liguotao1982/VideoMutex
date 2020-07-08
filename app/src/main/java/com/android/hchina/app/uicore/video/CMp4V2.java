package com.android.hchina.app.uicore.video;

/**
 * JAVA调用C类 - 写MP4视频文件(H264+AAC)
 *
 * copyright 2020 上海汁味信息科技有限公司 ----------------------------
 *            这不是一个自由软件，未经授权不许任何使用和传播。
 * @author li_guotao
 * @version $Id:1.0.0$
 * @since 2020-06-11
 */
public class CMp4V2 {
    static {
        System.loadLibrary("mp4v2");
    }

    /**
     * 创建MP4文件
     *
     * @param path : 文件路径名称
     */
    public native static void createMp4file(String path);

    /**
     * 写入视频
     *
     * @param data : 视频数据
     * @param size : 数据大小
     * @param width : 视频宽度
     * @param height : 视频高度
     * @param keyFrame : 关键帧
     */
    public static native int writeVideo(byte[] data, int size, int width, int height, boolean keyFrame);

    /**
     * 写入音频
     *
     * @param data : 音频数据
     * @param size : 数据大小
     */
    public static native int writeAudio(byte[] data, int size);

    /* 关闭MP4文件 */
    public static native void closeMp4file();
}
