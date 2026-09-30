package com.bookshop.app.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Vui lòng nhập đầy đủ thông tin")
    private String currentPassword;

    @NotBlank(message = "Vui lòng nhập đầy đủ thông tin")
    private String newPassword;
}
