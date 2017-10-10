package com.cgfay.caincamera.filter.camera;

import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.opengl.Matrix;

import com.cgfay.caincamera.filter.base.BaseImageFilter;
import com.cgfay.caincamera.utils.GlUtil;

import com.cgfay.caincamera.utils.TextureRotationUtils;

import java.nio.FloatBuffer;

/**
 * Created by cain on 2017/7/9.
 */

public class CameraFilter extends BaseImageFilter {
    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;                               \n" +
            "uniform mat4 uTexMatrix;                               \n" +
            "attribute vec4 aPosition;                              \n" +
            "attribute vec4 aTextureCoord;                          \n" +
            "varying vec2 textureCoordinate;                            \n" +
            "void main() {                                          \n" +
            "    gl_Position = uMVPMatrix * aPosition;              \n" +
            "    textureCoordinate = (uTexMatrix * aTextureCoord).xy;   \n" +
            "}                                                      \n";

    private static final String FRAGMENT_SHADER_OES =
            "#extension GL_OES_EGL_image_external : require         \n" +
            "precision mediump float;                               \n" +
            "varying vec2 textureCoordinate;                            \n" +
            "uniform samplerExternalOES inputTexture;                   \n" +
            "void main() {                                          \n" +
            "    gl_FragColor = texture2D(inputTexture, textureCoordinate); \n" +
            "}                                                      \n";


    private int muTexMatrixLoc;
    private float[] mTextureMatrix;

    public CameraFilter() {
        this(VERTEX_SHADER, FRAGMENT_SHADER_OES);
    }

    public CameraFilter(String vertexShader, String fragmentShader) {
        super(vertexShader, fragmentShader);
        muTexMatrixLoc = GLES30.glGetUniformLocation(mProgramHandle, "uTexMatrix");
        // 视图矩阵
        Matrix.setLookAtM(mViewMatrix, 0, 0, 0, -1, 0f, 0f, 0f, 0f, 1f, 0f);
    }

    @Override
    public void onInputSizeChanged(int width, int height) {
        super.onInputSizeChanged(width, height);
        float aspect = (float) width / height; // 计算宽高比
        Matrix.perspectiveM(mProjectionMatrix, 0, 60, aspect, 2, 10);
    }

    @Override
    public int getTextureType() {
        return GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
    }

    @Override
    public void drawFrame(int textureId, FloatBuffer vertexBuffer,
                          FloatBuffer textureBuffer){
        GLES30.glUseProgram(mProgramHandle);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        calculateMVPMatrix();
        GLES30.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMVPMatrix, 0);
        GLES30.glUniformMatrix4fv(muTexMatrixLoc, 1, false, mTextureMatrix, 0);
        runPendingOnDrawTasks();
        GLES30.glEnableVertexAttribArray(maPositionLoc);
        GLES30.glVertexAttribPointer(maPositionLoc, mCoordsPerVertex,
                GLES30.GL_FLOAT, false, mVertexStride, vertexBuffer);
        GLES30.glEnableVertexAttribArray(maTextureCoordLoc);
        GLES30.glVertexAttribPointer(maTextureCoordLoc, 2,
                GLES30.GL_FLOAT, false, mTexCoordStride, textureBuffer);
        GLES30.glUniform1i(mInputTextureLoc, 0);
        onDrawArraysBegin();
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, mVertexCount);
        GLES30.glDisableVertexAttribArray(maPositionLoc);
        GLES30.glDisableVertexAttribArray(maTextureCoordLoc);
        onDrawArraysAfter();
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        GLES30.glUseProgram(0);
    }

    public int drawFrameBuffer(int textureId) {
        return drawFrameBuffer(textureId, mVertexArray, mTexCoordArray);
    }

    public int drawFrameBuffer(int textureId, FloatBuffer vertexBuffer, FloatBuffer textureBuffer) {
        if (mFramebuffers == null) {
            return GlUtil.GL_NOT_INIT;
        }
        runPendingOnDrawTasks();
        GLES30.glViewport(0, 0, mFrameWidth, mFrameHeight);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFramebuffers[0]);
        GLES30.glUseProgram(mProgramHandle);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES30.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMVPMatrix, 0);
        GLES30.glUniformMatrix4fv(muTexMatrixLoc, 1, false, mTextureMatrix, 0);
        GLES30.glEnableVertexAttribArray(maPositionLoc);
        GLES30.glVertexAttribPointer(maPositionLoc, mCoordsPerVertex,
                GLES30.GL_FLOAT, false, mVertexStride, vertexBuffer);
        GLES30.glEnableVertexAttribArray(maTextureCoordLoc);
        GLES30.glVertexAttribPointer(maTextureCoordLoc, 2,
                GLES30.GL_FLOAT, false, mTexCoordStride, textureBuffer);
        GLES30.glUniform1i(mInputTextureLoc, 0);
        onDrawArraysBegin();
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, mVertexCount);
        GLES30.glDisableVertexAttribArray(maPositionLoc);
        GLES30.glDisableVertexAttribArray(maTextureCoordLoc);
        onDrawArraysAfter();
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        GLES30.glUseProgram(0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, mDisplayWidth, mDisplayHeight);
        return mFramebufferTextures[0];
    }

    public void updateTextureBuffer() {
        mTexCoordArray = TextureRotationUtils.getTextureBuffer();
    }

    /**
     * 设置SurfaceTexture的变换矩阵
     * @param texMatrix
     */
    public void setTextureTransformMatirx(float[] texMatrix) {
        mTextureMatrix = texMatrix;
    }

    /**
     * 镜像翻转
     * @param coords
     * @param matrix
     * @return
     */
    private float[] transformTextureCoordinates(float[] coords, float[] matrix) {
        float[] result = new float[coords.length];
        float[] vt = new float[4];

        for (int i = 0; i < coords.length; i += 2) {
            float[] v = { coords[i], coords[i + 1], 0, 1 };
            Matrix.multiplyMV(vt, 0, matrix, 0, v, 0);
            result[i] = vt[0];// x轴镜像
            // result[i + 1] = vt[1];y轴镜像
            result[i + 1] = coords[i + 1];
        }
        return result;
    }
}