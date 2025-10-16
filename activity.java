package com.saveetha.trafficguard;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.LinearInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private FirebaseAuth mAuth;
    private EditText etEmail, etPassword;
    private Button btnLogin;
    private Handler handler;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private GoogleSignInClient googleSignInClient;
    private ProgressDialog progressDialog;
    private LayoutInflater inflater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        // Set light status bar for black icons
        getWindow().getDecorView().setSystemUiVisibility(
                getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );

        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();
        handler = new Handler(Looper.getMainLooper());
        inflater = getLayoutInflater();

        // Initialize preloader
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Loading...");
        progressDialog.setCancelable(false);

        initializeViews();
    }

    // Full initializeViews() — registerForActivityResult uses method reference to onGoogleSignInActivityResult
    // Put this inside your LoginActivity

    private void initializeViews() {
        etEmail = findViewById(R.id.emailInput);
        etPassword = findViewById(R.id.passwordInput);
        btnLogin = findViewById(R.id.loginButton);
        Button btnGoogle = findViewById(R.id.googleLogin);

        TextView tvForgotPassword = findViewById(R.id.forgotPassword);
        TextView tvSignUpLink = findViewById(R.id.signUpLink);

        etEmail.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { etEmail.setError(null); }
        });
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { etPassword.setError(null); }
        });

        btnLogin.setEnabled(true);

        // Google Sign-In configuration - ensure default_web_client_id is correct in strings.xml
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Register launcher and point to the handler below
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onGoogleSignInActivityResult
        );

        // Email/password login (unchanged)
        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            etEmail.setError(null);
            etPassword.setError(null);

            boolean hasError = false;
            if (email.isEmpty()) { etEmail.setError("Please enter email"); hasError = true; }
            if (password.isEmpty()) { etPassword.setError("Please enter password"); hasError = true; }
            if (hasError) return;

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showCustomToast("Please enter a valid email address");
                return;
            }
            if (password.length() < 6) {
                showCustomToast("Password must be at least 6 characters long");
                return;
            }

            showProgressDialog();
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(signInTask -> {
                        hideProgressDialog();
                        if (signInTask.isSuccessful()) {
                            FirebaseUser currentUser = mAuth.getCurrentUser();
                            if (currentUser != null) {
                                if (currentUser.isEmailVerified()) {
                                    Log.d(TAG, "signInWithEmailAndPassword successful, email verified");
                                    navigateToMain(currentUser.getEmail(), currentUser.getUid());
                                } else {
                                    showCustomToast("Please verify your email before logging in");
                                    mAuth.signOut();
                                }
                            } else {
                                showCustomToast("Authentication failed: No user found");
                                Log.e(TAG, "Authentication failed: No user found");
                            }
                        } else {
                            Exception e = signInTask.getException();
                            if (e instanceof FirebaseAuthInvalidCredentialsException) {
                                showCustomToast("Invalid email or password");
                            } else if (e instanceof FirebaseAuthInvalidUserException) {
                                showCustomToast("Email not registered");
                            } else {
                                showCustomToast("Authentication failed: " + (e != null ? e.getMessage() : "unknown"));
                            }
                            Log.e(TAG, "Authentication failed", e);
                        }
                    });
        });

        // Google Sign-In button
        btnGoogle.setOnClickListener(v -> {
            Log.d(TAG, "Google Sign-In button clicked");
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });

        // Forgot password
        tvForgotPassword.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (email.isEmpty()) { showCustomToast("Please enter your email address"); return; }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { showCustomToast("Please enter a valid email address"); return; }
            showProgressDialog();
            mAuth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        hideProgressDialog();
                        if (task.isSuccessful()) {
                            showCustomToast("Password reset email sent");
                            Log.d(TAG, "Password reset email sent to: " + email);
                        } else {
                            showCustomToast("Failed to send reset email: " + (task.getException() != null ? task.getException().getMessage() : "unknown"));
                            Log.e(TAG, "Failed to send password reset email", task.getException());
                        }
                    });
        });

        // Navigate to sign-up
        tvSignUpLink.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignUpActivity.class));
            Log.d(TAG, "Navigating to SignUpActivity");
        });
    }



    // Full onGoogleSignInActivityResult handler — uses fully-qualified GoogleSignInStatusCodes constants,
// so you do not need to import them separately.
    // Handler for the Google Sign-In result. Accepts the ActivityResult from the launcher.
