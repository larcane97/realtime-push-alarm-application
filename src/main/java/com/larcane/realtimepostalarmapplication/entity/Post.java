package com.larcane.realtimepostalarmapplication.entity;

import lombok.*;

import java.time.LocalDateTime;


@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Post {
    Long postId;
    Long userId;
    LocalDateTime createdAt;
    String content;

}
