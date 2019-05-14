package com.iflytek.facedemo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Process;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.Toast;

import com.iflytek.cloud.FaceDetector;
import com.iflytek.cloud.FaceRequest;
import com.iflytek.cloud.RequestListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.util.Accelerometer;
import com.iflytek.facedemo.util.FaceRect;
import com.iflytek.facedemo.util.FaceUtil;
import com.iflytek.facedemo.util.ParseResult;

/**
 * ������Ƶ�����ʾ��
 * ��ҵ���֧�������������SDK���뿪����ǰ��<a href="http://www.xfyun.cn/">Ѷ��������</a>SDK���ؽ��棬���ض�Ӧ����SDK
 */
public class VideoDemo extends Activity {
	private final static String TAG = VideoDemo.class.getSimpleName();
	private SurfaceView mPreviewSurface;
	private SurfaceView mFaceSurface;
	private Camera mCamera;
	private int mCameraId = CameraInfo.CAMERA_FACING_FRONT;
	// Camera nv21��ʽԤ��֡�ĳߴ磬Ĭ������640*480
	private int PREVIEW_WIDTH = 640;
	private int PREVIEW_HEIGHT = 480;
	// Ԥ��֡���ݴ洢����ͻ�������
	private byte[] nv21;
	private byte[] buffer;
	// ���ž���
	private Matrix mScaleMatrix = new Matrix();
	// ���ٶȸ�Ӧ�������ڻ�ȡ�ֻ��ĳ���
	private Accelerometer mAcc;
	// FaceDetector���󣬼�������������ʶ��������⡢��Ƶ����⹦��
	private FaceDetector mFaceDetector;
	private boolean mStopTrack;
	private Toast mToast;
	private long mLastClickTime;
	private int isAlign = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_video_demo);

		initUI();
		mFaceRequest = new FaceRequest(this);
		nv21 = new byte[PREVIEW_WIDTH * PREVIEW_HEIGHT * 2];
		buffer = new byte[PREVIEW_WIDTH * PREVIEW_HEIGHT * 2];
		mAcc = new Accelerometer(VideoDemo.this);
		mFaceDetector = FaceDetector.createDetector(VideoDemo.this, null);

	}


	private Callback mPreviewCallback = new Callback() {

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			closeCamera();
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			openCamera();
		}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
								   int height) {
			mScaleMatrix.setScale(width / (float) PREVIEW_HEIGHT, height / (float) PREVIEW_WIDTH);
		}
	};

	private void setSurfaceSize() {
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);

		int width = metrics.widthPixels;
		int height = (int) (width * PREVIEW_WIDTH / (float) PREVIEW_HEIGHT);
		RelativeLayout.LayoutParams params = new LayoutParams(width, height);
		params.addRule(RelativeLayout.ALIGN_PARENT_TOP);

		mPreviewSurface.setLayoutParams(params);
		mFaceSurface.setLayoutParams(params);
	}

	@SuppressLint("ShowToast")
	@SuppressWarnings("deprecation")
	private void initUI() {
		mPreviewSurface = (SurfaceView) findViewById(R.id.sfv_preview);
		mFaceSurface = (SurfaceView) findViewById(R.id.sfv_face);

		mPreviewSurface.getHolder().addCallback(mPreviewCallback);
		mPreviewSurface.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		mFaceSurface.setZOrderOnTop(true);
		mFaceSurface.getHolder().setFormat(PixelFormat.TRANSLUCENT);

		// ���SurfaceView���л�����ͷ
		mFaceSurface.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// ֻ��һ������ͷ����֧���л�
				if (Camera.getNumberOfCameras() == 1) {
					showTip("ֻ�к�������ͷ�������л�");
					return;
				}
				closeCamera();
				if (CameraInfo.CAMERA_FACING_FRONT == mCameraId) {
					mCameraId = CameraInfo.CAMERA_FACING_BACK;
				} else {
					mCameraId = CameraInfo.CAMERA_FACING_FRONT;
				}
				openCamera();
			}
		});

		// ����SurfaceView 500ms���ɿ�������ͷ�ۼ�
		mFaceSurface.setOnTouchListener(new OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						mLastClickTime = System.currentTimeMillis();
						break;
					case MotionEvent.ACTION_UP:
						if (System.currentTimeMillis() - mLastClickTime > 500) {
							mCamera.autoFocus(null);
							return true;
						}
						break;

					default:
						break;
				}
				return false;
			}
		});

		RadioGroup alignGruop = (RadioGroup) findViewById(R.id.align_mode);
		alignGruop.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(RadioGroup arg0, int arg1) {
				switch (arg1) {
					case R.id.detect:
						isAlign = 0;
						break;
					case R.id.align:
						isAlign = 1;
						break;
					default:
						break;
				}
			}
		});

		setSurfaceSize();
		mToast = Toast.makeText(VideoDemo.this, "", Toast.LENGTH_SHORT);
	}

	private void openCamera() {
		if (null != mCamera) {
			return;
		}

		if (!checkCameraPermission()) {
			showTip("����ͷȨ��δ�򿪣���򿪺�����");
			mStopTrack = true;
			return;
		}

		// ֻ��һ������ͷ���򿪺���
		if (Camera.getNumberOfCameras() == 1) {
			mCameraId = CameraInfo.CAMERA_FACING_BACK;
		}

		try {
			mCamera = Camera.open(mCameraId);
			if (CameraInfo.CAMERA_FACING_FRONT == mCameraId) {
				showTip("ǰ���ѿ���������л�");
			} else {
				showTip("�����ѿ���������л�");
			}
		} catch (Exception e) {
			e.printStackTrace();
			closeCamera();
			return;
		}

		Parameters params = mCamera.getParameters();
		params.setPreviewFormat(ImageFormat.NV21);
		params.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
		mCamera.setParameters(params);

		// ������ʾ��ƫת�Ƕȣ��󲿷ֻ�����˳ʱ��90�ȣ�ĳЩ������Ҫ���������
		mCamera.setDisplayOrientation(90);
		mCamera.setPreviewCallback(new PreviewCallback() {

			@Override
			public void onPreviewFrame(byte[] data, Camera camera) {
				System.arraycopy(data, 0, nv21, 0, data.length);
			}
		});

		try {
			mCamera.setPreviewDisplay(mPreviewSurface.getHolder());
			mCamera.startPreview();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void closeCamera() {
		if (null != mCamera) {
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}

	private boolean checkCameraPermission() {
		int status = checkPermission(permission.CAMERA, Process.myPid(), Process.myUid());
		if (PackageManager.PERMISSION_GRANTED == status) {
			return true;
		}

		return false;
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (null != mAcc) {
			mAcc.start();
		}

		mStopTrack = false;
		new Thread(new Runnable() {

			@Override
			public void run() {
				while (!mStopTrack) {
					if (null == nv21) {
						continue;
					}

					synchronized (nv21) {
						System.arraycopy(nv21, 0, buffer, 0, nv21.length);
					}

					// ��ȡ�ֻ����򣬷���ֵ0,1,2,3�ֱ��ʾ0,90,180��270��
					int direction = Accelerometer.getDirection();
					boolean frontCamera = (Camera.CameraInfo.CAMERA_FACING_FRONT == mCameraId);
					// ǰ������ͷԤ����ʾ���Ǿ�����Ҫ���ֻ������������ͷ�ӽ��µĳ���
					// ת����ʽ��a' = (360 - a)%360��aΪ�����ӽ��µĳ��򣨵�λ���Ƕȣ�
					if (frontCamera) {
						// SDK��ʹ��0,1,2,3,4�ֱ��ʾ0,90,180,270��360��
						direction = (4 - direction) % 4;
					}

					if (mFaceDetector == null) {
						/**
						 * ������Ƶ����⹦����Ҫ��������֧������������SDK
						 * �뿪����ǰ�������ƹ������ض�ӦSDK
						 */
						showTip("��SDK��֧��������Ƶ�����");
						break;
					}

					String result = mFaceDetector.trackNV21(buffer, PREVIEW_WIDTH, PREVIEW_HEIGHT, isAlign, direction);
					Log.d(TAG, "result:" + result);

					FaceRect[] faces = ParseResult.parseResult(result);

					Canvas canvas = mFaceSurface.getHolder().lockCanvas();
					if (null == canvas) {
						continue;
					}

					canvas.drawColor(0, PorterDuff.Mode.CLEAR);
					canvas.setMatrix(mScaleMatrix);

					if (faces.length <= 0) {
						mFaceSurface.getHolder().unlockCanvasAndPost(canvas);
						continue;
					}

					if (null != faces && frontCamera == (Camera.CameraInfo.CAMERA_FACING_FRONT == mCameraId)) {
						for (FaceRect face : faces) {
							face.bound = FaceUtil.RotateDeg90(face.bound, PREVIEW_WIDTH, PREVIEW_HEIGHT);
							if (face.point != null) {
								for (int i = 0; i < face.point.length; i++) {
									face.point[i] = FaceUtil.RotateDeg90(face.point[i], PREVIEW_WIDTH, PREVIEW_HEIGHT);
								}
							}
							FaceUtil.drawFaceRect(canvas, face, PREVIEW_WIDTH, PREVIEW_HEIGHT,
									frontCamera, false);
						}

						if (!isVerified && faces.length == 1) {
							isVerified = true;
							// ��������ʱ������
							byte[] tmp = new byte[nv21.length];
							System.arraycopy(nv21, 0, tmp, 0, nv21.length);

							verify(Bitmap2Bytes(RotateDeg90(decodeToBitMap(tmp))));
						}


					} else {
						Log.d(TAG, "faces:0");
					}

					mFaceSurface.getHolder().unlockCanvasAndPost(canvas);
				}
			}
		}).start();
	}

	// FaceRequest���󣬼���������ʶ��ĸ��ֹ���
	private FaceRequest mFaceRequest;
	private boolean isVerified = false;

	private void verify(byte[] mImageData) {
		if (null != mImageData) {
			// �����û���ʶ����ʽΪ6-18���ַ�������ĸ�����֡��»�����ɣ����������ֿ�ͷ�����ܰ����ո񣩡�
			// ��������ʱ���ƶ˽�ʹ���û��豸���豸ID����ʶ�ն��û���
			mFaceRequest.setParameter(SpeechConstant.AUTH_ID, "qqqqqq");
			mFaceRequest.setParameter(SpeechConstant.WFR_SST, "verify");
			mFaceRequest.sendRequest(mImageData, new RequestListener() {

				@Override
				public void onEvent(int arg0, Bundle arg1) {
					// TODO Auto-generated method stub

				}

				@Override
				public void onCompleted(SpeechError arg0) {
					if (arg0 != null)
						Log.e(TAG, "error:" + arg0.getErrorCode());
				}

				@Override
				public void onBufferReceived(byte[] arg0) {
					try {
						String result = new String(arg0, "utf-8");
						Log.d("FaceDemo", "test");
						Log.d("FaceDemo", result);

						JSONObject object = new JSONObject(result);
						int ret = object.getInt("ret");
						if (ret != 0) {
							showTip("��֤ʧ��");
							return;
						}
						if ("success".equals(object.get("rst"))) {
							if (object.getBoolean("verf")) {
								showTip("ͨ����֤����ӭ������");
							} else {
								showTip("��֤��ͨ��");
							}
						} else {
							showTip("��֤ʧ��");
						}
					} catch (UnsupportedEncodingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (JSONException e) {
						// TODO: handle exception
					}
				}
			});
		} else {
			showTip("��ѡ��ͼƬ������֤");
		}
	}

	private Bitmap decodeToBitMap(byte[] data) {
		try {
			YuvImage image = new YuvImage(data, ImageFormat.NV21, PREVIEW_WIDTH,
					PREVIEW_HEIGHT, null);
			if (image != null) {
				ByteArrayOutputStream stream = new ByteArrayOutputStream();
				image.compressToJpeg(new Rect(0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT),
						80, stream);
				Bitmap bmp = BitmapFactory.decodeByteArray(
						stream.toByteArray(), 0, stream.size());
				stream.close();
				return bmp;
			}
		} catch (Exception ex) {
			Log.e("Sys", "Error:" + ex.getMessage());
		}
		return null;
	}

	private Bitmap RotateDeg90(Bitmap bmp) {
		// ����������  
		Matrix matrix = new Matrix();
		// ����ԭͼ
		matrix.postScale(1f, 1f);
		// ������ת45�ȣ�����Ϊ����������ת
		matrix.postRotate(-90);
		//bmp.getWidth(), 500�ֱ��ʾ�ػ���λͼ���
		Bitmap dstbmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(),
				matrix, true);
		return dstbmp;
	}


	private byte[] Bitmap2Bytes(Bitmap bm) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		bm.compress(Bitmap.CompressFormat.PNG, 100, baos);
		return baos.toByteArray();
	}

	@Override
	protected void onPause() {
		super.onPause();
		closeCamera();
		if (null != mAcc) {
			mAcc.stop();
		}
		mStopTrack = true;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		// ���ٶ���
		mFaceDetector.destroy();
	}

	private void showTip(final String str) {
		mToast.setText(str);
		mToast.show();
	}
}
