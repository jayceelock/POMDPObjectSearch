package com.example.jaycee.pomdpobjectsearch;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import com.example.jaycee.pomdpobjectsearch.mdptools.GuidanceInterface;
import com.example.jaycee.pomdpobjectsearch.mdptools.GuidanceManager;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

public class ActivityCamera extends AppCompatActivity implements BarcodeListener, GuidanceInterface, RenderListener
{
    private static final String TAG = ActivityCamera.class.getSimpleName();

    private static final int CAMERA_PERMISSION_CODE = 0;
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;

    private static final int O_NOTHING = 0;

    private static final int T_COMPUTER_MONITOR = 1;
    private static final int T_COMPUTER_MOUSE = 3;
    private static final int T_COMPUTER_KEYBOARD = 2;
    private static final int T_DESK = 4;
    private static final int T_MUG = 6;
    private static final int T_OFFICE_SUPPLIES = 7;
    private static final int T_WINDOW = 8;

    private Session session;
    private Pose devicePose;

    private CameraSurface surfaceView;
    private DrawerLayout drawerLayout;
    private Toast toast;

    private SoundGenerator soundGenerator;
    private BarcodeScanner barcodeScanner;

    private GuidanceManager guidanceManager;
    private Metrics metrics;

    private boolean requestARCoreInstall = true;

