//0816修改，加入多环飞行控制策略
//第一版
package com.dji.videostreamdecodingsample;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;

import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.widget.Toast;
import com.dji.videostreamdecodingsample.media.DJIVideoStreamDecoder;
import dji.common.error.DJIError;
import dji.common.flightcontroller.Attitude;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.util.CommonCallbacks;

import com.dji.videostreamdecodingsample.media.NativeHelper;
import dji.common.camera.SettingsDefinitions;
import com.dji.videostreamdecodingsample.media.DJISimulatorApplication;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import dji.common.error.DJISDKError;
import dji.common.flightcontroller.simulator.InitializationData;
import dji.common.flightcontroller.simulator.SimulatorState;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.model.LocationCoordinate2D;
import dji.common.useraccount.UserAccountState;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.common.error.DJIError;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.useraccount.UserAccountManager;



//import com.dji.videostreamdecodingsample.internal.controller.DJISampleApplication;
//import com.dji.videostreamdecodingsample.internal.utils.DialogUtils;
//import com.dji.videostreamdecodingsample.internal.utils.ModuleVerificationUtil;
//import com.dji.videostreamdecodingsample.internal.utils.OnScreenJoystick;
//import com.dji.videostreamdecodingsample.internal.utils.ToastUtils;
//import com.dji.videostreamdecodingsample.internal.view.PresentableView;
import dji.thirdparty.afinal.core.AsyncTask;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import dji.common.product.Model;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
public class
MainActivity extends Activity implements DJICodecManager.YuvDataCallback,View.OnClickListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int MSG_WHAT_SHOW_TOAST = 0;
    private static final int MSG_WHAT_UPDATE_TITLE = 1;
    private SurfaceHolder.Callback surfaceCallback;
    private Bitmap bmp = null;
    //OpenCV库静态加载并初始化
    static {
        if (!OpenCVLoader.initDebug()) {
            Log.wtf(TAG, "OpenCV failed to load!");
        }
    }
    public MainActivity() {
    }
    private boolean IsRecognizeCircle = false;
    private boolean DisplayIsRecognizeCircle = false;
    private int num_of_circle_rcg=0;
    private int CircleHaveBeenCrossed=0;
    boolean TakeOffFlag = false;
    boolean IsTakeOffFlinished = false;
    boolean SearchFlag = false;
    boolean AimFlag = false;
    boolean CrossFlag = false;
    boolean LandingFlag = false;
    int count_TakeOff = 0;
    int count_Search = 0;
    int count_Aim = 0;
    int count_Cross=0;
    boolean EnableFlag = false;
    public float errorx=0;
    public float errory=0;
    public float errorz=0;
    Deque<Integer> que = new LinkedList<>() ;
    public int JudgeQueue(Deque<Integer> queue){
//        queue.
        int counter = 0;
        Iterator it = queue.iterator();
        while (it.hasNext()){
            int ii = (int)it.next();
            if(ii!=0){
                counter++;
            }

        }
        return counter;
    }
    private FlightController mFlightController;
    private enum DemoType { USE_TEXTURE_VIEW, USE_SURFACE_VIEW, USE_SURFACE_VIEW_DEMO_DECODER}
    private enum Circumstance { Search, Aim, Cross,Landing}
    private static Circumstance ccmtc = Circumstance.Search;
    private static DemoType demoType = DemoType.USE_SURFACE_VIEW_DEMO_DECODER;
    private VideoFeeder.VideoFeed standardVideoFeeder;
    protected VideoFeeder.VideoDataListener mReceivedVideoDataListener = null;
    private TextView titleTv,tv;
    public Handler mainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_WHAT_SHOW_TOAST:
                    Toast.makeText(getApplicationContext(), (String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
                case MSG_WHAT_UPDATE_TITLE:
                    if (titleTv != null) {
                        titleTv.setText((String) msg.obj);
                    }
                    break;
                default:
                    break;
            }
        }
    };
    private Button mBtnEnableVirtualStick;
    private Button mBtnDisableVirtualStick;
    private Button mBtnTakeOff;
    private TextureView videostreamPreviewTtView;
    private SurfaceView videostreamPreviewSf;
    private SurfaceHolder videostreamPreviewSh;
    private Camera mCamera;
    private DJICodecManager mCodecManager;
    private TextView savePath;
    private Button screenShot;
    private StringBuilder stringBuilder;
    private int videoViewWidth;
    private int videoViewHeight;
    //count作为全局变量控制飞行
    private int count,IsRecognizeMode=0;
    private int controlflag=0;
    private double xx=320,yy=240,zz=145;
    private Timer mSendVirtualStickDataTimer;
    private SendVirtualStickDataTask mSendVirtualStickDataTask;
    @Override
    protected void onResume() {
        super.onResume();
        initSurfaceOrTextureView();
        notifyStatusChange();
        initFlightController();
    }
    private void initFlightController() {
        Aircraft aircraft = DJISimulatorApplication.getAircraftInstance();
        if (aircraft == null || !aircraft.isConnected()) {
            showToast("Disconnected");
            mFlightController = null;
            return;
        } else {
            mFlightController = aircraft.getFlightController();
            mFlightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
            mFlightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
            mFlightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
            mFlightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);

        }
    }
    private void initSurfaceOrTextureView(){
        switch (demoType) {
            case USE_SURFACE_VIEW:
                initPreviewerSurfaceView();
                break;
            case USE_SURFACE_VIEW_DEMO_DECODER:
                /**
                 * we also need init the textureView because the pre-transcoded video steam will display in the textureView
                 */
                initPreviewerTextureView();
                /**
                 * we use standardVideoFeeder to pass the transcoded video data to DJIVideoStreamDecoder, and then display it
                 * on surfaceView
                 */
                initPreviewerSurfaceView();
                break;
            case USE_TEXTURE_VIEW:
                initPreviewerTextureView();
                break;
        }
    }

    @Override
    protected void onPause() {
        if (mCamera != null) {
            if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
                VideoFeeder.getInstance().getPrimaryVideoFeed().removeVideoDataListener(mReceivedVideoDataListener);
            }
            if (standardVideoFeeder != null) {
                standardVideoFeeder.removeVideoDataListener(mReceivedVideoDataListener);
            }
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager.destroyCodec();
        }
        super.onDestroy();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initUi();
    }

    private void showToast(String s) {
        mainHandler.sendMessage(
                mainHandler.obtainMessage(MSG_WHAT_SHOW_TOAST, s)
        );
    }

    private void updateTitle(String s) {
        mainHandler.sendMessage(
                mainHandler.obtainMessage(MSG_WHAT_UPDATE_TITLE, s)
        );
    }

    private void initUi() {
        mBtnEnableVirtualStick = (Button) findViewById(R.id.btn_enable_virtual_stick);
        mBtnDisableVirtualStick = (Button) findViewById(R.id.btn_disable_virtual_stick);
        mBtnTakeOff = (Button) findViewById(R.id.btn_take_off);
        mBtnEnableVirtualStick.setOnClickListener(this);
        mBtnDisableVirtualStick.setOnClickListener(this);
        mBtnTakeOff.setOnClickListener(this);
        savePath = (TextView) findViewById(R.id.activity_main_save_path);
        screenShot = (Button) findViewById(R.id.activity_main_screen_shot);
        screenShot.setSelected(false);
        tv = (TextView)findViewById(R.id.text_x);
        titleTv = (TextView) findViewById(R.id.title_tv);
        videostreamPreviewTtView = (TextureView) findViewById(R.id.livestream_preview_ttv);
        videostreamPreviewSf = (SurfaceView) findViewById(R.id.livestream_preview_sf);
        videostreamPreviewSf.setClickable(true);
        videostreamPreviewSf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float rate = VideoFeeder.getInstance().getTranscodingDataRate();
                showToast("current rate:" + rate + "Mbps");
                if (rate < 10) {
                    VideoFeeder.getInstance().setTranscodingDataRate(10.0f);
                    showToast("set rate to 10Mbps");
                } else {
                    VideoFeeder.getInstance().setTranscodingDataRate(3.0f);
                    showToast("set rate to 3Mbps");
                }
            }
        });
        updateUIVisibility();
    }
    private void updateUIVisibility(){
        switch (demoType) {
            case USE_SURFACE_VIEW:
                Log.d("ooooo", "22222"  + "\n");
                videostreamPreviewSf.setVisibility(View.VISIBLE);
                videostreamPreviewTtView.setVisibility(View.GONE);
                break;
            case USE_SURFACE_VIEW_DEMO_DECODER:
                /**
                 * we need display two video stream at the same time, so we need let them to be visible.
                 */
                videostreamPreviewSf.setVisibility(View.VISIBLE);
                videostreamPreviewTtView.setVisibility(View.VISIBLE);
                break;

            case USE_TEXTURE_VIEW:
                videostreamPreviewSf.setVisibility(View.GONE);
                videostreamPreviewTtView.setVisibility(View.VISIBLE);
                break;
        }
    }
    private long lastupdate;
    private void notifyStatusChange() {
        final BaseProduct product = VideoDecodingApplication.getProductInstance();
        Log.d(TAG, "notifyStatusChange: " + (product == null ? "Disconnect" : (product.getModel() == null ? "null model" : product.getModel().name())));
        if (product != null && product.isConnected() && product.getModel() != null) {
            updateTitle(product.getModel().name() + " Connected " + demoType.name());
        } else {
            updateTitle("Disconnected");
        }
        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataListener = new VideoFeeder.VideoDataListener() {

            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                if (System.currentTimeMillis() - lastupdate > 1000) {
                    Log.d("kkkkkk","size="+size+"\n");
                    lastupdate = System.currentTimeMillis();
                }
                switch (demoType) {
                    case USE_SURFACE_VIEW:
                        if (mCodecManager != null) {
                            mCodecManager.sendDataToDecoder(videoBuffer, size);
                        }
                        break;
                    case USE_SURFACE_VIEW_DEMO_DECODER:
                        /**
                         we use standardVideoFeeder to pass the transcoded video data to DJIVideoStreamDecoder, and then display it
                         * on surfaceView
                         */
                        DJIVideoStreamDecoder.getInstance().parse(videoBuffer, size);
                        break;

                    case USE_TEXTURE_VIEW:
                        if (mCodecManager != null) {
                            mCodecManager.sendDataToDecoder(videoBuffer, size);
                        }
                        break;
                }

            }
        };

        if (null == product || !product.isConnected()) {
            mCamera = null;
            showToast("Disconnected");
        } else {
            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
                mCamera = product.getCamera();
                mCamera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, new CommonCallbacks.CompletionCallback()
                {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError != null) {
                            showToast("can't change mode of camera, error:"+djiError.getDescription());
                        }
                    }
                });

                if (demoType == DemoType.USE_SURFACE_VIEW_DEMO_DECODER) {
                    if (VideoFeeder.getInstance() != null) {
                        standardVideoFeeder = VideoFeeder.getInstance().provideTranscodedVideoFeed();
                        standardVideoFeeder.addVideoDataListener(mReceivedVideoDataListener);
                    }
                } else {
                    if (VideoFeeder.getInstance().getPrimaryVideoFeed() != null) {
                        VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(mReceivedVideoDataListener);
                    }
                }
            }
        }
    }

    /**
     * Init a fake texture view to for the codec manager, so that the video raw data can be received
     * by the camera
     */
    private void initPreviewerTextureView() {
        videostreamPreviewTtView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "real onSurfaceTextureAvailable");
                videoViewWidth = width;
                videoViewHeight = height;
                Log.d(TAG, "real onSurfaceTextureAvailable: width " + videoViewWidth + " height " + videoViewHeight);
                if (mCodecManager == null) {
                    mCodecManager = new DJICodecManager(getApplicationContext(), surface, width, height);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                videoViewWidth = width;
                videoViewHeight = height;
                Log.d(TAG, "real onSurfaceTextureAvailable2: width " + videoViewWidth + " height " + videoViewHeight);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (mCodecManager != null) {
                    mCodecManager.cleanSurface();
                }
                return false;
            }
            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }
    /**
     * Init a surface view for the DJIVideoStreamDecoder
     */
    private void initPreviewerSurfaceView() {
        videostreamPreviewSh = videostreamPreviewSf.getHolder();
        surfaceCallback = new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(TAG, "real onSurfaceTextureAvailable");
                videoViewWidth = videostreamPreviewSf.getWidth();
                videoViewHeight = videostreamPreviewSf.getHeight();
                Log.d(TAG, "real onSurfaceTextureAvailable3: width " + videoViewWidth + " height " + videoViewHeight);
                switch (demoType) {
                    case USE_SURFACE_VIEW:
                        if (mCodecManager == null) {
                            mCodecManager = new DJICodecManager(getApplicationContext(), holder, videoViewWidth,
                                    videoViewHeight);
                        }
                        break;
                    case USE_SURFACE_VIEW_DEMO_DECODER:
                        // This demo might not work well on P3C and OSMO.
                        NativeHelper.getInstance().init();
                        DJIVideoStreamDecoder.getInstance().init(getApplicationContext(), holder.getSurface());
                        DJIVideoStreamDecoder.getInstance().resume();
                        break;
                }

            }
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                videoViewWidth = width;
                videoViewHeight = height;
                Log.d(TAG, "real onSurfaceTextureAvailable4: width " + videoViewWidth + " height " + videoViewHeight);
                switch (demoType) {
                    case USE_SURFACE_VIEW:
                        mCodecManager.onSurfaceSizeChanged(videoViewWidth, videoViewHeight, 0);
                        break;
                    case USE_SURFACE_VIEW_DEMO_DECODER:
                        DJIVideoStreamDecoder.getInstance().changeSurface(holder.getSurface());
                        break;
                }
            }
            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                switch (demoType) {
                    case USE_SURFACE_VIEW:
                        if (mCodecManager != null) {
                            mCodecManager.cleanSurface();
                            mCodecManager.destroyCodec();
                            mCodecManager = null;
                        }
                        break;
                    case USE_SURFACE_VIEW_DEMO_DECODER:
                        DJIVideoStreamDecoder.getInstance().stop();
                        NativeHelper.getInstance().release();
                        break;
                }

            }
        };
        videostreamPreviewSh.addCallback(surfaceCallback);
    }
    @Override
    public void onYuvDataReceived(final ByteBuffer yuvFrame, int dataSize, final int width, final int height) {
        //In this demo, we test the YUV data by saving it into JPG files.
        //DJILog.d(TAG, "onYuvDataReceived " + dataSize);
        if (count++ % 5 == 0 && yuvFrame != null) {
            final byte[] bytes = new byte[dataSize];
            yuvFrame.get(bytes);
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
//                    saveYuvDataToJPEG(bytes, width, height);
                    GetXYZFromMat(bytes, width, height);
                }
            });
        }
    }

