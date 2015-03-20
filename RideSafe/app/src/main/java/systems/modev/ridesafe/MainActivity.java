/**
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package systems.modev.ridesafe;

import com.firebase.client.AuthData;
import com.firebase.client.Firebase;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.telephony.PhoneNumberUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.telephony.SmsManager;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.firebase.client.FirebaseError;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.Date;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;

/**
 * Getting Location Updates.
 *
 * Demonstrates how to use the Fused Location Provider API to get updates about a device's
 * location. The Fused Location Provider is part of the Google Play services location APIs.
 *
 * For a simpler example that shows the use of Google Play services to fetch the last known location
 * of a device, see
 * https://github.com/googlesamples/android-play-location/tree/master/BasicLocation.
 *
 * This sample uses Google Play services, but it does not require authentication. For a sample that
 * uses Google Play services for authentication, see
 * https://github.com/googlesamples/android-google-accounts/tree/master/QuickStart.
 */
public class MainActivity extends ActionBarActivity implements
        ConnectionCallbacks, OnConnectionFailedListener, LocationListener, OnMapReadyCallback {

    // Keys for storing activity state in the Bundle.
    protected final static String REQUESTING_LOCATION_UPDATES_KEY = "requesting-location-updates-key";
    protected final static String LOCATION_KEY = "location-key";
    protected final static String LAST_UPDATED_TIME_STRING_KEY = "last-updated-time-string-key";
    protected GoogleMap mMap;
    protected String phoneNumber;

    protected Firebase firebase;


    /**
     * Provides the entry point to Google Play services.
     */
    protected GoogleApiClient mGoogleApiClient;

    /**
     * Stores parameters for requests to the FusedLocationProviderApi.
     */
    protected LocationRequest mLocationRequest;

    /**
     * Represents a geographical location.
     */
    protected Location mCurrentLocation;

    protected String link, baseLink;
    private int pauseCount;

    // UI Widgets.
    protected ToggleButton mToggleButton;

    protected double mLatitude;
    protected double mLongitude;

    protected double pLatitude;
    protected double pLongitude;

    /**
     * Tracks the status of the location updates request. Value changes when the user presses the
     * Start Updates and Stop Updates buttons.
     */
    protected Boolean mRequestingLocationUpdates;


    /**
     * Time when the location was updated represented as a String.
     */
    protected String mLastUpdateTime;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Firebase.setAndroidContext(this);

        // Kick off the process of building a GoogleApiClient and requesting the LocationServices
        // API.
        buildGoogleApiClient();

        // Update values using data stored in the Bundle.
        updateValuesFromBundle(savedInstanceState);

        pauseCount = 0;

        try {
            phoneNumber = read("number.txt", getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }

        setContentView(R.layout.activity_maps);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        setUpMapIfNeeded();

        // Locate the UI widgets.
        mToggleButton = (ToggleButton) findViewById(R.id.toggle_updates);

        mRequestingLocationUpdates = false;
        mLastUpdateTime = "";

        mToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton toggleButton, boolean isChecked) {
                Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                vibrator.vibrate(250);

                try {
                    if (isChecked) { // if the user just enabled updates
                        if (phoneNumber == null || phoneNumber.length() < 9)
                            throw new IllegalArgumentException();

                        startLocationUpdates();
                        mToggleButton.setBackground(getResources().getDrawable(R.drawable.endtrackingbuttonsmall));

                        if (pauseCount >= 1) { // create a new child in the table each time the user starts tracking
                            // link = baseLink + String.valueOf(pauseCount);
                            // firebase = firebase.getRoot().child(link.split("\\?")[1]);
                            firebase.unauth();
                            firebase = new Firebase("https://ridesafe.firebaseio.com");
                            authenticateFirebase();
                        }

                        SmsManager.getDefault().sendTextMessage(phoneNumber, null, "I pressed the TRACK button in RideSafe! " +
                            String.format("Please track me at: %s", link), null, null);


                    }
                    else { // if the user just disabled updates
                        pauseCount++;
                        stopLocationUpdates();
                        mToggleButton.setBackground(getResources().getDrawable(R.drawable.begintrackingbuttonsmall));
                        SmsManager.getDefault().sendTextMessage(phoneNumber, null, "I pressed the STOP TRACKING button in RideSafe! "
                                + "Thanks for watching out for me!", null, null);
                    }
                } catch (java.lang.IllegalArgumentException e) {
                    if(phoneNumber == null) {
                        Toast.makeText(getApplicationContext(), String.format("Please enter a trusted phone number (using the button in the top right) before" +
                                " tracking."), Toast.LENGTH_SHORT).show();
                    }
                    else
                        Toast.makeText(getApplicationContext(), String.format("There was a problem. Please re-enter your safe contact number using" +
                                " the button in the top right."), Toast.LENGTH_SHORT).show();

                }

            }
        });




        ActionBar actionBar = getSupportActionBar();
        actionBar.setBackgroundDrawable(new ColorDrawable(Color.rgb(0, 209, 202)));
        actionBar.setDisplayOptions(actionBar.getDisplayOptions() | ActionBar.DISPLAY_SHOW_CUSTOM);
        ImageView imageView = new ImageView(actionBar.getThemedContext());
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(R.drawable.newlogosmall);
        ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        layoutParams.rightMargin = 40;
        imageView.setLayoutParams(layoutParams);
        actionBar.setCustomView(imageView);
        actionBar.setTitle("");


        firebase = new Firebase("https://ridesafe.firebaseio.com");
        authenticateFirebase();
    }

    protected void authenticateFirebase() {
        firebase.authAnonymously(new Firebase.AuthResultHandler() {
            @Override
            public void onAuthenticated(AuthData authData) {
                // we've authenticated this session with Firebase
                // authData object contains getter methods
                firebase = firebase.child(authData.getUid().split("-")[1]);
                baseLink = link = "http://ridesafe.modev.systems/?" + authData.getUid().split("-")[1];
            }

            public void onAuthenticationError(FirebaseError firebaseError) {
                // there was an error
                Toast.makeText(getApplicationContext(), String.format("Please ensure you are connected to the internet before you run RideSafe!"), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {

            case R.id.action_contacts:
                PromptDialog dlg = new PromptDialog(MainActivity.this, R.string.prompt_title, R.string.prompt_comment) {
                        @Override
                        public boolean onOkClicked(String input) {
                            try {
                                if (!PhoneNumberUtils.isGlobalPhoneNumber(input) || input.length() < 9)
                                    throw new IllegalArgumentException();

                                write("number.txt", getBaseContext(), input);
                                phoneNumber = input;
                                SmsManager.getDefault().sendTextMessage(phoneNumber, null, "I just set you as my trusted contact in RideSafe. "
                                        + "Ask me about it!", null, null);
                                Toast.makeText(getApplicationContext(), String.format("%s successfully registered as trusted contact!", phoneNumber), Toast.LENGTH_SHORT).show();
                            }

                            catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(getApplicationContext(), String.format("Error: invalid phone number entered. Please try again!"), Toast.LENGTH_SHORT).show();
                            }

                            return true;
                        }
                    };
                dlg.show();
                break;


            case R.id.action_help:
                try {

                    SmsManager.getDefault().sendTextMessage(phoneNumber, null, "I pressed the HELP button in RideSafe! " +
                            String.format("Please make sure I'm okay by checking my map at %s", link), null, null);

                    Toast.makeText(getApplicationContext(), String.format("Emergency message sent to: %s", phoneNumber), Toast.LENGTH_SHORT).show();
                }

                catch (Exception e){  // file is malformed
                    if(phoneNumber == null) {
                        Toast.makeText(getApplicationContext(), String.format("Please enter a trusted phone number (using the button in the top right) before" +
                                " tracking."), Toast.LENGTH_SHORT).show();

                        // recreate();

                    }
                    File file = new File("number.txt");
                    file.delete();
                    e.printStackTrace();
                }

                return true;

            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
            mMap.setMyLocationEnabled(true);


            // Check if we were successful in obtaining the map.
        }
    }

    /**
     * Updates fields based on data stored in the bundle.
     *
     * @param savedInstanceState The activity state saved in the Bundle.
     */
    private void updateValuesFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            // Update the value of mRequestingLocationUpdates from the Bundle, and make sure that
            // the Start Updates and Stop Updates buttons are correctly enabled or disabled.
            if (savedInstanceState.keySet().contains(REQUESTING_LOCATION_UPDATES_KEY)) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(
                        REQUESTING_LOCATION_UPDATES_KEY);
            }

            // Update the value of mCurrentLocation from the Bundle and update the UI to show the
            // correct latitude and longitude.
            if (savedInstanceState.keySet().contains(LOCATION_KEY)) {
                // Since LOCATION_KEY was found in the Bundle, we can be sure that mCurrentLocation
                // is not null.
                mCurrentLocation = savedInstanceState.getParcelable(LOCATION_KEY);
            }

            // Update the value of mLastUpdateTime from the Bundle and update the UI.
            if (savedInstanceState.keySet().contains(LAST_UPDATED_TIME_STRING_KEY)) {
                mLastUpdateTime = savedInstanceState.getString(LAST_UPDATED_TIME_STRING_KEY);
            }
        }
    }



    /**
     * Builds a GoogleApiClient. Uses the {@code #addApi} method to request the
     * LocationServices API.
     */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        createLocationRequest();
    }

    /**
     * Sets up the location request. Android has two location request settings:
     * {@code ACCESS_COARSE_LOCATION} and {@code ACCESS_FINE_LOCATION}. These settings control
     * the accuracy of the current location. This sample uses ACCESS_FINE_LOCATION, as defined in
     * the AndroidManifest.xml.
     * <p/>
     * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update
     * interval (5 seconds), the Fused Location Provider API returns location updates that are
     * accurate to within a few feet.
     * <p/>
     * These settings are appropriate for mapping applications that show real-time location
     * updates.
     */
    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }


    /**
     * Requests location updates from the FusedLocationApi.
     */
    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }


    /**
     * Updates the latitude, the longitude, and the last location time in the UI.
     */
    private void updateCoords() {
        if (pLongitude == 0 || pLatitude == 0 || mLongitude == 0 || mLatitude == 0) {
            pLatitude = mCurrentLocation.getLatitude();
            pLongitude = mCurrentLocation.getLongitude();
            mLatitude = mCurrentLocation.getLatitude();
            mLongitude = mCurrentLocation.getLongitude();
        }

        else {
            pLatitude = mLatitude;
            pLongitude = mLongitude;
            mLatitude = mCurrentLocation.getLatitude();
            mLongitude = mCurrentLocation.getLongitude();
        }

        mLastUpdateTime = DateFormat.getDateTimeInstance().format(new Date());
        // firebase push
        firebase.child(mLastUpdateTime).setValue(String.format("%f %f", mLatitude, mLongitude));
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        mMap.clear();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle bundle) {
        if (mCurrentLocation == null) {
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            mLastUpdateTime = DateFormat.getDateTimeInstance().format(new Date());
        }

        updateCoords();

        CameraPosition position =
                new CameraPosition.Builder().target(new LatLng(mLatitude, mLongitude))
                        .zoom(17f)
                        .bearing(0)
                        .tilt(25)
                        .build();

        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), 1000, new GoogleMap.CancelableCallback() {
            @Override
            public void onFinish() {

            }

            @Override
            public void onCancel() {

            }
        });
    }

    /**
     * Callback that fires when the location changes.
     */
    @Override
    public void onLocationChanged(Location location) {
        updateCoords();
        mCurrentLocation = location;
        // mMap.addMarker(new MarkerOptions().position(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude())));
        mMap.addPolyline((new PolylineOptions()).add(new LatLng(pLatitude, pLongitude), new LatLng(mLatitude, mLongitude)).width(15).color(Color.rgb(217, 22, 176)).geodesic(true));

        CameraPosition position =
                new CameraPosition.Builder().target(new LatLng(mLatitude, mLongitude))
                        .zoom(17f)
                        .bearing(0)
                        .tilt(25)
                        .build();

        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), 1000, new GoogleMap.CancelableCallback() {
            @Override
            public void onFinish() {

            }

            @Override
            public void onCancel() {

            }
        });

    }

    @Override
    public void onConnectionSuspended(int cause) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
    }


    /**
     * Stores activity data in the Bundle.
     */
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(REQUESTING_LOCATION_UPDATES_KEY, mRequestingLocationUpdates);
        savedInstanceState.putParcelable(LOCATION_KEY, mCurrentLocation);
        savedInstanceState.putString(LAST_UPDATED_TIME_STRING_KEY, mLastUpdateTime);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
    }

    public static void write (String filename,Context c,String string) throws IOException {
        try {
            FileOutputStream fos = c.openFileOutput(filename, Context.MODE_PRIVATE);
            fos.write(string.getBytes());
            fos.close();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public static String read (String filename,Context c) throws IOException{
        String read = "";
        StringBuffer buffer = new StringBuffer();

        FileInputStream fis = c.openFileInput(filename);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
        if (fis!=null) {
            while ((read = reader.readLine()) != null) {
                buffer.append(read + "\n" );
            }
        }
        fis.close();
        return buffer.toString();
    }


}
