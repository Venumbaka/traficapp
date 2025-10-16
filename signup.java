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

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class SignUpActivity extends AppCompatActivity {

    private static final String TAG = "SignUpActivity";
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private EditText etName, etEmail, etPassword, etConfirmPassword;
    private Button btnSignUp;
    private Handler handler;
    private Runnable verificationCheckRunnable;
    private BottomSheetDialog verificationDialog;
    private ProgressDialog progressDialog;
    private LayoutInflater inflater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);
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
        mDatabase = FirebaseDatabase.getInstance().getReference("users");
        handler = new Handler(Looper.getMainLooper());
        inflater = getLayoutInflater();

        // Initialize preloader
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Loading...");
        progressDialog.setCancelable(false);

        initializeViews();
    }

    private void initializeViews() {
        etName = findViewById(R.id.nameInput);
        etEmail = findViewById(R.id.emailInput);
        etPassword = findViewById(R.id.passwordInput);
        etConfirmPassword = findViewById(R.id.confirmPasswordInput);
        btnSignUp = findViewById(R.id.signUpButton);
        TextView tvLoginLink = findViewById(R.id.loginLink);

        etName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                etName.setError(null);
            }
        });
        etEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                etEmail.setError(null);
            }
        });
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                etPassword.setError(null);
            }
        });
        etConfirmPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                etConfirmPassword.setError(null);
            }
        });

        btnSignUp.setEnabled(true);

        btnSignUp.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString();
            String confirmPassword = etConfirmPassword.getText().toString();

            // Clear previous errors
            etName.setError(null);
            etEmail.setError(null);
            etPassword.setError(null);
            etConfirmPassword.setError(null);

            boolean hasError = false;
            if (name.isEmpty()) {
                etName.setError("Please enter your name");
                hasError = true;
            }
            if (email.isEmpty()) {
                etEmail.setError("Please enter email");
                hasError = true;
            }
            if (password.isEmpty()) {
                etPassword.setError("Please enter password");
                hasError = true;
            }
            if (confirmPassword.isEmpty()) {
                etConfirmPassword.setError("Please confirm your password");
                hasError = true;
            }
            if (hasError) {
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showCustomToast("Please enter a valid email address");
                return;
            }

            // Strong password validation (min 8, upper, lower, digit, symbol)
            String pwdError = validatePassword(password, confirmPassword);
            if (pwdError != null) {
                showCustomToast(pwdError);
                return;
            }

            // disable button while processing to prevent duplicate clicks
            btnSignUp.setEnabled(false);
            showProgressDialog();

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(createTask -> {
                        hideProgressDialog();
                        if (createTask.isSuccessful()) {
                            FirebaseUser currentUser = mAuth.getCurrentUser();
                            if (currentUser != null) {
                                Log.d(TAG, "createUserWithEmailAndPassword successful");
                                // Save user data to Realtime Database
                                String uid = currentUser.getUid();
                                User user = new User(name, email, uid);
                                mDatabase.child(uid).setValue(user)
                                        .addOnCompleteListener(dbTask -> {
                                            if (dbTask.isSuccessful()) {
                                                Log.d(TAG, "User data saved to Realtime Database");
                                                sendVerificationEmail();
                                            } else {
                                                btnSignUp.setEnabled(true);
                                                showCustomToast("Failed to save user data: " + (dbTask.getException() != null ? dbTask.getException().getMessage() : "Unknown error"));
                                                Log.e(TAG, "Failed to save user data", dbTask.getException());
                                                mAuth.signOut();
                                            }
                                        });
                            } else {
                                btnSignUp.setEnabled(true);
                                showCustomToast("Authentication failed: No user found");
                                Log.e(TAG, "Authentication failed: No user found");
                            }
                        } else {
                            btnSignUp.setEnabled(true);
                            Exception e = createTask.getException();
                            if (e instanceof FirebaseAuthInvalidCredentialsException) {
                                showCustomToast("Invalid email or password");
                                Log.e(TAG, "Invalid credentials", e);
                            } else {
                                showCustomToast("Sign-up failed: " + (e != null ? e.getMessage() : "Unknown error"));
                                Log.e(TAG, "Sign-up failed", e);
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Fallback failure handler
                        hideProgressDialog();
                        btnSignUp.setEnabled(true);
                        showCustomToast("Sign-up failed: " + e.getMessage());
                        Log.e(TAG, "createUserWithEmailAndPassword failure", e);
                    });
        });

        tvLoginLink.setOnClickListener(v -> {
            Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
            overridePendingTransition(R.anim.no_animation, R.anim.fade_out_fast);
            Log.d(TAG, "Navigating to LoginActivity");
        });
    }

    private void showProgressDialog() {
        if (!progressDialog.isShowing()) {
            progressDialog.show();
        }
    }
    private String validatePassword(String password, String confirmPassword) {
        if (password == null || password.isEmpty()) {
            return "Please enter password";
        }
        if (confirmPassword == null || confirmPassword.isEmpty()) {
            return "Please confirm your password";
        }
        if (!password.equals(confirmPassword)) {
            return "Passwords do not match";
        }

        StringBuilder errorBuilder = new StringBuilder();

        if (password.length() < 8) {
            errorBuilder.append("• At least 8 characters long\n");
        }

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;

        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSymbol = true;
        }

        if (!hasUpper) errorBuilder.append("• At least one uppercase letter\n");
        if (!hasLower) errorBuilder.append("• At least one lowercase letter\n");
        if (!hasDigit) errorBuilder.append("• At least one number\n");
        if (!hasSymbol) errorBuilder.append("• At least one special symbol (e.g. !@#$%^&*)\n");

        if (errorBuilder.length() > 0) {
            return "Password must contain:\n" + errorBuilder.toString().trim();
        }

        return null; // valid
    }


    private void hideProgressDialog() {
        if (progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void sendVerificationEmail() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            currentUser.sendEmailVerification()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Verification email sent to: " + currentUser.getEmail());
                            runOnUiThread(this::showVerificationDialog);
                            startVerificationCheck();
                        } else {
                            showCustomToast("Failed to send verification email: " + task.getException().getMessage());
                            Log.e(TAG, "Failed to send verification email", task.getException());
                            btnSignUp.setEnabled(true);
                            mAuth.signOut();
                        }
                    });
        } else {
            showCustomToast("No user signed in");
            Log.e(TAG, "No user signed in for email verification");
            btnSignUp.setEnabled(true);
        }
    }

    private void showVerificationDialog() {
        if (verificationDialog != null && verificationDialog.isShowing()) {
            verificationDialog.dismiss();
        }
        verificationDialog = new BottomSheetDialog(this, R.style.DialogAnimation);
        View dialogView = inflater.inflate(R.layout.custom_verification_dialog, null);
        verificationDialog.setContentView(dialogView);

        verificationDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        Button btnContinue = dialogView.findViewById(R.id.btn_continue);
        btnContinue.setEnabled(false);
        LottieAnimationView lottieAnimation = dialogView.findViewById(R.id.lottie_mail_sent);
        lottieAnimation.playAnimation();

        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from((View) dialogView.getParent());
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setDraggable(true);

        verificationDialog.setOnDismissListener(dialog -> {
            etName.setText("");
            etEmail.setText("");
            etPassword.setText("");
            etConfirmPassword.setText("");
            etName.setError(null);
            etEmail.setError(null);
            etPassword.setError(null);
            etConfirmPassword.setError(null);
            etName.setHint("Name");
            etEmail.setHint("Email");
            etPassword.setHint("Password");
            etConfirmPassword.setHint("Confirm Password");
            etName.setHintTextColor(getResources().getColor(android.R.color.darker_gray));
            etEmail.setHintTextColor(getResources().getColor(android.R.color.darker_gray));
            etPassword.setHintTextColor(getResources().getColor(android.R.color.darker_gray));
            etConfirmPassword.setHintTextColor(getResources().getColor(android.R.color.darker_gray));
            btnSignUp.setText("Sign Up");
            btnSignUp.setEnabled(true);
            if (handler != null && verificationCheckRunnable != null) {
                handler.removeCallbacks(verificationCheckRunnable);
                verificationCheckRunnable = null;
            }
            Log.d(TAG, "Verification dialog dismissed, fields and button reset");
            // Navigate back to LoginActivity on dismiss
            Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
            overridePendingTransition(R.anim.no_animation, R.anim.fade_out_fast);
        });

        btnContinue.setOnClickListener(v -> {
            verificationDialog.dismiss();
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                navigateToPreSetup(currentUser.getEmail(), currentUser.getUid());
            } else {
                showCustomToast("No user signed in");
                Log.e(TAG, "No user signed in after verification");
            }
        });

        verificationDialog.show();
        btnSignUp.setText("Continue");
        btnSignUp.setEnabled(false);
    }

    private void navigateToPreSetup(String email, String uid) {
        Intent intent = new Intent(SignUpActivity.this, MainActivity.class);
        intent.putExtra("email", email);
        intent.putExtra("uid", uid);
        startActivity(intent);
        Log.d(TAG, "Navigating to MainActivity with email: " + email + ", UID: " + uid);
        finish();
        overridePendingTransition(R.anim.no_animation, R.anim.fade_out_fast);
    }

    private void startVerificationCheck() {
        if (verificationCheckRunnable != null) {
            handler.removeCallbacks(verificationCheckRunnable);
            verificationCheckRunnable = null;
        }
        verificationCheckRunnable = new Runnable() {
            @Override
            public void run() {
                FirebaseUser currentUser = mAuth.getCurrentUser();
                if (currentUser != null && verificationDialog != null && verificationDialog.isShowing()) {
                    currentUser.reload().addOnCompleteListener(task -> {
                        if (task.isSuccessful() && currentUser.isEmailVerified()) {
                            runOnUiThread(() -> {
                                View dialogView = verificationDialog.findViewById(android.R.id.content);
                                if (dialogView != null) {
                                    LottieAnimationView lottieAnimation = dialogView.findViewById(R.id.lottie_mail_sent);
                                    if (lottieAnimation != null) {
                                        lottieAnimation.setAnimation(R.raw.success);
                                        lottieAnimation.playAnimation();
                                    }
                                    Button btnContinue = dialogView.findViewById(R.id.btn_continue);
                                    if (btnContinue != null) {
                                        btnContinue.setEnabled(true);
                                    }
                                    btnSignUp.setEnabled(true);
                                    Log.d(TAG, "Email verified for: " + currentUser.getEmail());
                                }
                            });
                        } else {
                            handler.postDelayed(this, 500);
                            Log.d(TAG, "Email not yet verified for: " + (currentUser != null ? currentUser.getEmail() : "null"));
                        }
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to reload user: " + e.getMessage());
                        handler.postDelayed(this, 500);
                    });
                } else {
                    handler.postDelayed(this, 500);
                    Log.w(TAG, "No user or dialog not showing for verification check");
                }
            }
        };
        handler.post(verificationCheckRunnable);
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
        if (handler != null && verificationCheckRunnable != null) {
            handler.removeCallbacks(verificationCheckRunnable);
            verificationCheckRunnable = null;
        }
        if (verificationDialog != null && verificationDialog.isShowing()) {
            verificationDialog.dismiss();
            verificationDialog = null;
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        overridePendingTransition(R.anim.no_animation, R.anim.fade_out_fast);
    }

    // User class for Realtime Database
    public static class User {
        public String name;
        public String email;
        public String uid;

        public User() {
            // Default constructor required for Firebase
        }

        public User(String name, String email, String uid) {
            this.name = name;
            this.email = email;
            this.uid = uid;
        }
    }
}
