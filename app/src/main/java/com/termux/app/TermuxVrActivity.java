package com.termux.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;

import com.google.vr.sdk.base.AndroidCompat;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import com.termux.R;
import com.termux.opengl.OpenGLUtils;
import com.termux.opengl.ShaderUtils;
import com.termux.opengl.MeshData;
import com.termux.opengl.Mesh;
import com.termux.terminal.KeyHandler;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalRenderer;

import javax.microedition.khronos.egl.EGLConfig;

public final class TermuxVrActivity extends GvrActivity implements GvrView.StereoRenderer, ServiceConnection {
    private static final String TAG = "TermuxVrActivity";

    private static final int FLOOR_DEPTH = 20;

    private static final int SCREEN_WIDTH = 480;
    private static final int SCREEN_HEIGHT = 320;

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100.0f;

    private static final float CAMERA_Z = 0.01f;

    // We keep the light always position just above the user.
    private static final float[] LIGHT_POS_IN_WORLD_SPACE = new float[] {0.0f, 2.0f, 0.0f, 1.0f};

    private final float[] mLightPosInEyeSpace = new float[4];

    private float[] mCamera;
    private float[] mView;

    private Vibrator mVibrator;

    private Mesh mFloor;
    private Mesh mScreen;

    private Bitmap mBitmap;
    private Canvas mCanvas;

    private TerminalRenderer mTerminalRenderer;
    private TermuxService mTermuxService;
    private TerminalSession mCurrentSession;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initializeGvrView();

        mCamera = new float[16];
        mView = new float[16];

