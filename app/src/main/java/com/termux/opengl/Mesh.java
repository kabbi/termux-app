package com.termux.opengl;

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * @author kabbi
 */
public class Mesh {
    private static final int COORDS_PER_VERTEX = 3;
    private static final int TEXTURE_COORDS_PER_VERTEX = 2;
    private static final int COORDS_PER_COLOR = 4;

    private FloatBuffer mVertices;
    private FloatBuffer mColors;
    private FloatBuffer mNormals;
    private FloatBuffer mTextureCoords;

    private int mProgramHandle;

    private int mPositionAttribHandle;
    private int mNormalAttribHandle;
    private int mColorsAttribHandle;
    private int mTextureCoordsAttribHandle = -1;

    private int mModelUniformHandle;
    private int mModelViewUniformHandle;
    private int mModelViewProjectionUniformHandle;
    private int mLightPositionUniformHandle;
    private int mTextureUniformHandle;

    private float[] mModelMatrix = new float[16];
    private float[] mModelViewMatrix = new float[16];
    private float[] mModelViewProjectionMatrix = new float[16];

    private int mTextureHandle = -1;

    public void init(float[] coords, float[] normals, float[] colors, int vertexShader, int fragmentShader) {
        ByteBuffer bbFloorVertices = ByteBuffer.allocateDirect(coords.length * 4);
        bbFloorVertices.order(ByteOrder.nativeOrder());
        mVertices = bbFloorVertices.asFloatBuffer();
        mVertices.put(coords);
        mVertices.position(0);

        ByteBuffer bbFloorNormals = ByteBuffer.allocateDirect(normals.length * 4);
        bbFloorNormals.order(ByteOrder.nativeOrder());
        mNormals = bbFloorNormals.asFloatBuffer();
        mNormals.put(normals);
        mNormals.position(0);

        ByteBuffer bbFloorColors = ByteBuffer.allocateDirect(colors.length * 4);
        bbFloorColors.order(ByteOrder.nativeOrder());
        mColors = bbFloorColors.asFloatBuffer();
        mColors.put(colors);
        mColors.position(0);

        mProgramHandle = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgramHandle, vertexShader);
        GLES20.glAttachShader(mProgramHandle, fragmentShader);
        GLES20.glLinkProgram(mProgramHandle);
        GLES20.glUseProgram(mProgramHandle);

        OpenGLUtils.checkGLError("Mesh program");

        mModelUniformHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_Model");
        mModelViewUniformHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_MVMatrix");
        mModelViewProjectionUniformHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_MVP");
        mLightPositionUniformHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_LightPos");
        mTextureUniformHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_Texture");

        mPositionAttribHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Position");
        mNormalAttribHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Normal");
        mColorsAttribHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Color");

        OpenGLUtils.checkGLError("Mesh program params");

        Matrix.setIdentityM(mModelMatrix, 0);

        OpenGLUtils.checkGLError("Mesh init");
    }

    public void draw(float[] lightPosInEyeSpace, float[] view, float[] perspective) {
        Matrix.multiplyMM(mModelViewMatrix, 0, view, 0, mModelMatrix, 0);
        Matrix.multiplyMM(mModelViewProjectionMatrix, 0, perspective, 0, mModelViewMatrix, 0);

        GLES20.glUseProgram(mProgramHandle);

        GLES20.glUniform3fv(mLightPositionUniformHandle, 1, lightPosInEyeSpace, 0);
        GLES20.glUniformMatrix4fv(mModelUniformHandle, 1, false, mModelMatrix, 0);
        GLES20.glUniformMatrix4fv(mModelViewUniformHandle, 1, false, mModelViewMatrix, 0);
        GLES20.glUniformMatrix4fv(mModelViewProjectionUniformHandle, 1, false, mModelViewProjectionMatrix, 0);

        if (mTextureHandle != -1) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureHandle);
            GLES20.glUniform1i(mTextureUniformHandle, 0);
        }

        GLES20.glVertexAttribPointer(mPositionAttribHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, mVertices);
        GLES20.glVertexAttribPointer(mNormalAttribHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, mNormals);
        GLES20.glVertexAttribPointer(mColorsAttribHandle, COORDS_PER_COLOR, GLES20.GL_FLOAT, false, 0, mColors);

        GLES20.glEnableVertexAttribArray(mPositionAttribHandle);
        GLES20.glEnableVertexAttribArray(mNormalAttribHandle);
        GLES20.glEnableVertexAttribArray(mColorsAttribHandle);

        if (mTextureCoordsAttribHandle != -1) {
            GLES20.glVertexAttribPointer(mTextureCoordsAttribHandle, TEXTURE_COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, mTextureCoords);
            GLES20.glEnableVertexAttribArray(mTextureCoordsAttribHandle);
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mVertices.capacity() / COORDS_PER_VERTEX);

        OpenGLUtils.checkGLError("Mesh rendering");
    }

    public float[] getModelMatrix() {
        return this.mModelMatrix;
    }

    public int getTextureHandle() {
        return mTextureHandle;
    }

    public void setTextureHandle(int textureHandle) {
        mTextureHandle = textureHandle;
    }

    public void setTextureCoords(float[] textureCoords) {
        ByteBuffer bbFloorNormals = ByteBuffer.allocateDirect(textureCoords.length * 4);
        bbFloorNormals.order(ByteOrder.nativeOrder());
        mTextureCoords = bbFloorNormals.asFloatBuffer();
        mTextureCoords.put(textureCoords);
        mTextureCoords.position(0);

        mTextureCoordsAttribHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_TextureCoords");
    }
}
