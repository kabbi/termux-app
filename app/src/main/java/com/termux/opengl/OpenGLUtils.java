package com.termux.opengl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

/**
 * @author kabbi
 */
public class OpenGLUtils {
    private static final String TAG = OpenGLUtils.class.getSimpleName();

    public static void checkGLError(String label) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, label + ": glError " + error);
            throw new RuntimeException(label + ": glError " + error);
        }
    }

    public static int makeTexture(Bitmap bitmap, boolean recycle) {
        final int[] textureHandle = new int[1];

        GLES20.glGenTextures(1, textureHandle, 0);

        if (textureHandle[0] == 0) {
            throw new RuntimeException("Error loading texture.");
        }

        // Bind to the texture in OpenGL
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);

        // Set filtering
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

        // Load the bitmap into the bound texture.
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

        // Recycle the bitmap, since its data has been loaded into OpenGL.
        if (recycle) {
            bitmap.recycle();
        }

        return textureHandle[0];
    }

    public static int loadTexture(Context context, int resourceId) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false; // No pre-scaling
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId, options);
        return makeTexture(bitmap, true);
    }

    public static void updateTexture(int handle, Bitmap bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, handle);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
    }
}
