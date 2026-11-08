package com.example.clandestinely.ui.signin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

import com.example.clandestinely.MainActivity;
import com.example.clandestinely.R;
import com.example.clandestinely.auth.AuthRepository;
import com.example.clandestinely.data.model.UserProfile;
import com.google.android.gms.tasks.Task;

/**
 * First screen of the app (FR6.1): "Sign in with Google" or "Continue as guest".
 * If the user is already signed in, it goes straight to MainActivity.
 */
public class SignInActivity extends ComponentActivity {

    private final AuthRepository authRepository = new AuthRepository();

    private Button googleButton;
    private Button guestButton;
    private ProgressBar progress;
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_sign_in);
        googleButton = findViewById(R.id.button_google);
        guestButton = findViewById(R.id.button_guest);
        progress = findViewById(R.id.progress);
        errorText = findViewById(R.id.text_error);

        googleButton.setOnClickListener(v -> signIn(authRepository.signInWithGoogle(this)));
        guestButton.setOnClickListener(v -> signIn(authRepository.signInAsGuest()));

        // Signed in from last time: check the profile exists, then skip this screen.
        // If the profile can't be loaded, resumeSession() signs the user out and we stay here.
        if (authRepository.isSignedIn()) {
            signIn(authRepository.resumeSession());
        }
    }

    private void signIn(Task<UserProfile> signInTask) {
        setLoading(true);
        signInTask
                .addOnSuccessListener(this, profile -> openHome())
                .addOnFailureListener(this, e -> {
                    setLoading(false);
                    errorText.setText(AuthRepository.friendlyMessage(e));
                    errorText.setVisibility(View.VISIBLE);
                });
    }

    private void setLoading(boolean loading) {
        googleButton.setEnabled(!loading);
        guestButton.setEnabled(!loading);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) errorText.setVisibility(View.GONE);
    }

    private void openHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
