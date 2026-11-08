package com.example.clandestinely.ui.onboarding;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.example.clandestinely.R;
import com.example.clandestinely.ui.signin.SignInActivity;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * First screen of the app (M6). On the very first launch it shows 3 intro slides, then asks for
 * location and notification permissions. After that it is skipped and goes straight to sign-in.
 */
public class OnboardingActivity extends AppCompatActivity {

    // Same preferences file as ThemeHelper, so all app settings live in one place.
    private static final String PREFS = "settings";
    private static final String KEY_DONE = "onboarding_done";
    private static final String STATE_ON_PERMISSIONS = "on_permissions";

    private ViewPager2 pager;
    private View slidesGroup;
    private View permissionsGroup;
    private Button nextButton;
    private Button locationButton;
    private Button notificationsButton;
    private OnBackPressedCallback backToSlides;

    // Location: either precise or approximate is enough.
    private final ActivityResultLauncher<String[]> locationRequest = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> updatePermissionButtons());

    private final ActivityResultLauncher<String> notificationRequest = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> updatePermissionButtons());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Seen it before: skip straight to sign-in.
        if (isDone(this)) {
            openSignIn();
            return;
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_onboarding);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.onb_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        slidesGroup = findViewById(R.id.group_slides);
        permissionsGroup = findViewById(R.id.group_permissions);
        pager = findViewById(R.id.pager);
        nextButton = findViewById(R.id.button_next);
        locationButton = findViewById(R.id.button_allow_location);
        notificationsButton = findViewById(R.id.button_allow_notifications);

        // Slides + page dots
        OnboardingAdapter adapter = new OnboardingAdapter();
        pager.setAdapter(adapter);
        TabLayout dots = findViewById(R.id.dots);
        new TabLayoutMediator(dots, pager, (tab, position) -> { }).attach();

        nextButton.setOnClickListener(v -> {
            int current = pager.getCurrentItem();
            if (current < adapter.getItemCount() - 1) {
                pager.setCurrentItem(current + 1);
            } else {
                showPermissions(true);
            }
        });
        findViewById(R.id.button_skip).setOnClickListener(v -> showPermissions(true));

        // Permissions page
        locationButton.setOnClickListener(v -> locationRequest.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}));
        notificationsButton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        });
        // Works even if both are denied: the app can still be used without them.
        findViewById(R.id.button_get_started).setOnClickListener(v -> {
            markDone();
            openSignIn();
        });

        // Back on the permissions page goes back to the slides.
        backToSlides = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                showPermissions(false);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backToSlides);

        boolean onPermissions = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_ON_PERMISSIONS);
        showPermissions(onPermissions);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may have changed a permission in phone settings while away.
        if (locationButton != null) updatePermissionButtons();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (permissionsGroup != null) {
            outState.putBoolean(STATE_ON_PERMISSIONS, permissionsGroup.getVisibility() == View.VISIBLE);
        }
    }

    private void showPermissions(boolean show) {
        slidesGroup.setVisibility(show ? View.GONE : View.VISIBLE);
        permissionsGroup.setVisibility(show ? View.VISIBLE : View.GONE);
        backToSlides.setEnabled(show);
        if (show) updatePermissionButtons();
    }

    /** Shows "Allowed ✓" (and disables the button) once a permission is granted. */
    private void updatePermissionButtons() {
        setAllowed(locationButton, hasLocation());
        setAllowed(notificationsButton, hasNotifications());
    }

    private void setAllowed(Button button, boolean allowed) {
        button.setEnabled(!allowed);
        button.setText(allowed ? R.string.onb_allowed : R.string.onb_allow);
    }

    private boolean hasLocation() {
        return isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
                || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private boolean hasNotifications() {
        // Before Android 13 notifications are allowed by default.
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || isGranted(Manifest.permission.POST_NOTIFICATIONS);
    }

    private boolean isGranted(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void markDone() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply();
    }

    /** True once the user has finished onboarding on this phone. */
    public static boolean isDone(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_DONE, false);
    }

    private void openSignIn() {
        startActivity(new Intent(this, SignInActivity.class));
        finish();
    }
}
