package com.cerist.summer.virtualassistant.Views.Fragments;


import android.Manifest;
import android.app.Fragment;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.cerist.summer.virtualassistant.Models.AutoFitTextureView;
import com.cerist.summer.virtualassistant.Models.Classifier;
import com.cerist.summer.virtualassistant.Models.ImageClassifierSSD;
import com.cerist.summer.virtualassistant.R;
//import com.cerist.summer.virtualassistant.ViewModels.RecognitionViewModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class CameraFragment extends Fragment {

    //private Context context;

    private static final String TAG = "TfLiteCameraFragment";
    private static final String LOG_TAG = "Error";


    private static final int INPUT_SIZE = 300;
    private static final float GOOD_PROB_THRESHOLD = 0.3f;
    private static final float BEST_PROB_THRESHOLD = 0.7f;
    private static final int SMALL_COLOR = 0xffddaa88;

    private Classifier classifier;
    private Classifier.Recognition BestResult;
    public static Classifier.Recognition currentRecognition;

    //Init Recognition View Model
    //private RecognitionViewModel sharedViewModel;


    private Object lockObj = new Object();
    private boolean runClassifier = false;

    private AutoFitTextureView mTextureView;
    private TextView mTextView;
    public static TextView mTextViewState;

    //Background Thread
    private HandlerThread mHandlerThread;
    private Handler mHandler;

    //Camera preview settings

    /** Max preview width that is guaranteed by Camera2 API */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /** Max preview height that is guaranteed by Camera2 API */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private String mCameraid;
    private Size mPreviewSise;

    private CaptureRequest.Builder mCR_Builder;

    // return a new Instance of the Fragment
    public static CameraFragment newInstance() {
        return new CameraFragment();
    }

    public CameraFragment() {  }

    public static void DisplayDeviceState(Context context,String state){
        Log.d("DEVICE_CHECK_2","The "+currentRecognition.getTitle()+" is "+state);
        //Toast.makeText(context, "The "+currentRecognition.getTitle()+" is "+state, Toast.LENGTH_SHORT).show();
    }

    //Orientation Settings
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceRotation) {
        int sensorRotation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceRotation = ORIENTATIONS.get(deviceRotation);
        return (sensorRotation + deviceRotation + 360) % 360;
    }

    //Preview  Size settings
    private static class CompareSizeByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    private static Size optimalSize(Size[] choices, int width, int height) {
        List<Size> requiredSizes = new ArrayList<>();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * height / width &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                requiredSizes.add(option);
            }
        }
        if (requiredSizes.size() > 0)
            return Collections.min(requiredSizes, new CompareSizeByArea());
        else
            return choices[0];
    }

    private static Size selectOptimalSize(
            Size[] choices,
            int textureViewWidth,
            int textureViewHeight,
            int maxWidth,
            int maxHeight,
            Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth
                    && option.getHeight() <= maxHeight
                    && option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizeByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizeByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.camera_fragment, container, false);

        mTextureView = view.findViewById(R.id.autoFitTextureView);
        mTextView = view.findViewById(R.id.tv_result);
        mTextViewState = view.findViewById(R.id.tv_state);

        //sharedViewModel =  ViewModelProviders.of((FragmentActivity) getActivity()).get(RecognitionViewModel.class);

        Log.d(TAG,"onCreateView");
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        try {
            // create either a Quantized Image Classifier or a Float Image Classifier
            classifier = ImageClassifierSSD.create(getActivity().getAssets());
            //croppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);

        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize an image classifier.", e);
        }

        //classifier.setNumThreads(1);
        //classifier.setUseNNAPI(false);

        setupBackgroundThread();
    }

    @Override
    public void onResume() {
        super.onResume();
        setupBackgroundThread();
        if (mTextureView.isAvailable()) {
            setupCamera(mTextureView.getWidth(), mTextureView.getHeight());
            transformImg(mTextureView.getWidth(),mTextureView.getHeight());
            connectCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        classifier.close();
        super.onDestroyView();
    }

    // Transform the image in rotation mode
    private void transformImg(int width, int height){
        if(mPreviewSise == null ||mTextureView == null)
            return;

        Matrix matrix = new Matrix();
        int rotatation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        RectF textureRectF = new RectF(0,0,width,height);
        RectF previewRectF = new RectF(0,0,mPreviewSise.getHeight(),mPreviewSise.getWidth());
        float centerX =  textureRectF.centerX();
        float centerY =  textureRectF.centerY();

        if(rotatation == Surface.ROTATION_90 || rotatation == Surface.ROTATION_270 ){
            previewRectF.offset(centerX - previewRectF.centerX(), centerY - previewRectF.centerY());
            matrix.setRectToRect(textureRectF,previewRectF, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float)width /mPreviewSise.getWidth(),(float)height / mPreviewSise.getHeight());
            matrix.setScale(scale,scale,centerX,centerY);
            matrix.setRotate(90 * (rotatation - 2), centerX, centerY);
        }

        mTextureView.setTransform(matrix );

    }

    private void setupBackgroundThread() {
        mHandlerThread = new HandlerThread("Camera");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        synchronized (lockObj) {
            runClassifier = true;
        }
        mHandler.post(periodicClassify);
    }

    private void stopBackgroundThread() {
        mHandlerThread.quitSafely();
        try {
            mHandlerThread.join();
            mHandlerThread = null;
            mHandler = null;
            synchronized (lockObj) {
                runClassifier = false;
            }

        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted when stopping background thread", e);
            e.printStackTrace();
        }
    }

    //Cameara Settings
    private void setupCamera(int width, int height) {
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraid : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraid);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // // For still image captures, we use the largest available size.
                Size largest =
                        Collections.max(
                                Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizeByArea());

                int deviceRotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
                //int totalRotation = sensorToDeviceRotation(characteristics, deviceRotation);

                /* Orientation of the camera sensor */
                int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);


                boolean swapRotation = false;
                switch (deviceRotation){
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if(sensorOrientation == 90 || sensorOrientation == 270){
                            swapRotation = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if(sensorOrientation == 0 || sensorOrientation == 180){
                            swapRotation = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + deviceRotation);

                }


                Point displaySize = new Point();
                getActivity().getWindowManager().getDefaultDisplay().getSize(displaySize);
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;
                int rotatedWidth = width;
                int rotatedHeight = height;

                if (swapRotation) {
                    rotatedWidth = height;
                    rotatedHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                mPreviewSise = selectOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedWidth,
                        rotatedHeight,
                        maxPreviewWidth,
                        maxPreviewHeight,
                        largest);
                //mPreviewSise = optimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);

                Log.d("PREVIEW",""+mPreviewSise.toString());
                Log.d("PREVIEW_TEXTURE",""+mTextureView.getBitmap().getHeight()+"x"+mTextureView.getBitmap().getWidth());

                // Fix the textureView
                // Fit the aspect ratio of TextureView to the size of preview picked.
                /*int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(mPreviewSise.getWidth(), mPreviewSise.getHeight());
                } else {
                    mTextureView.setAspectRatio(mPreviewSise.getHeight(), mPreviewSise.getWidth());
                }*/
                mCameraid = cameraid;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //connecting Camera
    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private void connectCamera() {
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                if(ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
                    manager.openCamera(mCameraid, mStateCallback, mHandler);
                }else {
                    if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
                        Toast.makeText(getActivity(), "App required access to Camera", Toast.LENGTH_SHORT).show();
                    requestPermissions(new String[]{ Manifest.permission.CAMERA},REQUEST_CAMERA_PERMISSION_RESULT);
                }

            }else {
                manager.openCamera(mCameraid, mStateCallback, mHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //Start Recording
    private void startPreview(){
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSise.getWidth(),mPreviewSise.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            mCR_Builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCR_Builder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mCR_Builder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
                            try {
                                session.setRepeatingRequest(mCR_Builder.build(),null,mHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }

                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(getActivity(), "Unable to setup  camera preview", Toast.LENGTH_SHORT).show();
                        }
                    },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void closeCamera(){
        if(mCameraDevice != null){
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    //CallBacks ...
    private CameraDevice mCameraDevice;
    private CameraDevice.StateCallback  mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened. Starting camera preview here.

            mCameraDevice = cameraDevice;
            startPreview();

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;

        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            mCameraDevice = null;
        }
    };

    private TextureView.SurfaceTextureListener mSurfaceListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int heitgh) {
            //Toast.makeText(getActivity(), "suraceTexture avable", Toast.LENGTH_SHORT).show();
            setupCamera(width,heitgh);
            transformImg(width,heitgh);
            connectCamera();

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    /*
     *  Classifies a frame from the preview stream.
     *  */
    private void classifyFrame(){
        if(classifier == null || getActivity() == null || mCameraDevice ==null){
            showToast("Uninitialized Classifier or invalid context.");
            return;
        }

        SpannableStringBuilder textToShow = new SpannableStringBuilder();

        Bitmap bitmap = mTextureView.getBitmap(INPUT_SIZE,INPUT_SIZE);
        final List<Classifier.Recognition> results = classifier.recognizeImage(bitmap);

        //Collections.sort(results); // No need, already sorted in ImageClassifierSSD class
        if (!results.isEmpty()) {
            Log.d("TAG_RESULT",""+results.size());
            Log.d("TAG_RESULT_1",""+results.get(0).toString());
            Log.d("TAG_RESULT_1_DETAILS"," width: height "+results.get(0).getLocation().width()+":"+results.get(0).getLocation().height());

            BestResult = results.get(0);
            SpannableString span ;
            if (BestResult.getConfidence() >= BEST_PROB_THRESHOLD) {
                span = new SpannableString(String.format("%s: %4.2f\n", BestResult.getTitle(), BestResult.getConfidence()));

                currentRecognition = BestResult;
                //sharedViewModel.setRecognition(BestResult);
            }
            else{
                span = new SpannableString(String.format("Unkonwn Object"));
                currentRecognition = new Classifier.Recognition(BestResult.getId(),"Unkonwn Object",BestResult.getConfidence(),BestResult.getLocation());
            }


            int color;
            // Make it white when probability larger than threshold.
            if (BestResult.getConfidence() > GOOD_PROB_THRESHOLD) {
                color = android.graphics.Color.WHITE;
            } else {
                color = SMALL_COLOR;
            }
            span.setSpan(new ForegroundColorSpan(color), 0, span.length(), 0);
            textToShow.insert(0, span);

        }
        else {
            textToShow.append(new SpannableString("No results From Classifier."));
        }

        //classifier.classifyFrame(bitmap,textToShow);
        bitmap.recycle();
        showToast(textToShow);
    }


    /** Takes & classify the pics periodically. */
    private Runnable periodicClassify =
            new Runnable() {
                @Override
                public void run() {
                    synchronized (lockObj) {
                        if (runClassifier) {
                            classifyFrame();
                        }
                    }
                    mHandler.post(periodicClassify);
                }
            };

    /**
     * Shows a {@link Toast} on the UI thread for the classification results.
     *
     * @param s The message to show
     */
    private void showToast(String s) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        SpannableString str1 = new SpannableString(s);
        builder.append(str1);
        showToast(builder);
    }

    private void showToast(SpannableStringBuilder builder) {
        final SpannableStringBuilder mBuilder =builder;
        getActivity().runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        mTextView.setText(mBuilder, TextView.BufferType.SPANNABLE);
                    }
                });
    }


}