    private long timestamp;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu);

        surfaceView = findViewById(R.id.surfaceview);

        drawerLayout = findViewById(R.id.layout_drawer_objects);
        NavigationView navigationView = findViewById(R.id.navigation_view_objects);

        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener()
        {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item)
            {
                int target = 0;
                switch (item.getItemId())
                {
                    case R.id.item_object_mug:
                        target = T_MUG;
                        break;
                    case R.id.item_object_desk:
                        target = T_DESK;
                        break;
                    case R.id.item_object_office_supplies:
                        target = T_OFFICE_SUPPLIES;
                        break;
                    case R.id.item_object_keyboard:
                        target = T_COMPUTER_KEYBOARD;
                        break;
                    case R.id.item_object_monitor:
                        target = T_COMPUTER_MONITOR;
                        break;
                    case R.id.item_object_mouse:
                        target = T_COMPUTER_MOUSE;
                        break;
                    case R.id.item_object_window:
                        target = T_WINDOW;
                        break;
                }

                onGuidanceStart(target);

                item.setCheckable(true);

                drawerLayout.closeDrawers();

                return true;
            }
        });
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if(session == null)
        {
            try
            {
                switch(ArCoreApk.getInstance().requestInstall(this, requestARCoreInstall))
                {
                    case INSTALLED:
                        break;
                    case INSTALL_REQUESTED:
                        requestARCoreInstall = false;
                        return;
                }

                if(!hasCameraPermission())
                {
                    requestCameraPermission();
                    return;
                }
                session = new Session(this);

                // Set config settings
                Config conf = new Config(session);
                conf.setFocusMode(Config.FocusMode.AUTO);
                session.configure(conf);
            }
            catch(UnavailableUserDeclinedInstallationException | UnavailableArcoreNotInstalledException  e)
            {
                Log.e(TAG, "Please install ARCore.");
                return;
            }
            catch(UnavailableDeviceNotCompatibleException e)
            {
                Log.e(TAG, "This device does not support ARCore.");
                return;
            }
            catch(UnavailableApkTooOldException e)
            {
                Log.e(TAG, "Please update the app.");
                return;
            }
            catch(UnavailableSdkTooOldException e)
            {
                Log.e(TAG, "Please update ARCore. ");
                return;
            }
            catch(Exception e)
            {
                Log.e(TAG, "Failed to create AR session.");
            }
        }

        try
        {
            session.resume();
        }
        catch(CameraNotAvailableException e)
        {
            session = null;
            Log.e(TAG, "Camera not available. Please restart app.");
            return;
        }

        surfaceView.onResume();

        if(!JNIBridge.initSound())
        {
            Log.e(TAG, "OpenAL init error");
        }
    }

    @Override
    protected void onPause()
    {
        onBarcodeScannerStop();
        onGuidanceEnd();

        if(soundGenerator != null)
        {
            soundGenerator.stop();
            soundGenerator = null;
        }

        if(session != null)
        {
            surfaceView.onPause();
            session.pause();
        }

        if(!JNIBridge.killSound())
        {
            Log.e(TAG, "OpenAL kill error");
        }

        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                drawerLayout.openDrawer(GravityCompat.START);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public boolean hasCameraPermission()
    {
        return ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    public void requestCameraPermission()
    {
        ActivityCompat.requestPermissions(this, new String[] {CAMERA_PERMISSION}, CAMERA_PERMISSION_CODE);
    }

    @Override
    public long onBarcodeScan()
    {
        long scannedObject = O_NOTHING;
        if(barcodeScanner != null)
        {
            scannedObject = barcodeScanner.getCode();
        }

        if(metrics != null)
        {
            metrics.updateObservation(scannedObject);
        }

        if(scannedObject != O_NOTHING && scannedObject != -1)
        {
            if (toast != null)
            {
                toast.cancel();
            }
            toast = Toast.makeText(this, guidanceManager.objectCodeToString(scannedObject), Toast.LENGTH_SHORT);
            toast.show();
        }

        return scannedObject;
    }

    @Override
    public void onBarcodeScannerStart()
    {
        barcodeScanner = new BarcodeScanner(this, 525, 525, surfaceView.getRenderer());
        barcodeScanner.run();
    }

    @Override
    public void onBarcodeScannerStop()
    {
        if(barcodeScanner != null)
        {
            barcodeScanner.stop();
            barcodeScanner = null;
        }
    }

    // Triggers pose update, returns timer status
    @Override
    public boolean onGuidanceLoop()
    {
        if(metrics != null)
        {
            metrics.updateWaypointPosition(guidanceManager.getWaypointPose());
            metrics.updateDevicePose(devicePose);
            metrics.updateTimestamp(timestamp);

        }
        if(guidanceManager != null)
        {
            return guidanceManager.updateDevicePose(devicePose);
        }

        return false;
    }

    @Override
    public void onGuidanceStart(int target)
    {
        guidanceManager = new GuidanceManager(session, devicePose, ActivityCamera.this, target);
        metrics = new Metrics();
        metrics.updateTarget(target);
        metrics.run();

        soundGenerator = new SoundGenerator(this);//, surfaceView.getRenderer());
        soundGenerator.setTarget(target);
        soundGenerator.run();

        surfaceView.getRenderer().setDrawWaypoint(true);
    }

    @Override
    public void onGuidanceEnd()
    {
        if(guidanceManager != null)
        {
            guidanceManager.end();
            guidanceManager = null;
        }

        if(metrics != null)
        {
            metrics.stop();
            metrics = null;
        }

        surfaceView.getRenderer().setDrawWaypoint(false);
    }

    @Override
    public boolean onWaypointReached()
    {
        if(guidanceManager != null)
        {
            return guidanceManager.waypointReached();
        }

        return false;
    }

    @Override
    public void onGuidanceRequested(long observation)
    {
        if(guidanceManager != null)
        {
            guidanceManager.provideGuidance(session, observation);
        }
    }

    @Override
    public Pose onDrawWaypoint()
    {
        if(guidanceManager != null)
        {
            return guidanceManager.getWaypointPose();
        }
        return null;
    }

    @Override
    public Pose onWaypointPoseRequested()
    {
        if(guidanceManager != null)
        {
            return guidanceManager.getWaypointPose();
        }
        return null;
    }

/*    @Override
    public Pose onDevicePoseRequested()
    {
        return devicePose;
    }*/

    @Override
    public float[] onCameraVectorRequested()
    {
        if(guidanceManager != null)
        {
            return guidanceManager.getCameraVector();
        }

        return null;
    }

    @Override
    public Frame onFrameRequest()
    {
        try
        {
            Frame newFrame = session.update();
            timestamp = newFrame.getTimestamp();
            devicePose = newFrame.getCamera().getPose();
            return newFrame;
        }
        catch (CameraNotAvailableException e)
        {
            Log.e(TAG, "AR Camera not available: " + e);
            return null;
        }
    }

    @Override
    public void onViewportChange(int width, int height)
    {
        try
        {
            int displayRotation = getSystemService(WindowManager.class).getDefaultDisplay().getRotation();
            session.setDisplayGeometry(displayRotation, width, height);
        }
        catch(NullPointerException e)
        {
            Log.e(TAG, "Default display exception: " + e);
        }
    }

    @Override
    public void onDrawRequest(int textureId)
    {
        session.setCameraTextureName(textureId);
    }
}