        mTerminalRenderer = new TerminalRenderer(12, Typeface.MONOSPACE);
        mBitmap = Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);

        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mFloor = new Mesh();
        mScreen = new Mesh();

        Intent serviceIntent = new Intent(this, TermuxService.class);
        startService(serviceIntent);
        if (!bindService(serviceIntent, this, 0)) {
            throw new RuntimeException("bindService() failed");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(this);
    }

    public void initializeGvrView() {
        setContentView(R.layout.vr_layout);

        GvrView gvrView = (GvrView) findViewById(R.id.gvr_view);
        gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);

        gvrView.setRenderer(this);
        gvrView.setTransitionViewEnabled(true);

        // Hide all the buttons as they may be accidentally focused and triggered by external keyboard
        gvrView.setSettingsButtonEnabled(false);

        if (gvrView.setAsyncReprojectionEnabled(true)) {
            // Async reprojection decouples the app framerate from the display framerate,
            // allowing immersive interaction even at the throttled clockrates set by
            // sustained performance mode.
            AndroidCompat.setSustainedPerformanceMode(this, true);
        }

        setGvrView(gvrView);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder binder) {
        mTermuxService = ((TermuxService.LocalBinder) binder).service;

        mTermuxService.mSessionChangeCallback = new TerminalSession.SessionChangedCallback() {
            @Override
            public void onTextChanged(TerminalSession changedSession) {
                changedSession.getEmulator().clearScrollCounter();
            }

            @Override
            public void onTitleChanged(TerminalSession updatedSession) {
                // FIXME: We don't care just yet
            }

            @Override
            public void onSessionFinished(final TerminalSession finishedSession) {
                // FIXME: We can't yet handle session switching, so just exit here
                Log.i(TAG, "Some session finished, exiting");
                finish();
            }

            @Override
            public void onClipboardText(TerminalSession session, String text) {
                // Just try not to break something useful
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(new ClipData(null, new String[]{"text/plain"}, new ClipData.Item(text)));
            }

            @Override
            public void onBell(TerminalSession session) {
                // FIXME: Do something
            }

            @Override
            public void onColorsChanged(TerminalSession changedSession) {
                // FIXME: Do something
            }
        };

        if (mTermuxService.getSessions().isEmpty()) {
            TermuxInstaller.setupIfNeeded(TermuxVrActivity.this, new Runnable() {
                @Override
                public void run() {
                    mCurrentSession = mTermuxService.createTermSession(null, null, null, false);
                    mCurrentSession.updateSize(64, 20);
                }
            });
        } else {
            mCurrentSession = mTermuxService.getSessions().get(mTermuxService.getSessions().size() - 1);
        }
    }

    @Override
    @SuppressWarnings("VariableNotUsedInsideIf")
    public void onServiceDisconnected(ComponentName componentName) {
        if (mTermuxService != null) {
            Log.i(TAG, "Service died, exiting");
            finish();
        }
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged");
    }

    @Override
    public void onSurfaceCreated(EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");
        GLES20.glClearColor(1, 1, 1, 1);

        int vertexShader = ShaderUtils.loadGLShader(this, GLES20.GL_VERTEX_SHADER, R.raw.light_vertex);
        int gridShader = ShaderUtils.loadGLShader(this, GLES20.GL_FRAGMENT_SHADER, R.raw.grid_fragment);
        int passthroughShader = ShaderUtils.loadGLShader(this, GLES20.GL_FRAGMENT_SHADER, R.raw.passthrough_fragment);

        mFloor.init(MeshData.FLOOR_COORDS, MeshData.FLOOR_NORMALS, MeshData.FLOOR_COLORS, vertexShader, gridShader);
        Matrix.translateM(mFloor.getModelMatrix(), 0, 0, -FLOOR_DEPTH, 0);

        mScreen.init(MeshData.SCREEN_COORDS, MeshData.SCREEN_NORMALS, MeshData.SCREEN_COLORS, vertexShader, passthroughShader);
        mScreen.setTextureHandle(OpenGLUtils.makeTexture(mBitmap, false));
        mScreen.setTextureCoords(MeshData.SCREEN_TEXTURE_COORDS);
        Matrix.translateM(mScreen.getModelMatrix(), 0, 0, 0, -8);
    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {
        // Build the camera matrix and apply it to the ModelView.
        Matrix.setLookAtM(mCamera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        OpenGLUtils.checkGLError("onReadyToDraw");

        if (mTermuxService != null && mCurrentSession != null && mCurrentSession.getEmulator() != null) {
            mCanvas.drawColor(Color.BLACK);
            mTerminalRenderer.render(mCurrentSession.getEmulator(), mCanvas, 0, -1, -1, -1, -1);
            OpenGLUtils.updateTexture(mScreen.getTextureHandle(), mBitmap);
        }
    }

    @Override
    public void onDrawEye(Eye eye) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        OpenGLUtils.checkGLError("colorParam");

        // Apply the eye transformation to the camera.
        Matrix.multiplyMM(mView, 0, eye.getEyeView(), 0, mCamera, 0);

        // Set the position of the light
        Matrix.multiplyMV(mLightPosInEyeSpace, 0, mView, 0, LIGHT_POS_IN_WORLD_SPACE, 0);

        // Build the ModelView and ModelViewProjection matrices
        // for calculating cube position and light.
        float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);

        mFloor.draw(mLightPosInEyeSpace, mView, perspective);
        mScreen.draw(mLightPosInEyeSpace, mView, perspective);
    }

    @Override
    public void onFinishFrame(Viewport viewport) {}

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mCurrentSession.getEmulator() == null) return true;

        if (event.isSystem() && keyCode != KeyEvent.KEYCODE_BACK) {
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        } else if (event.getAction() == KeyEvent.ACTION_MULTIPLE && keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            mCurrentSession.write(event.getCharacters());
            return true;
        }

        final int metaState = event.getMetaState();
        final boolean controlDownFromEvent = event.isCtrlPressed();
        final boolean leftAltDownFromEvent = (metaState & KeyEvent.META_ALT_LEFT_ON) != 0;
        final boolean rightAltDownFromEvent = (metaState & KeyEvent.META_ALT_RIGHT_ON) != 0;

        int keyMod = 0;
        if (controlDownFromEvent) keyMod |= KeyHandler.KEYMOD_CTRL;
        if (event.isAltPressed()) keyMod |= KeyHandler.KEYMOD_ALT;
        if (event.isShiftPressed()) keyMod |= KeyHandler.KEYMOD_SHIFT;

        // Clear Ctrl since we handle that ourselves:
        int bitsToClear = KeyEvent.META_CTRL_MASK;
        if (rightAltDownFromEvent) {
            // Let right Alt/Alt Gr be used to compose characters.
        } else {
            // Use left alt to send to terminal (e.g. Left Alt+B to jump back a word), so remove:
            bitsToClear |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        }

        int effectiveMetaState = event.getMetaState() & ~bitsToClear;
        int result = event.getUnicodeChar(effectiveMetaState);
        if (result == 0) {
            return true;
        }

        inputCodePoint(result, controlDownFromEvent, leftAltDownFromEvent);
        return true;
    }

    void inputCodePoint(int codePoint, boolean controlDownFromEvent, boolean leftAltDownFromEvent) {

        if (controlDownFromEvent) {
            if (codePoint >= 'a' && codePoint <= 'z') {
                codePoint = codePoint - 'a' + 1;
            } else if (codePoint >= 'A' && codePoint <= 'Z') {
                codePoint = codePoint - 'A' + 1;
            } else if (codePoint == ' ' || codePoint == '2') {
                codePoint = 0;
            } else if (codePoint == '[' || codePoint == '3') {
                codePoint = 27; // ^[ (Esc)
            } else if (codePoint == '\\' || codePoint == '4') {
                codePoint = 28;
            } else if (codePoint == ']' || codePoint == '5') {
                codePoint = 29;
            } else if (codePoint == '^' || codePoint == '6') {
                codePoint = 30; // control-^
            } else if (codePoint == '_' || codePoint == '7' || codePoint == '/') {
                // "Ctrl-/ sends 0x1f which is equivalent of Ctrl-_ since the days of VT102"
                // - http://apple.stackexchange.com/questions/24261/how-do-i-send-c-that-is-control-slash-to-the-terminal
                codePoint = 31;
            } else if (codePoint == '8') {
                codePoint = 127; // DEL
            }
        }

        if (codePoint > -1) {
            // If left alt, send escape before the code point to make e.g. Alt+B and Alt+F work in readline:
            mCurrentSession.writeCodePoint(leftAltDownFromEvent, codePoint);
        }
    }

    @Override
    public void onCardboardTrigger() {
        Log.i(TAG, "onCardboardTrigger");
        // Always give user feedback.
        mVibrator.vibrate(50);
    }
}
