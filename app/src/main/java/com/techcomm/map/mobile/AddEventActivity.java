package com.techcomm.map.mobile;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.mobsandgeeks.saripaar.ValidationError;
import com.mobsandgeeks.saripaar.Validator;
import com.mobsandgeeks.saripaar.annotation.NotEmpty;
import com.mobsandgeeks.saripaar.annotation.Pattern;
import com.mobsandgeeks.saripaar.annotation.Select;
import com.mobsandgeeks.saripaar.annotation.Url;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * A form allowing the user to add an event to the map.
 * Uses a Google Form to input and submit items. The data source is a Google Sheet.
 */
public class AddEventActivity extends FragmentActivity
        implements DatePickerDialog.OnDateSetListener {

    private static final String TAG = AddEventActivity.class.getSimpleName();
    private static final String FORM_URL = "https://docs.google.com/forms/d/1uIEpAu0vpiDwNqwQ" +
            "cr-912TD1_nG_PND9J3NDCPvEXI/formResponse";

    // An identifier for the place picker request.
    private static final int PLACE_PICKER_REQUEST = 1;
    private Place mPickedPlace;
    private String mPickedType;
    private int mPickedTypePos = 0;

    // Annotations on input fields are used for validation.
    @Select
    private Spinner typeSpinner;

    @NotEmpty
    private EditText nameEditText;

    private EditText descriptionEditText;

    @NotEmpty
    @Url
    private EditText websiteEditText;

    @Pattern(regex = "(\\d{2} \\d{2} \\d{4})?", message = "Date must follow the dd mm yyyy format")
    private EditText startDateEditText;

    @Pattern(regex = "(\\d{2} \\d{2} \\d{4})?", message = "Date must follow the dd mm yyyy format")
    private EditText endDateEditText;

    private Validator validator;

    private EditText mCurrentEditedDateEditText;

    /**
     * Initializes the add-event activity.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_event);

        typeSpinner = (Spinner) findViewById(R.id.type);
        nameEditText = (EditText) findViewById(R.id.name);
        descriptionEditText = (EditText) findViewById(R.id.description);
        websiteEditText = (EditText) findViewById(R.id.website);
        startDateEditText = (EditText) findViewById(R.id.start_date);
        endDateEditText = (EditText) findViewById(R.id.end_date);

        // Create an ArrayAdapter using a resource array of event types and a default spinner layout
        // so that we can prompt the user to select an event type.
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.add_types_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        typeSpinner.setAdapter(adapter);

        /**
         * Displays a date picker when the user clicks on the start date or end date.
         */
        View.OnClickListener dateClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!(v instanceof EditText)) {
                    return;
                }
                mCurrentEditedDateEditText = (EditText) v;

                // Use the current date as the default date in the picker
                final Calendar c = Calendar.getInstance();
                int year = c.get(Calendar.YEAR);
                int month = c.get(Calendar.MONTH);
                int day = c.get(Calendar.DAY_OF_MONTH);

                new DatePickerDialog(AddEventActivity.this, AddEventActivity.this, year, month, day)
                        .show();
            }
        };

        startDateEditText.setOnClickListener(dateClickListener);
        endDateEditText.setOnClickListener(dateClickListener);

        /**
         * Retrieves the event type selected by the user.
         */
        AdapterView.OnItemSelectedListener typeSelectedListener =
                new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int pos, long id) {
                // Retrieve the selected item.
                mPickedType = (String) parent.getItemAtPosition(pos);
                mPickedTypePos = pos;
                if (pos == 0) {
                    Toast.makeText(AddEventActivity.this, R.string.add_event_missing_type,
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing.
            }
        };

        typeSpinner.setOnItemSelectedListener(typeSelectedListener);

        /**
         * Validates the input fields, using the android-saripaar validation library.
         * Validation is based on annotations on each field, such as @NotEmpty, @Pattern, and @Url.
         * If validation is successful, submits the form.
         * If validation is not successful, displays an error message.
         */
        validator = new Validator(this);
        validator.setValidationListener(new Validator.ValidationListener() {
            @Override
            public void onValidationSucceeded() {
                new SubmitTask().execute();
            }

            @Override
            public void onValidationFailed(List<ValidationError> errors) {
                for (ValidationError error : errors) {
                    View view = error.getView();
                    String message = error.getCollatedErrorMessage(AddEventActivity.this);

                    // Display error messages.
                    if (view instanceof EditText) {
                        ((EditText) view).setError(message);
                    } else {
                        Toast.makeText(AddEventActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        /**
         * Displays a place picker when the user clicks the pick-a-place button.
         */
        (findViewById(R.id.pick_place)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();

                Context context = getApplicationContext();
                try {
                    startActivityForResult(builder.build(context), PLACE_PICKER_REQUEST);
                } catch (GooglePlayServicesRepairableException e) {
                    Log.e(TAG, "An error has occurred", e);
                } catch (GooglePlayServicesNotAvailableException e) {
                    Log.e(TAG, "An error has occurred", e);
                }
            }
        });

        /**
         * Checks that a type and place have been selected, then starts the validation process,
         * when the user clicks the submit button.
         */
        (findViewById(R.id.submit)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPickedTypePos == 0) {
                    Toast.makeText(AddEventActivity.this, R.string.add_event_missing_type,
                            Toast.LENGTH_SHORT).show();
                } else {
                    if (mPickedPlace == null) {
                        Toast.makeText(AddEventActivity.this, R.string.add_event_missing_place,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        validator.validate();
                    }
                }
            }
        });
    }

    /**
     * Retrieves the selected place from the place picker.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLACE_PICKER_REQUEST) {
            if (resultCode == RESULT_OK) {
                mPickedPlace = PlacePicker.getPlace(data, this);
            } else {
                mPickedPlace = null;
            }
            // Display the address of the selected place.
            CharSequence placeText = (mPickedPlace == null)
                    ? getString(R.string.add_event_no_place_selected)
                    : mPickedPlace.getAddress();
            ((TextView) findViewById(R.id.picked_place_address)).setText(placeText);
        }
    }

    /**
     * Retrieves the selected start date or end date from the date picker.
     */
    @Override
    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
        mCurrentEditedDateEditText.setText(String.format("%02d %02d %04d", dayOfMonth,
                monthOfYear + 1, year));
    }

    /**
     * Submits the form data to the spreadsheet which is the data source.
     */
    private class SubmitTask extends AsyncTask<Void, Void, HttpResponse> {

        @Override
        protected HttpResponse doInBackground(Void... params) {
            HttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(FORM_URL);

            String name = ((EditText) findViewById(R.id.name)).getText().toString();
            String description = ((EditText) findViewById(R.id.description)).getText().toString();
            String website = ((EditText) findViewById(R.id.website)).getText().toString();
            String startDate = ((EditText) findViewById(R.id.start_date)).getText().toString();
            String endDate = ((EditText) findViewById(R.id.end_date)).getText().toString();
            String address = mPickedPlace.getAddress().toString();
            String latitude = Double.toString(mPickedPlace.getLatLng().latitude);
            String longitude = Double.toString(mPickedPlace.getLatLng().longitude);

            List<BasicNameValuePair> results = new ArrayList<BasicNameValuePair>();
            results.add(new BasicNameValuePair("entry.149038398", mPickedType));
            results.add(new BasicNameValuePair("entry.313069715", name));
            results.add(new BasicNameValuePair("entry.1612579277", description));
            results.add(new BasicNameValuePair("entry.441807608", website));
            results.add(new BasicNameValuePair("entry.818747834", startDate));
            results.add(new BasicNameValuePair("entry.338417099", endDate));
            results.add(new BasicNameValuePair("entry.1311795500", address));
            results.add(new BasicNameValuePair("entry.1041158190", latitude));
            results.add(new BasicNameValuePair("entry.1989319686", longitude));
            results.add(new BasicNameValuePair("fbzx", "123456789123"));

            try {
                post.setEntity(new UrlEncodedFormEntity(results));
            } catch (UnsupportedEncodingException e) {
                // Auto-generated catch block
                Log.e(TAG, "An error has occurred", e);
            }
            try {
                return client.execute(post);
            } catch (ClientProtocolException e) {
                // Auto-generated catch block
                Log.e(TAG, "An error has occurred", e);
            } catch (IOException e) {
                // Auto-generated catch block
                Log.e(TAG, "An error has occurred", e);
            }
            return null;
        }

        /**
         * Displays a message reporting the success or failure of the form submission.
         */
        @Override
        protected void onPostExecute(HttpResponse response) {
            super.onPostExecute(response);

            if (response == null || response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                Toast.makeText(AddEventActivity.this, R.string.add_event_error,
                        Toast.LENGTH_LONG).show();
                return;
            }
            
            Toast.makeText(AddEventActivity.this, R.string.add_event_success,
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
