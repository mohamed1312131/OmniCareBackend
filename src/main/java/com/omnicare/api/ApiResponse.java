package com.omnicare.api;

public record ApiResponse<T>(boolean success, String error, String message, T data) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, null, message, data);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, null, data);
    }

    public static <T> ApiResponse<T> failure(String error, String message) {
        return new ApiResponse<>(false, error, message, null);
    }
}
