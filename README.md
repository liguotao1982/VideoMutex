# VideoMutex

安卓版视频合成H264+AAC解决方案

本工程的作用用于将H264视频和AAC音频文件合成MP4文件，提供两种解决方案

1. 通过使用MediaMuxer和MediaCodec技术将音视频合成MP4视频文件

   缺点：生成的视频播放中存在中间多条块状横线，前几秒存在卡住问题
   
2. 通过使用google 提供的mp4v2库文件生成MP4视频文件
   优化：比较完美


调用方法说明：
1. 方案1调用步骤：
   1). H264codecEncoder.start();
       开始创建MP4文件

   2). H264codecEncoder.addVideo();
       写入视频数据：此处会等到关键帧才会写入数据

   3). H264codecEncoder.addAudio();
       写入音频数据

   4). H264codecEncoder.destory();
       结束
   
2. 方案2调用步骤：
   1). VideoMuxer.start();
       开始创建MP4文件

   2). VideoMuxer.addVideo();
       写入视频数据：此处会等到关键帧才会写入数据
       
   3). VideoMuxer.addAudio();
       写入音频数据
       
   4). VideoMuxer.stop();
       结束