// IMPORTANT: this method will attempt to extract account from the Intent if data != null,
// even when resultCode == RESULT_CANCELED (covers the OEM/device case you hit).
    private void onGoogleSignInActivityResult(androidx.activity.result.ActivityResult result) {
        Intent data = result.getData();
        int resultCode = result.getResultCode();

        Log.d(TAG, "onGoogleSignInActivityResult: resultCode=" + resultCode + ", data=" + (data != null ? "present" : "null"));

        // If no intent data was returned, treat it as a cancellation/failure.
        if (data == null) {
            // explicit cancel
            if (resultCode == RESULT_CANCELED) {
                hideProgressDialog();
                showCustomToast("Google Sign-In cancelled by user");
                Log.w(TAG, "Google Sign-In cancelled by user (no data returned)");
                return;
            } else {
                hideProgressDialog();
                showCustomToast("Google Sign-In failed: no data returned");
                Log.w(TAG, "Google Sign-In failed: resultCode=" + resultCode + ", data is null");
                return;
            }
        }

        // If we do have data, try to extract the account regardless of resultCode
        showProgressDialog();

        try {
            GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);

            if (account != null) {
                String idToken = account.getIdToken();
                if (idToken != null && !idToken.isEmpty()) {
                    Log.d(TAG, "Google Sign-In successful, ID token present");
                    // Continue with Firebase auth (your existing method)
                    firebaseAuthWithGoogle(idToken);
                    return;
                } else {
                    hideProgressDialog();
                    showCustomToast("Google Sign-In failed: missing ID token (check OAuth client / SHA fingerprints)");
                    Log.e(TAG, "Google Sign-In account returned but idToken is null. Check default_web_client_id and SHA settings.");
                    return;
                }
            } else {
                hideProgressDialog();
                showCustomToast("Google Sign-In failed: no account returned");
                Log.e(TAG, "GoogleSignInAccount is null despite data present");
                return;
            }
        } catch (ApiException e) {
            hideProgressDialog();
            int statusCode = e.getStatusCode();
            Log.e(TAG, "Google Sign-In ApiException: StatusCode=" + statusCode + ", Message=" + e.getMessage(), e);

            // Use fully-qualified GoogleSignInStatusCodes to avoid unresolved symbols
            if (statusCode == com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                showCustomToast("Google Sign-In cancelled");
                Log.w(TAG, "GoogleSignInStatusCodes.SIGN_IN_CANCELLED");
            } else if (statusCode == com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.SIGN_IN_FAILED) {
                showCustomToast("Google Sign-In failed");
                Log.w(TAG, "GoogleSignInStatusCodes.SIGN_IN_FAILED");
            } else if (statusCode == com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS) {
                showCustomToast("Google Sign-In already in progress");
                Log.w(TAG, "GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS");
            } else {
                // Helpful fallback — include ApiException message (e.g., 12501 or 12502 are common)
                showCustomToast("Google Sign-In error: " + (e.getMessage() != null ? e.getMessage() : "unknown"));
            }
        } catch (Exception ex) {
            hideProgressDialog();
            showCustomToast("Unexpected error: " + (ex.getMessage() != null ? ex.getMessage() : "unknown"));
            Log.e(TAG, "Unexpected exception handling Google Sign-In result", ex);
        }
    }




    private void showProgressDialog() {
        if (!progressDialog.isShowing()) {
            progressDialog.show();
        }
    }

    private void hideProgressDialog() {
        if (progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    hideProgressDialog();
                    if (task.isSuccessful()) {
                        FirebaseUser currentUser = mAuth.getCurrentUser();
                        if (currentUser != null) {
                            Log.d(TAG, "Firebase auth with Google successful");
                            navigateToMain(currentUser.getEmail(), currentUser.getUid());
                        } else {
                            showCustomToast("Google Sign-In failed: No user found");
                            Log.e(TAG, "Google Sign-In failed: No user found");
                        }
                    } else {
                        showCustomToast("Google Sign-In failed: " + task.getException().getMessage());
                        Log.e(TAG, "Firebase auth with Google failed", task.getException());
                    }
                });
    }

    private void navigateToMain(String email, String uid) {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.putExtra("email", email);
        intent.putExtra("uid", uid);
        startActivity(intent);
        Log.d(TAG, "Navigating to MainActivity with email: " + email + ", UID: " + uid);
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.fade_out_fast);
    }

    private void showCustomToast(String message) {
        View toastView = inflater.inflate(R.layout.custom_toast, null);
        TextView textView = toastView.findViewById(R.id.toast_text);
        textView.setText(message);
        View progressLine = toastView.findViewById(R.id.progress_line);
        progressLine.setScaleX(1f);
        progressLine.setVisibility(View.VISIBLE);

        Toast toast = new Toast(this);
        toast.setGravity(Gravity.BOTTOM | Gravity.END, 16, 50);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(toastView);

        toastView.clearAnimation();
        progressLine.clearAnimation();

        AnimationSet animationSet = new AnimationSet(true);
        animationSet.setInterpolator(new LinearInterpolator());

        TranslateAnimation openAnim = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 1f,
                Animation.RELATIVE_TO_PARENT, 0f,
                Animation.RELATIVE_TO_PARENT, 0f,
                Animation.RELATIVE_TO_PARENT, 0f);
        openAnim.setDuration(100);
        animationSet.addAnimation(openAnim);

        ScaleAnimation progressAnim = new ScaleAnimation(
                1f, 0f, 1f, 1f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        progressAnim.setDuration(1500);
        progressAnim.setInterpolator(new LinearInterpolator());
        progressAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                progressLine.setVisibility(View.GONE);
                toastView.setVisibility(View.GONE);
            }
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        progressLine.startAnimation(progressAnim);

        toastView.startAnimation(animationSet);

        handler.postDelayed(() -> {
            toast.cancel();
            progressLine.clearAnimation();
            toastView.clearAnimation();
        }, 1600);

        toast.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        overridePendingTransition(R.anim.no_animation, R.anim.fade_out_fast);
    }
}
