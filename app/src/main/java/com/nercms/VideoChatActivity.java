package com.nercms;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.nercms.net.RtpSession;
import com.nercms.receive.VideoPlayView;

import java.io.IOException;

/**
 * 视频聊天活动
 */
public class VideoChatActivity extends AppCompatActivity {
    private VideoPlayView mShowView = null;
    private SurfaceView surfaceView;
    private Camera mCamera = null; //创建摄像头处理类
    private SurfaceHolder holder = null; //创建界面句柄，显示视频的窗口句柄
    private Handler handler = new Handler();

    private String remote_ip;
    private int remote_port;
    private boolean onlyDecode;
    private RtpSession mRtpSession;
    private int width = 352;
    private int height = 288;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_chat);
        remote_ip = getIntent().getStringExtra("remote_ip");
        remote_port = getIntent().getIntExtra("remote_port", 8080);
        onlyDecode = getIntent().getBooleanExtra("only_decode", false);
        mRtpSession = new RtpSession(remote_ip, remote_port, width, height);

        initView();
    }

    private void initView() {
        mShowView = (VideoPlayView) this.findViewById(R.id.video_play);
        surfaceView = (SurfaceView) findViewById(R.id.surface_view);
        holder = surfaceView.getHolder();
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                doStart();
            }
        }, 1000);
    }

    /**
     * 开启 接受 发送rtp线程  开启本地摄像头
     */
    public void doStart() {
        mRtpSession.init();
        mRtpSession.setDecodeCallback(mDecodeCallback);
        mRtpSession.start();
        if(!onlyDecode) {
            openCamera();
        }
    }

    private void openCamera(){
        if (mCamera == null) {

            /*mCamera = Camera.open(1); //实例化摄像头类对象  0为后置 1为前置
            mCamera.setDisplayOrientation(90); //视频旋转90度
            Camera.Parameters p = mCamera.getParameters(); //将摄像头参数传入p中
            p.setFlashMode("off");
            p.setPreviewSize(352, 288); //设置预览视频的尺寸，CIF格式352×288
            p.setPreviewFrameRate(15); //设置预览的帧率，15帧/秒
            mCamera.setParameters(p); //设置参数*/

            //摄像头设置，预览视频
            mCamera = Camera.open(0); //实例化摄像头类对象  0为后置 1为前置
            Camera.Parameters p = mCamera.getParameters(); //将摄像头参数传入p中
            p.setFlashMode("off");
            p.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            p.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
            p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            //p.setPreviewFormat(PixelFormat.YCbCr_420_SP); //设置预览视频的格式
            p.setPreviewFormat(ImageFormat.NV21);
            p.setPreviewSize(width, height); //设置预览视频的尺寸，CIF格式352×288
            //p.setPreviewSize(800, 600);
            p.setPreviewFrameRate(15); //设置预览的帧率，15帧/秒
            mCamera.setParameters(p); //设置参数

            byte[] rawBuf = new byte[1400];
            mCamera.addCallbackBuffer(rawBuf);
            mCamera.setDisplayOrientation(90); //视频旋转90度
            try {
                mCamera.setPreviewDisplay(holder); //预览的视频显示到指定窗口
            } catch (IOException e) {
                e.printStackTrace();
            }
            mCamera.startPreview(); //开始预览

            //获取帧
            //预览的回调函数在开始预览的时候以中断方式被调用，每秒调用15次，回调函数在预览的同时调出正在播放的帧
            Callback a = new Callback();
            mCamera.setPreviewCallback(a);
        }
    }

    //mCamera回调的类
    class Callback implements Camera.PreviewCallback {
        @Override
        public void onPreviewFrame(byte[] frame, Camera camera) {
            mRtpSession.sendPreviewFrame(frame);
        }
    }

    private RtpSession.DecodeCallback mDecodeCallback = new RtpSession.DecodeCallback() {
        @Override
        public void onDecoderEnd() {
            mShowView.postInvalidate();
        }

        @Override
        public byte[] getDecodeBuf() {
            return mShowView.mPixel;
        }
    };

    /**
     * 关闭摄像头 并释放资源
     */
    public void close() {

        mRtpSession.close();
        //释放摄像头资源
        if (mCamera != null) {
            mCamera.setPreviewCallback(null); //停止回调函数
            mCamera.stopPreview(); //停止预览
            mCamera.release(); //释放资源
            mCamera = null; //重新初始化
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        close();
    }
}
