package com.example.clandestinely;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.clandestinely.ui.forecast.ForecastFragment;
import com.example.clandestinely.ui.home.HomeFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottom_nav), (v, insets) -> insets);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
        }
        bottomNav.setOnItemReselectedListener(item -> {
            if (item.getItemId() == R.id.nav_forecast) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new ForecastFragment())
                        .commit();
            }
        });
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment fragment;
            if (id == R.id.nav_forecast) {
                fragment = new ForecastFragment();
            } else if (id == R.id.nav_route) {
                fragment = PlaceholderFragment.newInstance("Route");
            } else if (id == R.id.nav_report) {
                fragment = PlaceholderFragment.newInstance("Report");
            } else {
                fragment = PlaceholderFragment.newInstance("Profile");
            }
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            return true;
        });

        // Back: from any tab, return to Home. On Home, Back closes the app as normal.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Fragment current = getSupportFragmentManager()
                        .findFragmentById(R.id.fragment_container);
                if (current instanceof HomeFragment) {
                    setEnabled(false);                          // let the system handle it
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                    return;
                }
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, new HomeFragment())
                        .commit();
                // Reset the highlight to the start state without firing the tab listener,
                // so tapping Forecast from Home still opens it (via the reselect listener).
                bottomNav.getMenu().findItem(R.id.nav_forecast).setChecked(true);
            }
        });
    }
}