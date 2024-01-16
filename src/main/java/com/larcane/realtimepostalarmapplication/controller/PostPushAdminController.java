package com.larcane.realtimepostalarmapplication.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.larcane.realtimepostalarmapplication.entity.Post;
import com.larcane.realtimepostalarmapplication.util.JacksonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@Slf4j
@RequiredArgsConstructor
public class PostPushAdminController {
    @Value("${redis.pubsub.channel}")
    String channel;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = JacksonUtil.INSTANCE.getInstance();


    @PostMapping("/post")
    public Long postEvent(String content, Long userId, Long postId) throws JsonProcessingException {
        Post post = Post.builder()
                .postId(postId)
                .userId(userId)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();

        String postString = objectMapper.writeValueAsString(post);
        redisTemplate.convertAndSend(channel, postString);

        return postId;
    }
}
