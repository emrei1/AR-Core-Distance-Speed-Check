/*
 * Copyright 2018 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.sceneform.samples.gltf;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;

import androidx.appcompat.app.AppCompatActivity;
import android.util.ArraySet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.filament.gltfio.Animator;
import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is an example activity that uses the Sceneform UX package to make common AR tasks easier.
 */
public class GltfActivity extends AppCompatActivity {
  private static final String TAG = GltfActivity.class.getSimpleName();
  private static final double MIN_OPENGL_VERSION = 3.0;

  private ArFragment arFragment;
  private Renderable renderable;

  ArrayDeque<YuvImage> imageQueue = new ArrayDeque<>();

  double currentCaptureReadyTime = System.currentTimeMillis();

  double standX;
  double standY;
  double standZ;

  double initialCameraX;
  double initialCameraY;
  double initialCameraZ;

  AtomicBoolean stillTouch = new AtomicBoolean(true);

  AtomicBoolean coordinatesReceived = new AtomicBoolean(false);

  int imageSaveCount = 0;

  double currentTimeMillis;

  int frameCount = 0;

  TextView fpsView;

  TextView distanceBar;

  Button showPhotosButton;

  Anchor currentAnchor;

  ArrayList<String> fileNames = new ArrayList<>();

  WeakReference<GltfActivity> weakActivity = new WeakReference<>(this);

    private static class AnimationInstance {
    Animator animator;
    Long startTime;
    float duration;
    int index;

    AnimationInstance(Animator animator, int index, Long startTime) {
      this.animator = animator;
      this.startTime = startTime;
      this.duration = animator.getAnimationDuration(index);
      this.index = index;
    }
  }

  private final Set<AnimationInstance> animators = new ArraySet<>();

  private final List<Color> colors =
      Arrays.asList(
          new Color(0, 0, 0, 1),
          new Color(1, 0, 0, 1),
          new Color(0, 1, 0, 1),
          new Color(0, 0, 1, 1),
          new Color(1, 1, 0, 1),
          new Color(0, 1, 1, 1),
          new Color(1, 0, 1, 1),
          new Color(1, 1, 1, 1));
  private int nextColor = 0;

  @Override
  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  // CompletableFuture requires api level 24
  // FutureReturnValueIgnored is not valid
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (!checkIsSupportedDeviceOrFinish(this)) {
      return;
    }



