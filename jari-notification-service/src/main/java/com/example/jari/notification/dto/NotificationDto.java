package com.example.jari.notification.dto;

import com.example.jari.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NotificationDto extends BaseDto {
    private Long userId;
    private String title;
    private String message;
    private String type;
    private boolean read;
    private String link;
}
