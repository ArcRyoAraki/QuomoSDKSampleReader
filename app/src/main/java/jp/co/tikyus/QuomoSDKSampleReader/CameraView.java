package jp.co.tikyus.QuomoSDKSampleReader;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import jp.co.tikyus.quomo.sdk;
import java.io.ByteArrayOutputStream;

import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;


@SuppressWarnings("deprecation")
public class CameraView extends SurfaceView implements Camera.PreviewCallback, SurfaceHolder.Callback {
    public sdk _quomosdk;
    private Camera _camera;
    private Thread _decodeThread[];
    private OnListener _onListener;
    private int[] _rgb;
    private Bitmap _workBitmap;


    public interface OnListener {
        public void onDecode(CameraView cameraView, long id);
    }

    private class DecodeTask implements Runnable {
        private final int[] _rgb24;
        private final int _height;
        private final int _width;
        private final int _threadIndex;

        public DecodeTask(int[] rgb, int width, int height,int threadIndex) {
            _height = height;
            _width = width;
            _rgb24 = new int[_width * _height];
            _threadIndex = threadIndex;
            System.arraycopy(rgb, 0, _rgb24, 0, _rgb24.length);
        }

        @Override
        public void run() {
            if (_decodeThread[_threadIndex] == null) {
                return;
            }

            int height = _height;
            int width = _width;

            byte[] rgb = new byte[width * height * 3];

            int j = 0;
            for(int k=0;k<height;k++){
                for(int i = 0;i < width*3; i += 3, j++){
                    rgb[i+0+width*3*k] = (byte) ((_rgb24[j] >> 16) & 0xff);
                    rgb[i+1+width*3*k] = (byte) ((_rgb24[j] >>  8) & 0xff);
                    rgb[i+2+width*3*k] = (byte) ( _rgb24[j]        & 0xff);
                }
            }
            //idが読み込めない場合0が返ってきます。
            final long id = _quomosdk.QuomoCzDecoder(width, height, rgb);
            //
            if (id != 0) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        onDecode(id);
                    }
                });
            }

            _decodeThread[_threadIndex] = null;
        }
    }

    public CameraView(Context context) {
        super(context);
        init();
    }

    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_NORMAL);

        //
        _decodeThread = new Thread[1];
        for(int i=0;i<_decodeThread.length;i++){
            _decodeThread[i]=null;
        }
    }

    private void onDecode(long id) {
        if (_onListener != null) {
            _onListener.onDecode(this, id);
        }
    }

    public void setOnListener(OnListener onListener) {
        _onListener = onListener;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        long nowTime = System.currentTimeMillis();
        int threadIndex = -1;

        for(int i=0;i<_decodeThread.length;i++){
            if(_decodeThread[i]==null){
                threadIndex=i;
                break;
            }
        }
        if(threadIndex == -1)return;
        //

        Camera.Parameters params = camera.getParameters();
        Size cameraSize = params.getPictureSize();
        int height = cameraSize.height;
        int width = cameraSize.width;
        _workBitmap = null;
        _workBitmap = getBitmapImageFromYUV(data,width,height);
        _workBitmap.getPixels(_rgb, 0, width, 0, 0, width, height);

        int targetWidth = width /2;
        int targetHeight = height;
        int[] rgb = new int[targetWidth * targetHeight];
        for (int index = 0; index < targetHeight; index++) {
            System.arraycopy(_rgb, width * index, rgb, targetWidth * index, targetWidth);
        }
        _decodeThread[threadIndex] = new Thread(new DecodeTask(rgb, targetWidth, targetHeight,threadIndex));
        _decodeThread[threadIndex].start();
    }

    // SurfaceHolder.Callback

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        _camera = Camera.open();
        Camera.Parameters params = _camera.getParameters();
        List< Size > sizeList = params.getSupportedPictureSizes();

        params.setPictureSize(320, 240);
        _camera.setParameters(params);
        try {
            _camera.setPreviewDisplay(holder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        _camera.stopPreview();

        Camera.Parameters parameters = _camera.getParameters();
        Size size = parameters.getPictureSize();

        _rgb = new int[size.height * size.width];

        parameters.setPreviewSize(size.width, size.height);
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        _camera.setParameters(parameters);
        _camera.setPreviewCallback(this);
        _camera.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        _camera.stopPreview();
        _camera.setPreviewCallback(null);
        _camera.release();
        _camera = null;
    }

    public static Bitmap getBitmapImageFromYUV(byte[] data, int width, int height) {
        YuvImage yuvimage = new YuvImage(data, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        yuvimage.compressToJpeg(new Rect(0, 0, width, height), 80, baos);
        byte[] jdata = baos.toByteArray();
        BitmapFactory.Options bitmapFatoryOptions = new BitmapFactory.Options();
        bitmapFatoryOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bmp = BitmapFactory.decodeByteArray(jdata, 0, jdata.length, bitmapFatoryOptions);
        return bmp;
    }
}

