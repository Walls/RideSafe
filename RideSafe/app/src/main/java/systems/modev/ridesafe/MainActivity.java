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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.telephony.PhoneNumberUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
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
        ConnectionCallbacks, OnConnectionFailedListener, LocationListener, OnMapReadyCallback, GpsStatus.Listener {

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

    protected String link;

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

    protected final int CONTACT_CODE = 1234;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Firebase.setAndroidContext(this);
        firebase = new Firebase("https://ridesafe.firebaseio.com");

        // Check if GPS is enabled and if not send user to the GPS settings
        if (!haveGPSConnection()) {
            Toast.makeText(getApplicationContext(), String.format("There was a problem. Please make sure you have GPS " +
                    "and location services enabled before beginning!"), Toast.LENGTH_LONG).show();

            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
            finish();
        }

        // Check if mobile data is enabled and if not send user to the mobile data settings
        if (!haveNetworkConnection()) {
            Toast.makeText(getApplicationContext(), String.format("There was a problem. Please ensure you are connected to mobile data" +
                    " and GPS/location services."), Toast.LENGTH_LONG).show();

            Intent intent = new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS);
            startActivity(intent);
            finish();

        }


        // Kick off the process of building a GoogleApiClient and requesting the LocationServices
        // API.
        buildGoogleApiClient();

        // Update values using data stored in the Bundle.
        updateValuesFromBundle(savedInstanceState);

        // ensure that phone number is read correctly from file
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
                        mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                        resetCoordinates();

                        if (phoneNumber == null || phoneNumber.equals("")) {
                            throw new IllegalArgumentException();
                        }

                        else {
                            startLocationUpdates();
                            mToggleButton.setBackground(getResources().getDrawable(R.drawable.endtrackingbuttonsmall));

                            // create a new child in the table each time the user starts tracking
                            firebase = new Firebase("https://ridesafe.firebaseio.com");
                            authenticateFirebase();
                        }
                    }

                    else { // if the user just disabled updates
                        SmsManager.getDefault().sendTextMessage(phoneNumber, null, "I pressed the STOP TRACKING button in RideSafe! "
                                + "Thanks for watching out for me!", null, null);
                        stopLocationUpdates();
                        mToggleButton.setBackground(getResources().getDrawable(R.drawable.begintrackingbuttonsmall));

                    }
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    mToggleButton.setChecked(false); // prevent button from toggling when no phone # is not known
                    Toast.makeText(getApplicationContext(), String.format("Please choose a trusted contact" +
                                " (using the button in the top right) before tracking."), Toast.LENGTH_SHORT).show();

                }

                catch (NullPointerException e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(), String.format("There was a problem. Please ensure you are connected to mobile data" +
                            " and GPS/location services."), Toast.LENGTH_LONG).show();
                    finish();
                }

            }
        });


        // Style action bar
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


        // Style status bar (only for Lollipop+ devices)
        try {
            Window window = this.getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(Color.parseColor("#008783"));
        }

        catch (NoSuchMethodError e) {
            e.printStackTrace();
        }

        /*firebase = new Firebase("https://ridesafe.firebaseio.com");
        authenticateFirebase();*/
    }

    protected void authenticateFirebase() {
        firebase.authAnonymously(new Firebase.AuthResultHandler() {
            @Override
            public void onAuthenticated(AuthData authData) {
                // we've authenticated this session with Firebase
                // authData object contains getter methods
                String extension = authData.getUid().split("-")[1];
                firebase = firebase.child(extension);
                link = "http://ridesafe.modev.systems/?" + extension;
                SmsManager.getDefault().sendTextMessage(phoneNumber, null, "I pressed the TRACK button in RideSafe! " +
                        String.format("Please track me at: %s", link), null, null);
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
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        switch (item.getItemId()) {

            case R.id.action_contacts:
                vibrator.vibrate(250);
                Intent pickContactIntent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                pickContactIntent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
                startActivityForResult(pickContactIntent, CONTACT_CODE);
                break;


            case R.id.action_help:
                vibrator.vibrate(250);
                try {
                    if (link != null && !link.equals("")) { // if link contains a valid UID
                        SmsManager.getDefault().sendTextMessage(phoneNumber, null, "I pressed the HELP button in RideSafe! " +
                                String.format("Please make sure I'm okay by checking my map at %s", link), null, null);

                        Toast.makeText(getApplicationContext(), String.format("Emergency message sent to: %s", phoneNumber), Toast.LENGTH_SHORT).show();
                    }

                    else { // if link is empty
                        Toast.makeText(getApplicationContext(), String.format("Please begin tracking first."), Toast.LENGTH_SHORT).show();
                    }
                }

                catch (Exception e){  // file is malformed
                    e.printStackTrace();
                    if(phoneNumber == null) {
                        Toast.makeText(getApplicationContext(), String.format("Please choose a trusted contact (using the button in the top right) before" +
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

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == CONTACT_CODE && resultCode == RESULT_OK) {
            try {
                Uri uri = data.getData();
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                cursor.moveToFirst();

                int phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                phoneNumber = cursor.getString(phoneIndex);

                try {
                    // if phone number is null/empty, or doesn't match a US or global phone number
                    if ((phoneNumber == null) || phoneNumber.equals("") || !isValidPhoneNumber(phoneNumber)) {
                        System.out.println("PHONE NUMBER = " + phoneNumber);
                        throw new IllegalArgumentException();
                    }

                    else {
                        write("number.txt", getBaseContext(), phoneNumber);
                        SmsManager.getDefault().sendTextMessage(phoneNumber, null, "I just set you as my trusted contact in RideSafe. "
                                + "Ask me about it!", null, null);
                        Toast.makeText(getApplicationContext(), String.format("%s successfully registered as trusted contact!", phoneNumber), Toast.LENGTH_SHORT).show();
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                    phoneNumber = null;
                    Toast.makeText(getApplicationContext(), String.format("Error: invalid phone number. Please choose another contact."), Toast.LENGTH_SHORT).show();
                }

                cursor.close();

            } catch (NullPointerException e) {
                e.printStackTrace();
                Toast.makeText(getApplicationContext(), String.format("There was a problem retrieving the contact data."), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
            mMap.setMyLocationEnabled(true);
            mMap.setBuildingsEnabled(true); // turns on 3d buildings
            mMap.setTrafficEnabled(false); // turns off traffic view
        }
    }

    /**
     * Updates fields based on data stored in the bundle.
     *
     * @param savedInstanceState The activity state saved in the Bundle.
     */
    private void updateValuesFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.keySet().contains(REQUESTING_LOCATION_UPDATES_KEY)) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(
                        REQUESTING_LOCATION_UPDATES_KEY);
            }

            if (savedInstanceState.keySet().contains(LOCATION_KEY)) {
                mCurrentLocation = savedInstanceState.getParcelable(LOCATION_KEY);
            }

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
        mLocationRequest.setInterval(5000);
        mLocationRequest.setFastestInterval(2500);
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
            resetCoordinates();
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
        /*if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }*/
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


    @Override
    public void onGpsStatusChanged(int event) {
        switch (event) {
            case GpsStatus.GPS_EVENT_STOPPED:
                Toast.makeText(getApplicationContext(), String.format("There was a problem. Please make sure you have GPS " +
                        "and location services enabled before beginning!"), Toast.LENGTH_SHORT).show();
                stopLocationUpdates();
                finish();
        }
    }

    public boolean isValidPhoneNumber(String number) {
        // returns true if number is a valid US or Global number
        return PhoneNumberUtils.isGlobalPhoneNumber(number) || number.matches("1?[\\s-]?\\(?(\\d{3})\\)?[\\s-]?\\d{3}[\\s-]?\\d{4}");
    }

    public void resetCoordinates () {
        try {
            pLatitude = mLatitude = mCurrentLocation.getLatitude();
            pLongitude = mLongitude = mCurrentLocation.getLongitude();
            mLastUpdateTime = DateFormat.getDateTimeInstance().format(new Date());
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    private boolean haveNetworkConnection() {
        boolean haveConnectedWifi = false;
        boolean haveConnectedMobile = false;

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] netInfo = cm.getAllNetworkInfo();
        for (NetworkInfo ni : netInfo) {
            if (ni.getTypeName().equalsIgnoreCase("WIFI"))
                if (ni.isConnected())
                    haveConnectedWifi = true;
            if (ni.getTypeName().equalsIgnoreCase("MOBILE"))
                if (ni.isConnected())
                    haveConnectedMobile = true;
        }
        return haveConnectedWifi || haveConnectedMobile;
    }

    private boolean haveGPSConnection() {
        LocationManager service = (LocationManager) getSystemService(LOCATION_SERVICE);
        return service.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

}