    setContentView(R.layout.activity_ux);
    arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);

      TextView textView = findViewById(R.id.distance);

      fpsView = findViewById(R.id.framefps);

      distanceBar = findViewById(R.id.distanceBar);

      showPhotosButton = findViewById(R.id.showPhotos);

      showPhotosButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View view) {
              openActivity2();
          }
      });

      new Thread("speed check thread") {
          public void run() {

              try {
                  executeSpeedCheck(textView);
              } catch (UnavailableDeviceNotCompatibleException e) {
                  e.printStackTrace();
              } catch (UnavailableSdkTooOldException e) {
                  e.printStackTrace();
              } catch (UnavailableArcoreNotInstalledException e) {
                  e.printStackTrace();
              } catch (UnavailableApkTooOldException e) {
                  e.printStackTrace();
              } catch (CameraNotAvailableException | InterruptedException | NotYetAvailableException e) {
                  e.printStackTrace();
              }
          }
      }.start();

      ArSceneView sceneView = arFragment.getArSceneView();

      sceneView.getScene().addOnUpdateListener(this::onSceneUpdate);


      new Thread("camera fps thread") {
          public void run() {
              try {
                  executeCameraFPSCheck();
              } catch (NotYetAvailableException | InterruptedException e) {
                  e.printStackTrace();
              }
          }
      }.start();

      new Thread("frame touch thread") {
          public void run() {
              try {
                  executeDistanceCheck();
              } catch (Exception e) {
                  e.printStackTrace();
              }
          }
      }.start();

      /*
      new Thread("camera distance thread") {
          public void run() {
              try {
                  // executeCameraDistanceCheck();
              } catch (Exception e) {
                  e.printStackTrace();
              }
          }
      }.start();
       */

      ModelRenderable.builder()
              .setSource(
                      this, R.raw.flying)
              .setIsFilamentGltf(true)
              .build()
              .thenAccept(
                      modelRenderable -> {
                          GltfActivity activity = weakActivity.get();
                          if (activity != null) {
                              activity.renderable = modelRenderable;
                          }
                      })
              .exceptionally(
                      throwable -> {
                          Toast toast =
                                  Toast.makeText(this, "Unable to load Tiger renderable", Toast.LENGTH_LONG);
                          toast.setGravity(Gravity.CENTER, 0, 0);
                          toast.show();
                          return null;
                      });

      ModelRenderable.builder()
              .setSource(
                      this,
                      Uri.parse(
                              "https://storage.googleapis.com/ar-answers-in-search-models/static/Tiger/model.glb"))
              .setIsFilamentGltf(true)
              .build()
              .thenAccept(
                      modelRenderable -> {
                          GltfActivity activity = weakActivity.get();
                          if (activity != null) {
                              activity.renderable = modelRenderable;
                          }
                      })
              .exceptionally(
                      throwable -> {
                          Toast toast =
                                  Toast.makeText(this, "Unable to load Tiger renderable", Toast.LENGTH_LONG);
                          toast.setGravity(Gravity.CENTER, 0, 0);
                          toast.show();
                          return null;
                      });




      //updateModel(weakActivity);

    arFragment.setOnTapArPlaneListener(
        (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {

            Log.e("Tag", "ar tapped ");


            double eventTime = motionEvent.getEventTime();
            double downTime = motionEvent.getDownTime();
            int action = motionEvent.getAction();
            float x = motionEvent.getX();
            float y = motionEvent.getY();
            int metastate = motionEvent.getMetaState();

            Log.e("TAG", "event time is " + eventTime);
            Log.e("TAG", "downtime is " + downTime);
            Log.e("TAG", "action is " + action);
            Log.e("TAg", "x is " + x);
            Log.e("TAG", "y is " + y);
            Log.e("TAG", "metastate is " + metastate);

            stillTouch.set(false);


          if (renderable == null) {
            return;
          }

          // Create the Anchor.
          Anchor anchor = hitResult.createAnchor();
          AnchorNode anchorNode = new AnchorNode(anchor);
          anchorNode.setParent(arFragment.getArSceneView().getScene());

          executeCameraDistanceCheck(anchorNode);

          /*

          // Create the transformable model and add it to the anchor.
          TransformableNode model = new TransformableNode(arFragment.getTransformationSystem());
          model.setParent(anchorNode);
          model.setRenderable(renderable);
          model.select();

          FilamentAsset filamentAsset = model.getRenderableInstance().getFilamentAsset();
          if (filamentAsset.getAnimator().getAnimationCount() > 0) {
            animators.add(new AnimationInstance(filamentAsset.getAnimator(), 0, System.nanoTime()));
          }

          Color color = colors.get(nextColor);
          nextColor++;
          for (int i = 0; i < renderable.getSubmeshCount(); ++i) {
            Material material = renderable.getMaterial(i);
            material.setFloat4("baseColorFactor", color);
          }

          Node tigerTitleNode = new Node();
          tigerTitleNode.setParent(model);
          tigerTitleNode.setEnabled(false);
          tigerTitleNode.setLocalPosition(new Vector3(0.0f, 1.0f, 0.0f));

           */

          // renders tiger title
          /*
          ViewRenderable.builder()
                  .setView(this, R.layout.tiger_card_view)
                  .build()
                  .thenAccept(
                          (renderable) -> {
                              tigerTitleNode.setRenderable(renderable);
                              tigerTitleNode.setEnabled(true);
                          })
                  .exceptionally(
                          (throwable) -> {
                              throw new AssertionError("Could not load card view.", throwable);
                          }
                  );

           */
        });

    arFragment
        .getArSceneView()
        .getScene()
        .addOnUpdateListener(
            frameTime -> {
              Long time = System.nanoTime();
              for (AnimationInstance animator : animators) {
                animator.animator.applyAnimation(
                    animator.index,
                    (float) ((time - animator.startTime) / (double) SECONDS.toNanos(1))
                        % animator.duration);
                animator.animator.updateBoneMatrices();
              }
            });
  }

  private void executeCameraDistanceCheck(AnchorNode anchorNode) {
      Vector3 v3 = arFragment.getArSceneView().getScene().getCamera().getWorldPosition();
      initialCameraX = v3.x;
      initialCameraY = v3.y;
      initialCameraZ = v3.z;
      standX = anchorNode.getWorldPosition().x;
      standY = anchorNode.getWorldPosition().y;
      standZ = anchorNode.getWorldPosition().z;
      coordinatesReceived.set(true);
  }

  private void executeDistanceCheck() throws InterruptedException {
      while (true) {
          if (stillTouch.get()) {
              long downTime = SystemClock.uptimeMillis();
              long eventTime = SystemClock.uptimeMillis() + 50;
              float x = 50.0f;
              float y = 50.0f;

              double a = 0.000005;

              int metaState = 0;
              MotionEvent motionEvent = MotionEvent.obtain(
                      (long) a,
                      (long) a,
                      MotionEvent.ACTION_UP,
                      548.0f,
                      1110.0f,
                      metaState
              );

              super.dispatchTouchEvent(motionEvent);
              arFragment.getArSceneView().onTouchEvent(motionEvent);

              // Log.e("TAG", "executed motion event logic");
          } else if (stillTouch.get() == false && coordinatesReceived.get() == true) {
              Vector3 v3 = arFragment.getArSceneView().getScene().getCamera().getWorldPosition();
              double x = v3.x;
              double y = v3.y;
              double z = v3.z;

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

              distanceBar.setText("distance to shelf: " + distanceToShelf);

              Thread.sleep(200);

          }
      }
  }

  private void openActivity2() {
      Intent intent = new Intent(this, Activity2.class);
      intent.putExtra("filenames", fileNames);
      startActivity(intent);
  }

    private void onSceneUpdate(FrameTime frameTime) throws NotYetAvailableException {
      if (calculateCaptureReadyTime()) {
          Session session = arFragment.getArSceneView().getSession();
          // Log.e("TAG", "distance checked");
          Image image = arFragment.getArSceneView().getArFrame().acquireCameraImage();
          YuvImage yuvImage = toYuvImage(image);
          imageQueue.add(yuvImage);
          image.close();
          // Log.e("TAG", "scene updated AAAA");
          calculateFPSTime();
      }
    }

    private boolean calculateCaptureReadyTime() {
        double temp = currentCaptureReadyTime;
        double current = System.currentTimeMillis();

        if (current - temp >= 500) {
            currentCaptureReadyTime = current;
            return true;
        }
        return false;
    }

    public YuvImage toYuvImage(Image image) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Invalid image format");
        }

        int width = image.getWidth();
        int height = image.getHeight();

        // Order of U/V channel guaranteed, read more:
        // https://developer.android.com/reference/android/graphics/ImageFormat#YUV_420_888
        Image.Plane yPlane = image.getPlanes()[0];
        Image.Plane uPlane = image.getPlanes()[1];
        Image.Plane vPlane = image.getPlanes()[2];

        ByteBuffer yBuffer = yPlane.getBuffer();
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();

        // Full size Y channel and quarter size U+V channels.
        int numPixels = (int) (width * height * 1.5f);
        byte[] nv21 = new byte[numPixels];
        int index = 0;

        // Copy Y channel.
        int yRowStride = yPlane.getRowStride();
        int yPixelStride = yPlane.getPixelStride();
        for(int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                nv21[index++] = yBuffer.get(y * yRowStride + x * yPixelStride);
            }
        }

        // Copy VU data; NV21 format is expected to have YYYYVU packaging.
        // The U/V planes are guaranteed to have the same row stride and pixel stride.
        int uvRowStride = uPlane.getRowStride();
        int uvPixelStride = uPlane.getPixelStride();
        int uvWidth = width / 2;
        int uvHeight = height / 2;

        for(int y = 0; y < uvHeight; ++y) {
            for (int x = 0; x < uvWidth; ++x) {
                int bufferIndex = (y * uvRowStride) + (x * uvPixelStride);
                // V channel.
                nv21[index++] = vBuffer.get(bufferIndex);
                // U channel.
                nv21[index++] = uBuffer.get(bufferIndex);
            }
        }
        return new YuvImage(
                nv21, ImageFormat.NV21, width, height, /* strides= */ null);
    }

    private void calculateFPSTime() {
      double temp = currentTimeMillis;
      double current = System.currentTimeMillis();

      if (current - temp >= 1000) {
          currentTimeMillis = current;
          fpsView.setText("current frame fps is " + frameCount + " frames per second");
          frameCount = 0;
      } else {
          frameCount += 1;
      }
    }


  public void executeCameraFPSCheck() throws NotYetAvailableException, InterruptedException {

       while (true) {
          if (!imageQueue.isEmpty()) {
              YuvImage yuvImage = imageQueue.remove();
              String image_name = "" + System.currentTimeMillis() + ".jpg";
              String state = Environment.getExternalStorageState();
              if (!Environment.MEDIA_MOUNTED.equals(state)) {
                  Log.e("TAG", "problem");
              }
              File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
              if (!dir.exists()) {
                  Log.e("TAG", "created directory");
                  dir.mkdir();
              }

              ByteArrayOutputStream out = new ByteArrayOutputStream();
              yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 50, out);
              byte[] imageBytes = out.toByteArray();

              String downloadPath = "/storage/self/primary/Download";

              File file = new File(downloadPath, image_name);

              String finalPath = file.getAbsolutePath();

              try {
                  if (imageSaveCount < 0) {
                      Log.e("TAG", "image being saved currently");
                      FileOutputStream output = new FileOutputStream(file);
                      output.write(imageBytes);
                      output.flush();
                      output.close();

                      DownloadManager downloadManager = (DownloadManager) this.getSystemService(DOWNLOAD_SERVICE);
                      downloadManager.addCompletedDownload(file.getName(), file.getName(), true, "text/plain", file.getAbsolutePath(), file.length(), true);
                      imageSaveCount += 1;
                      fileNames.add(finalPath);
                  }
              } catch (FileNotFoundException e) {
                  e.printStackTrace();
              } catch (IOException e) {
                  e.printStackTrace();
              }
          }
      }
  }

  public void executeSpeedCheck(TextView textView) throws UnavailableDeviceNotCompatibleException, UnavailableSdkTooOldException, UnavailableArcoreNotInstalledException, UnavailableApkTooOldException, CameraNotAvailableException, InterruptedException, NotYetAvailableException {

      while (true) {

          ArSceneView sceneView = arFragment.getArSceneView();

          double firstTime = System.currentTimeMillis();
          Camera camera1 = sceneView.getScene().getCamera();
          Vector3 firstPosition = camera1.getWorldPosition();
          Thread.sleep(100);
          double secondTime = System.currentTimeMillis();
          Vector3 secondPosition = camera1.getWorldPosition();

          float xDifference = secondPosition.x - firstPosition.x;
          float yDifference = secondPosition.y - firstPosition.y;
          float zDifference = secondPosition.z - firstPosition.z;

          double distance = Math.sqrt((xDifference * xDifference) + (yDifference * yDifference) + (zDifference * zDifference));

          double timePassed = secondTime - firstTime;

          double speed = distance / timePassed;

          // Log.e("current speed", "current speed is " + speed);

          String textString = "current speed is " + speed;

          textView.setText(textString);


      }

  }


  /**
   * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
   * on this device.
   *
   * <p>Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
   *
   * <p>Finishes the activity if Sceneform can not run
   */
  public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
    if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
      Log.e(TAG, "Sceneform requires Android N or later");
      Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
      activity.finish();
      return false;
    }
    String openGlVersionString =
        ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
            .getDeviceConfigurationInfo()
            .getGlEsVersion();
    if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
      Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
      Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
          .show();
      activity.finish();
      return false;
    }
    return true;
  }

}
