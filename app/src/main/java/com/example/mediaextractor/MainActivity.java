package com.example.mediaextractor;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE" };
    private static final int CAPACITY = 500 * 1024;
    private static final String DCIM_PATH =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath();
            //Environment.getExternalStorageDirectory().getPath();
    private static final String MUSIC_PATH =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getPath();
    private static final String INPUT_PATH = "/input.mp4";
    private static final String OUTPUT_VIDEO_PATH = "/output_video.mp4";
    private static final String OUTPUT_AUDIO_PATH = "/output_audio.mp3";
    private static final String OUTPUT_COMPOSITE_PATH = "/output_composite.mp4";
    private static final String PREFIX_VIDEO = "video/";
    private static final String PREFIX_AUDIO = "audio/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        verifyStoragePermissions();
        addOnClickListener(R.id.btn_extractor_video, R.id.btn_extractor_audio, R.id.btn_muter_video_audio);
        Log.d(TAG, "DCIM_PATH: " + DCIM_PATH);
        Log.d(TAG, "MUSIC_PATH: " + MUSIC_PATH);
    }

    private void addOnClickListener(int... ids) {
        for (int i = 0; i < ids.length; i++) {
            Button button = findViewById(ids[i]);
            button.setOnClickListener(this);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_extractor_video:
                extractorVideo();
                break;
            case R.id.btn_extractor_audio:
                extractorAudio();
                break;
            case R.id.btn_muter_video_audio:
                muterVideoAudio();
                break;
            default:
                break;
        }
    }

    /**
     * 分离视频
     */
    @SuppressLint("WrongConstant")
    private void extractorVideo() {
        //创建MediaExtractor实例
        MediaExtractor mediaExtractor = new MediaExtractor();
        MediaMuxer mediaMuxer = null;
        // 轨道索引
        int videoIndex = -1;
        try {
            //设置数据源
            mediaExtractor.setDataSource(DCIM_PATH + INPUT_PATH);
            //数据源的轨道数
            int count = mediaExtractor.getTrackCount();
            for (int i = 0; i < count; i++) {
                //视频轨道格式信息
                MediaFormat format = mediaExtractor.getTrackFormat(i);
                if (format.getString(MediaFormat.KEY_MIME).startsWith(PREFIX_VIDEO)) {
                    //该轨道是视频轨道
                    videoIndex = i;
                }
            }

            //切换到想要的轨道
            mediaExtractor.selectTrack(videoIndex);

            //创建MediaMuxer实例，通过new MediaMuxer(String path, int format)指定视频文件输出路径和文件格式
            mediaMuxer = new MediaMuxer(DCIM_PATH + OUTPUT_VIDEO_PATH,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            //视频轨道格式信息
            MediaFormat format = mediaExtractor.getTrackFormat(videoIndex);

            //添加媒体通道
            int trackIndex = mediaMuxer.addTrack(format);

            ByteBuffer byteBuffer = ByteBuffer.allocate(CAPACITY);
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            //添加完所有track后调用start方法，开始音视频合成
            mediaMuxer.start();

            //获取帧之间的间隔时间
            long videoSampleTime;
            //将样本数据存储到字节缓存区
            mediaExtractor.readSampleData(byteBuffer, 0);
            //skip first I frame
            if (mediaExtractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC) {
                //读取下一帧数据
                mediaExtractor.advance();
            }
            mediaExtractor.readSampleData(byteBuffer, 0);
            long first = mediaExtractor.getSampleTime();

            mediaExtractor.advance();
            mediaExtractor.readSampleData(byteBuffer, 0);
            long second = mediaExtractor.getSampleTime();

            videoSampleTime = Math.abs(second - first);
            Log.d(TAG, "videoSampleTime is " + videoSampleTime);

            mediaExtractor.unselectTrack(videoIndex);
            mediaExtractor.selectTrack(videoIndex);

            while (true) {
                //将样本数据存储到字节缓存区
                int readSampleSize = mediaExtractor.readSampleData(byteBuffer, 0);
                if (readSampleSize < 0) {
                    break;
                }
                //读取下一帧数据
                mediaExtractor.advance();

                bufferInfo.size = readSampleSize;
                bufferInfo.offset = 0;
                bufferInfo.flags = mediaExtractor.getSampleFlags();
                bufferInfo.presentationTimeUs += videoSampleTime;

                //调用MediaMuxer.writeSampleData()向mp4文件中写入数据
                mediaMuxer.writeSampleData(trackIndex, byteBuffer, bufferInfo);
            }

            mediaMuxer.stop();
            mediaExtractor.release();
            mediaMuxer.release();
            Toast.makeText(this, getString(R.string.extractor_video_finish), Toast.LENGTH_LONG).show();
            Log.i(TAG, getString(R.string.extractor_video_finish));
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.extractor_video_fail), Toast.LENGTH_LONG).show();
            Log.e(TAG, getString(R.string.extractor_video_fail) + e.toString());
        }
    }

    @SuppressLint("WrongConstant")
    private void extractorAudio() {
        MediaExtractor mediaExtractor = new MediaExtractor();
        MediaMuxer mediaMuxer = null;
        int audioIndex = -1;
        try {
            mediaExtractor.setDataSource(DCIM_PATH + INPUT_PATH);
            int count = mediaExtractor.getTrackCount();
            for (int i = 0; i < count; i++) {
                MediaFormat format = mediaExtractor.getTrackFormat(i);
                if (format.getString(MediaFormat.KEY_MIME).startsWith(PREFIX_AUDIO)) {
                    audioIndex = i;
                }
            }
            mediaExtractor.selectTrack(audioIndex);

            MediaFormat format = mediaExtractor.getTrackFormat(audioIndex);
            mediaMuxer = new MediaMuxer(MUSIC_PATH + OUTPUT_AUDIO_PATH, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int writeAudioIndex = mediaMuxer.addTrack(format);
            mediaMuxer.start();

            ByteBuffer byteBuffer = ByteBuffer.allocate(CAPACITY);
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            long stampTime = 0;
            mediaExtractor.readSampleData(byteBuffer, 0);
            if (mediaExtractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC) {
                mediaExtractor.advance();
            }

            mediaExtractor.readSampleData(byteBuffer, 0);
            long secondTime = mediaExtractor.getSampleTime();
            mediaExtractor.advance();

            mediaExtractor.readSampleData(byteBuffer, 0);
            long thirdTime = mediaExtractor.getSampleTime();

            stampTime = Math.abs(thirdTime - secondTime);
            Log.d(TAG, "stampTime: " + stampTime);

            mediaExtractor.unselectTrack(audioIndex);
            mediaExtractor.selectTrack(audioIndex);

            while (true) {
                int readSampleSize = mediaExtractor.readSampleData(byteBuffer, 0);
                if (readSampleSize < 0) {
                    break;
                }
                mediaExtractor.advance();

                bufferInfo.size = readSampleSize;
                bufferInfo.flags = mediaExtractor.getSampleFlags();
                bufferInfo.offset = 0;
                bufferInfo.presentationTimeUs += stampTime;

                mediaMuxer.writeSampleData(writeAudioIndex, byteBuffer, bufferInfo);
            }

            mediaMuxer.stop();
            mediaMuxer.release();
            mediaExtractor.release();
            Toast.makeText(this, getString(R.string.extractor_audio_finish), Toast.LENGTH_LONG).show();
            Log.i(TAG, getString(R.string.extractor_audio_finish));
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.extractor_audio_fail), Toast.LENGTH_LONG).show();
            Log.e(TAG, getString(R.string.extractor_audio_fail) + e.toString());
        }

    }

    /**
     * 合成音视频
     */
    @SuppressLint("WrongConstant")
    private void muterVideoAudio() {
        try {
            //找到output_video.mp4中视频轨道
            MediaExtractor videoExtractor = new MediaExtractor();
            videoExtractor.setDataSource(DCIM_PATH + OUTPUT_VIDEO_PATH);
            MediaFormat videoformat = null;
            int videoTrackIndex = -1;
            int videoTrackCount = videoExtractor.getTrackCount();
            for (int i = 0; i < videoTrackCount; i++) {
                videoformat = videoExtractor.getTrackFormat(i);
                if (videoformat.getString(MediaFormat.KEY_MIME).startsWith(PREFIX_VIDEO)) {
                    videoTrackIndex = i;
                    break;
                }
            }

            //找到output_audio.mp3中音频轨道
            MediaExtractor audioExtractor = new MediaExtractor();
            audioExtractor.setDataSource(MUSIC_PATH + OUTPUT_AUDIO_PATH);
            MediaFormat audioformat = null;
            int audioTrackIndex = -1;
            int audioTrackCount = audioExtractor.getTrackCount();
            for (int i = 0; i < audioTrackCount; i++) {
                audioformat = audioExtractor.getTrackFormat(i);
                if (audioformat.getString(MediaFormat.KEY_MIME).startsWith(PREFIX_AUDIO)) {
                    audioTrackIndex = i;
                    break;
                }
            }

            videoExtractor.selectTrack(videoTrackIndex);
            audioExtractor.selectTrack(audioTrackIndex);

            MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();

            //通过new MediaMuxer(String path, int format)指定视频文件输出路径和文件格式
            MediaMuxer mediaMuxer = new MediaMuxer(DCIM_PATH + OUTPUT_COMPOSITE_PATH,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            //MediaMuxer添加媒体通道(视频)
            int writeVideoTrackIndex = mediaMuxer.addTrack(videoformat);
            //MediaMuxer添加媒体通道(音频)
            int writeAudioTrackIndex = mediaMuxer.addTrack(audioformat);
            //开始音视频合成
            mediaMuxer.start();

            ByteBuffer byteBuffer = ByteBuffer.allocate(CAPACITY);
            long sampleTime = 0;
            videoExtractor.readSampleData(byteBuffer, 0);
            if (videoExtractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC) {
                videoExtractor.advance();
            }

            videoExtractor.readSampleData(byteBuffer, 0);
            long secondTime = videoExtractor.getSampleTime();
            videoExtractor.advance();

            long thirdTime = videoExtractor.getSampleTime();
            sampleTime = Math.abs(thirdTime - secondTime);

            videoExtractor.unselectTrack(videoTrackIndex);
            videoExtractor.selectTrack(videoTrackIndex);

            while (true) {
                int readVideoSampleSize = videoExtractor.readSampleData(byteBuffer, 0);
                if (readVideoSampleSize < 0) {
                    break;
                }

                videoInfo.size = readVideoSampleSize;
                videoInfo.flags = videoExtractor.getSampleFlags();
                videoInfo.offset = 0;
                videoInfo.presentationTimeUs += sampleTime;

                mediaMuxer.writeSampleData(writeVideoTrackIndex, byteBuffer, videoInfo);
                videoExtractor.advance();
            }

            while (true) {
                int readAudioSampleSize = audioExtractor.readSampleData(byteBuffer, 0);
                if (readAudioSampleSize < 0) {
                    break;
                }

                audioInfo.size = readAudioSampleSize;
                audioInfo.offset = 0;
                audioInfo.flags = audioExtractor.getSampleFlags();
                audioInfo.presentationTimeUs += sampleTime;

                mediaMuxer.writeSampleData(writeAudioTrackIndex, byteBuffer, audioInfo);
                audioExtractor.advance();
            }

            mediaMuxer.stop();
            mediaMuxer.release();
            videoExtractor.release();
            audioExtractor.release();

            Toast.makeText(this, getString(R.string.muter_finish), Toast.LENGTH_LONG).show();
            Log.i(TAG, getString(R.string.muter_finish));
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.muter_fail), Toast.LENGTH_LONG).show();
            Log.e(TAG, getString(R.string.muter_fail) + e.toString());
        }
    }

    public void verifyStoragePermissions() {
        try {
            // 检测是否有写的权限
            int permission = ActivityCompat.checkSelfPermission(this,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_EXTERNAL_STORAGE) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, permissions[i] + "权限被禁止");
                }
            }
        }
    }
}