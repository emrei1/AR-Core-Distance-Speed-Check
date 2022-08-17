package com.example.arcoredistancecheck9;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.arcoredistancecheck9.helpers.DisplayRotationHelper;
import com.example.arcoredistancecheck9.samplerender.SampleRender;
import com.example.arcoredistancecheck9.samplerender.arcore.BackgroundRenderer;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity implements SampleRender.Renderer  {

    private GLSurfaceView surfaceView;

    private Session session;
    private SampleRender render;
    private BackgroundRenderer backgroundRenderer;
    private DisplayRotationHelper displayRotationHelper;

    private TextView distanceBar;

    private boolean hasSetTextureNames = false;

    private Frame globalFrame;

    private ArrayList<String> fileNames = new ArrayList<>();

    private boolean standCoordinatesReady = false;
    private boolean frameReady = false;

    private double[] initialStandCoordinates = new double[3];
    private double[] initialCameraCoordinates = new double[3];

    private ArrayDeque<Image> imageQueue = new ArrayDeque<>();

    private AtomicBoolean saveImage = new AtomicBoolean(false);
    private AtomicBoolean saveImageSecond = new AtomicBoolean(false);

    private int savedImageCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surfaceview);

        render = new SampleRender(surfaceView, this, getAssets());

        displayRotationHelper = new DisplayRotationHelper(this);

        distanceBar = findViewById(R.id.distanceBar);

        Button showPhotos = findViewById(R.id.showPhotos);
        showPhotos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.e("tag", "clicked open activity 2");
                openActivity2();
            }
        });

        TextView speedView = findViewById(R.id.speed);

        new Thread("speed check thread") {
            public void run() {
                try {
                    executeSpeedCheck(speedView);
                } catch (Exception e) {
                    Log.e("tag", "catched exception " + e.toString());
                    e.printStackTrace();
                }
            }
        }.start();

        new Thread("distance check thread") {
            public void run() {
                try {
                    executeDistanceCheck();
                } catch (Exception e) {
                    Log.e("tag", "catched exception " + e.toString());
                    e.printStackTrace();
                }
            }
        }.start();

        new Thread("image save thread") {
            public void run() {
                try {
                    executeImageSave();
                } catch (Exception e) {
                    Log.e("tag", "catched exception " + e.toString());
                    e.printStackTrace();
                }
            }
        }.start();

        new Thread("frame check thread") {
            public void run() {
                try {
                    executeFrameCheck();
                } catch (Exception e) {
                    Log.e("tag", "catched exception " + e.toString());
                    e.printStackTrace();
                }
            }
        }.start();

    }

    private void openActivity2() {
        Log.e("tag", "activity 2 opened");
        Intent intent = new Intent(this, Activity2.class);
        intent.putExtra("filenames", fileNames);
        startActivity(intent);
    }

    private void executeFrameCheck() {
        double firstTime = System.currentTimeMillis();
        double time = System.currentTimeMillis();
        while (time - firstTime < 10000) {
            time = System.currentTimeMillis();
        }
        frameReady = true;
    }

    private void executeImageSave() {
        while (true) {

            if (saveImage.get() == false) {
                saveImage.set(true);

                while (saveImageSecond.get() == false) {}

                Image image = imageQueue.pop();
                YuvImage yuvImage = toYuvImage(image);
                image.close();
                if (savedImageCount < 30) {
                    handleImageCapture(yuvImage);
                    savedImageCount += 1;
                    // Log.e("tag", "downloaded image");
                }
                saveImageSecond.set(false);
            }
        }
    }

    private YuvImage toYuvImage(Image image) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Invalid image format");
        }

        int width = image.getWidth();
        int height = image.getHeight();

        Image.Plane yPlane = image.getPlanes()[0];
        Image.Plane uPlane = image.getPlanes()[1];
        Image.Plane vPlane = image.getPlanes()[2];

        ByteBuffer yBuffer = yPlane.getBuffer();
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();

        int numPixels = (int) (width * height * 1.5f);
        byte[] nv21 = new byte[numPixels];
        int index = 0;

        int yRowStride = yPlane.getRowStride();
        int yPixelStride = yPlane.getPixelStride();
        for(int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                nv21[index++] = yBuffer.get(y * yRowStride + x * yPixelStride);
            }
        }

        int uvRowStride = uPlane.getRowStride();
        int uvPixelStride = uPlane.getPixelStride();
        int uvWidth = width / 2;
        int uvHeight = height / 2;

        for(int y = 0; y < uvHeight; ++y) {
            for (int x = 0; x < uvWidth; ++x) {
                int bufferIndex = (y * uvRowStride) + (x * uvPixelStride);
                nv21[index++] = vBuffer.get(bufferIndex);
                nv21[index++] = uBuffer.get(bufferIndex);
            }
        }
        return new YuvImage(
                nv21, ImageFormat.NV21, width, height,null);
    }

    private void executeDistanceCheck() {
        while (true) {
            if (globalFrame != null && standCoordinatesReady == true) {
                Pose cameraPose = globalFrame.getCamera().getPose();
                double x = cameraPose.tx();
                double y = cameraPose.ty();
                double z = cameraPose.tz();

                double standX = initialStandCoordinates[0];
                double standY = initialStandCoordinates[1];
                double standZ = initialStandCoordinates[2];

                double initialCameraX = initialCameraCoordinates[0];
                double initialCameraY = initialCameraCoordinates[1];
                double initialCameraZ = initialCameraCoordinates[2];

                double xDifference = standX - x;
                double yDifference = standY - y;
                double zDifference = standZ - z;

                double xDifferenceFirst = standX - initialCameraX;
                double yDifferenceFirst = standY - initialCameraY;
                double zDifferenceFirst = standZ - initialCameraZ;

                double dot = (xDifference * xDifferenceFirst) + (yDifference * yDifferenceFirst) + (zDifference * zDifferenceFirst);
                double mag1 = Math.sqrt((xDifferenceFirst * xDifferenceFirst) + (yDifferenceFirst * yDifferenceFirst) + (zDifferenceFirst * zDifferenceFirst));
                double hypo = Math.sqrt((xDifference * xDifference) + (yDifference * yDifference) + (zDifference * zDifference));


                double firstAngle = Math.acos(dot / (mag1 * hypo));
                firstAngle = Math.toDegrees(firstAngle);

                double secondAngle = 90 - firstAngle;

                double secondAngleRadians = Math.toRadians(secondAngle);

                double distanceToShelf = hypo * Math.sin(secondAngleRadians);

                // Log.e("tag", "distance " + distanceToShelf);

                distanceBar.setText("distance to shelf: " + String.format("%.8f", distanceToShelf) + " m");
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void executeSpeedCheck(TextView speedText) throws CameraNotAvailableException, InterruptedException {

        while (true) {

            if (globalFrame != null) {
                double time1 = System.currentTimeMillis();
                double x1 = globalFrame.getCamera().getPose().tx();
                double y1 = globalFrame.getCamera().getPose().ty();
                double z1 = globalFrame.getCamera().getPose().tz();

                // Log.e("tag", "camera coordinates are " + globalFrame.getCamera().getPose().tx() + ", " +  globalFrame.getCamera().getPose().ty() + ", " + globalFrame.getCamera().getPose().tz());

                Thread.sleep(20);

                double time2 = System.currentTimeMillis();
                double x2 = globalFrame.getCamera().getPose().tx();
                double y2 = globalFrame.getCamera().getPose().ty();
                double z2 = globalFrame.getCamera().getPose().tz();

                // Log.e("tag", "camera coordinates are " + globalFrame.getCamera().getPose().tx() + ", " +  globalFrame.getCamera().getPose().ty() + ", " + globalFrame.getCamera().getPose().tz());

                double xDifference = x1 - x2;
                double yDifference = y1 - y2;
                double zDifference = z1 - z2;

                double timeDifference = time2 - time1;
                timeDifference /= 1000;

                double distance = Math.sqrt(xDifference * xDifference + yDifference * yDifference + zDifference * zDifference);

                double speed = distance / timeDifference;

                speedText.setText("current speed is " + String.format("%.4f", speed) + " m/s");

            }
            Thread.sleep(50);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (session == null) {
            try {
                session = new Session(/* context= */ this);
            } catch (UnavailableArcoreNotInstalledException e) {
                e.printStackTrace();
            } catch (UnavailableApkTooOldException e) {
                e.printStackTrace();
            } catch (UnavailableSdkTooOldException e) {
                e.printStackTrace();
            } catch (UnavailableDeviceNotCompatibleException e) {
                e.printStackTrace();
            }
        }

        try {
            configureSession();
            session.resume();
        } catch (CameraNotAvailableException e) {
            session = null;
            return;
        }

        surfaceView.onResume();

        displayRotationHelper.onResume();
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            session.close();
            session = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (session != null) {
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    private void configureSession() {
        Config config = session.getConfig();
        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        } else {
            config.setDepthMode(Config.DepthMode.DISABLED);
        }
        config.setInstantPlacementMode(Config.InstantPlacementMode.DISABLED);
        session.configure(config);
    }

    private void handleImageCapture(YuvImage yuvImage) {
        String image_name = "" + System.currentTimeMillis() + ".jpg";
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            Log.e("TAG", "media mounted problem");
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists()) {
            dir.mkdir();
        }


        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 50, out);
        byte[] imageBytes = out.toByteArray();

        File downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        File file = new File(downloadPath.getAbsolutePath(), image_name);

        String finalPath = file.getAbsolutePath();

        try {
            // Log.e("TAG", "image being saved currently");
            FileOutputStream output = new FileOutputStream(file);
            output.write(imageBytes);
            output.flush();
            output.close();
            fileNames.add(finalPath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceCreated(SampleRender render) {
        backgroundRenderer = new BackgroundRenderer(render);
    }

    @Override
    public void onSurfaceChanged(SampleRender render, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame(SampleRender render) throws CameraNotAvailableException, InterruptedException, NotYetAvailableException {
        if (session == null) {
            return;
        }

        if (!hasSetTextureNames) {
            session.setCameraTextureNames(
                    new int[] {backgroundRenderer.getCameraColorTexture().getTextureId()}
            );
            hasSetTextureNames = true;
        }

        displayRotationHelper.updateSessionIfNeeded(session);

        Frame frame;
        frame = session.update();

        if (globalFrame == null) {
            globalFrame = frame;
        }

        if (frameReady) {
            if (saveImage.get() == true) {
                Image image = frame.acquireCameraImage();
                imageQueue.add(image);
                saveImage.set(false);
                saveImageSecond.set(true);
            }
        }

        Camera camera = frame.getCamera();

        try {
            backgroundRenderer.setUseDepthVisualization(
                    render, false);
            backgroundRenderer.setUseOcclusion(render, false);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        backgroundRenderer.updateDisplayGeometry(frame);

        if (camera.getTrackingState() == TrackingState.TRACKING) {
            try (Image depthImage = frame.acquireDepthImage16Bits()) {
                backgroundRenderer.updateCameraDepthTexture(depthImage);
            } catch (NotYetAvailableException e) {
            }
        }

        if (frame.getTimestamp() != 0) {
            backgroundRenderer.drawBackground(render);
        }

        Collection<Plane> planes = frame.getUpdatedTrackables(Plane.class);

        if (!standCoordinatesReady) {
            distanceBar.setText("calculating distance...");
            if (planes.size() > 0) {
                double initialX = 0;
                double initialY = 0;
                double initialZ = 0;
                for (Plane plane : planes) {

                    initialX = plane.getCenterPose().tx();
                    initialY = plane.getCenterPose().ty();
                    initialZ = plane.getCenterPose().tz();

                    break;
                }

                double dist = Math.sqrt(initialX * initialX + initialY * initialY + initialZ * initialZ);
                dist *= 0.88;

                initialStandCoordinates[0] = 0;
                initialStandCoordinates[1] = 0;
                initialStandCoordinates[2] = -1 * dist;

                standCoordinatesReady = true;
            }
        }

        if (camera.getTrackingState() == TrackingState.PAUSED) {
            return;
        }
    }
}