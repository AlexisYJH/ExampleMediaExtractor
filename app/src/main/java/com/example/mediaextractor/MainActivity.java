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
    private static final String DCIM_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath();
    private static final String MUSIC_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getPath();
    private static final String INPUT_PATH = DCIM_PATH + "/input.mp4";
    private static final String OUTPUT_VIDEO_PATH = DCIM_PATH + "/output_video.mp4";
    private static final String OUTPUT_AUDIO_PATH = MUSIC_PATH + "/output_audio.mp3";
    private static final String OUTPUT_COMPOSITE_PATH = DCIM_PATH + "/output_composite.mp4";
    private static final String SUFFIX_AUDIO = ".mp3";
    private static final String PREFIX_VIDEO = "video/";
    private static final String PREFIX_AUDIO = "audio/";
    private static final int INVALID_INDEX = -1;

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
     * ????????????
     */
    private void extractorVideo() {
        showResult(extractor(OUTPUT_VIDEO_PATH), R.string.extractor_video_finish, R.string.extractor_video_fail);
    }

    /**
     * ????????????
     */
    private void extractorAudio() {
        showResult(extractor(OUTPUT_AUDIO_PATH), R.string.extractor_audio_finish, R.string.extractor_audio_fail);
    }

    /**
     * ???????????????
     */
    private void muterVideoAudio() {
        showResult(muter(OUTPUT_COMPOSITE_PATH, OUTPUT_VIDEO_PATH, OUTPUT_AUDIO_PATH)
                ,R.string.muter_finish, R.string.muter_fail);
    }

    private static boolean extractor(String outPath) {
        return muter(outPath, INPUT_PATH);
    }

    @SuppressLint("WrongConstant")
    private static boolean muter(String outPath, String... paths) {
        if (paths == null || paths.length <= 0) {
            return false;
        }

        int length = paths.length;
        MediaExtractor[] extractors = new MediaExtractor[length];
        int[] trackIndexs = new int[length];
        int[] writeTrackIndexs = new int[length];

        //?????????MediaMuxer
        MediaMuxer mediaMuxer = null;
        try {
            for (int i = 0; i < length; i++) {
                //??????MediaExtractor??????
                MediaExtractor extractor = new MediaExtractor();
                extractors[i] = extractor;
                //???????????????
                extractor.setDataSource(paths[i]);
                Log.d(TAG, "in: " + paths[i]);
                //????????????
                int trackIndex = getTrackIndex(extractor, getMediaPrefix(outPath, paths[i]));
                //Log.d(TAG, "getTrackIndex: " + trackIndex);
                if  (trackIndex == INVALID_INDEX) {
                    Log.w(TAG, "trackIndex invalid");
                    return false;
                }
                trackIndexs[i] = trackIndex;

                //????????????????????????
                extractor.selectTrack(trackIndex);

                //????????????????????????
                MediaFormat format = extractor.getTrackFormat(trackIndex);

                /*MediaMuxer?????????????????????????????????????????????????????????????????????????????????:
                1. ????????????
                */
                //??????MediaMuxer???????????????new MediaMuxer(String path, int format)?????????????????????????????????????????????
                if (mediaMuxer == null) {
                    mediaMuxer = new MediaMuxer(outPath,
                            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                }

                //2. ??????????????????????????????????????????????????????MediaMuxer?????????????????????
                writeTrackIndexs[i] = mediaMuxer.addTrack(format);
            }

            //3. ????????????
            //???????????????track?????????start??????????????????????????????
            mediaMuxer.start();

            //4. ???????????????????????????????????????????????????
            MediaExtractor extractor;
            ByteBuffer byteBuffer = ByteBuffer.allocate(CAPACITY);

            for (int i = 0; i < length; i++) {
                extractor = extractors[i];

                long sampleTime = getSmapleTime(extractor, byteBuffer);
                extractor.unselectTrack(trackIndexs[i]);
                extractor.selectTrack(trackIndexs[i]);

                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                while (true) {
                    //???????????????????????????????????????
                    int readVideoSampleSize = extractor.readSampleData(byteBuffer, 0);
                    //?????????????????????????????????????????????
                    if (readVideoSampleSize < 0) {
                        break;
                    }

                    bufferInfo.size = readVideoSampleSize;
                    bufferInfo.flags = extractor.getSampleFlags();
                    bufferInfo.offset = 0;
                    bufferInfo.presentationTimeUs += sampleTime;

                    //????????????
                    mediaMuxer.writeSampleData(writeTrackIndexs[i], byteBuffer, bufferInfo);
                    //?????????????????????
                    extractor.advance();
                }
            }

            //5. ?????????????????????
            mediaMuxer.stop();
            mediaMuxer.release();
            for (int i = 0; i < length; i++) {
                extractors[i].release();
            }
            Log.d(TAG, "out: " + outPath);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static String getMediaPrefix(String path1, String path2) {
        return (path1.endsWith(SUFFIX_AUDIO) || path2.endsWith(SUFFIX_AUDIO)) ? PREFIX_AUDIO : PREFIX_VIDEO;
    }

    private static int getTrackIndex(MediaExtractor extractor, String prefix) {
        int count = extractor.getTrackCount();
        for (int i = 0; i < count; i++) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith(prefix)) {
                return i;
            }
        }
        return INVALID_INDEX;
    }

    private static long getSmapleTime(MediaExtractor extractor, ByteBuffer byteBuffer) {
        long smapleTime = 0;
        //???????????????????????????????????????
        extractor.readSampleData(byteBuffer, 0);
        //skip first I frame
        if (extractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC) {
            //?????????????????????
            extractor.advance();
        }

        extractor.readSampleData(byteBuffer, 0);
        long secondTime = extractor.getSampleTime();
        extractor.advance();

        long thirdTime = extractor.getSampleTime();
        smapleTime = Math.abs(thirdTime - secondTime);
        //Log.d(TAG, "getSmapleTime: " + smapleTime);
        return smapleTime;
    }

    private void showResult(boolean success, int successId, int failId) {
        Toast.makeText(this,
                success ? getString(successId) : getString(failId),
                Toast.LENGTH_LONG).show();
    }

    public void verifyStoragePermissions() {
        try {
            // ???????????????????????????
            int permission = ActivityCompat.checkSelfPermission(this,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // ???????????????????????????????????????????????????????????????
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
                    Log.w(TAG, permissions[i] + "???????????????");
                }
            }
        }
    }
}