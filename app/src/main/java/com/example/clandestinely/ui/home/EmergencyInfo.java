package com.example.clandestinely.ui.home;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.clandestinely.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public class EmergencyInfo {

    //M7: Emergency Info Screen (Static, Offline, Tap To Call)
    public static final String PHONE_UM_SECURITY = "0379677777";
    public static final String PHONE_EMERGENCY_999 = "999";
    public static final String PHONE_AMBULANCE = "0379492898";
    public static final String PHONE_CLINIC = "0379673212";
    public static final String PHONE_BOMBA = "0322824444";

    public interface CallCallback {
        void onCallRequested(String phoneNumber);
    }

    private EmergencyInfo() {}

    public static void setupEmergencyFeatures(Fragment fragment, View rootView, CallCallback callCallback) {
        if (fragment == null || rootView == null) return;

        MaterialButton btnCallSecurity = rootView.findViewById(R.id.btnCallSecurity);
        MaterialButton btnCall999 = rootView.findViewById(R.id.btnCall999);
        MaterialCardView cardAmbulance = rootView.findViewById(R.id.cardAmbulance);
        MaterialCardView cardClinic = rootView.findViewById(R.id.cardClinic);
        MaterialCardView cardBomba = rootView.findViewById(R.id.cardBomba);

        if (btnCallSecurity != null) {
            btnCallSecurity.setOnClickListener(v -> callCallback.onCallRequested(PHONE_UM_SECURITY));
        }
        if (btnCall999 != null) {
            btnCall999.setOnClickListener(v -> callCallback.onCallRequested(PHONE_EMERGENCY_999));
        }
        if (cardAmbulance != null) {
            cardAmbulance.setOnClickListener(v -> callCallback.onCallRequested(PHONE_AMBULANCE));
        }
        if (cardClinic != null) {
            cardClinic.setOnClickListener(v -> callCallback.onCallRequested(PHONE_CLINIC));
        }
        if (cardBomba != null) {
            cardBomba.setOnClickListener(v -> callCallback.onCallRequested(PHONE_BOMBA));
        }

        setupSopToggles(rootView);
    }

    public static void makePhoneCall(Fragment fragment, ActivityResultLauncher<String> permissionLauncher, String phoneNumber) {
        if (fragment == null || !fragment.isAdded()) return;
        Context context = fragment.requireContext();

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CALL_PHONE);
        } else {
            startCallIntent(fragment, phoneNumber);
        }
    }

    public static void startCallIntent(Fragment fragment, String phoneNumber) {
        if (fragment == null || !fragment.isAdded() || phoneNumber == null) return;
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + phoneNumber));
        try {
            fragment.startActivity(callIntent);
        } catch (Exception e) {
            startDialIntent(fragment, phoneNumber);
        }
    }

    public static void startDialIntent(Fragment fragment, String phoneNumber) {
        if (fragment == null || !fragment.isAdded() || phoneNumber == null) return;
        Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneNumber));
        fragment.startActivity(dialIntent);
    }

    private static void setupSopToggles(View rootView) {
        View sopHeaderLightning = rootView.findViewById(R.id.sopHeaderLightning);
        TextView sopBodyLightning = rootView.findViewById(R.id.sopBodyLightning);

        View sopHeaderTrees = rootView.findViewById(R.id.sopHeaderTrees);
        TextView sopBodyTrees = rootView.findViewById(R.id.sopBodyTrees);

        View sopHeaderFlood = rootView.findViewById(R.id.sopHeaderFlood);
        TextView sopBodyFlood = rootView.findViewById(R.id.sopBodyFlood);

        if (sopHeaderLightning != null && sopBodyLightning != null) {
            sopHeaderLightning.setOnClickListener(v -> toggleVisibility(sopBodyLightning));
        }

        if (sopHeaderTrees != null && sopBodyTrees != null) {
            sopHeaderTrees.setOnClickListener(v -> toggleVisibility(sopBodyTrees));
        }

        if (sopHeaderFlood != null && sopBodyFlood != null) {
            sopHeaderFlood.setOnClickListener(v -> toggleVisibility(sopBodyFlood));
        }
    }

    private static void toggleVisibility(View view) {
        if (view.getVisibility() == View.VISIBLE) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
        }
    }
}