//    private void saveYuvDataToJPEG(byte[] yuvFrame, int width, int height){
//        if (yuvFrame.length < width * height) {
//            return;
//        }
//        byte[] bytes;
//        //bytes是未经过处理的yuv数据,研究发现这个数据可以被转换为Mat格式并保存下来！！！
//        bytes = yuvFrame;
//        GetXYZFromMat(bytes,Environment.getExternalStorageDirectory() + "/DJI_ScreenShot", width, height);
//    }
    /**
     * 得到Mat中识别到圆环的XYZ，以右手坐标系为标准
     */
    private void GetXYZFromMat(byte[] buf, int width, int height) {
        //LY
        Mat mat_temp = new Mat(height*3/2,width,0);//初始化一个矩阵,没数据
        mat_temp.put(0,0,buf);
        Mat bgr_i420 = new Mat();
//        Imgproc.cvtColor(mat_temp , bgr_i420, Imgproc.COLOR_YUV2BGR_I420);//转换颜色空间
        Imgproc.cvtColor(mat_temp , bgr_i420, Imgproc.COLOR_YUV2RGB_I420);//转换颜色空间
        //开始识别
        Mat input_rgb = new Mat();
//        Imgproc.pyrDown(bgr_i420,input_rgb,new Size(640,480));
        //降低图片分辨率
        Imgproc.resize(bgr_i420,input_rgb,new Size(640,360));
        //膨胀，设置操作内核
        Mat strElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT,
                new Size(3, 3),
                new Point(-1, -1));
        //阈值分割
        Mat hsv_image = new Mat();
        Imgproc.cvtColor(input_rgb, hsv_image, Imgproc.COLOR_RGB2HSV);
        Mat blue_pic = new Mat();
