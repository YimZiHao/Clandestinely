package com.example.clandestinely.ui.report;
import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.clandestinely.R;
import com.example.clandestinely.data.model.Schema;
import com.example.clandestinely.data.repository.HazardRepository;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.GeoPoint;

public class ReportHazardFragment extends Fragment {

    private TextView tvLocation;
    private ImageView imgPhotoPreview;
    private Spinner spinnerCategory;
    private Button btnSubmit;
    private FusedLocationProviderClient fusedClient;
    private HazardRepository repository;

    // Saved here so Submit can send them to Firebase
    private Double latitude = null;
    private Double longitude = null;
    private Uri photoUri = null;

    // Shows the "Allow location?" popup and tells us the answer
    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    fetchLocation();
                } else if (tvLocation != null) {
                    tvLocation.setText(R.string.report_location_denied);
                }
            });

    // Opens the gallery and gives back the picture the user chose
    private final ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null && imgPhotoPreview != null) {
                    photoUri = uri;
                    imgPhotoPreview.setImageURI(uri);
                }
            });

    public static ReportHazardFragment newInstance() {
        return new ReportHazardFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_report_hazard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        fusedClient = LocationServices.getFusedLocationProviderClient(requireContext());
        repository = new HazardRepository();

        tvLocation = view.findViewById(R.id.tvLocation);
        imgPhotoPreview = view.findViewById(R.id.imgPhotoPreview);
        spinnerCategory = view.findViewById(R.id.spinnerCategory);
        btnSubmit = view.findViewById(R.id.btnSubmit);
        Button btnAddPhoto = view.findViewById(R.id.btnAddPhoto);
        Button btnGetLocation = view.findViewById(R.id.btnGetLocation);

        btnAddPhoto.setOnClickListener(v -> photoPickerLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        btnGetLocation.setOnClickListener(v -> {
            boolean granted = ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                fetchLocation();
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        });

        btnSubmit.setOnClickListener(v -> submitReport());
    }

    private void fetchLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        tvLocation.setText(R.string.report_location_fetching);

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (!isAdded()) return;
                    if (location == null) {
                        tvLocation.setText(R.string.report_location_failed);
                        return;
                    }
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    tvLocation.setText(getString(R.string.report_location_result, latitude, longitude));
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) tvLocation.setText(R.string.report_location_failed);
                });
    }

    private void submitReport() {
        // 1. Must be signed in (guests can't report)
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), R.string.report_need_login, Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. Must have a location
        if (latitude == null || longitude == null) {
            Toast.makeText(requireContext(), R.string.report_need_location, Toast.LENGTH_SHORT).show();
            return;
        }

        // 3. Turn the dropdown choice into the Schema value (e.g. "flood")
        String category = Schema.HAZARD_CATEGORIES.get(spinnerCategory.getSelectedItemPosition());
        GeoPoint location = new GeoPoint(latitude, longitude);

        // 4. Send it, and block double taps while it uploads
        btnSubmit.setEnabled(false);
        Toast.makeText(requireContext(), R.string.report_submitting, Toast.LENGTH_SHORT).show();

        repository.submitReport(user.getUid(), category, location, null, null, photoUri)
                .addOnSuccessListener(reportId -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.report_submit_success, Toast.LENGTH_LONG).show();
                    resetForm();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    btnSubmit.setEnabled(true);
                    Toast.makeText(requireContext(),
                            getString(R.string.report_submit_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void resetForm() {
        latitude = null;
        longitude = null;
        photoUri = null;
        tvLocation.setText(R.string.report_location_none);
        imgPhotoPreview.setImageResource(android.R.drawable.ic_menu_gallery);
        spinnerCategory.setSelection(0);
        btnSubmit.setEnabled(true);
    }
}