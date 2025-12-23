package com.securesoft.pay;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.securesoft.pay.internal.ApiClient;
import com.securesoft.pay.internal.ApiService;
import com.securesoft.pay.internal.InitiatePaymentRequestBody;
import com.securesoft.pay.internal.InitiatePaymentResponse;
import com.securesoft.pay.internal.PaymentActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class SecureSoftPay {

    private static SecureSoftPayConfig config;
    private static PaymentResultListener paymentCallback;
    private static final String CALLBACK_SCHEME = "com.securesoft.pay.callback";
    private static final String CALLBACK_HOST = "payment-result";

    private SecureSoftPay() {}

    public static void initialize(SecureSoftPayConfig config) {
        SecureSoftPay.config = config;
    }

    public static void startPayment(@NonNull Context context, @NonNull PaymentRequest request, @NonNull PaymentResultListener callback) {
        if (config == null) {
            callback.onFailure("SDK not initialized. Please call SecureSoftPay.initialize() first.");
            return;
        }
        paymentCallback = callback;
        ApiService apiService = ApiClient.create(config.baseUrl);
        InitiatePaymentRequestBody requestBody = new InitiatePaymentRequestBody(request.amount, config.baseUrl, request.customerName, request.customerEmail, CALLBACK_SCHEME + "://" + CALLBACK_HOST + "/success", CALLBACK_SCHEME + "://" + CALLBACK_HOST + "/cancel");

        apiService.initiatePayment("Bearer " + config.apiKey, requestBody).enqueue(new Callback<InitiatePaymentResponse>() {
            @Override
            public void onResponse(@NonNull Call<InitiatePaymentResponse> call, @NonNull Response<InitiatePaymentResponse> response) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    InitiatePaymentResponse body = response.body();
                    if (response.isSuccessful() && body != null && "success".equals(body.status)) {
                        if (body.paymentUrl != null && !body.paymentUrl.isEmpty()) {
                            // ★★★ মূল পরিবর্তন এখানে ★★★
                            // Chrome Custom Tab-এর পরিবর্তে আমাদের PaymentActivity চালু করা হচ্ছে
                            launchPaymentActivity(context, body.paymentUrl);
                        } else {
                            onPaymentFailure("API did not return a valid payment_url.");
                        }
                    } else {
                        String errorMsg = (body != null && body.message != null) ? body.message : "Failed to initiate payment. Code: " + response.code();
                        onPaymentFailure(errorMsg);
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<InitiatePaymentResponse> call, @NonNull Throwable t) {
                new Handler(Looper.getMainLooper()).post(() -> onPaymentFailure("A network error occurred: " + t.getMessage()));
            }
        });
    }

    public static void onPaymentSuccess(String transactionId) {
        if (paymentCallback != null) {
            paymentCallback.onSuccess(transactionId);
            paymentCallback = null;
        }
    }

    public static void onPaymentFailure(String errorMessage) {
        if (paymentCallback != null) {
            paymentCallback.onFailure(errorMessage);
            paymentCallback = null;
        }
    }

    // ★★★ নতুন মেথড ★★★
    private static void launchPaymentActivity(Context context, String url) {
        try {
            Intent intent = new Intent(context, PaymentActivity.class);
            intent.putExtra(PaymentActivity.EXTRA_URL, url);
            context.startActivity(intent);
        } catch (Exception e) {
            onPaymentFailure("Could not open payment page. Error: " + e.getMessage());
        }
    }
}