//        //阈值分割,橙黄色
//        Core.inRange(hsv_image, new Scalar(11, 43, 46), new Scalar(255, 255, 255), blue_pic);
//        阈值分割,陈
        Core.inRange(hsv_image, new Scalar(0, 20, 120), new Scalar(255, 255, 255), blue_pic);
//        // 室外黄色
//        Core.inRange(hsv_image, new Scalar(0, 93, 130), new Scalar(255, 255, 255), blue_pic);
//        Imgproc.GaussianBlur(blue_pic, blue_pic, new Size(9, 9), 2, 2);
        Log.d("cam", "进入Imgproc.GaussianBlur" + "\n");
        //腐蚀
        //膨胀操作
        Imgproc.dilate(blue_pic,blue_pic,strElement,new Point(-1, -1), 1);
        Mat edges = new Mat();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(blue_pic, contours, edges, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        Point center = new Point();
        float[] radius = new float[10];
        double[] arrayXYZ = new double[100];
        Vector<Float> arrayX = new Vector<>();
        Vector<Float> arrayY = new Vector<>();
        Vector<Float> arrayZ = new Vector<>();
        if (contours.size() > 0) {
            for (int i = 0; i < contours.size(); i++) {
                MatOfPoint SrcMtx = contours.get(i);
                MatOfPoint2f NewMtx = new MatOfPoint2f(SrcMtx.toArray());
//                Log.d("center", "采集到的面积为:  " + Imgproc.contourArea(contours.get(i))+"\n");
                if (Imgproc.contourArea(contours.get(i)) > 8000 && Imgproc.contourArea(contours.get(i)) <300000) {
                    Log.d("center", "采集到的面积为:  " + Imgproc.contourArea(contours.get(i))+"\n");
                    Imgproc.minEnclosingCircle(NewMtx, center, radius);
                    if (null == center) {
                        break;
                    }
                    Log.d("center", "最小外接圆面积为:  " + 3.1415 * radius[0] * radius[0]+"\n");
                    if (3.1415 * radius[0] * radius[0] / Imgproc.contourArea(contours.get(i)) < 1.5) {
                        Log.d("center", "识别到了符合要求的圆环IsRecognizeCircle = true" + "\n");
                        Log.d("center", "Imgproc.contourArea(contours.get(i)) < 1.5" + "\n");
                        Moments retral = Imgproc.moments(contours.get(i));
                        float c_x = (float) (retral.m10 / retral.m00);
                        float c_y = (float) (retral.m01 / retral.m00);
                        center.x = c_x;
                        center.y = c_y;
                        arrayX.add(c_x);
                        arrayY.add(c_y);
                        arrayZ.add(radius[0]);
                        Log.d("center", "center.x =" + c_x + "\n");
                        Log.d("center", "center.y =" + c_y + "\n");
                        Log.d("center", "radius =" + radius[0] + "\n");
//                        Log.i(TAG, String.valueOf("size: ") + "<1.5");
//                        Imgproc.circle(input_rgb, center, 5, new Scalar(0, 255, 0), 5);
//                        Imgproc.circle(input_rgb, center, (int) radius[0], new Scalar(0, 255, 0), 5);
                    }
                }
            }
        }
        num_of_circle_rcg = arrayX.size();
        IsRecognizeCircle = IsRecognized(num_of_circle_rcg);
        if(IsRecognizeCircle)
        {
            xx = averageFloat(arrayX);
            yy = averageFloat(arrayY);
            zz = minFloat(arrayZ);
            Imgproc.circle(input_rgb,
                    new Point((int)xx,(int)yy),
                    5,
                    new Scalar(0, 255, 0),
                    5);
            Imgproc.circle(input_rgb,
                    new Point((int)xx,(int)yy),
                    (int) zz,
                    new Scalar(0, 255, 0),
                    5);
            arrayX.clear();
            arrayY.clear();
            arrayZ.clear();
        }
        Log.d("center", "center.x =" + xx + "\n");
        Log.d("center", "center.y =" + yy + "\n");
        Log.d("center", "center.z =" + zz + "\n");
//        //识别结束，新建文件以保存
//        File dir = new File(Environment.getExternalStorageDirectory() + "/DJI_Test7");
//        if (!dir.exists() || !dir.isDirectory()) {
//            dir.mkdirs();
//        }
//        OutputStream outputFile;
//        final String path = dir + "/Test_" + System.currentTimeMillis() + ".jpg";
//        try {
//            outputFile = new FileOutputStream(new File(path));
//        } catch (FileNotFoundException e) {
//            Log.e(TAG, "test screenShot: new bitmap output file error: " + e);
//            return;
//        }
//        //将得到的Mat类型在转换为Bitmap类型以便保存观察
//        bmp = Bitmap.createBitmap(input_rgb.cols(), input_rgb.rows(), Bitmap.Config.ARGB_8888);
//        Utils.matToBitmap(input_rgb,bmp);
//        bmp.compress(Bitmap.CompressFormat.JPEG, 80, outputFile);
//        try {
//            outputFile.close();
//        } catch (IOException e) {
//            Log.e(TAG, "test screenShot: compress yuv image error: " + e);
//            e.printStackTrace();
//        }
//        //识别结束，新建文件以保存end

//        gray_img.release();
//        circles.release();
        input_rgb.release();
        hsv_image.release();
        blue_pic.release();
        bgr_i420.release();
        //开启UI线程
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                displayPath();
            }
        });
    }
    private boolean IsRecognized(int size)
    {
        boolean IsCircle = false;
        if(size>0)
            IsCircle = true;
        return IsCircle;

    }
    private void displayPath() {
        if (stringBuilder == null) {
            stringBuilder = new StringBuilder();
        }
        String Tmessage;
        Tmessage = "xx = " + xx + "#" +"\n"+
                "yy = " + yy + "#"+"\n"+
                "zz = " + zz + "#"+"\n"+
                "num_of_circle_rcg = "+num_of_circle_rcg+"\n"+
                "count_TakeOff =" + count_TakeOff+ "#"+"\n"+
                "count_Search =" + count_Search+ "#"+"\n"+
                "count_Aim =" + count_Aim+ "#"+"\n"+
                "CircleHaveBeenCrossed =" + CircleHaveBeenCrossed+ "#"+"\n"+
                "count_Cross =" + count_Cross+ "#"+"\n";
        tv.setText(Tmessage);
    }
    private static float averageFloat(Vector<Float> array)
    {
        float temp = 0;
        float sum = 0;
        for (int i = 0; i < array.size(); i++) {
            sum+=array.get(i);
        }
        temp=sum/array.size();
        return temp;
    }

    private static float minFloat(Vector<Float> array)
    {
        float temp = array.get(0);
        for (int i = 0; i < array.size(); i++) {
            if(temp>array.get(i))
            {
                temp = array.get(i);
            }
        }
        return temp;
    }

    public void onClick(View v) {
        Aircraft aircraft = DJISimulatorApplication.getAircraftInstance();
        FlightController mFlightController = aircraft.getFlightController();
        if (mFlightController == null) {
            return;
        }
        switch (v.getId()) {
            case R.id.btn_enable_virtual_stick:
                EnableFlag = true;
                if (mFlightController != null){
                    Log.d("erer", "222222222222"  + "\n");
                    if (null == mSendVirtualStickDataTimer) {controlflag=0;
                        mSendVirtualStickDataTask = new SendVirtualStickDataTask();
                        mSendVirtualStickDataTimer = new Timer();

                        mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 100);
                        Log.d("ooooo", "111111"  + "\n");
                        Log.d("ooooo","controlflag="+controlflag+"\n");

                    }
                }
                break;

            case R.id.btn_disable_virtual_stick:
                EnableFlag = false;
                if (mFlightController != null){
                    controlflag=1;
                    Log.d("erer", "11111111111111"  + "\n");
                    mSendVirtualStickDataTask.cancel();
                    mSendVirtualStickDataTimer.cancel();
                    mSendVirtualStickDataTask=null;
                    mSendVirtualStickDataTimer=null;
                }
                break;

            case R.id.btn_take_off:
                Log.d("erer", "222222222"  + "\n");
//                if (mFlightController != null){
//                    Log.d("erer", "33333333"  + "\n");
//                    mFlightController.startTakeoff(
//                            new CommonCallbacks.CompletionCallback() {
//                                @Override
//                                public void onResult(DJIError djiError) {
//                                    if (djiError != null) {
//                                        showToast(djiError.getDescription());
//                                    } else {
//                                        showToast("Take off Success");
//                                    }
//                                }
//                            }
//                    );
//                }
                break;

            case   R.id.activity_main_screen_shot:

                Log.d("erer", "4444"  + "\n");
                handleYUVClick();
                break;

            default:
                break;
        }

    }
    private void handleYUVClick() {
        if (screenShot.isSelected()) {
            //此时未进入识别模式
            screenShot.setText("ShtC");
            IsRecognizeMode=0;
            screenShot.setSelected(false);

            switch (demoType) {
                case USE_SURFACE_VIEW:
                case USE_TEXTURE_VIEW:
                    Log.d("demoType", "Now Using"  +
                            demoType+"When IsRecognizeMode="+IsRecognizeMode+"\n");
                    mCodecManager.enabledYuvData(false);
                    mCodecManager.setYuvDataCallback(null);
                    // ToDo:
                    break;
                case USE_SURFACE_VIEW_DEMO_DECODER:
                    DJIVideoStreamDecoder.getInstance().changeSurface(videostreamPreviewSh.getSurface());
                    DJIVideoStreamDecoder.getInstance().setYuvDataListener(null);
                    break;
            }
            savePath.setText("");
            savePath.setVisibility(View.INVISIBLE);
            stringBuilder = null;
        } else {
            //此时进入识别模式！
            screenShot.setText("Live");
            TakeOffFlag = true;
            IsRecognizeMode=1;
            screenShot.setSelected(true);
            switch (demoType) {
                case USE_TEXTURE_VIEW:
                case USE_SURFACE_VIEW:
                    Log.d("demoType", "Now Using"  +
                            demoType+"When IsRecognizeMode="+IsRecognizeMode+"\n");
                    mCodecManager.enabledYuvData(true);
                    mCodecManager.setYuvDataCallback(this);
                    break;
                case USE_SURFACE_VIEW_DEMO_DECODER:
                    DJIVideoStreamDecoder.getInstance().changeSurface(null);
                    DJIVideoStreamDecoder.getInstance().setYuvDataListener(MainActivity.this);
                    break;
            }
            Log.d("run6", "6666666666666666666"  + "\n");
            savePath.setText("");
            savePath.setVisibility(View.INVISIBLE);
        }
    }



    private class SendVirtualStickDataTask extends TimerTask {
        public void run(){
            count_TakeOff++;
            FlightControlData fcd = new FlightControlData(0,0,0,0);
            if(EnableFlag&&mFlightController != null)
            {
                mFlightController.setVirtualStickModeEnabled(true, new CommonCallbacks.CompletionCallback(){
                    @Override
                    public void onResult(DJIError djiError) {
                    }
                });
            }
            if(count_TakeOff>200)
            {
                IsTakeOffFlinished = true;
            }
            if(TakeOffFlag&&!IsTakeOffFlinished)
            {
                mFlightController.startTakeoff(
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                if (djiError != null) {
                                    showToast(djiError.getDescription());
                                } else {
                                    showToast("Take off Success");
                                }
                            }
                        }
                );
                TakeOffFlag = false;
            }

            if(IsTakeOffFlinished)
            {
                switch (ccmtc)
                {
                    case Search:
                        if(!IsRecognizeCircle)
                        {
                            count_Search++;
                            if(count_Search<100)
                            {
                                fcd.setPitch((float) (-0.05));
                                mFlightController.sendVirtualStickFlightControlData(fcd,new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {
                                    }
                                });
                            }
                            else
                            {
                                fcd.setPitch((float) (0.05));
                                mFlightController.sendVirtualStickFlightControlData(fcd,new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {
                                    }
                                });
                            }

                        }
                        else
                        {
                            ccmtc = Circumstance.Aim;
                            count_Search = 0;
                        }
                    case Aim:
                        errorx = ((float) xx - 320)/200;
                        errory = -((float) yy - 180)/200;
                        errorz = -((float) zz - 145)/200;
                        if(Math.abs(errorx)<0.10)
                        {
                            errorx = 0;
                        }
                        if(Math.abs(errory)<0.10)
                        {
                            errory = 0;
                        }
                        if(Math.abs(errorz)<0.10)
                        {
                            errorz = 0;
                        }
                        //限制最大速度
                        if (errorx > 0.5) {
                            errorx = (float) 0.5;
                        }
                        if (errorx < -0.5) {
                            errorx = (float) -0.5;
                        }

                        //限制最大速度
                        if (errory > 0.5) {
                            errory = (float) 0.5;
                        }
                        if (errory < -0.5) {
                            errory = (float) -0.5;
                        }

                        //限制最大速度
                        if (errorz > 0.5) {
                            errorz = (float) 0.5;
                        }
                        if (errorz < -0.5) {
                            errorz = (float) -0.5;
                        }
                        if(errorx==0&&errory==0&&errorz==0)
                        {
                            count_Aim++;
                        }
                        Log.d("center", "after:errorx=" + errorx + "\n");
                        Log.d("center", "after:errory=" + errory + "\n");
                        Log.d("center", "after:errorz=" + errorz + "\n");
                        Log.d("center", "发送指令给飞控"+ "\n");
                        fcd.setPitch((float) (1.2*errorx));
                        fcd.setRoll((float) (1.2*errorz));
                        fcd.setVerticalThrottle((float) (1.2*errory));
                        mFlightController.sendVirtualStickFlightControlData(fcd,new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                            }
                        });
                        if(count_Aim>40)
                        {
                            ccmtc = Circumstance.Cross;
                            count_Aim = 0;

                        }

                    case Cross:
                        if(count_Cross<100)
                        {
                            count_Cross++;
                            fcd.setRoll((float) (0.2));
                            mFlightController.sendVirtualStickFlightControlData(fcd,new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                }
                            });
                        }
                        else
                        {
                            CircleHaveBeenCrossed += 1;
                            count_Cross = 0;
                            if(CircleHaveBeenCrossed>6)
                            {
                                ccmtc = Circumstance.Landing;
                            }
                            else
                            {
                                ccmtc = Circumstance.Search;
                            }
                        }
                    case Landing:
                        mFlightController.confirmLanding(new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                            }
                        });
                }
            }
        }
    }
}
