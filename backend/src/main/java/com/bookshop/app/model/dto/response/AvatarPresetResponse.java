package com.bookshop.app.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AvatarPresetResponse {
    private String key;
    private String url;
}
